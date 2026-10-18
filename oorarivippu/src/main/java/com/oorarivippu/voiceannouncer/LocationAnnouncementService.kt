package com.oorarivippu.voiceannouncer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Foreground Location & Voice Announcement Service.
 *
 * Key Responsibilities:
 * 1. Runs continuously in the background while Master ON button is active.
 * 2. Monitors GPS ON/OFF status: if GPS is turned off, stops voice announcements immediately.
 * 3. Detects Bike/Car movement & distance threshold so staying at a routine place produces 0 announcements.
 * 4. Reverse-geocodes coordinates into:
 *    - City / Town / Village (`locality` / `subAdminArea`)
 *    - Same-City Next Place / Neighborhood (`subLocality` / `thoroughfare`)
 * 5. Announces place names in Tamil (default) or English depending on user setting,
 *    respecting the "Same-City Next Place Announcement" ON/OFF toggle.
 */
class LocationAnnouncementService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START_TRACKING = "com.oorarivippu.voiceannouncer.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.oorarivippu.voiceannouncer.action.STOP_TRACKING"
        const val ACTION_TEST_VOICE = "com.oorarivippu.voiceannouncer.action.TEST_VOICE"
        const val ACTION_SETTINGS_CHANGED = "com.oorarivippu.voiceannouncer.action.SETTINGS_CHANGED"

        private const val NOTIFICATION_CHANNEL_ID = "oor_arivippu_location_channel"
        private const val NOTIFICATION_ID = 1001
        private const val GEOCODE_MIN_INTERVAL_MS = 30_000L
        private const val GEOCODE_MIN_DISTANCE_METERS = 100f
        private const val MAX_STALE_GEOCODE_DISTANCE_METERS = 500f
        private const val OFFLINE_BEEP_DURATION_MS = 300
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val defaultTtsVoices = mutableMapOf<String, Voice?>()
    private data class PendingSpeech(
        val text: String,
        val alternateLanguageText: String,
        val requestedLanguage: String,
        val requiresInternet: Boolean,
        val queueMode: Int,
        val fallbackBeepIfVoiceUnavailable: Boolean = false,
        val isOfflineReminder: Boolean = false,
        val requireOnDeviceVoice: Boolean = false
    )
    private var pendingSpeechQueue: PendingSpeech? = null
    private var latestSpeakingRequest: PendingSpeech? = null
    private var speakingRequiresInternet = false
    private var callWaitJob: Job? = null
    private var callModeMonitorJob: Job? = null
    private var offlineReminderJob: Job? = null
    private var scheduledOfflineReminderIntervalMinutes: Int? = null
    private var activeOfflineTone: ToneGenerator? = null
    private var offlineBeepReleaseJob: Job? = null
    private var whereAmIMediaSession: MediaSession? = null
    private var isForegroundStarted = false
    private var lastDestinationDistanceMeters: Float? = null
    private var activeDestinationKey: String? = null
    private var lastAnnouncedDistrict: String = ""
    private var lastAnnouncedHighwayLandmark: String = ""
    private var hasEstablishedPlaceBaseline = false

    private val travelDetector = TravelStateDetector()
    private val adaptivePollingPolicy = AdaptiveLocationPollingPolicy()
    private var activeLocationIntervalMs: Long? = null
    private var gpsReceiver: GpsStateReceiver? = null
    private var internetMonitor: InternetConnectivityMonitor? = null

    // Track previously announced City/Village and Sub-Locality so we never repeat the same place
    private var lastAnnouncedCity: String = ""
    private var lastAnnouncedSubArea: String = ""
    // Towns announced in the last minutes, so a town border does not repeat "A… B… A…".
    private val recentCities = RecentPlaces()
    private var hasMadeInitialStartAnnouncement: Boolean = false
    private var reverseGeocodeJob: Job? = null
    private var lastGeocodedLocation: Location? = null
    private var lastGeocodeStartedAtElapsedMs: Long = 0L
    private var geocodeRequestToken: Long = 0L
    private var latestReceivedLocation: Location? = null

    private val fusedLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocationReceived(location)
        }
    }

    private val fallbackLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onNewLocationReceived(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            handleGpsStateChange(GpsStateReceiver.isGpsProviderEnabled(this@LocationAnnouncementService))
        }
        override fun onProviderDisabled(provider: String) {
            handleGpsStateChange(GpsStateReceiver.isGpsProviderEnabled(this@LocationAnnouncementService))
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        tts = TextToSpeech(this, this)
        internetMonitor = InternetConnectivityMonitor(this) { available ->
            serviceScope.launch { handleInternetAvailability(available) }
        }.also { it.start() }

        createNotificationChannel()
        registerGpsStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingAndService()
                return START_NOT_STICKY
            }
            ACTION_SETTINGS_CHANGED -> {
                if (AppPreferences.state.value.isTrackingEnabled && hasLocationPermission()) {
                    if (!isForegroundStarted) startTrackingInForeground()
                    else updateHeadsetWhereAmIControl()
                    updateOfflineReminderSchedule()
                    return START_STICKY
                }
                return START_NOT_STICKY
            }
            ACTION_TEST_VOICE -> {
                val snap = AppPreferences.state.value
                val testSentence = BilingualStrings.buildTestSpeech(
                    voiceLang = snap.voiceLanguage,
                    currentCity = snap.currentCityOrVillage
                )
                val alternateLanguage = if (snap.voiceLanguage == "en") "ta" else "en"
                val fallbackSentence = BilingualStrings.buildTestSpeech(
                    voiceLang = alternateLanguage,
                    currentCity = snap.currentCityOrVillage
                )
                val selectedVoiceNeedsInternet = snap.availableTtsVoices
                    .firstOrNull { it.name == snap.selectedTtsVoiceNameFor(snap.voiceLanguage) }
                    ?.requiresNetworkConnection == true
                if (selectedVoiceNeedsInternet && snap.isInternetAvailable != true) {
                    AppPreferences.setTtsVoiceStatus(TtsVoiceStatus.NETWORK_REQUIRED)
                    if (!snap.isTrackingEnabled) stopSelf(startId)
                    return if (snap.isTrackingEnabled) START_STICKY else START_NOT_STICKY
                }
                speakAnnouncement(
                    text = testSentence,
                    voiceLang = snap.voiceLanguage,
                    alternateLanguageText = fallbackSentence,
                    requiresInternet = selectedVoiceNeedsInternet
                )
                // A test-voice service should not be resurrected as a tracking service. Keep
                // sticky behavior only when an already-active tracking session needs it.
                return if (snap.isTrackingEnabled) START_STICKY else START_NOT_STICKY
            }
            ACTION_START_TRACKING, null -> {
                // START_STICKY may recreate the service with a null intent. Do not silently
                // turn tracking back on if the saved user setting is OFF.
                if (intent == null && !AppPreferences.state.value.isTrackingEnabled) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                if (!hasLocationPermission()) {
                    AppPreferences.setTrackingEnabled(false)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startTrackingInForeground()
            }
        }
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun startTrackingInForeground() {
        val isGpsOn = GpsStateReceiver.isGpsProviderEnabled(this)
        AppPreferences.updateLiveStatus(isGpsEnabled = isGpsOn)

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
        AppPreferences.setTrackingEnabled(true)
        updateHeadsetWhereAmIControl()

        if (isGpsOn) {
            requestLocationUpdatesSafely()
        } else {
            stopVoiceImmediately()
        }
        updateOfflineReminderSchedule(announceImmediately = true)
    }

    private fun updateHeadsetWhereAmIControl() {
        val enabled = AppPreferences.state.value.isTrackingEnabled &&
            AppPreferences.state.value.headsetWhereAmIEnabled
        if (!enabled) {
            whereAmIMediaSession?.isActive = false
            whereAmIMediaSession?.release()
            whereAmIMediaSession = null
            return
        }
        if (whereAmIMediaSession != null) return

        val session = MediaSession(this, "OorArivippuWhereAmI")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onSkipToNext() {
                speakCurrentPlaceNow()
            }
        }, Handler(Looper.getMainLooper()))
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT)
                .setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build()
        )
        session.isActive = true
        whereAmIMediaSession = session
    }

    private fun speakCurrentPlaceNow() {
        val snapshot = AppPreferences.state.value
        if (!snapshot.isTrackingEnabled || snapshot.isInternetAvailable != true) return
        val location = latestReceivedLocation?.let { Location(it) } ?: return

        serviceScope.launch {
            val current = AppPreferences.state.value
            val resolved = reverseGeocodePlace(location, current.voiceLanguage)
            if (!AppPreferences.state.value.isTrackingEnabled ||
                AppPreferences.state.value.isInternetAvailable != true
            ) return@launch

            val city = resolved?.cityOrVillage?.takeIf { it.isNotBlank() }
                ?: AppPreferences.state.value.currentCityOrVillage
            if (city.isBlank()) return@launch
            val subArea = resolved?.subArea ?: AppPreferences.state.value.currentSubArea
            val locale = if (current.voiceLanguage == "en") Locale("en", "IN") else Locale("ta", "IN")
            val timeText = java.text.SimpleDateFormat("h:mm a", locale).format(java.util.Date())
            val primary = BilingualStrings.buildWhereAmIAnnouncement(
                current.voiceLanguage,
                city,
                subArea,
                timeText
            )
            val alternateLanguage = if (current.voiceLanguage == "en") "ta" else "en"
            val alternate = BilingualStrings.buildWhereAmIAnnouncement(
                alternateLanguage,
                city,
                subArea,
                java.text.SimpleDateFormat(
                    "h:mm a",
                    if (alternateLanguage == "en") Locale("en", "IN") else Locale("ta", "IN")
                ).format(java.util.Date())
            )
            AppPreferences.appendAnnouncementLog("Current place: $city", primary)
            speakAnnouncement(primary, current.voiceLanguage, alternate)
        }
    }

    private fun stopTrackingAndService() {
        AppPreferences.setTrackingEnabled(false)
        updateOfflineReminderSchedule()
        isForegroundStarted = false
        updateHeadsetWhereAmIControl()
        callWaitJob?.cancel()
        callWaitJob = null
        pendingSpeechQueue = null
        AppPreferences.setCallAudioPaused(false)
        lastDestinationDistanceMeters = null
        activeDestinationKey = null
        lastAnnouncedDistrict = ""
        lastAnnouncedHighwayLandmark = ""
        hasEstablishedPlaceBaseline = false
        removeLocationUpdatesSafely()
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = null
        stopVoiceImmediately()
        travelDetector.reset()
        adaptivePollingPolicy.reset()
        AppPreferences.setLocationUpdateIntervalMillis(AdaptiveLocationPollingPolicy.HIGHWAY_INTERVAL_MS)
        lastAnnouncedCity = ""
        lastAnnouncedSubArea = ""
        recentCities.clear()
        hasMadeInitialStartAnnouncement = false
        latestReceivedLocation = null
        lastGeocodedLocation = null
        lastGeocodeStartedAtElapsedMs = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesSafely(intervalMs: Long = AdaptiveLocationPollingPolicy.HIGHWAY_INTERVAL_MS) {
        if (activeLocationIntervalMs == intervalMs) return
        removeLocationUpdatesSafely()
        val safeIntervalMs = intervalMs.coerceAtLeast(5_000L)
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, safeIntervalMs)
                .setMinUpdateIntervalMillis(safeIntervalMs)
                .setMinUpdateDistanceMeters(15f)
                .build()
            fusedLocationClient.requestLocationUpdates(
                request,
                fusedLocationCallback,
                Looper.getMainLooper()
            )
            activeLocationIntervalMs = safeIntervalMs
        } catch (_: Exception) {
            // Fallback to native Android LocationManager if Google Play Services is unavailable.
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    safeIntervalMs,
                    15f,
                    fallbackLocationListener,
                    Looper.getMainLooper()
                )
                activeLocationIntervalMs = safeIntervalMs
            } catch (_: Exception) {}
        }
        AppPreferences.setLocationUpdateIntervalMillis(safeIntervalMs)
    }

    private fun removeLocationUpdatesSafely() {
        try {
            fusedLocationClient.removeLocationUpdates(fusedLocationCallback)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(fallbackLocationListener)
        } catch (_: Exception) {}
        activeLocationIntervalMs = null
    }

    private fun handleGpsStateChange(isGpsEnabled: Boolean) {
        AppPreferences.updateLiveStatus(isGpsEnabled = isGpsEnabled)
        if (!isGpsEnabled) {
            // User turned GPS off: immediately silence announcements, cancel geocoding,
            // and remove high-rate location callbacks while the service stays user-visible.
            stopVoiceImmediately()
            removeLocationUpdatesSafely()
            reverseGeocodeJob?.cancel()
            reverseGeocodeJob = null
            lastGeocodedLocation = null
            lastGeocodeStartedAtElapsedMs = 0L
            latestReceivedLocation = null
            travelDetector.reset()
            adaptivePollingPolicy.reset()
            AppPreferences.setLocationUpdateIntervalMillis(AdaptiveLocationPollingPolicy.HIGHWAY_INTERVAL_MS)
        } else if (AppPreferences.state.value.isTrackingEnabled) {
            lastGeocodedLocation = null
            lastGeocodeStartedAtElapsedMs = 0L
            requestLocationUpdatesSafely()
        }
        updateForegroundNotification()
    }

    private fun handleInternetAvailability(available: Boolean) {
        val wasAvailable = AppPreferences.state.value.isInternetAvailable
        AppPreferences.setInternetAvailable(available)
        if (wasAvailable == available) return

        if (!available) {
            // Do not continue an in-flight lookup or speak stale place information offline.
            geocodeRequestToken++
            reverseGeocodeJob?.cancel()
            reverseGeocodeJob = null
            if (pendingSpeechQueue?.requiresInternet == true) pendingSpeechQueue = null
            if (speakingRequiresInternet) {
                callModeMonitorJob?.cancel()
                callModeMonitorJob = null
                latestSpeakingRequest = null
                tts?.stop()
                speakingRequiresInternet = false
            }
        } else if (AppPreferences.state.value.isTrackingEnabled) {
            lastGeocodedLocation = null
            lastGeocodeStartedAtElapsedMs = 0L
            latestReceivedLocation?.let(::onNewLocationReceived)
        }
        updateOfflineReminderSchedule(announceImmediately = !available)
        updateForegroundNotification()
    }

    private fun updateOfflineReminderSchedule(announceImmediately: Boolean = false) {
        val snapshot = AppPreferences.state.value
        val intervalMinutes = snapshot.offlineReminderIntervalMinutes
        if (!OfflineReminderPolicy.shouldSchedule(
                trackingEnabled = snapshot.isTrackingEnabled,
                internetAvailable = snapshot.isInternetAvailable,
                intervalMinutes = intervalMinutes
            )
        ) {
            offlineReminderJob?.cancel()
            offlineReminderJob = null
            scheduledOfflineReminderIntervalMinutes = null
            clearOfflineReminderSpeech(stopCurrent = true)
            stopOfflineBeep()
            return
        }

        if (offlineReminderJob?.isActive == true &&
            scheduledOfflineReminderIntervalMinutes == intervalMinutes
        ) return

        offlineReminderJob?.cancel()
        clearOfflineReminderSpeech(stopCurrent = true)
        scheduledOfflineReminderIntervalMinutes = intervalMinutes
        offlineReminderJob = serviceScope.launch {
            if (announceImmediately) announceOfflineReminder()
            while (true) {
                kotlinx.coroutines.delay(intervalMinutes * 60_000L)
                val current = AppPreferences.state.value
                if (!OfflineReminderPolicy.shouldSchedule(
                        trackingEnabled = current.isTrackingEnabled,
                        internetAvailable = current.isInternetAvailable,
                        intervalMinutes = current.offlineReminderIntervalMinutes
                    ) || current.offlineReminderIntervalMinutes != intervalMinutes
                ) break
                announceOfflineReminder()
            }
        }
    }

    private fun announceOfflineReminder() {
        val snapshot = AppPreferences.state.value
        if (!OfflineReminderPolicy.shouldSchedule(
                trackingEnabled = snapshot.isTrackingEnabled,
                internetAvailable = snapshot.isInternetAvailable,
                intervalMinutes = snapshot.offlineReminderIntervalMinutes
            )
        ) return

        val requestedLanguage = if (snapshot.voiceLanguage == "en") "en" else "ta"
        val alternateLanguage = if (requestedLanguage == "en") "ta" else "en"
        val text = BilingualStrings.buildOfflineReminderAnnouncement(requestedLanguage)
        val alternateText = BilingualStrings.buildOfflineReminderAnnouncement(alternateLanguage)
        speakAnnouncement(
            text = text,
            voiceLang = requestedLanguage,
            alternateLanguageText = alternateText,
            requiresInternet = false,
            fallbackBeepIfVoiceUnavailable = true,
            isOfflineReminder = true,
            requireOnDeviceVoice = true
        )
    }

    private fun clearOfflineReminderSpeech(stopCurrent: Boolean) {
        if (pendingSpeechQueue?.isOfflineReminder == true) {
            pendingSpeechQueue = null
            if (callWaitJob?.isActive == true) {
                callWaitJob?.cancel()
                callWaitJob = null
                AppPreferences.setCallAudioPaused(false)
            }
        }
        if (stopCurrent && latestSpeakingRequest?.isOfflineReminder == true) {
            callModeMonitorJob?.cancel()
            callModeMonitorJob = null
            latestSpeakingRequest = null
            speakingRequiresInternet = false
            try {
                tts?.stop()
            } catch (_: Exception) {}
            if (pendingSpeechQueue == null && callWaitJob == null) {
                AppPreferences.setCallAudioPaused(false)
            }
        }
    }

    private fun shouldSendOfflineReminder(): Boolean {
        val snapshot = AppPreferences.state.value
        return OfflineReminderPolicy.shouldSchedule(
            trackingEnabled = snapshot.isTrackingEnabled,
            internetAvailable = snapshot.isInternetAvailable,
            intervalMinutes = snapshot.offlineReminderIntervalMinutes
        )
    }

    private fun playOfflineBeepIfRelevant(speech: PendingSpeech) {
        if (speech.fallbackBeepIfVoiceUnavailable && speech.isOfflineReminder &&
            shouldSendOfflineReminder()
        ) playOfflineBeep()
    }

    private fun playOfflineBeep() {
        if (!shouldSendOfflineReminder() || isPhoneCallAudioActive()) return
        stopOfflineBeep()
        val tone = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        }.getOrNull() ?: return
        activeOfflineTone = tone
        if (!tone.startTone(ToneGenerator.TONE_PROP_BEEP, OFFLINE_BEEP_DURATION_MS)) {
            activeOfflineTone = null
            tone.release()
            return
        }
        offlineBeepReleaseJob = serviceScope.launch {
            kotlinx.coroutines.delay(600L)
            if (activeOfflineTone === tone) {
                activeOfflineTone = null
                runCatching { tone.stopTone() }
                runCatching { tone.release() }
            }
        }
    }

    private fun stopOfflineBeep() {
        offlineBeepReleaseJob?.cancel()
        offlineBeepReleaseJob = null
        activeOfflineTone?.let { tone ->
            runCatching { tone.stopTone() }
            runCatching { tone.release() }
        }
        activeOfflineTone = null
    }

    private fun onNewLocationReceived(location: Location) {
        val snap = AppPreferences.state.value
        if (!snap.isTrackingEnabled) return

        // Verify GPS is still turned ON; if turned OFF, make no announcement.
        if (!GpsStateReceiver.isGpsProviderEnabled(this)) {
            handleGpsStateChange(false)
            return
        }

        latestReceivedLocation = Location(location)
        val motion = travelDetector.evaluate(
            newLocation = location,
            minDistanceMeters = snap.minDistanceMeters,
            bikeCarDetectionEnabled = snap.bikeCarDetectionEnabled
        )
        AppPreferences.updateLiveStatus(
            isGpsEnabled = true,
            speedKmh = motion.currentSpeedKmh,
            isVehicleMoving = motion.isTravelingByVehicleOrMovedFar
        )
        val adaptiveInterval = adaptivePollingPolicy.intervalFor(
            speedKmh = motion.currentSpeedKmh,
            movementDetected = motion.isTravelingByVehicleOrMovedFar
        )
        if (activeLocationIntervalMs != adaptiveInterval) {
            requestLocationUpdatesSafely(adaptiveInterval)
        }

        processDestinationProximity(location)
        if (AppPreferences.state.value.isInternetAvailable != true) {
            updateForegroundNotification()
            return
        }
        if (!shouldStartReverseGeocode(location)) return

        val requestToken = ++geocodeRequestToken
        reverseGeocodeJob = serviceScope.launch {
            try {
                val resolved = reverseGeocodePlace(location, snap.voiceLanguage) ?: return@launch
                val currentAfterLookup = AppPreferences.state.value
                if (!currentAfterLookup.isTrackingEnabled ||
                    currentAfterLookup.isInternetAvailable != true ||
                    requestToken != geocodeRequestToken
                ) return@launch

                // Do not speak a place result if the device has already travelled far from
                // the location that was being resolved; the next throttled request will catch up.
                val latestLocation = latestReceivedLocation
                if (latestLocation != null &&
                    location.distanceTo(latestLocation) > MAX_STALE_GEOCODE_DISTANCE_METERS
                ) return@launch

                val cityOrVillage = resolved.cityOrVillage
                val subArea = resolved.subArea
                val current = AppPreferences.state.value
                AppPreferences.updateLiveStatus(
                    isGpsEnabled = true,
                    cityOrVillage = cityOrVillage,
                    subArea = subArea,
                    district = resolved.district
                )
                updateForegroundNotification()
                if (cityOrVillage.isBlank()) return@launch

                val isFirstStartAnnouncement =
                    !hasMadeInitialStartAnnouncement && current.announceImmediatelyOnStart
                if (isFirstStartAnnouncement) {
                    // Honor the explicit startup option even with a 0 km/h fix or when the
                    // resolved place matches a value retained by a prior service session.
                    announceInitialPlace(
                        location = location,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        voiceLanguage = current.voiceLanguage,
                        includeSubArea = current.announceSameCitySubArea,
                        district = resolved.district,
                        highwayLandmark = resolved.highwayLandmark.orEmpty()
                    )
                    return@launch
                }

                val firstPlaceFix = !hasEstablishedPlaceBaseline
                if (firstPlaceFix) {
                    lastAnnouncedDistrict = resolved.district
                    lastAnnouncedHighwayLandmark = resolved.highwayLandmark.orEmpty()
                    hasEstablishedPlaceBaseline = true
                }

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val differsFromLastCity = !PlaceDataPolicy.samePlaceName(cityOrVillage, lastAnnouncedCity)
                // Near a town border the geocoder can alternate between two neighbouring towns:
                // a town announced in the last few minutes is not announced again.
                val backInRecentCity = differsFromLastCity &&
                    recentCities.wasAnnouncedRecently(cityOrVillage, nowElapsedMs)
                if (backInRecentCity) {
                    lastAnnouncedCity = cityOrVillage
                    lastAnnouncedSubArea = subArea
                }
                val isNewCityOrVillage = differsFromLastCity && !backInRecentCity
                val isNewSubAreaInSameCity = !isNewCityOrVillage &&
                    subArea.isNotBlank() &&
                    !PlaceDataPolicy.samePlaceName(subArea, cityOrVillage) &&
                    !PlaceDataPolicy.samePlaceName(subArea, lastAnnouncedSubArea)
                val districtChanged = !firstPlaceFix &&
                    resolved.district.isNotBlank() &&
                    !PlaceDataPolicy.samePlaceName(resolved.district, lastAnnouncedDistrict)
                val landmark = resolved.highwayLandmark.orEmpty()
                val landmarkChanged = !firstPlaceFix &&
                    landmark.isNotBlank() &&
                    !PlaceDataPolicy.samePlaceName(landmark, lastAnnouncedHighwayLandmark)
                if (!current.isVehicleMoving) return@launch

                val alternateLanguage = if (current.voiceLanguage == "en") "ta" else "en"
                val primaryParts = mutableListOf<String>()
                val alternateParts = mutableListOf<String>()
                var logTitle = cityOrVillage

                if (isNewCityOrVillage) {
                    primaryParts += BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = current.voiceLanguage,
                        isNewCityOrVillage = true,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = current.announceSameCitySubArea
                    )
                    alternateParts += BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = alternateLanguage,
                        isNewCityOrVillage = true,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = current.announceSameCitySubArea
                    )
                    lastAnnouncedCity = cityOrVillage
                    lastAnnouncedSubArea = subArea
                    recentCities.remember(cityOrVillage, nowElapsedMs)
                    logTitle = if (subArea.isNotBlank() &&
                        !PlaceDataPolicy.samePlaceName(subArea, cityOrVillage)
                    ) "$cityOrVillage ($subArea)" else cityOrVillage
                } else if (isNewSubAreaInSameCity && current.announceSameCitySubArea) {
                    primaryParts += BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = current.voiceLanguage,
                        isNewCityOrVillage = false,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = true
                    )
                    alternateParts += BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = alternateLanguage,
                        isNewCityOrVillage = false,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = true
                    )
                    lastAnnouncedSubArea = subArea
                    logTitle = "$subArea, $cityOrVillage"
                }

                if (districtChanged) {
                    primaryParts += BilingualStrings.buildDistrictAnnouncement(
                        current.voiceLanguage,
                        resolved.district
                    )
                    alternateParts += BilingualStrings.buildDistrictAnnouncement(
                        alternateLanguage,
                        resolved.district
                    )
                    lastAnnouncedDistrict = resolved.district
                    logTitle = "${resolved.district} district"
                }
                if (landmarkChanged) {
                    primaryParts += BilingualStrings.buildHighwayLandmarkAnnouncement(
                        current.voiceLanguage,
                        landmark
                    )
                    alternateParts += BilingualStrings.buildHighwayLandmarkAnnouncement(
                        alternateLanguage,
                        landmark
                    )
                    lastAnnouncedHighwayLandmark = landmark
                    logTitle = landmark
                }

                if (primaryParts.isNotEmpty()) {
                    hasMadeInitialStartAnnouncement = true
                    travelDetector.markPlaceAnnounced(location)
                    val primary = primaryParts.joinToString(" ")
                    val alternate = alternateParts.joinToString(" ")
                    AppPreferences.appendAnnouncementLog(logTitle, primary)
                    speakAnnouncement(primary, current.voiceLanguage, alternate)
                }
            } finally {
                if (requestToken == geocodeRequestToken) reverseGeocodeJob = null
            }
        }
    }

    private fun processDestinationProximity(location: Location) {
        val snapshot = AppPreferences.state.value
        val name = snapshot.destinationName
        val latitude = snapshot.destinationLatitude
        val longitude = snapshot.destinationLongitude
        if (name.isBlank() || latitude == null || longitude == null) {
            activeDestinationKey = null
            lastDestinationDistanceMeters = null
            return
        }

        val destinationKey = "$name|$latitude|$longitude"
        if (activeDestinationKey != destinationKey) {
            activeDestinationKey = destinationKey
            lastDestinationDistanceMeters = null
        }
        val destination = Location("saved-destination").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        val distance = location.distanceTo(destination)
        val result = DestinationProximityPolicy.evaluate(
            currentDistanceMeters = distance,
            previousDistanceMeters = lastDestinationDistanceMeters,
            announcedMask = snapshot.destinationAlertMask
        )
        lastDestinationDistanceMeters = distance
        if (result.updatedMask != snapshot.destinationAlertMask) {
            AppPreferences.setDestinationAlertMask(result.updatedMask)
        }
        val alertDistance = result.alertDistanceMeters ?: return
        if (snapshot.isInternetAvailable != true) return

        val arrived = alertDistance == DestinationProximityPolicy.ARRIVAL_RADIUS_METERS.toInt()
        val primary = BilingualStrings.buildDestinationAnnouncement(
            voiceLang = snapshot.voiceLanguage,
            destinationName = name,
            distanceMeters = alertDistance,
            arrived = arrived
        )
        val alternateLanguage = if (snapshot.voiceLanguage == "en") "ta" else "en"
        val alternate = BilingualStrings.buildDestinationAnnouncement(
            voiceLang = alternateLanguage,
            destinationName = name,
            distanceMeters = alertDistance,
            arrived = arrived
        )
        AppPreferences.appendAnnouncementLog(name, primary)
        speakAnnouncement(
            text = primary,
            voiceLang = snapshot.voiceLanguage,
            alternateLanguageText = alternate,
            queueMode = TextToSpeech.QUEUE_ADD
        )
    }

    private fun announceInitialPlace(
        location: Location,
        cityOrVillage: String,
        subArea: String,
        voiceLanguage: String,
        includeSubArea: Boolean,
        district: String,
        highwayLandmark: String
    ) {
        val primary = BilingualStrings.buildSpokenAnnouncement(
            voiceLang = voiceLanguage,
            isNewCityOrVillage = true,
            cityOrVillage = cityOrVillage,
            subArea = subArea,
            includeSubArea = includeSubArea
        )
        val alternateLanguage = if (voiceLanguage == "en") "ta" else "en"
        val alternate = BilingualStrings.buildSpokenAnnouncement(
            voiceLang = alternateLanguage,
            isNewCityOrVillage = true,
            cityOrVillage = cityOrVillage,
            subArea = subArea,
            includeSubArea = includeSubArea
        )
        lastAnnouncedCity = cityOrVillage
        lastAnnouncedSubArea = subArea
        recentCities.remember(cityOrVillage, SystemClock.elapsedRealtime())
        lastAnnouncedDistrict = district
        lastAnnouncedHighwayLandmark = highwayLandmark
        hasEstablishedPlaceBaseline = true
        hasMadeInitialStartAnnouncement = true
        travelDetector.markPlaceAnnounced(location)
        val displayTitle = if (subArea.isNotBlank() &&
            !PlaceDataPolicy.samePlaceName(subArea, cityOrVillage)
        ) "$cityOrVillage ($subArea)" else cityOrVillage
        AppPreferences.appendAnnouncementLog(displayTitle, primary)
        speakAnnouncement(primary, voiceLanguage, alternate)
    }

    private fun shouldStartReverseGeocode(location: Location): Boolean {
        if (!Geocoder.isPresent() || reverseGeocodeJob?.isActive == true) return false

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val previousRequestLocation = lastGeocodedLocation
        if (previousRequestLocation != null) {
            val elapsedMs = nowElapsedMs - lastGeocodeStartedAtElapsedMs
            val distanceMeters = previousRequestLocation.distanceTo(location)
            if (elapsedMs < GEOCODE_MIN_INTERVAL_MS || distanceMeters < GEOCODE_MIN_DISTANCE_METERS) {
                return false
            }
        }

        lastGeocodedLocation = Location(location)
        lastGeocodeStartedAtElapsedMs = nowElapsedMs
        return true
    }

    data class ResolvedPlace(
        val cityOrVillage: String,
        val subArea: String,
        val district: String,
        val highwayLandmark: String?
    )

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodePlace(location: Location, voiceLang: String): ResolvedPlace? {
        if (!Geocoder.isPresent()) return null
        val locale = if (voiceLang == "en") Locale("en", "IN") else Locale("ta", "IN")
        val geocoder = Geocoder(this, locale)

        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }
        }

        address ?: return null

        // Extract Village / Town / City name reliably across rural and urban India
        val cityOrVillage = PlaceDataPolicy.cleanCityOrVillage(
            // The state (adminArea) is never announced as a town.
            listOf(address.locality, address.subAdminArea)
        )

        // Prefer a geocoder's named feature (e.g. a hospital or bus stand) to its road field.
        // Plus Codes and long route/address chains are filtered before they can be announced.
        val rawSubArea = PlaceDataPolicy.cleanSubArea(
            candidates = listOf(address.featureName, address.subLocality, address.thoroughfare),
            cityOrVillage = cityOrVillage
        )
        val district = PlaceDataPolicy.cleanPlaceName(address.subAdminArea)
            ?.takeUnless { PlaceDataPolicy.samePlaceName(it, cityOrVillage) }
            .orEmpty()
        val highwayLandmark = PlaceDataPolicy.highwayLandmark(
            listOf(address.thoroughfare, address.featureName, address.subLocality)
        )

        return ResolvedPlace(
            cityOrVillage = cityOrVillage,
            subArea = rawSubArea,
            district = district,
            highwayLandmark = highwayLandmark
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            configureTtsLanguage(AppPreferences.state.value.voiceLanguage)
            pendingSpeechQueue?.let { queued ->
                pendingSpeechQueue = null
                speakAnnouncement(
                    text = queued.text,
                    voiceLang = queued.requestedLanguage,
                    alternateLanguageText = queued.alternateLanguageText,
                    requiresInternet = queued.requiresInternet,
                    queueMode = queued.queueMode,
                    fallbackBeepIfVoiceUnavailable = queued.fallbackBeepIfVoiceUnavailable,
                    isOfflineReminder = queued.isOfflineReminder,
                    requireOnDeviceVoice = queued.requireOnDeviceVoice
                )
            }
        } else {
            isTtsReady = false
            val queued = pendingSpeechQueue
            pendingSpeechQueue = null
            queued?.let(::playOfflineBeepIfRelevant)
            AppPreferences.setCallAudioPaused(false)
            AppPreferences.setTtsVoiceStatus(TtsVoiceStatus.UNAVAILABLE)
            if (!AppPreferences.state.value.isTrackingEnabled) {
                // A one-shot test-voice service has nothing else to do if the TTS engine failed.
                stopSelf()
            }
        }
    }

    /**
     * Checks regional and generic voices before use. If the selected language is missing,
     * try the other supported app language and tell the UI that a fallback is active.
     */
    private fun configureTtsLanguage(
        voiceLang: String,
        requireOnDeviceVoice: Boolean = false
    ): String? {
        val engine = tts
        if (engine == null || !isTtsReady) {
            AppPreferences.setTtsVoiceStatus(TtsVoiceStatus.UNAVAILABLE)
            return null
        }

        val requestedLanguage = if (voiceLang == "en") "en" else "ta"
        val candidates = if (requestedLanguage == "en") {
            listOf(
                Locale("en", "IN") to "en",
                Locale.ENGLISH to "en",
                Locale("ta", "IN") to "ta",
                Locale("ta") to "ta"
            )
        } else {
            listOf(
                Locale("ta", "IN") to "ta",
                Locale("ta") to "ta",
                Locale("en", "IN") to "en",
                Locale.ENGLISH to "en"
            )
        }

        val availableVoices = engine.voices.orEmpty()
        for ((locale, languageCode) in candidates) {
            try {
                if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) continue
                if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) continue

                val languageVoices = availableVoices.filter { it.locale.language == locale.language }
                val defaultVoice = defaultTtsVoices.getOrPut(languageCode) {
                    engine.voice?.takeIf { it.locale.language == locale.language }
                        ?: languageVoices.firstOrNull()
                }
                val selectedVoiceName = AppPreferences.state.value
                    .selectedTtsVoiceNameFor(languageCode)
                val selectedVoice = languageVoices.firstOrNull { it.name == selectedVoiceName }
                val voiceToUse = if (requireOnDeviceVoice) {
                    selectedVoice?.takeIf { !it.isNetworkConnectionRequired }
                        ?: defaultVoice?.takeIf { !it.isNetworkConnectionRequired }
                        ?: languageVoices.firstOrNull { !it.isNetworkConnectionRequired }
                } else {
                    selectedVoice ?: defaultVoice
                }
                if (requireOnDeviceVoice && voiceToUse == null) continue
                if (voiceToUse != null) engine.voice = voiceToUse
                if (requireOnDeviceVoice && engine.voice?.isNetworkConnectionRequired != false) continue

                engine.setSpeechRate(SpeechRatePolicy.androidRate(AppPreferences.state.value.speechRatePercent))
                val voiceStatus = when {
                    languageCode != requestedLanguage -> TtsVoiceStatus.FALLBACK_LANGUAGE
                    !requireOnDeviceVoice && selectedVoiceName.isNotBlank() && selectedVoice == null ->
                        TtsVoiceStatus.SELECTED_VOICE_UNAVAILABLE
                    else -> TtsVoiceStatus.AVAILABLE
                }
                AppPreferences.setTtsVoiceStatus(voiceStatus)
                return languageCode
            } catch (_: Exception) {
                // Continue to the next installed locale rather than failing the service.
            }
        }

        AppPreferences.setTtsVoiceStatus(TtsVoiceStatus.UNAVAILABLE)
        return null
    }

    private fun speakAnnouncement(
        text: String,
        voiceLang: String,
        alternateLanguageText: String,
        requiresInternet: Boolean = true,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        fallbackBeepIfVoiceUnavailable: Boolean = false,
        isOfflineReminder: Boolean = false,
        requireOnDeviceVoice: Boolean = false
    ) {
        val snapshot = AppPreferences.state.value
        if (requiresInternet && snapshot.isInternetAvailable != true) return
        // Place alerts need GPS; an explicit offline-status reminder does not.
        if (!isOfflineReminder && !GpsStateReceiver.isGpsProviderEnabled(this) &&
            snapshot.isTrackingEnabled
        ) return

        val requestedLanguage = if (voiceLang == "en") "en" else "ta"
        val speech = PendingSpeech(
            text = text,
            alternateLanguageText = alternateLanguageText,
            requestedLanguage = requestedLanguage,
            requiresInternet = requiresInternet,
            queueMode = queueMode,
            fallbackBeepIfVoiceUnavailable = fallbackBeepIfVoiceUnavailable,
            isOfflineReminder = isOfflineReminder,
            requireOnDeviceVoice = requireOnDeviceVoice
        )
        if (isPhoneCallAudioActive()) {
            pendingSpeechQueue = speech
            AppPreferences.setCallAudioPaused(true)
            updateForegroundNotification()
            waitForPhoneCallToEnd()
            return
        }

        val engine = tts
        if (!isTtsReady || engine == null) {
            pendingSpeechQueue = speech
            return
        }

        val actualLanguage = configureTtsLanguage(requestedLanguage, requireOnDeviceVoice)
        if (actualLanguage == null) {
            playOfflineBeepIfRelevant(speech)
            if (!snapshot.isTrackingEnabled) stopSelf()
            return
        }
        val localizedText = if (actualLanguage == requestedLanguage) text else alternateLanguageText
        speakingRequiresInternet = requiresInternet
        latestSpeakingRequest = speech
        requestAudioFocusAndSpeak(engine, localizedText, queueMode, speech)
        monitorCallModeDuringSpeech(engine)
    }

    private fun isPhoneCallAudioActive(): Boolean {
        val mode = (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                mode == AudioManager.MODE_CALL_SCREENING)
    }

    private fun waitForPhoneCallToEnd() {
        if (callWaitJob?.isActive == true) return
        callWaitJob = serviceScope.launch {
            while (pendingSpeechQueue != null && isPhoneCallAudioActive()) {
                kotlinx.coroutines.delay(1_000L)
            }
            val queued = pendingSpeechQueue
            callWaitJob = null
            if (queued == null) {
                AppPreferences.setCallAudioPaused(false)
                updateForegroundNotification()
                return@launch
            }
            pendingSpeechQueue = null
            AppPreferences.setCallAudioPaused(false)
            updateForegroundNotification()
            if (!queued.requiresInternet || AppPreferences.state.value.isInternetAvailable == true) {
                speakAnnouncement(
                    text = queued.text,
                    voiceLang = queued.requestedLanguage,
                    alternateLanguageText = queued.alternateLanguageText,
                    requiresInternet = queued.requiresInternet,
                    queueMode = queued.queueMode,
                    fallbackBeepIfVoiceUnavailable = queued.fallbackBeepIfVoiceUnavailable,
                    isOfflineReminder = queued.isOfflineReminder,
                    requireOnDeviceVoice = queued.requireOnDeviceVoice
                )
            }
        }
    }

    private fun requestAudioFocusAndSpeak(
        engine: TextToSpeech,
        text: String,
        queueMode: Int,
        speech: PendingSpeech
    ) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        var focusRequest: AudioFocusRequest? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener {}
                .build()
            audioManager.requestAudioFocus(focusRequest)
        }

        engine.setAudioAttributes(audioAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
                if (!AppPreferences.state.value.isTrackingEnabled) stopSelf()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
                playOfflineBeepIfRelevant(speech)
                if (!AppPreferences.state.value.isTrackingEnabled) stopSelf()
            }
        })

        engine.speak(text, queueMode, null, "place_announce_${System.currentTimeMillis()}")
    }

    private fun monitorCallModeDuringSpeech(engine: TextToSpeech) {
        if (callModeMonitorJob?.isActive == true) return
        callModeMonitorJob = serviceScope.launch {
            var idleChecks = 0
            while (idleChecks < 5) {
                kotlinx.coroutines.delay(300L)
                if (engine.isSpeaking()) idleChecks = 0 else idleChecks++
                if (isPhoneCallAudioActive()) {
                    if (pendingSpeechQueue == null) {
                        pendingSpeechQueue = latestSpeakingRequest
                    }
                    if (pendingSpeechQueue != null) {
                        AppPreferences.setCallAudioPaused(true)
                        updateForegroundNotification()
                        try {
                            engine.stop()
                        } catch (_: Exception) {}
                        waitForPhoneCallToEnd()
                    }
                    break
                }
            }
            callModeMonitorJob = null
        }
    }

    private fun stopVoiceImmediately() {
        pendingSpeechQueue = null
        latestSpeakingRequest = null
        speakingRequiresInternet = false
        callModeMonitorJob?.cancel()
        callModeMonitorJob = null
        callWaitJob?.cancel()
        callWaitJob = null
        AppPreferences.setCallAudioPaused(false)
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    private fun registerGpsStateReceiver() {
        val receiver = GpsStateReceiver { isEnabled ->
            handleGpsStateChange(isEnabled)
        }
        gpsReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ui = BilingualStrings.forLang(AppPreferences.state.value.appLanguage)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                ui.notificationChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ui.appSubtitle
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val snap = AppPreferences.state.value
        val ui = BilingualStrings.forLang(snap.appLanguage)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationAnnouncementService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            !snap.isGpsEnabled -> ui.statusGpsOff
            snap.isInternetAvailable == false -> ui.statusInternetUnavailable
            snap.isCallAudioPaused -> ui.statusCallPaused
            snap.currentCityOrVillage.isNotBlank() -> {
                val sub = if (snap.currentSubArea.isNotBlank()) " • ${snap.currentSubArea}" else ""
                "${snap.currentCityOrVillage}$sub (${snap.currentSpeedKmh.toInt()} km/h)"
            }
            else -> ui.statusTrackingActive
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(ui.appTitle)
            .setContentText(contentText)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_notification_location,
                ui.notificationStopAction,
                stopPendingIntent
            )
            .build()
    }

    private fun updateForegroundNotification() {
        if (!AppPreferences.state.value.isTrackingEnabled) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onDestroy() {
        offlineReminderJob?.cancel()
        offlineReminderJob = null
        stopOfflineBeep()
        internetMonitor?.stop()
        internetMonitor = null
        removeLocationUpdatesSafely()
        whereAmIMediaSession?.isActive = false
        whereAmIMediaSession?.release()
        whereAmIMediaSession = null
        callWaitJob?.cancel()
        AppPreferences.setCallAudioPaused(false)
        stopVoiceImmediately()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        try {
            gpsReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
