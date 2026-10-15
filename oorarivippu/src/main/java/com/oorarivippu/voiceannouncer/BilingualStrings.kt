package com.oorarivippu.voiceannouncer

/**
 * Full bilingual dictionary supporting Tamil ("ta", default) and English ("en").
 * Used by both Jetpack Compose UI and the Background Foreground Service.
 */
data class UiStrings(
    val appTitle: String,
    val appSubtitle: String,
    val locationDisclosure: String,
    val appLanguageLabel: String,
    val voiceLanguageLabel: String,
    val voiceStatusNotChecked: String,
    val voiceStatusAvailable: String,
    val voiceStatusFallback: String,
    val voiceStatusUnavailable: String,
    val tamilName: String,
    val englishName: String,
    val masterTurnOn: String,
    val masterTurnOff: String,
    val statusTrackingActive: String,
    val statusTrackingStopped: String,
    val statusGpsOff: String,
    val statusWaitingForMove: String,
    val currentPlaceLabel: String,
    val currentCityLabel: String,
    val currentSubAreaLabel: String,
    val currentSpeedLabel: String,
    val travelModeDetected: String,
    val travelModeStationary: String,
    val sameCityOptionTitle: String,
    val sameCityOptionSubtitle: String,
    val bikeCarModeTitle: String,
    val bikeCarModeSubtitle: String,
    val announceOnStartTitle: String,
    val announceOnStartSubtitle: String,
    val minDistanceTitle: String,
    val testVoiceButton: String,
    val historySectionTitle: String,
    val emptyHistoryText: String,
    val clearHistoryButton: String,
    val permissionNeededBanner: String,
    val grantPermissionButton: String,
    val backgroundLocationTitle: String,
    val backgroundLocationRationale: String,
    val allowBackgroundLocationButton: String,
    val continueWithoutBackgroundButton: String,
    val notificationChannelName: String,
    val notificationStopAction: String,
    val unknownPlace: String
)

object BilingualStrings {

    val TAMIL = UiStrings(
        appTitle = "ஊர் அறிவிப்பு",
        appSubtitle = "பயணத்தின் போது ஊர் மற்றும் பகுதி பெயரை குரலில் அறிவிக்கும் செயலி",
        locationDisclosure = "இயக்கும்போது, அருகிலுள்ள இடப் பெயர்களைக் கண்டறிந்து குரலில் சொல்ல உங்கள் இருப்பிடம் பின்னணியில் பயன்படுத்தப்படும். Android 10+ இல் திரை அணைந்தபோதும் தொடர ‘Allow all the time’ அனுமதியைத் தனியாக வழங்கலாம்; வழங்காவிட்டாலும் இப்போது தொடங்கலாம், ஆனால் பின்னணி மீட்பு சாதனத்தைப் பொறுத்து மாறலாம். இடப் பெயர் தேடலுக்கு Android சேவைகள் இணையத்தைப் பயன்படுத்தலாம்; GPS பேட்டரியை அதிகம் பயன்படுத்தலாம். தொடர்ச்சியான அறிவிப்பு காணப்படும் வரை கண்காணிப்பு இயங்கும்; எப்போது வேண்டுமானாலும் நிறுத்தலாம்.",
        appLanguageLabel = "செயலி மொழி (App Language)",
        voiceLanguageLabel = "குரல் அறிவிப்பு மொழி (Voice Language)",
        voiceStatusNotChecked = "குரல் கிடைப்பை இன்னும் சோதிக்கவில்லை. ‘குரல் சோதனை’ பட்டனைத் தட்டவும்.",
        voiceStatusAvailable = "தேர்ந்தெடுத்த மொழிக்கான குரல் இந்தச் சாதனத்தில் கிடைக்கிறது.",
        voiceStatusFallback = "தேர்ந்தெடுத்த குரல் இல்லை; சாதனத்தில் உள்ள மற்றொரு மொழிக் குரலைப் பயன்படுத்துகிறது.",
        voiceStatusUnavailable = "தமிழ் அல்லது ஆங்கிலக் குரல் கிடைக்கவில்லை. சாதனத்தின் Text-to-Speech அமைப்புகளில் குரல் தரவை நிறுவவும்.",
        tamilName = "தமிழ்",
        englishName = "English",
        masterTurnOn = "பயண அறிவிப்பை ஆன் (ON) செய்",
        masterTurnOff = "பயண அறிவிப்பை ஆஃப் (OFF) செய்",
        statusTrackingActive = "பின்னணியில் ஜி.பி.எஸ் (GPS) கண்காணிப்பு இயங்குகிறது",
        statusTrackingStopped = "அறிவிப்பு நிறுத்தப்பட்டுள்ளது (OFF)",
        statusGpsOff = "ஜி.பி.எஸ் (GPS) ஆஃப் செய்யப்பட்டுள்ளது - அறிவிப்பு இல்லை",
        statusWaitingForMove = "பைக் / கார் பயண இயக்கத்திற்காக காத்திருக்கிறது...",
        currentPlaceLabel = "தற்போதைய இட விவரம்",
        currentCityLabel = "ஊர் / நகரம்",
        currentSubAreaLabel = "பகுதி / தெரு",
        currentSpeedLabel = "வேகம் & நிலை",
        travelModeDetected = "பைக் / வாகன பயணம் கண்டறியப்பட்டது",
        travelModeStationary = "ஒரே இடத்தில் உள்ளீர்கள் (அறிவிப்பு அமைதி)",
        sameCityOptionTitle = "ஒரே ஊருக்குள் அடுத்த பகுதி அறிவிப்பு",
        sameCityOptionSubtitle = "ஆன் (ON): ஒரே நகரத்திற்குள் புதிய பகுதி வந்தாலும் அறிவிக்கும்.\nஆஃப் (OFF): அடுத்த ஊர் அல்லது கிராமத்திற்கு மாறினால் மட்டுமே அறிவிக்கும்.",
        bikeCarModeTitle = "பைக் / கார் பயணத்தை கண்டறிந்து அறிவி",
        bikeCarModeSubtitle = "பைக் அல்லது காரில் பயணிக்கும் போது மட்டும் (அல்லது குறிப்பிட்ட தூரம் கடந்த பின்) அறிவிக்கும். ஒரே இடத்தில் இருக்கும்போது அறிவிக்காது.",
        announceOnStartTitle = "ஆன் செய்தவுடன் தற்போதைய ஊரை அறிவி",
        announceOnStartSubtitle = "பட்டனை ஆன் செய்தவுடன் முதல் முறை தற்போதைய ஊர் பெயரை குரலில் அறிவிக்கும்.",
        minDistanceTitle = "குறைந்தபட்ச நகர்வு தூரம்",
        testVoiceButton = "குரல் சோதனை (Test Voice)",
        historySectionTitle = "சமீபத்திய ஊர் அறிவிப்புகள்",
        emptyHistoryText = "இன்னும் எந்த ஊர் அறிவிப்பும் செய்யப்படவில்லை.",
        clearHistoryButton = "அழி",
        permissionNeededBanner = "பயண அறிவிப்பை இயக்க இருப்பிட (Location) மற்றும் அறிவிப்பு (Notification) அனுமதி தேவை. துல்லியமான இருப்பிட அனுமதி சிறந்த முடிவுகளைத் தரும்.",
        grantPermissionButton = "அனுமதி வழங்கு (Grant Permission)",
        backgroundLocationTitle = "பின்னணி இருப்பிட அணுகல் (Background Location)",
        backgroundLocationRationale = "திரை அணைந்திருக்கும்போதும் பயண அறிவிப்பைத் தொடர, Android 10+ சாதனங்களில் ‘Allow all the time’ இருப்பிட அணுகலைத் தனியாகத் தேர்வு செய்யலாம். இந்த அனுமதி அறிவிப்பு ON-இல் இருக்கும் போது மட்டுமே பயன்படும். இதைத் தவிர்த்தாலும் இப்போது தொடங்கலாம்; ஆனால் பின்னணியில் மீண்டும் தொடங்குவது Android / சாதனத்தைப் பொறுத்து மாறலாம்.",
        allowBackgroundLocationButton = "அமைப்புகளில் அனுமதி திற (Open Settings)",
        continueWithoutBackgroundButton = "இப்போது வேண்டாம் — தொடர் (Continue)",
        notificationChannelName = "பயண ஊர் அறிவிப்பு சேவை",
        notificationStopAction = "நிறுத்து (OFF)",
        unknownPlace = "கண்டறியப்படவில்லை"
    )

    val ENGLISH = UiStrings(
        appTitle = "Oor Arivippu (Place Announcer)",
        appSubtitle = "Live GPS Voice Announcer for Cities, Villages & Neighborhoods",
        locationDisclosure = "When enabled, your location is used in the background to identify nearby place names and speak them. On Android 10+, you can separately allow ‘Allow all the time’ for continued use while the screen is off; you can still start without it, but background recovery may vary by device. Android location/geocoding services may use network access, and GPS can increase battery use. Tracking runs while its ongoing notification is active; you can stop it at any time.",
        appLanguageLabel = "App Language (செயலி மொழி)",
        voiceLanguageLabel = "Voice Language (குரல் மொழி)",
        voiceStatusNotChecked = "Voice availability has not been checked yet. Tap Test Voice.",
        voiceStatusAvailable = "The selected voice is available on this device.",
        voiceStatusFallback = "The selected voice is unavailable; using an installed voice in the other language.",
        voiceStatusUnavailable = "No Tamil or English voice is available. Install voice data in the device's Text-to-Speech settings.",
        tamilName = "தமிழ்",
        englishName = "English",
        masterTurnOn = "TURN ON VOICE ANNOUNCER",
        masterTurnOff = "TURN OFF VOICE ANNOUNCER",
        statusTrackingActive = "Background GPS Tracking & Voice Active",
        statusTrackingStopped = "Announcer is Turned OFF",
        statusGpsOff = "Phone GPS is Turned OFF — No Announcements",
        statusWaitingForMove = "Waiting for Bike / Car movement...",
        currentPlaceLabel = "Current Location Status",
        currentCityLabel = "City / Village",
        currentSubAreaLabel = "Sub-Area / Locality",
        currentSpeedLabel = "Speed & Motion",
        travelModeDetected = "Bike / Vehicle Travel Active",
        travelModeStationary = "Stationary / Routine Area (Muted)",
        sameCityOptionTitle = "Same-City Next Place Announcement",
        sameCityOptionSubtitle = "ON: Announces new neighborhoods/areas within the same city.\nOFF: Announces ONLY when entering a new city, town, or village.",
        bikeCarModeTitle = "Bike / Car Travel Detection",
        bikeCarModeSubtitle = "Starts announcing only when traveling by bike/car or moving past the distance threshold. Prevents announcements when staying at the same place.",
        announceOnStartTitle = "Announce Current Place When Turned ON",
        announceOnStartSubtitle = "Speaks your current village/city once immediately when you press ON.",
        minDistanceTitle = "Minimum Travel Distance",
        testVoiceButton = "Test Voice Announcement",
        historySectionTitle = "Recent Voice Announcements",
        emptyHistoryText = "No places announced yet in this session.",
        clearHistoryButton = "Clear",
        permissionNeededBanner = "Location and Notification permissions are required to enable travel announcements. Precise location provides the best results.",
        grantPermissionButton = "Grant Permissions",
        backgroundLocationTitle = "Background location access",
        backgroundLocationRationale = "To keep travel announcements working while the screen is off, Android 10+ lets you separately choose ‘Allow all the time’ for location. This access is used only while the announcer is ON. You can skip it and start now; background recovery may then depend on Android and the device manufacturer.",
        allowBackgroundLocationButton = "Open location settings",
        continueWithoutBackgroundButton = "Continue without it",
        notificationChannelName = "Background Place Announcer",
        notificationStopAction = "Turn OFF",
        unknownPlace = "Not detected yet"
    )

    fun forLang(langCode: String): UiStrings {
        return if (langCode == "en") ENGLISH else TAMIL
    }

    /**
     * Formats the spoken voice announcement text in Tamil ("ta") or English ("en").
     */
    fun buildSpokenAnnouncement(
        voiceLang: String,
        isNewCityOrVillage: Boolean,
        cityOrVillage: String,
        subArea: String?,
        includeSubArea: Boolean
    ): String {
        return if (voiceLang == "en") {
            if (isNewCityOrVillage) {
                if (includeSubArea && !subArea.isNullOrBlank() && !subArea.equals(cityOrVillage, ignoreCase = true)) {
                    "Entering $cityOrVillage, $subArea."
                } else {
                    "Entering $cityOrVillage."
                }
            } else {
                "Entering $subArea, $cityOrVillage."
            }
        } else {
            // Default Tamil ("ta")
            if (isNewCityOrVillage) {
                if (includeSubArea && !subArea.isNullOrBlank() && !subArea.equals(cityOrVillage, ignoreCase = true)) {
                    "தற்போது $cityOrVillage, $subArea பகுதிக்கு வந்துள்ளீர்கள்."
                } else {
                    "தற்போது $cityOrVillage ஊருக்குள் வந்துள்ளீர்கள்."
                }
            } else {
                "$cityOrVillage, அடுத்த பகுதி $subArea."
            }
        }
    }

    fun buildTestSpeech(voiceLang: String, currentCity: String): String {
        val samplePlace = if (currentCity.isNotBlank() && currentCity != TAMIL.unknownPlace && currentCity != ENGLISH.unknownPlace) {
            currentCity
        } else {
            if (voiceLang == "en") "Alangudi" else "ஆலங்குடி"
        }
        return if (voiceLang == "en") {
            "Voice announcer is active. Current place: $samplePlace."
        } else {
            "ஊர் அறிவிப்பு செயலி தயாராக உள்ளது. தற்போதைய இடம்: $samplePlace."
        }
    }
}
