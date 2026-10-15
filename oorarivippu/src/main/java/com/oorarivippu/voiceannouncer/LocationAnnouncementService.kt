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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

        private const val NOTIFICATION_CHANNEL_ID = "oor_arivippu_location_channel"
        private const val NOTIFICATION_ID = 1001
        private const val GEOCODE_MIN_INTERVAL_MS = 30_000L
        private const val GEOCODE_MIN_DISTANCE_METERS = 100f
        private const val MAX_STALE_GEOCODE_DISTANCE_METERS = 500f
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private data class PendingSpeech(
        val text: String,
        val alternateLanguageText: String,
        val requestedLanguage: String
    )
    private var pendingSpeechQueue: PendingSpeech? = null

    private val travelDetector = TravelStateDetector()
    private var gpsReceiver: GpsStateReceiver? = null

    // Track previously announced City/Village and Sub-Locality so we never repeat the same place
    private var lastAnnouncedCity: String = ""
    private var lastAnnouncedSubArea: String = ""
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

        createNotificationChannel()
        registerGpsStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingAndService()
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
                speakAnnouncement(testSentence, snap.voiceLanguage, fallbackSentence)
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
        AppPreferences.setTrackingEnabled(true)

        if (isGpsOn) {
            requestLocationUpdatesSafely()
        } else {
            stopVoiceImmediately()
        }
    }

    private fun stopTrackingAndService() {
        AppPreferences.setTrackingEnabled(false)
        removeLocationUpdatesSafely()
        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = null
        stopVoiceImmediately()
        travelDetector.reset()
        lastAnnouncedCity = ""
        lastAnnouncedSubArea = ""
        hasMadeInitialStartAnnouncement = false
        latestReceivedLocation = null
        lastGeocodedLocation = null
        lastGeocodeStartedAtElapsedMs = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesSafely() {
        removeLocationUpdatesSafely()
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(15f)
                .build()
            fusedLocationClient.requestLocationUpdates(
                request,
                fusedLocationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            // Fallback to native Android LocationManager if Google Play Services is unavailable
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L,
                    15f,
                    fallbackLocationListener,
                    Looper.getMainLooper()
                )
            } catch (_: Exception) {}
        }
    }

    private fun removeLocationUpdatesSafely() {
        try {
            fusedLocationClient.removeLocationUpdates(fusedLocationCallback)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(fallbackLocationListener)
        } catch (_: Exception) {}
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
        } else if (AppPreferences.state.value.isTrackingEnabled) {
            lastGeocodedLocation = null
            lastGeocodeStartedAtElapsedMs = 0L
            requestLocationUpdatesSafely()
        }
        updateForegroundNotification()
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

        if (!shouldStartReverseGeocode(location)) return

        val requestToken = ++geocodeRequestToken
        reverseGeocodeJob = serviceScope.launch {
            try {
                val resolved = reverseGeocodePlace(location, snap.voiceLanguage) ?: return@launch
                if (!AppPreferences.state.value.isTrackingEnabled) return@launch

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
                    subArea = subArea
                )
                updateForegroundNotification()
                if (cityOrVillage.isBlank()) return@launch

                val isFirstStartAnnouncement =
                    !hasMadeInitialStartAnnouncement && current.announceImmediatelyOnStart
                val isNewCityOrVillage = !cityOrVillage.equals(lastAnnouncedCity, ignoreCase = true)
                val isNewSubAreaInSameCity = !isNewCityOrVillage &&
                    subArea.isNotBlank() &&
                    !subArea.equals(cityOrVillage, ignoreCase = true) &&
                    !subArea.equals(lastAnnouncedSubArea, ignoreCase = true)
                val movementAllowsAnnouncement = isFirstStartAnnouncement || current.isVehicleMoving
                if (!movementAllowsAnnouncement) return@launch

                if (isNewCityOrVillage) {
                    val sentence = BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = current.voiceLanguage,
                        isNewCityOrVillage = true,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = current.announceSameCitySubArea
                    )
                    val alternateLanguage = if (current.voiceLanguage == "en") "ta" else "en"
                    val alternateSentence = BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = alternateLanguage,
                        isNewCityOrVillage = true,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = current.announceSameCitySubArea
                    )
                    lastAnnouncedCity = cityOrVillage
                    lastAnnouncedSubArea = subArea
                    hasMadeInitialStartAnnouncement = true
                    travelDetector.markPlaceAnnounced(location)

                    val displayTitle = if (subArea.isNotBlank() &&
                        !subArea.equals(cityOrVillage, ignoreCase = true)
                    ) "$cityOrVillage ($subArea)" else cityOrVillage
                    AppPreferences.appendAnnouncementLog(displayTitle, sentence)
                    speakAnnouncement(sentence, current.voiceLanguage, alternateSentence)
                } else if (isNewSubAreaInSameCity && current.announceSameCitySubArea) {
                    val sentence = BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = current.voiceLanguage,
                        isNewCityOrVillage = false,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = true
                    )
                    val alternateLanguage = if (current.voiceLanguage == "en") "ta" else "en"
                    val alternateSentence = BilingualStrings.buildSpokenAnnouncement(
                        voiceLang = alternateLanguage,
                        isNewCityOrVillage = false,
                        cityOrVillage = cityOrVillage,
                        subArea = subArea,
                        includeSubArea = true
                    )
                    lastAnnouncedSubArea = subArea
                    hasMadeInitialStartAnnouncement = true
                    travelDetector.markPlaceAnnounced(location)
                    AppPreferences.appendAnnouncementLog("$subArea, $cityOrVillage", sentence)
                    speakAnnouncement(sentence, current.voiceLanguage, alternateSentence)
                }
            } finally {
                if (requestToken == geocodeRequestToken) reverseGeocodeJob = null
            }
        }
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
        val subArea: String
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
        val cityOrVillage = listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        // Extract Same-City Next Place (Sub-Locality / Neighborhood / Street)
        val rawSubArea = listOfNotNull(
            address.subLocality,
            address.thoroughfare,
            address.featureName?.takeIf { !it.matches(Regex("^[0-9+\\-.,\\s]+$")) }
        ).firstOrNull { it.isNotBlank() && !it.equals(cityOrVillage, ignoreCase = true) }?.trim().orEmpty()

        return ResolvedPlace(
            cityOrVillage = cityOrVillage,
            subArea = rawSubArea
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
                    alternateLanguageText = queued.alternateLanguageText
                )
            }
        } else {
            isTtsReady = false
            pendingSpeechQueue = null
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
    private fun configureTtsLanguage(voiceLang: String): String? {
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

        for ((locale, languageCode) in candidates) {
            try {
                if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) continue
                if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) continue
                engine.setSpeechRate(0.95f)
                AppPreferences.setTtsVoiceStatus(
                    if (languageCode == requestedLanguage) {
                        TtsVoiceStatus.AVAILABLE
                    } else {
                        TtsVoiceStatus.FALLBACK_LANGUAGE
                    }
                )
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
        alternateLanguageText: String
    ) {
        // Double check GPS is still enabled before speaking.
        if (!GpsStateReceiver.isGpsProviderEnabled(this) && AppPreferences.state.value.isTrackingEnabled) {
            return
        }
        val requestedLanguage = if (voiceLang == "en") "en" else "ta"
        val engine = tts
        if (!isTtsReady || engine == null) {
            pendingSpeechQueue = PendingSpeech(
                text = text,
                alternateLanguageText = alternateLanguageText,
                requestedLanguage = requestedLanguage
            )
            return
        }

        val actualLanguage = configureTtsLanguage(requestedLanguage)
        if (actualLanguage == null) {
            if (!AppPreferences.state.value.isTrackingEnabled) stopSelf()
            return
        }
        val localizedText = if (actualLanguage == requestedLanguage) text else alternateLanguageText
        requestAudioFocusAndSpeak(engine, localizedText)
    }

    private fun requestAudioFocusAndSpeak(engine: TextToSpeech, text: String) {
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
                if (!AppPreferences.state.value.isTrackingEnabled) stopSelf()
            }
        })

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "place_announce_${System.currentTimeMillis()}")
    }

    private fun stopVoiceImmediately() {
        pendingSpeechQueue = null
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
        removeLocationUpdatesSafely()
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
