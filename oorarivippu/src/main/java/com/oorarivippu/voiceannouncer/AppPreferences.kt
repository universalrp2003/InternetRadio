package com.oorarivippu.voiceannouncer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AnnouncementLogItem(
    val timestamp: String,
    val title: String,
    val spokenText: String
)

enum class TtsVoiceStatus {
    UNKNOWN,
    AVAILABLE,
    FALLBACK_LANGUAGE,
    SELECTED_VOICE_UNAVAILABLE,
    NETWORK_REQUIRED,
    UNAVAILABLE
}

data class AppStateSnapshot(
    val isTrackingEnabled: Boolean = false,
    val appLanguage: String = "ta",        // Default Tamil ("ta")
    val voiceLanguage: String = "ta",      // Default Tamil ("ta")
    // Owner request after 1.0.2: the next area within the same town is off unless chosen.
    val announceSameCitySubArea: Boolean = false,
    val bikeCarDetectionEnabled: Boolean = true,
    val announceImmediatelyOnStart: Boolean = true,
    val minDistanceMeters: Int = 500,      // Minimum distance before re-evaluating same-area drift
    val isGpsEnabled: Boolean = true,
    val isInternetAvailable: Boolean? = null,
    val isCallAudioPaused: Boolean = false,
    val offlineReminderIntervalMinutes: Int = OfflineReminderPolicy.DEFAULT_INTERVAL_MINUTES,
    val currentCityOrVillage: String = "",
    val currentSubArea: String = "",
    val currentDistrict: String = "",
    val currentSpeedKmh: Float = 0f,
    val isVehicleMoving: Boolean = false,
    val locationUpdateIntervalSeconds: Int = 5,
    val ttsVoiceStatus: TtsVoiceStatus = TtsVoiceStatus.UNKNOWN,
    val tamilTtsVoiceName: String = "",
    val englishTtsVoiceName: String = "",
    val availableTtsVoices: List<TtsVoiceOption> = emptyList(),
    val isTtsVoiceCatalogLoaded: Boolean = false,
    val destinationName: String = "",
    /** The chosen place's district, state and PIN code, so the person can see which place it is. */
    val destinationDetails: String = "",
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationAlertMask: Int = 0,
    val headsetWhereAmIEnabled: Boolean = false,
    /** Speech speed in percent of Android's normal rate (80 slow, 95 normal, 110 fast). */
    val speechRatePercent: Int = SpeechRatePolicy.DEFAULT_PERCENT,
    val history: List<AnnouncementLogItem> = emptyList()
) {
    fun selectedTtsVoiceNameFor(languageCode: String): String =
        if (languageCode == "en") englishTtsVoiceName else tamilTtsVoiceName
}

object AppPreferences {
    private const val PREFS_NAME = "oor_arivippu_prefs"

    private const val KEY_TRACKING_ENABLED = "is_tracking_enabled"
    private const val KEY_APP_LANG = "app_language"
    private const val KEY_VOICE_LANG = "voice_language"
    private const val KEY_TAMIL_TTS_VOICE = "tamil_tts_voice"
    private const val KEY_ENGLISH_TTS_VOICE = "english_tts_voice"
    private const val KEY_SAME_CITY_SUB_AREA = "announce_same_city_sub_area"
    private const val KEY_BIKE_CAR_MODE = "bike_car_detection_enabled"
    private const val KEY_ANNOUNCE_ON_START = "announce_immediately_on_start"
    private const val KEY_MIN_DISTANCE_METERS = "min_distance_meters"
    private const val KEY_DESTINATION_NAME = "destination_name"
    private const val KEY_DESTINATION_DETAILS = "destination_details"
    private const val KEY_SPEECH_RATE = "speech_rate_percent"
    private const val KEY_DESTINATION_LATITUDE = "destination_latitude"
    private const val KEY_DESTINATION_LONGITUDE = "destination_longitude"
    private const val KEY_DESTINATION_ALERT_MASK = "destination_alert_mask"
    private const val KEY_HEADSET_WHERE_AM_I = "headset_where_am_i_enabled"
    private const val KEY_OFFLINE_REMINDER_INTERVAL_MINUTES = "offline_reminder_interval_minutes"

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppStateSnapshot())
    val state: StateFlow<AppStateSnapshot> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = AppStateSnapshot(
            isTrackingEnabled = prefs.getBoolean(KEY_TRACKING_ENABLED, false),
            appLanguage = prefs.getString(KEY_APP_LANG, "ta") ?: "ta",
            voiceLanguage = prefs.getString(KEY_VOICE_LANG, "ta") ?: "ta",
            tamilTtsVoiceName = prefs.getString(KEY_TAMIL_TTS_VOICE, "") ?: "",
            englishTtsVoiceName = prefs.getString(KEY_ENGLISH_TTS_VOICE, "") ?: "",
            announceSameCitySubArea = prefs.getBoolean(KEY_SAME_CITY_SUB_AREA, false),
            bikeCarDetectionEnabled = prefs.getBoolean(KEY_BIKE_CAR_MODE, true),
            announceImmediatelyOnStart = prefs.getBoolean(KEY_ANNOUNCE_ON_START, true),
            minDistanceMeters = prefs.getInt(KEY_MIN_DISTANCE_METERS, 500).coerceIn(200, 3000),
            destinationName = prefs.getString(KEY_DESTINATION_NAME, "") ?: "",
            destinationDetails = prefs.getString(KEY_DESTINATION_DETAILS, "") ?: "",
            destinationLatitude = prefs.getString(KEY_DESTINATION_LATITUDE, null)?.toDoubleOrNull(),
            destinationLongitude = prefs.getString(KEY_DESTINATION_LONGITUDE, null)?.toDoubleOrNull(),
            destinationAlertMask = prefs.getInt(KEY_DESTINATION_ALERT_MASK, 0),
            headsetWhereAmIEnabled = prefs.getBoolean(KEY_HEADSET_WHERE_AM_I, false),
            speechRatePercent = SpeechRatePolicy.normalize(prefs.getInt(KEY_SPEECH_RATE, SpeechRatePolicy.DEFAULT_PERCENT)),
            offlineReminderIntervalMinutes = OfflineReminderPolicy.normalizeInterval(
                prefs.getInt(
                    KEY_OFFLINE_REMINDER_INTERVAL_MINUTES,
                    OfflineReminderPolicy.DEFAULT_INTERVAL_MINUTES
                )
            )
        )
    }

    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
        _state.value = _state.value.copy(
            isTrackingEnabled = enabled,
            currentSpeedKmh = if (enabled) _state.value.currentSpeedKmh else 0f,
            isVehicleMoving = if (enabled) _state.value.isVehicleMoving else false
        )
    }

    fun setAppLanguage(lang: String) {
        prefs.edit().putString(KEY_APP_LANG, lang).apply()
        _state.value = _state.value.copy(appLanguage = lang)
    }

    fun setVoiceLanguage(lang: String) {
        prefs.edit().putString(KEY_VOICE_LANG, lang).apply()
        _state.value = _state.value.copy(
            voiceLanguage = lang,
            ttsVoiceStatus = TtsVoiceStatus.UNKNOWN
        )
    }

    fun setTtsVoiceStatus(status: TtsVoiceStatus) {
        _state.value = _state.value.copy(ttsVoiceStatus = status)
    }

    fun setSelectedTtsVoice(languageCode: String, voiceName: String) {
        val isEnglish = languageCode == "en"
        val key = if (isEnglish) KEY_ENGLISH_TTS_VOICE else KEY_TAMIL_TTS_VOICE
        val cleanName = voiceName.trim()
        prefs.edit().putString(key, cleanName).apply()
        _state.value = _state.value.copy(
            englishTtsVoiceName = if (isEnglish) cleanName else _state.value.englishTtsVoiceName,
            tamilTtsVoiceName = if (isEnglish) _state.value.tamilTtsVoiceName else cleanName,
            ttsVoiceStatus = TtsVoiceStatus.UNKNOWN
        )
    }

    fun setTtsVoiceCatalog(voices: List<TtsVoiceOption>, loaded: Boolean) {
        _state.value = _state.value.copy(
            availableTtsVoices = voices,
            isTtsVoiceCatalogLoaded = loaded
        )
    }

    fun setInternetAvailable(available: Boolean) {
        _state.value = _state.value.copy(isInternetAvailable = available)
    }

    fun setOfflineReminderIntervalMinutes(minutes: Int) {
        val normalized = OfflineReminderPolicy.normalizeInterval(minutes)
        prefs.edit().putInt(KEY_OFFLINE_REMINDER_INTERVAL_MINUTES, normalized).apply()
        _state.value = _state.value.copy(offlineReminderIntervalMinutes = normalized)
    }

    fun setCallAudioPaused(paused: Boolean) {
        _state.value = _state.value.copy(isCallAudioPaused = paused)
    }

    fun setLocationUpdateIntervalMillis(intervalMillis: Long) {
        val seconds = (intervalMillis / 1_000L).toInt().coerceAtLeast(1)
        _state.value = _state.value.copy(locationUpdateIntervalSeconds = seconds)
    }

    fun setHeadsetWhereAmIEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HEADSET_WHERE_AM_I, enabled).apply()
        _state.value = _state.value.copy(headsetWhereAmIEnabled = enabled)
    }

    fun setDestination(name: String, latitude: Double, longitude: Double, details: String = "") {
        val cleanName = name.trim()
        val cleanDetails = details.trim()
        prefs.edit()
            .putString(KEY_DESTINATION_NAME, cleanName)
            .putString(KEY_DESTINATION_DETAILS, cleanDetails)
            .putString(KEY_DESTINATION_LATITUDE, latitude.toString())
            .putString(KEY_DESTINATION_LONGITUDE, longitude.toString())
            .putInt(KEY_DESTINATION_ALERT_MASK, 0)
            .apply()
        _state.value = _state.value.copy(
            destinationName = cleanName,
            destinationDetails = cleanDetails,
            destinationLatitude = latitude,
            destinationLongitude = longitude,
            destinationAlertMask = 0
        )
    }

    fun clearDestination() {
        prefs.edit()
            .remove(KEY_DESTINATION_NAME)
            .remove(KEY_DESTINATION_DETAILS)
            .remove(KEY_DESTINATION_LATITUDE)
            .remove(KEY_DESTINATION_LONGITUDE)
            .remove(KEY_DESTINATION_ALERT_MASK)
            .apply()
        _state.value = _state.value.copy(
            destinationName = "",
            destinationDetails = "",
            destinationLatitude = null,
            destinationLongitude = null,
            destinationAlertMask = 0
        )
    }

    fun setDestinationAlertMask(mask: Int) {
        val normalized = mask and 0x0F
        prefs.edit().putInt(KEY_DESTINATION_ALERT_MASK, normalized).apply()
        _state.value = _state.value.copy(destinationAlertMask = normalized)
    }

    fun setSpeechRatePercent(percent: Int) {
        val normalized = SpeechRatePolicy.normalize(percent)
        prefs.edit().putInt(KEY_SPEECH_RATE, normalized).apply()
        _state.value = _state.value.copy(speechRatePercent = normalized)
    }

    fun setAnnounceSameCitySubArea(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAME_CITY_SUB_AREA, enabled).apply()
        _state.value = _state.value.copy(announceSameCitySubArea = enabled)
    }

    fun setBikeCarDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIKE_CAR_MODE, enabled).apply()
        _state.value = _state.value.copy(bikeCarDetectionEnabled = enabled)
    }

    fun setAnnounceImmediatelyOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCE_ON_START, enabled).apply()
        _state.value = _state.value.copy(announceImmediatelyOnStart = enabled)
    }

    fun setMinDistanceMeters(meters: Int) {
        // Keep the persisted setting aligned with the 200-3,000 m control shown in the UI.
        val clamped = meters.coerceIn(200, 3000)
        prefs.edit().putInt(KEY_MIN_DISTANCE_METERS, clamped).apply()
        _state.value = _state.value.copy(minDistanceMeters = clamped)
    }

    fun updateLiveStatus(
        isGpsEnabled: Boolean = _state.value.isGpsEnabled,
        cityOrVillage: String = _state.value.currentCityOrVillage,
        subArea: String = _state.value.currentSubArea,
        district: String = _state.value.currentDistrict,
        speedKmh: Float = _state.value.currentSpeedKmh,
        isVehicleMoving: Boolean = _state.value.isVehicleMoving
    ) {
        _state.value = _state.value.copy(
            isGpsEnabled = isGpsEnabled,
            currentCityOrVillage = cityOrVillage,
            currentSubArea = subArea,
            currentDistrict = district,
            currentSpeedKmh = speedKmh,
            isVehicleMoving = isVehicleMoving
        )
    }

    fun appendAnnouncementLog(placeTitle: String, spokenText: String) {
        val timeFmt = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val item = AnnouncementLogItem(
            timestamp = timeFmt,
            title = placeTitle,
            spokenText = spokenText
        )
        val updated = listOf(item) + _state.value.history.take(29)
        _state.value = _state.value.copy(history = updated)
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = emptyList())
    }
}
