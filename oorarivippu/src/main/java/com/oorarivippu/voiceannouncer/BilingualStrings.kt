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
    val voiceChoiceLabel: String,
    val voiceChoiceSubtitle: String,
    val voiceChoiceSystemDefault: String,
    val voiceChoiceLoading: String,
    val voiceChoicesUnavailable: String,
    val voiceChoiceOnDevice: String,
    val voiceChoiceOnline: String,
    val voiceStatusNotChecked: String,
    val voiceStatusAvailable: String,
    val voiceStatusFallback: String,
    val voiceStatusSelectedUnavailable: String,
    val voiceStatusNetworkRequired: String,
    val voiceStatusUnavailable: String,
    val tamilName: String,
    val englishName: String,
    val masterTurnOn: String,
    val masterTurnOff: String,
    val statusTrackingActive: String,
    val statusTrackingStopped: String,
    val statusGpsOff: String,
    val statusInternetUnavailable: String,
    val internetUnavailableDetails: String,
    val statusCallPaused: String,
    val statusWaitingForMove: String,
    val currentPlaceLabel: String,
    val currentCityLabel: String,
    val currentSubAreaLabel: String,
    val currentDistrictLabel: String,
    val currentSpeedLabel: String,
    val locationIntervalLabel: String,
    val adaptiveBatteryNote: String,
    val travelModeDetected: String,
    val travelModeStationary: String,
    val sameCityOptionTitle: String,
    val sameCityOptionSubtitle: String,
    val bikeCarModeTitle: String,
    val bikeCarModeSubtitle: String,
    val announceOnStartTitle: String,
    val announceOnStartSubtitle: String,
    val minDistanceTitle: String,
    val destinationSectionTitle: String,
    val destinationFieldLabel: String,
    val destinationSetButton: String,
    val destinationClearButton: String,
    val destinationLookupInProgress: String,
    val destinationSetMessage: String,
    val destinationNotFoundMessage: String,
    val destinationNeedsInternetMessage: String,
    val destinationAlertExplanation: String,
    val headsetWhereAmITitle: String,
    val headsetWhereAmISubtitle: String,
    val offlineReminderTitle: String,
    val offlineReminderSubtitle: String,
    val offlineReminderOff: String,
    val offlineReminderMinuteUnit: String,
    val aboutSectionTitle: String,
    val aboutVersionLabel: String,
    val contributorsTitle: String,
    val projectDevelopmentCredit: String,
    val aiAssistanceCredit: String,
    val sourceRepositoryLabel: String,
    val downloadRepositoryLabel: String,
    val otherAppsTitle: String,
    val liveGuardReleaseLabel: String,
    val radioReleaseLabel: String,
    val pulseEqReleaseLabel: String,
    val vocalRemoverReleaseLabel: String,
    val oorArivippuReleaseLabel: String,
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
    val detailsShow: String,
    val detailsHide: String,
    val voiceQualityVeryHigh: String,
    val voiceQualityHigh: String,
    val voiceQualityStandard: String,
    val speechSpeedLabel: String,
    val speechSpeedSlow: String,
    val speechSpeedNormal: String,
    val speechSpeedFast: String,
    val moreVoicesButton: String,
    val destinationChooseTitle: String,
    val destinationChooseHint: String,
    val distanceKmSuffix: String,
    val destinationViewOnMap: String,
    val destinationChooseCancel: String,
    val secretMessageReleaseLabel: String,
    val unknownPlace: String
)

object BilingualStrings {

    val TAMIL = UiStrings(
        appTitle = "ஊர் அறிவிப்பு",
        appSubtitle = "பயணத்தின் போது ஊர் மற்றும் பகுதி பெயரை குரலில் அறிவிக்கும் செயலி",
        locationDisclosure = "இயக்கும்போது, அருகிலுள்ள இடப் பெயர்களைக் கண்டறிந்து குரலில் சொல்ல உங்கள் இருப்பிடம் பின்னணியில் பயன்படுத்தப்படும். Android 10+ இல் திரை அணைந்தபோதும் தொடர ‘Allow all the time’ அனுமதியைத் தனியாக வழங்கலாம்; வழங்காவிட்டாலும் இப்போது தொடங்கலாம், ஆனால் பின்னணி மீட்பு சாதனத்தைப் பொறுத்து மாறலாம். இடப் பெயர் தேடலுக்கு Android சேவைகள் இணையத்தைப் பயன்படுத்தலாம்; GPS பேட்டரியை அதிகம் பயன்படுத்தலாம். தொடர்ச்சியான அறிவிப்பு காணப்படும் வரை கண்காணிப்பு இயங்கும்; எப்போது வேண்டுமானாலும் நிறுத்தலாம்.",
        appLanguageLabel = "செயலி மொழி (App Language)",
        voiceLanguageLabel = "குரல் அறிவிப்பு மொழி (Voice Language)",
        voiceChoiceLabel = "குரல் தேர்வு (Android TTS)",
        voiceChoiceSubtitle = "இந்தத் தொலைபேசியில் உள்ள இந்த மொழியின் எல்லா Android குரல்களும், சிறந்தவை முதலில். ‘இணையம் தேவை’ குரல்கள் பொதுவாக மிகத் தெளிவாக ஒலிக்கும்; அவை பேசும் உரையை அவற்றின் TTS சேவைக்கு அனுப்பலாம். குரலைத் தேர்ந்தெடுத்ததும் அது ஒலிக்கும். இணையமில்லா நினைவூட்டல்கள் எப்போதும் சாதனக் குரல் அல்லது பீப் ஒலியைப் பயன்படுத்தும்.",
        voiceChoiceSystemDefault = "Android இயல்புக் குரல் (இணையம் தேவைப்படலாம்)",
        voiceChoiceLoading = "சாதனக் குரல்கள் ஏற்றப்படுகின்றன…",
        voiceChoicesUnavailable = "இந்த மொழிக்கான கூடுதல் குரல்கள் இல்லை. Android Text-to-Speech அமைப்பில் குரல் தரவை நிறுவவும்.",
        voiceChoiceOnDevice = "சாதனத்திலேயே செயல்படும்",
        voiceChoiceOnline = "இணையம் தேவை",
        voiceStatusNotChecked = "குரல் கிடைப்பை இன்னும் சோதிக்கவில்லை. ‘குரல் சோதனை’ பட்டனைத் தட்டவும்.",
        voiceStatusAvailable = "தேர்ந்தெடுத்த Android TTS குரல் கிடைக்கிறது.",
        voiceStatusFallback = "தேர்ந்தெடுத்த மொழிக் குரல் இல்லை; மற்றொரு மொழிக் குரலைப் பயன்படுத்துகிறது.",
        voiceStatusSelectedUnavailable = "தேர்ந்தெடுத்த குரல் இல்லை; Android இயல்புக் குரலைப் பயன்படுத்துகிறது.",
        voiceStatusNetworkRequired = "இந்த Online குரலைச் சோதிக்க இணையத்தை இணைக்கவும்.",
        voiceStatusUnavailable = "தமிழ் அல்லது ஆங்கிலக் குரல் கிடைக்கவில்லை. சாதனத்தின் Text-to-Speech அமைப்புகளில் குரல் தரவை நிறுவவும்.",
        tamilName = "தமிழ்",
        englishName = "English",
        masterTurnOn = "பயண அறிவிப்பை ஆன் (ON) செய்",
        masterTurnOff = "பயண அறிவிப்பை ஆஃப் (OFF) செய்",
        statusTrackingActive = "பின்னணியில் ஜி.பி.எஸ் (GPS) கண்காணிப்பு இயங்குகிறது",
        statusTrackingStopped = "அறிவிப்பு நிறுத்தப்பட்டுள்ளது (OFF)",
        statusGpsOff = "ஜி.பி.எஸ் (GPS) ஆஃப் செய்யப்பட்டுள்ளது - அறிவிப்பு இல்லை",
        statusInternetUnavailable = "இணைய இணைப்பு இல்லை — இடப் பெயர் தேடலும் இடம்/இலக்கு குரல் அறிவிப்புகளும் நிறுத்தப்பட்டுள்ளன.",
        internetUnavailableDetails = "GPS இடத்தைப் பெறுகிறது; ஆனால் இணையம்/Android Geocoder கிடைக்கும் வரை புதிய ஊர், மாவட்ட, இலக்கு அறிவிப்புகள் இல்லை. இயக்கப்பட்டிருந்தால், தேர்ந்த இடைவெளியில் சாதனக் குரல் அல்லது பீப் மூலம் இணையம் இல்லை என நினைவூட்டும்.",
        statusCallPaused = "அழைப்பின் போது குரல் அறிவிப்பு நிறுத்தப்பட்டுள்ளது; அழைப்பு முடிந்ததும் தற்போதைய இடத்தை அறிவிக்கும்.",
        statusWaitingForMove = "பைக் / கார் பயண இயக்கத்திற்காக காத்திருக்கிறது...",
        currentPlaceLabel = "தற்போதைய இட விவரம்",
        currentCityLabel = "ஊர் / நகரம்",
        currentSubAreaLabel = "பகுதி / தெரு",
        currentDistrictLabel = "மாவட்டம்",
        currentSpeedLabel = "வேகம் & நிலை",
        locationIntervalLabel = "GPS புதுப்பிப்பு இடைவெளி",
        adaptiveBatteryNote = "நிலையான இடத்தில் 45s, நகரப் போக்குவரத்தில் 15s, நெடுஞ்சாலை வேகத்தில் 5s. உண்மையான பேட்டரி சேமிப்பு சாதனம்/சூழலைப் பொறுத்தது; சதவீதம் உறுதி இல்லை.",
        travelModeDetected = "பைக் / வாகன பயணம் கண்டறியப்பட்டது",
        travelModeStationary = "ஒரே இடத்தில் உள்ளீர்கள் (அறிவிப்பு அமைதி)",
        sameCityOptionTitle = "ஒரே ஊருக்குள் அடுத்த பகுதி அறிவிப்பு",
        sameCityOptionSubtitle = "ஆன் (ON): ஒரே நகரத்திற்குள் புதிய பகுதி வந்தாலும் அறிவிக்கும்.\nஆஃப் (OFF): அடுத்த ஊர் அல்லது கிராமத்திற்கு மாறினால் மட்டுமே அறிவிக்கும்.",
        bikeCarModeTitle = "பைக் / கார் பயணத்தை கண்டறிந்து அறிவி",
        bikeCarModeSubtitle = "பைக் அல்லது காரில் பயணிக்கும் போது மட்டும் (அல்லது குறிப்பிட்ட தூரம் கடந்த பின்) அறிவிக்கும். ஒரே இடத்தில் இருக்கும்போது அறிவிக்காது.",
        announceOnStartTitle = "ஆன் செய்தவுடன் தற்போதைய ஊரை அறிவி",
        announceOnStartSubtitle = "பட்டனை ஆன் செய்தவுடன் முதல் முறை தற்போதைய ஊர் பெயரை குரலில் அறிவிக்கும்.",
        minDistanceTitle = "குறைந்தபட்ச நகர்வு தூரம்",
        destinationSectionTitle = "இலக்கு நெருங்கும் அறிவிப்பு",
        destinationFieldLabel = "இலக்கு ஊர் / நகரம்",
        destinationSetButton = "இலக்கை அமை",
        destinationClearButton = "இலக்கை நீக்கு",
        destinationLookupInProgress = "இடத்தைத் தேடுகிறது...",
        destinationSetMessage = "இலக்கு அமைக்கப்பட்டது. 10, 5, 2 km மற்றும் சுமார் 500 m-இல் அறிவிக்கும்.",
        destinationNotFoundMessage = "இந்த இடத்தை Android Geocoder-ல் கண்டறிய முடியவில்லை. பெயரைத் தெளிவாக உள்ளிடவும்.",
        destinationNeedsInternetMessage = "இலக்கைத் தேடுவதற்கும் குரல் அறிவிப்பிற்கும் இணையம் தேவை.",
        destinationAlertExplanation = "இணையம் கிடைக்கும் போது GPS தூரத்தைப் பயன்படுத்தி 10 km, 5 km, 2 km மற்றும் சுமார் 500 m (வந்தடைந்தது) அறிவிக்கும். துல்லியம் GPS/Geocoder-ஐப் பொறுத்தது.",
        headsetWhereAmITitle = "ஹெட்செட் பொத்தானில் ‘இப்போது எந்த ஊர்?’",
        headsetWhereAmISubtitle = "விருப்பமாக இயக்கவும்: பயண அறிவிப்பு ON-இல், ஹெட்செட்டின் Next/skip பொத்தானை அழுத்தினால் தற்போதைய இடம் குரலில் சொல்லும். ஹெட்செட்/OEM-ஐப் பொறுத்து செயல்பாடு மாறலாம்; இசைக் கட்டுப்பாட்டுடன் மோதலாம்.",
        offlineReminderTitle = "இணையம் இல்லாதபோது நினைவூட்டு",
        offlineReminderSubtitle = "கண்காணிப்பு ON-இல், இணையம் இல்லை என்பதைத் தேர்ந்த இடைவெளியில் சாதனத்தில் உள்ள இணையமில்லா குரல் அல்லது குறுகிய பீப் மூலம் தெரிவிக்கும். இட/இலக்கு அறிவிப்புகள் நிறுத்தப்பட்டே இருக்கும்.",
        offlineReminderOff = "முடக்கு",
        offlineReminderMinuteUnit = "நிமி.",
        aboutSectionTitle = "செயலி விவரம் (About)",
        aboutVersionLabel = "பதிப்பு",
        contributorsTitle = "பங்களிப்பாளர்கள்",
        projectDevelopmentCredit = "திட்ட உருவாக்கம்: Ramesh prathap .R · universalrp2003@gmail.com",
        aiAssistanceCredit = "AI மென்பொருள் உதவிப் பங்களிப்பு: Arena.ai Agent Mode",
        sourceRepositoryLabel = "மூலக் குறியீடு — InternetRadio",
        downloadRepositoryLabel = "அதிகாரப்பூர்வ பதிவிறக்கங்கள் — RP-Android-Apps",
        otherAppsTitle = "RP Ideas செயலிகள்",
        liveGuardReleaseLabel = "Live Guard (CleanSweep)",
        radioReleaseLabel = "Ramesh Radio",
        pulseEqReleaseLabel = "PulseEQ",
        vocalRemoverReleaseLabel = "RP Vocal Remover Studio",
        oorArivippuReleaseLabel = "ஊர் அறிவிப்பு (Oor Arivippu)",
        testVoiceButton = "தேர்ந்தெடுத்த குரல் சோதனை (Test Voice)",
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
        detailsShow = "விவரம்",
        detailsHide = "மறை",
        voiceQualityVeryHigh = "மிக உயர் தரம்",
        voiceQualityHigh = "உயர் தரம்",
        voiceQualityStandard = "சாதாரண தரம்",
        speechSpeedLabel = "பேச்சு வேகம்",
        speechSpeedSlow = "மெதுவாக",
        speechSpeedNormal = "இயல்பு",
        speechSpeedFast = "வேகமாக",
        moreVoicesButton = "மேலும் குரல்களைப் பெறு (Android அமைப்பு)",
        destinationChooseTitle = "சரியான இடத்தைத் தேர்ந்தெடுக்கவும்",
        destinationChooseHint = "ஒரே பெயரில் பல இடங்கள் இருக்கலாம். மாவட்டம், அஞ்சல் குறியீடு மற்றும் தூரத்தைப் பார்த்துத் தேர்வு செய்யவும்.",
        distanceKmSuffix = "கி.மீ. தொலைவில்",
        destinationViewOnMap = "வரைபடத்தில் பார்",
        destinationChooseCancel = "ரத்துசெய்",
        secretMessageReleaseLabel = "இரகசிய செய்தி (Secret Message)",
        unknownPlace = "கண்டறியப்படவில்லை"
    )

    val ENGLISH = UiStrings(
        appTitle = "Oor Arivippu (Place Announcer)",
        appSubtitle = "Live GPS Voice Announcer for Cities, Villages & Neighborhoods",
        locationDisclosure = "When enabled, your location is used in the background to identify nearby place names and speak them. On Android 10+, you can separately allow ‘Allow all the time’ for continued use while the screen is off; you can still start without it, but background recovery may vary by device. Android location/geocoding services may use network access, and GPS can increase battery use. Tracking runs while its ongoing notification is active; you can stop it at any time.",
        appLanguageLabel = "App Language (செயலி மொழி)",
        voiceLanguageLabel = "Voice Language (குரல் மொழி)",
        voiceChoiceLabel = "Spoken voice (Android TTS)",
        voiceChoiceSubtitle = "All Android voices on this phone for this language, best first. Online voices usually sound the clearest and may send the spoken text to their TTS provider. Choosing a voice plays it. Offline reminders always use an on-device voice or a beep.",
        voiceChoiceSystemDefault = "Android system default (may use Internet)",
        voiceChoiceLoading = "Loading Android voices…",
        voiceChoicesUnavailable = "No additional voices are available for this language. Install voice data in Android Text-to-Speech settings.",
        voiceChoiceOnDevice = "On-device",
        voiceChoiceOnline = "Requires Internet",
        voiceStatusNotChecked = "Voice availability has not been checked yet. Tap Test Voice.",
        voiceStatusAvailable = "The selected Android TTS voice is available.",
        voiceStatusFallback = "The selected language voice is unavailable; using a voice in the other language.",
        voiceStatusSelectedUnavailable = "The selected voice is missing; using the Android system default.",
        voiceStatusNetworkRequired = "Connect to the Internet to test this online voice.",
        voiceStatusUnavailable = "No Tamil or English voice is available. Install voice data in the device's Text-to-Speech settings.",
        tamilName = "தமிழ்",
        englishName = "English",
        masterTurnOn = "TURN ON VOICE ANNOUNCER",
        masterTurnOff = "TURN OFF VOICE ANNOUNCER",
        statusTrackingActive = "Background GPS Tracking & Voice Active",
        statusTrackingStopped = "Announcer is Turned OFF",
        statusGpsOff = "Phone GPS is Turned OFF — No Announcements",
        statusInternetUnavailable = "No validated internet — place lookups and place/destination announcements are paused.",
        internetUnavailableDetails = "GPS can still receive your position, but new place, district, and destination announcements wait until internet access returns. When enabled, a local voice or beep reminds you that there is no internet at the selected interval.",
        statusCallPaused = "Announcements are paused during a call; the latest place will be spoken after it ends.",
        statusWaitingForMove = "Waiting for Bike / Car movement...",
        currentPlaceLabel = "Current Location Status",
        currentCityLabel = "City / Village",
        currentSubAreaLabel = "Sub-Area / Locality",
        currentDistrictLabel = "District",
        currentSpeedLabel = "Speed & Motion",
        locationIntervalLabel = "GPS update interval",
        adaptiveBatteryNote = "45s while stationary, 15s in city traffic, and 5s at highway speeds. Actual battery savings depend on device and conditions; no percentage is guaranteed.",
        travelModeDetected = "Bike / Vehicle Travel Active",
        travelModeStationary = "Stationary / Routine Area (Muted)",
        sameCityOptionTitle = "Same-City Next Place Announcement",
        sameCityOptionSubtitle = "ON: Announces new neighborhoods/areas within the same city.\nOFF: Announces ONLY when entering a new city, town, or village.",
        bikeCarModeTitle = "Bike / Car Travel Detection",
        bikeCarModeSubtitle = "Starts announcing only when traveling by bike/car or moving past the distance threshold. Prevents announcements when staying at the same place.",
        announceOnStartTitle = "Announce Current Place When Turned ON",
        announceOnStartSubtitle = "Speaks your current village/city once immediately when you press ON.",
        minDistanceTitle = "Minimum Travel Distance",
        destinationSectionTitle = "Destination proximity alerts",
        destinationFieldLabel = "Destination town / city",
        destinationSetButton = "Set destination",
        destinationClearButton = "Clear destination",
        destinationLookupInProgress = "Looking up place...",
        destinationSetMessage = "Destination set. Alerts are planned at 10, 5, 2 km and about 500 m (arrival).",
        destinationNotFoundMessage = "Android Geocoder could not find that place. Try a more specific name.",
        destinationNeedsInternetMessage = "Internet is needed to look up a destination and for voice place announcements.",
        destinationAlertExplanation = "Uses GPS distance for 10 km, 5 km, 2 km and about 500 m (arrival) alerts while internet is available. Accuracy depends on GPS and the geocoder.",
        headsetWhereAmITitle = "“Where am I?” from headset button",
        headsetWhereAmISubtitle = "Optional: while tracking is ON, press the headset Next/skip button to speak your current place. Support varies by headset/OEM and may conflict with music controls.",
        offlineReminderTitle = "Offline reminder interval",
        offlineReminderSubtitle = "When tracking is on and the internet is unavailable, announce this at the selected interval using an on-device offline voice or a short beep. Place and destination announcements stay paused.",
        offlineReminderOff = "Off",
        offlineReminderMinuteUnit = "min",
        aboutSectionTitle = "About",
        aboutVersionLabel = "Version",
        contributorsTitle = "Contributors",
        projectDevelopmentCredit = "Project development: Ramesh prathap .R · universalrp2003@gmail.com",
        aiAssistanceCredit = "AI software assistance: Arena.ai Agent Mode",
        sourceRepositoryLabel = "Source code — InternetRadio",
        downloadRepositoryLabel = "Official downloads — RP-Android-Apps",
        otherAppsTitle = "RP Ideas apps",
        liveGuardReleaseLabel = "Live Guard (CleanSweep)",
        radioReleaseLabel = "Ramesh Radio",
        pulseEqReleaseLabel = "PulseEQ",
        vocalRemoverReleaseLabel = "RP Vocal Remover Studio",
        oorArivippuReleaseLabel = "Oor Arivippu",
        testVoiceButton = "Test Selected Voice",
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
        detailsShow = "Details",
        detailsHide = "Hide",
        voiceQualityVeryHigh = "Very high quality",
        voiceQualityHigh = "High quality",
        voiceQualityStandard = "Standard quality",
        speechSpeedLabel = "Speech speed",
        speechSpeedSlow = "Slow",
        speechSpeedNormal = "Normal",
        speechSpeedFast = "Fast",
        moreVoicesButton = "Get more voices (Android settings)",
        destinationChooseTitle = "Choose the right place",
        destinationChooseHint = "Several places can share a name. Check the district, PIN code and distance before you choose.",
        distanceKmSuffix = "km away",
        destinationViewOnMap = "View on map",
        destinationChooseCancel = "Cancel",
        secretMessageReleaseLabel = "Secret Message",
        unknownPlace = "Not detected yet"
    )

    fun forLang(langCode: String): UiStrings {
        return if (langCode == "en") ENGLISH else TAMIL
    }

    /**
     * Formats the spoken place announcement in Tamil ("ta", the default) or English ("en").
     *
     * Place announcements repeat throughout a journey, so they speak only the place names,
     * without filler such as "Entering" or "தற்போது … ஊருக்குள் வந்துள்ளீர்கள்". The closing
     * full stop separates sentences when a district or landmark announcement follows.
     */
    fun buildSpokenAnnouncement(
        voiceLang: String,
        isNewCityOrVillage: Boolean,
        cityOrVillage: String,
        subArea: String?,
        includeSubArea: Boolean
    ): String {
        val includesDistinctSubArea = includeSubArea &&
            !subArea.isNullOrBlank() &&
            !PlaceDataPolicy.samePlaceName(subArea, cityOrVillage)
        return when {
            // A new town, village or city reads the same in both languages: "Alangudi, Santhappettai."
            isNewCityOrVillage && includesDistinctSubArea -> "$cityOrVillage, $subArea."
            isNewCityOrVillage -> "$cityOrVillage."
            // The next area within the same town.
            voiceLang == "en" -> "$subArea, $cityOrVillage."
            else -> "$cityOrVillage, அடுத்த பகுதி $subArea."
        }
    }

    fun buildDestinationAnnouncement(
        voiceLang: String,
        destinationName: String,
        distanceMeters: Int,
        arrived: Boolean
    ): String {
        return if (voiceLang == "en") {
            if (arrived) "You have arrived at $destinationName."
            else "$destinationName is ${distanceMeters / 1_000} kilometres away."
        } else {
            if (arrived) "நீங்கள் $destinationName-ஐ வந்தடைந்துவிட்டீர்கள்."
            else "$destinationName இலக்கு இன்னும் ${distanceMeters / 1_000} கிலோமீட்டர் தொலைவில் உள்ளது."
        }
    }

    fun buildOfflineReminderAnnouncement(voiceLang: String): String =
        if (voiceLang == "en") {
            "Internet is unavailable. Place and destination voice announcements are paused until the connection returns."
        } else {
            "இணைய இணைப்பு இல்லை. இணையம் திரும்பும் வரை இடப் பெயர் மற்றும் இலக்கு குரல் அறிவிப்புகள் நிறுத்தப்பட்டுள்ளன."
        }

    /** Names the new district without filler, matching the place announcements. */
    fun buildDistrictAnnouncement(voiceLang: String, district: String): String {
        val name = PlaceDataPolicy.withoutDistrictSuffix(district)
        return if (voiceLang == "en") "$name district." else "$name மாவட்டம்."
    }

    fun buildHighwayLandmarkAnnouncement(voiceLang: String, landmark: String): String =
        if (voiceLang == "en") "Approaching $landmark."
        else "$landmark பகுதியை நெருங்குகிறீர்கள்."

    fun buildWhereAmIAnnouncement(
        voiceLang: String,
        cityOrVillage: String,
        subArea: String,
        timeText: String
    ): String {
        val place = if (subArea.isBlank() || PlaceDataPolicy.samePlaceName(subArea, cityOrVillage)) {
            cityOrVillage
        } else {
            "$subArea, $cityOrVillage"
        }
        return if (voiceLang == "en") {
            "It is $timeText. Your current location is $place."
        } else {
            "மணி $timeText. தற்போதைய இடம் $place."
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
