package com.universalrp.cleansweep.data

/**
 * The app speaks English and Tamil.
 *
 * How it works: every visible label is written in English in the code and passed through
 * [tr]. If the chosen language is Tamil and a translation exists, the Tamil text is shown;
 * otherwise the English text is kept — nothing is ever blank or machine-mangled. Dynamic
 * values (numbers, file names) are formatted first and looked up afterwards, so a line like
 * "23 items selected" simply falls back to English rather than breaking.
 *
 * The language is written to SharedPreferences as well, so code outside Compose (services)
 * can read it instantly.
 */
enum class AppLang(val id: String, val label: String) {
    EN("en", "English"),
    TA("ta", "தமிழ்"),
    ;

    companion object {
        fun fromId(id: String?): AppLang =
            entries.firstOrNull { it.id == id } ?: TA
    }
}

object Lang {

    /** Prefix for the language mirror that non-Compose code reads. */
    const val PREFS = "cleansweep_state"
    const val KEY = "app_lang"

    /** Where the Tamil name of the app lives, so every screen can show it consistently. */
    private const val APP_NAME_EN = "Live Guard"
    private const val APP_NAME_TA = "லைவ் கார்டு"

    @Volatile
    var current: AppLang = AppLang.TA
        private set

    val isTamil: Boolean get() = current == AppLang.TA

    fun set(lang: AppLang) {
        current = lang
    }

    /**
     * The language as stored on this phone. Compose screens get it from the ViewModel, but a
     * worker or a service can wake up in a fresh process where [current] is still the default —
     * this reads the mirror the settings screen writes.
     */
    fun languageIn(context: android.content.Context): AppLang = try {
        AppLang.fromId(
            context.applicationContext
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getString(KEY, null)
        )
    } catch (e: Exception) {
        AppLang.TA
    }

    fun appName(): String = if (isTamil) APP_NAME_TA else APP_NAME_EN

    /** The tagline under the app title. */
    fun tagline(): String = if (isTamil) {
        "எந்த ஆண்ட்ராய்டு போனுக்கும் க்ளீனர், பேட்டரி ஆரோக்கியம் & பாதுகாப்பு"
    } else {
        "Cleaner, battery health & security for any Android phone"
    }

    /** Translates one label, with optional %s values. */
    fun t(text: String, vararg args: Any): String = translate(current, text, *args)

    /** Services/widgets use the saved app language, not OS locale or a stale global default. */
    fun translate(language: AppLang, text: String, vararg args: Any): String {
        val pattern = if (language == AppLang.TA) TAMIL[text] ?: text else text
        return if (args.isEmpty()) pattern else safeFormat(pattern, args)
    }

    fun forContext(context: android.content.Context, text: String, vararg args: Any): String =
        translate(languageIn(context), text, *args)

    fun translatedKeys(): Set<String> = TAMIL.keys

    private fun safeFormat(pattern: String, args: Array<out Any>): String =
        try {
            String.format(java.util.Locale.US, pattern, *args)
        } catch (e: Exception) {
            pattern
        }

    /** Shown in Settings and About. */
    val languageNote: String get() = if (isTamil) {
        "பயன்பாட்டின் மொழி. தமிழ் தேர்ந்தெடுத்தால் பெயர் “லைவ் கார்டு” எனக் காட்டப்படும்."
    } else {
        "App language. Tamil shows the app name as “லைவ் கார்டு”. The launcher label also follows the phone language."
    }

    /**
     * English text → Tamil. Kept flat on purpose: one table, easy to extend, and a missing
     * entry is harmless (English shows through).
     */
    private val TAMIL: Map<String, String> = mapOf(
        // ------------------------------------------------------------- app chrome
        "Live Guard" to "லைவ் கார்டு",
        "About Live Guard" to "லைவ் கார்டு பற்றி",
        "CleanSweep v2.16" to "சுத்தம் செய்பவர் v2.16",
        "CleanSweep v2.15" to "சுத்தம் செய்பவர் v2.15",
        "CleanSweep v2.14" to "சுத்தம் செய்பவர் v2.14",
        "Background battery & health monitor" to "பின்னணி பேட்டரி & ஆரோக்கிய கண்காணிப்பு",
        "Louder voice alerts" to "அதிக ஒலி வாய்ஸ் எச்சரிக்கைகள்",
        // ------------------------------------------------------ status-bar reading position

        // ------------------------------------------- longer explanations (v2.3 screens)
        "Save" to
            "சேமி",
        "Everything on this screen is measured on your phone right now. Nothing is uploaded; the ping test sends a few kilobytes and the speed test only runs when you tap it." to
            "இந்தத் திரையில் உள்ள அனைத்தும் இப்போது உங்கள் போனிலேயே அளக்கப்படுகின்றன. எதுவும் பதிவேற்றப்படுவதில்லை; பிங் சோதனை சில கிலோபைட்டுகளை அனுப்பும், வேகச் சோதனை நீங்கள் தட்டினால் மட்டுமே இயங்கும்.",
        "dBm is the honest number: −60 is excellent, −100 is barely usable, and a stronger bar count can lie. Values are read from the system, not estimated." to
            "dBm தான் உண்மையான அளவு: −60 மிகச் சிறந்தது, −100 அரிதாகவே பயன்படும், அதிக பார்கள் பொய் சொல்லலாம். மதிப்புகள் சாதனத்திலிருந்து படிக்கப்படுகின்றன, மதிப்பிடப்படவில்லை.",
        "Android only reveals tower identity to apps that have Location allowed, and some carriers hide it completely. A dual-SIM phone shows the towers of the SIM that is carrying data." to
            "இருப்பிட அனுமதி உள்ள ஆப்களுக்கு மட்டுமே ஆண்ட்ராய்டு டவர் தகவலைத் தரும்; சில ஆபரேட்டர்கள் அதை முழுவதுமாக மறைக்கின்றனர். இரட்டை சிம் போனில் தரவு செல்லும் சிம்மின் டவர்கள் காட்டப்படும்.",
        "Latency (ping) is how long a round trip takes; jitter is how much it wobbles. High jitter makes video calls stutter even when the ping looks fine." to
            "பிங் என்பது ஒரு சுற்றுப் பயணத்தின் நேரம்; ஜிட்டர் என்பது அது எவ்வளவு ஏறி இறங்குகிறது என்பது. பிங் நன்றாக இருந்தாலும் அதிக ஜிட்டர் வீடியோ அழைப்புகளைத் தடுமாறச் செய்யும்.",
        "Tap to see the IP and operator the internet sees (one small request)." to
            "இணையம் பார்க்கும் IP மற்றும் ஆபரேட்டரைப் பார்க்க தட்டவும் (ஒரு சிறிய கோரிக்கை).",
        "No answer" to
            "பதில் இல்லை",
        "This downloads real data and measures how fast it arrived. Small sizes are kind to a metered plan; on an unlimited 5G plan pick the biggest, because the link needs a few seconds before it reaches full speed." to
            "இது உண்மையான தரவைப் பதிவிறக்கி, எவ்வளவு வேகமாக வந்தது என்பதை அளக்கும். சிறிய அளவுகள் குறைந்த தரவு திட்டத்திற்கு உகந்தவை; வரம்பற்ற 5G திட்டத்தில் மிகப் பெரியதைத் தேர்ந்தெடுக்கவும், ஏனெனில் முழு வேகம் வர சில வினாடிகள் ஆகும்.",

        // ------------------------------------------------------- voice & daily watch
        "Speaking… if you hear nothing, check that a text-to-speech engine is installed." to
            "பேசுகிறேன்… எதுவும் கேட்கவில்லை என்றால், உங்கள் போனில் text-to-speech இன்ஜின் நிறுவப்பட்டுள்ளதா எனப் பார்க்கவும்.",
        "No text-to-speech engine is available on this phone." to
            "இந்தப் போனில் text-to-speech இன்ஜின் இல்லை.",
        "Taking today's reading…" to
            "இன்றைய அளவீட்டை எடுக்கிறேன்…",
        "The daily check could not run right now." to
            "தினசரி சோதனையை இப்போது இயக்க முடியவில்லை.",
        "Add an API key (or pick the free AI) and ask again." to
            "API சாவியைச் சேர்க்கவும் (அல்லது இலவச AI-ஐத் தேர்ந்தெடுக்கவும்), பிறகு மீண்டும் கேளுங்கள்.",
        "No speech recogniser is installed on this phone — the keyboard still works." to
            "இந்தப் போனில் பேச்சு அறியும் கருவி இல்லை — கீபோர்டு இன்னும் வேலை செய்யும்.",
        "Ask CleanSweep…" to
            "சுத்தம் செய்பவரிடம் கேளுங்கள்…",
        "The speech recogniser could not be opened." to
            "பேச்சு அறியும் கருவியைத் திறக்க முடியவில்லை.",
        "Speak" to
            "பேசு",
        "Read aloud" to
            "சத்தமாகப் படி",
        "What the AI read just now:" to
            "AI இப்போது படித்தது:",
        "Show all %d" to
            "அனைத்தையும் காட்டு (%d)",
        "Show less" to
            "குறைத்துக் காட்டு",
        "EN" to
            "EN",
        "த" to
            "த",
        "Daily brief" to
            "தினசரி அறிக்கை",
        "Once a day CleanSweep checks the battery, temperature, storage, junk and security, says a short summary out loud and keeps the full report here." to
            "ஒரு நாளைக்கு ஒரு முறை பேட்டரி, வெப்பம், சேமிப்பு, குப்பை, பாதுகாப்பு அனைத்தையும் சுத்தம் செய்பவர் சோதித்து, சுருக்கத்தைச் சத்தமாகச் சொல்லி, முழு அறிக்கையை இங்கே வைக்கும்.",
        "Run the daily check now" to
            "இப்போதே தினசரி சோதனையை இயக்கு",
        "Daily brief: on" to
            "தினசரி அறிக்கை: இயக்கத்தில்",
        "Daily brief: off" to
            "தினசரி அறிக்கை: நிறுத்தத்தில்",
        "Voice & daily watch" to
            "குரல் & தினசரி கண்காணிப்பு",
        "Warnings spoken out loud, quiet hours, and the daily brief." to
            "எச்சரிக்கைகள் சத்தமாக, அமைதி நேரம், மற்றும் தினசரி அறிக்கை.",
        "Answer language" to
            "பதில் மொழி",
        "Which language the AI writes in. Separate from the menu language — you can read English menus and still get Tamil answers." to
            "AI எந்த மொழியில் எழுத வேண்டும். மெனுவின் மொழியிலிருந்து இது தனி — ஆங்கில மெனுவுடன் தமிழ் பதில்களும் பெறலாம்.",
        "Same as I type" to
            "நான் எழுதும் மொழியே",
        "Storage nearly full" to
            "சேமிப்பு கிட்டத்தட்ட நிரம்பியது",
        "Under 1 GB free — the warning that gets worse the longer it waits." to
            "1 GB-க்குக் கீழே காலி — தாமதமாகும்போது மோசமாகும் எச்சரிக்கை.",
        "Charger removed early" to
            "சார்ஜர் முன்பே கழற்றப்பட்டது",
        "Unplugged below %s%%, and only after the hour you set below." to
            "%s%%-க்குக் கீழே கழற்றினால், நீங்கள் கீழே அமைத்த நேரத்திற்குப் பிறகு மட்டும்.",
        "Charger connected but not charging" to
            "சார்ஜர் இணைந்துள்ளது, ஆனால் சார்ஜ் ஆகவில்லை",
        "A worn cable or weak charger shows as plugged in and delivers nothing." to
            "தேய்ந்த கேபிள் அல்லது பலவீனமான சார்ஜர் இணைந்ததுபோலக் காட்டும், ஆனால் எதுவும் தராது.",
        "Battery health looks worn" to
            "பேட்டரி ஆரோக்கியம் பலவீனமாக உள்ளது",
        "Said at most once a week, and only when the kernel reports the real capacity." to
            "வாரத்திற்கு ஒரு முறைக்கு மேல் அல்ல; கர்னல் உண்மையான திறனைத் தெரிவித்தால் மட்டும்.",
        "Wi-Fi ⇄ mobile data switches" to
            "Wi-Fi ⇄ மொபைல் டேட்டா மாற்றங்கள்",
        "Off by default: a phone that hops networks would otherwise talk all day." to
            "இயல்பாக நிறுத்தத்தில்: நெட்வொர்க் மாறிக்கொண்டே இருக்கும் போன் நாள் முழுவதும் பேசும்.",
        "New device joined my Wi-Fi" to
            "என் Wi-Fi-ல் புதிய சாதனம் இணைந்தது",
        "Uses the network scan; spoken only when it is on and you run a scan." to
            "நெட்வொர்க் ஸ்கேனைப் பயன்படுத்தும்; இது இயக்கத்தில் இருந்து நீங்கள் ஸ்கேன் செய்தால் மட்டும் பேசும்.",
        "Early-unplug reminders only after" to
            "முன்கூட்டிய கழற்றல் நினைவூட்டல் இந்த நேரத்திற்குப் பிறகு மட்டும்",

        // ------------------------------------------------------------- v2.6
        "Scanning your storage…" to
            "உங்கள் சேமிப்பை ஸ்கேன் செய்கிறது…",
        "Starting…" to
            "தொடங்குகிறது…",
        "files scanned" to
            "கோப்புகள் ஸ்கேன்",
        "junk found" to
            "குப்பை கண்டறியப்பட்டது",
        "Nothing is scanning right now." to
            "இப்போது எதுவும் ஸ்கேன் ஆகவில்லை.",
        "Start the scan" to
            "ஸ்கேனைத் தொடங்கு",
        "Ping" to
            "பிங்",
        "Jitter" to
            "ஜிட்டர்",
        "Wi-Fi connected" to
            "Wi-Fi இணைக்கப்பட்டுள்ளது",
        "Natural voice (online)" to
            "இயற்கையான குரல் (ஆன்லைன்)",
        "Sounds far more human than the phone's built-in robot voice: Android's own " +
            "cloud voice reads the sentence instead of the offline one. Only the words " +
            "being spoken leave the phone — never a file, never a message." to
            "போனின் இயந்திரக் குரலைவிட மிக இயற்கையாக ஒலிக்கும்: சொல்லப்படும் வாக்கியத்தை " +
            "Android-ன் கிளவுட் குரலே படிக்கும். பேசப்படும் வார்த்தைகள் மட்டுமே போனை விட்டு " +
            "வெளியேறும் — கோப்புகளோ செய்திகளோ அல்ல.",
        "Works in Tamil and English. If the phone has no cloud voice installed, or the " +
            "network is out, the offline voice reads the same line instead — " +
            "announcements never go silent. No API key is used for this." to
            "தமிழ், ஆங்கிலம் இரண்டும் வேலை செய்யும். கிளவுட் குரல் நிறுவப்படவில்லை என்றாலோ " +
            "இணையம் இல்லாவிட்டாலோ, அதே வரியை ஆஃப்லைன் குரல் படிக்கும் — அறிவிப்புகள் " +
            "ஒருபோதும் அமைதியாகாது. இதற்கு API விசை தேவையில்லை.",
        "Last crash" to
            "கடைசி செயலிழப்பு",
        "The last time CleanSweep stopped by itself. Nothing is sent " +
            "anywhere — this is here so you can read it out or screenshot it." to
            "CleanSweep தானாக நின்ற கடைசி முறை. இது எங்கும் அனுப்பப்படுவதில்லை — " +
            "நீங்கள் படிக்க அல்லது ஸ்கிரீன்ஷாட் எடுக்க மட்டுமே இங்கே உள்ளது.",
        "Clear" to
            "அழி",
        "Free software under the GNU GPL v3" to
            "GNU GPL v3 இன் கீழ் இலவச மென்பொருள்",
        "Speak to me" to
            "என்னிடம் பேசு",
        "CleanSweep talks: warnings about the battery, heat and the daily brief." to
            "சுத்தம் செய்பவர் பேசும்: பேட்டரி, வெப்பம் பற்றிய எச்சரிக்கைகள் மற்றும் தினசரி அறிக்கை.",
        "Voice is off. Nothing will be spoken, at any hour." to
            "குரல் நிறுத்தத்தில் உள்ளது. எந்த நேரத்திலும் எதுவும் பேசப்படாது.",
        "Hear the voice" to
            "குரலைக் கேள்",
        "Speaking uses Android's own text-to-speech engine. If your phone has a Tamil voice installed, Tamil text is spoken in Tamil; otherwise the English wording is spoken instead of reading Tamil in an English accent." to
            "பேசுவதற்கு ஆண்ட்ராய்டின் சொந்த text-to-speech இன்ஜின் பயன்படுகிறது. உங்கள் போனில் தமிழ்க் குரல் நிறுவப்பட்டிருந்தால் தமிழ் உரை தமிழிலேயே பேசப்படும்; இல்லையெனில் தமிழை ஆங்கில உச்சரிப்பில் படிக்காமல் ஆங்கில வாக்கியம் பேசப்படும்.",
        "Quiet hours" to
            "அமைதி நேரம்",
        "Nothing is spoken between these hours — not even important warnings. Anything you ask for yourself is still spoken when you ask." to
            "இந்த நேரங்களுக்கு இடையில் எதுவும் பேசப்படாது — முக்கியமான எச்சரிக்கைகள் கூட. நீங்கள் நேரடியாகக் கேட்பதை மட்டும் அப்போதே பேசும்.",
        "to" to
            "முதல்",
        "All day" to
            "நாள் முழுவதும்",
        "It is quiet time right now, so warnings are paused." to
            "இப்போது அமைதி நேரம், எனவே எச்சரிக்கைகள் நிறுத்தப்பட்டுள்ளன.",
        "What CleanSweep may say" to
            "சுத்தம் செய்பவர் என்ன சொல்லலாம்",
        "Battery low" to
            "பேட்டரி குறைவு",
        "Under %s%%, while the phone is running on the battery." to
            "போன் பேட்டரியில் இயங்கும்போது %s%%-க்குக் கீழே.",
        "Battery full" to
            "பேட்டரி முழுவதும்",
        "When the charge reaches 100% — a reminder to unplug." to
            "சார்ஜ் 100% ஆனதும் — சார்ஜரைக் கழற்ற நினைவூட்டல்.",
        "Running hot" to
            "சூடாக இயங்குகிறது",
        "Battery above %s°C or the processor above %s°C." to
            "பேட்டரி %s°C-க்கு மேல் அல்லது செயலி %s°C-க்கு மேல்.",
        "Charging started" to
            "சார்ஜ் தொடங்கியது",
        "A short word when the charger goes in — off keeps plug-ins silent." to
            "சார்ஜர் இணைந்ததும் ஒரு சிறு வாசகம் — நிறுத்தினால் எதுவும் பேசாது.",
        "One short spoken summary a day, with the details in a notification." to
            "ஒரு நாளைக்கு ஒரு சிறு பேச்சுச் சுருக்கம்; விவரங்கள் அறிவிப்பில்.",
        "Read AI answers aloud" to
            "AI பதில்களைச் சத்தமாகப் படி",
        "Speak the answer when you ask the assistant something." to
            "நீங்கள் உதவியாளரிடம் கேட்கும்போது பதிலைப் பேசும்.",
        "Daily full check" to
            "தினசரி முழு சோதனை",
        "Once a day CleanSweep reads the battery, temperature, storage, junk and security, asks the AI for a two-sentence summary when a key is saved, speaks it and posts the full report." to
            "ஒரு நாளைக்கு ஒரு முறை பேட்டரி, வெப்பம், சேமிப்பு, குப்பை, பாதுகாப்பு அனைத்தையும் படித்து, சாவி சேமிக்கப்பட்டிருந்தால் AI-யிடம் இரண்டு வாக்கியச் சுருக்கம் கேட்டு, அதைப் பேசி, முழு அறிக்கையை அறிவிப்பாக அனுப்பும்.",
        "Run it at" to
            "இயக்கும் நேரம்",
        "Privacy: the microphone is used only when you tap the mic button in the assistant, the phone's own recogniser turns your words into text, and CleanSweep never records or keeps audio. Voice announcements are made on the phone and can be switched off here at any time." to
            "தனியுரிமை: உதவியாளரில் உள்ள மைக் பொத்தானைத் தட்டும்போது மட்டுமே மைக் பயன்படுகிறது; போனின் சொந்த பேச்சு அறியும் கருவி உங்கள் வார்த்தைகளை உரையாக மாற்றும்; சுத்தம் செய்பவர் ஒருபோதும் ஒலியைப் பதிவு செய்யாது, வைத்திருக்காது. குரல் அறிவிப்புகள் போனிலேயே நிகழ்கின்றன; இங்கே எப்போது வேண்டுமானாலும் நிறுத்தலாம்.",
        "Auto" to "தானியங்கி",
        "Drag" to "இழுத்து வை",
        "Pick a test size first" to "முதலில் சோதனை அளவைத் தேர்ந்தெடுக்கவும்",
        "Test size — tap the size you want" to
            "சோதனை அளவு — உங்களுக்கு வேண்டிய அளவைத் தட்டவும்",
        "Pick a test size first — that is how much data the test uses." to
            "முதலில் சோதனை அளவைத் தேர்ந்தெடுக்கவும் — சோதனை அவ்வளவு தரவைப் பயன்படுத்தும்.",
        "Nothing runs until you pick a size — the test costs that much data." to
            "நீங்கள் அளவைத் தேர்ந்தெடுக்கும் வரை எதுவும் இயங்காது — சோதனை அவ்வளவு தரவைச் செலவாக்கும்.",
        "Done" to "முடிந்தது",
        "Move left" to "இடது பக்கம் நகர்த்து",
        "Move right" to "வலது பக்கம் நகர்த்து",
        "Move up" to "மேலே நகர்த்து",
        "Move down" to "கீழே நகர்த்து",
        "Reading back at the automatic spot (beside the camera)." to
            "வாசிப்பு மீண்டும் தானியங்கி இடத்தில் (கேமரா அருகில்).",
        "Drag mode on — slide the watt reading where you want it, then tap Done." to
            "இழுத்து வை இயக்கம் இயக்கத்தில் — வாட்ட்ஸ் வாசிப்பை விரலால் நகர்த்தி, சரியான இடத்தில் வைத்து “முடிந்தது” தட்டவும்.",
        "Saved. The reading gets its place back every time you charge." to
            "சேமிக்கப்பட்டது. ஒவ்வொரு முறை சார்ஜ் செய்யும்போதும் வாசிப்பு அதே இடத்தில் வரும்.",
        "Cleaner, battery health & security for any Android phone" to
            "எந்த ஆண்ட்ராய்டு போனுக்கும் க்ளீனர், பேட்டரி ஆரோக்கியம் & பாதுகாப்பு",
        "Settings" to "அமைப்புகள்",
        "Close" to "மூடு",
        "Cancel" to "ரத்து",
        "Delete" to "நீக்கு",
        "Clean" to "சுத்தம் செய்",
        "Great!" to "அருமை!",
        "All" to "அனைத்தும்",
        "None" to "எதுவும் இல்லை",
        "Add" to "சேர்",
        "free" to "இலவசம்",
        "LIVE" to "நேரடி",
        "Try again" to "மீண்டும் முயற்சி",
        "What went wrong" to "என்ன தவறு நடந்தது",
        "Check again" to "மீண்டும் சரிபார்",
        "Use a free AI" to "இலவச AI-ஐப் பயன்படுத்து",
        "Which AI answers?" to "எந்த AI பதில் தருகிறது?",
        "AI settings" to "AI அமைப்புகள்",
        "Set up AI" to "AI அமைக்க",
        "Test" to "சோதி",
        "Load models" to "மாடல்களை ஏற்று",
        "Save settings" to "அமைப்புகளைச் சேமி",
        "Try the free lane" to "இலவச வழியை முயற்சி",

        // ----------------------------------------------------------- home screen
        "Phone health" to "போன் ஆரோக்கியம்",
        "Battery, heat, CPU, watts" to "பேட்டரி, வெப்பம், CPU, வாட்",
        "Security check" to "பாதுகாப்பு சோதனை",
        "Permissions, risky apps" to "அனுமதிகள், ஆபத்தான ஆப்ஸ்",
        "Wi-Fi devices" to "Wi-Fi சாதனங்கள்",
        "Who is on your network" to "உங்கள் நெட்வொர்க்கில் யார் இருக்கிறார்கள்",
        "Installed apps" to "நிறுவப்பட்ட ஆப்ஸ்",
        "Bloatware, unused apps" to "தேவையற்ற, பயன்படுத்தாத ஆப்ஸ்",
        "Mobile & data" to "மொபைல் & டேட்டா",
        "Signal, speed test, data used" to "சிக்னல், வேகப் பரிசோதனை, டேட்டா பயன்பாடு",
        "Phone health, security & network" to "போன் ஆரோக்கியம், பாதுகாப்பு & நெட்வொர்க்",
        "Quick cleaning actions" to "விரைவு சுத்தம்",
        "Each tile scans for its own kind of junk only — then you tick what to clean." to
            "ஒவ்வொரு பெட்டியும் அதற்குரிய குப்பையை மட்டுமே தேடும் — பிறகு நீங்கள் தேர்ந்தெடுக்க வேண்டும்.",
        "Temp & junk" to "தற்காலிக & குப்பை கோப்புகள்",
        "Only .tmp, .log, leftovers" to ".tmp, .log, எஞ்சியவை மட்டும்",
        "Thumbnails" to "தம்ப்நெயில் கேஷ்",
        "Only image cache files" to "பட கேஷ் கோப்புகள் மட்டும்",
        "APK files" to "APK கோப்புகள்",
        "Only installer packages" to "இன்ஸ்டாலர் கோப்புகள் மட்டும்",
        "Duplicates" to "நகல் கோப்புகள்",
        "Only identical files" to "ஒரே மாதிரியான கோப்புகள் மட்டும்",
        "Duplicate scan hit the 1,500-candidate limit. There may be more duplicates than shown." to
            "நகல் தேடல் 1,500 கோப்பு வரம்பை எட்டியது. காட்டியதை விட அதிக நகல்கள் இருக்கலாம்.",
        "Battery temperature and voltage above are validated Android battery-broadcast readings; OEM support may vary." to
            "மேலுள்ள பேட்டரி வெப்பநிலை மற்றும் மின்னழுத்தம் சரிபார்க்கப்பட்ட Android பேட்டரி ஒலிபரப்பு அளவீடுகள்; உற்பத்தியாளர் ஆதரவு மாறலாம்.",
        "Battery temperature source: Android battery broadcast (validated)." to
            "பேட்டரி வெப்பநிலை ஆதாரம்: சரிபார்க்கப்பட்ட Android பேட்டரி ஒலிபரப்பு.",
        "Battery temperature source: kernel thermal zone (OEM-dependent)." to
            "பேட்டரி வெப்பநிலை ஆதாரம்: கர்னல் வெப்ப மண்டலம் (உற்பத்தியாளர் சார்ந்தது).",
        "Battery temperature is unavailable; this system may restrict sensor access." to
            "பேட்டரி வெப்பநிலை கிடைக்கவில்லை; இந்த அமைப்பு சென்சார் அணுகலைக் கட்டுப்படுத்தலாம்.",
        "Empty folders" to "காலி போல்டர்கள்",
        "Only empty folders" to "காலி போல்டர்கள் மட்டும்",
        "Old downloads" to "பழைய பதிவிறக்கங்கள்",
        "Only old Download files" to "Download-ல் பழைய கோப்புகள் மட்டும்",
        "Large files" to "பெரிய கோப்புகள்",
        "Only files over the limit" to "அளவை மீறிய கோப்புகள் மட்டும்",
        "App cache" to "ஆப் கேஷ்",
        "Per-app caches, cached" to "ஆப் வாரியாக கேஷ் சுத்தம்",
        "Ask the assistant" to "உதவியாளரிடம் கேளுங்கள்",
        "Storage access needed" to "சேமிப்பு அனுமதி தேவை",
        "Allow storage access" to "சேமிப்பு அனுமதி தருக",
        "Allow storage access to delete files" to "கோப்புகளை நீக்க சேமிப்பு அனுமதி தேவை",
        "Works on every Android phone" to "எல்லா ஆண்ட்ராய்டு போன்களிலும் வேலை செய்யும்",
        "Preselect after a scan" to "ஸ்கேன் முடிந்ததும் முன்-தேர்வு",

        // --------------------------------------------------------------- scanning
        "Cancel scan" to "ஸ்கேனை நிறுத்து",
        "Scan results" to "ஸ்கேன் முடிவுகள்",
        "Nothing to clean!" to "சுத்தம் செய்ய ஒன்றும் இல்லை!",
        "Delete permanently?" to "நிரந்தரமாக நீக்கவா?",
        "Nothing was deleted" to "எதுவும் நீக்கப்படவில்லை",
        "Open permission settings" to "அனுமதி அமைப்புகளைத் திற",
        "Cleaning complete ✨" to "சுத்தம் முடிந்தது ✨",
        "Cleaning…" to "சுத்தம் செய்கிறது…",

        // -------------------------------------------------------------- assistant
        "CleanSweep Assistant" to "லைவ் கார்டு உதவியாளர்",
        "Detailed answers" to "விரிவான பதில்கள்",
        "Detailed assistant answers" to "விரிவான உதவியாளர் பதில்கள்",
        "Off = the on-device assistant replies in one short line" to
            "ஆஃப் = உதவியாளர் ஒரு சிறு வரியில் பதில் தரும்",
        "Ask about your storage…" to "உங்கள் சேமிப்பைப் பற்றிக் கேளுங்கள்…",
        "CleanSweep AI" to "லைவ் கார்டு AI",
        "Thinking on this device…" to "இந்த போனிலேயே யோசிக்கிறது…",

        // ----------------------------------------------------------- health screen
        "Full analysis with AI" to "AI மூலம் முழு பரிசோதனை",
        "Charging watts beside the clock" to "கடிகாரம் அருகே சார்ஜ் வாட்",
        "Allow overlay" to "ஓவர்லே யை அனுமதி",
        "Floating volume button" to "மிதக்கும் ஒலி கட்டுப்பாட்டு பொத்தான்",
        "Optional separate overlay button. Tap it to open Android's native media-volume panel; it does not change volume by itself." to
            "விருப்பமான தனி மேலடுக்கு பொத்தான். Android மீடியா ஒலி கட்டுப்பாட்டைத் திறக்க தட்டவும்; இது தானாக ஒலியளவை மாற்றாது.",
        "This button is separate from the charging/data pill but uses the same Android overlay permission." to
            "இந்த பொத்தான் சார்ஜ்/டேட்டா பட்டையிலிருந்து தனியானது; அதே Android மேலடுக்கு அனுமதியைப் பயன்படுத்துகிறது.",
        "On. The separate floating button opens Android's media-volume panel; turning it off leaves the charging/data pill unchanged." to
            "இயக்கத்தில் உள்ளது. இந்த தனி மிதக்கும் பொத்தான் Android மீடியா ஒலி பலகையைத் திறக்கும்; இதை அணைப்பது சார்ஜ்/டேட்டா பட்டையை மாற்றாது.",
        "Open system media volume controls" to "சிஸ்டம் மீடியா ஒலி கட்டுப்பாட்டைத் திற",
        "Tap to open system volume controls; drag to move" to "சிஸ்டம் ஒலி கட்டுப்பாட்டைத் திறத் தட்டவும்; நகர்த்த இழுக்கவும்",
        "Battery" to "பேட்டரி",
        "Temperature" to "வெப்பநிலை",
        "Processor" to "செயலி (CPU)",
        "Memory & storage" to "மெமரி & சேமிப்பு",
        "Reading the sensors…" to "சென்சார்களைப் படிக்கிறது…",

        // -------------------------------------------------------------- app cache
        "App cache cleaner" to "ஆப் கேஷ் க்ளீனர்",
        "System apps" to "சிஸ்டம் ஆப்ஸ்",
        "Select apps" to "ஆப்ஸைத் தேர்ந்தெடு",
        "Usage access needed" to "பயன்பாட்டு அனுமதி தேவை",
        "Grant usage access" to "பயன்பாட்டு அனுமதி தருக",
        "Guided two-tap clean" to "வழிகாட்டும் இரண்டு-தொடு சுத்தம்",
        "No Accessibility permission needed — v1.3 dropped it" to
            "Accessibility அனுமதி தேவையில்லை — v1.3-ல் நீக்கப்பட்டது",
        "1. Tick apps  →  2. Clean cache  →  3. Tap “Clear cache” in Settings  →  4. Back → Next app" to
            "1. ஆப்ஸைத் தேர்ந்தெடு  →  2. கேஷ் சுத்தம்  →  3. Settings-ல் “Clear cache”  →  4. திரும்பி வா → அடுத்த ஆப்",

        // ------------------------------------------------------------ apps screen
        "Search an app or package" to "ஆப் அல்லது பேக்கேஜ் தேடு",
        "Nothing matches that filter." to "இந்த வடிகட்டலுக்கு எதுவும் பொருந்தவில்லை.",
        "Permissions worth knowing about" to "தெரிந்து கொள்ள வேண்டிய அனுமதிகள்",
        "Want a second opinion?" to "இரண்டாவது கருத்து வேண்டுமா?",

        // -------------------------------------------------------- network screen
        "Wi-Fi & network" to "Wi-Fi & நெட்வொர்க்",
        "Permission needed for Wi-Fi details" to "Wi-Fi விவரங்களுக்கு அனுமதி தேவை",
        "App settings" to "ஆப் அமைப்புகள்",

        // ------------------------------------------------------ settings screen
        "Sound effects" to "ஒலி விளைவுகள்",
        "Play a chime when scanning and cleaning" to "ஸ்கேன் மற்றும் சுத்தத்தின் போது ஒலி",
        "AI analysis (optional)" to "AI பரிசோதனை (விருப்பம்)",
        "Open AI settings" to "AI அமைப்புகளைத் திற",
        "Assistant answers" to "உதவியாளர் பதில்கள்",
        "On-device" to "போனிலேயே",
        "Online AI" to "ஆன்லைன் AI",
        "Charging status in the status bar" to "ஸ்டேட்டஸ் பாரில் சார்ஜிங் தகவல்",
        "Ongoing notification with charging watts, battery % and time to full" to
            "சார்ஜ் வாட், பேட்டரி %, முழு சார்ஜ் ஆகும் நேரம் — நிலையான அறிவிப்பில்",
        "Watt reading beside the clock" to "கடிகாரம் அருகே வாட் வாசிப்பு",
        "Tiny “⚡ 3.9 W” pill in the empty part of the status bar while charging" to
            "சார்ஜ் செய்யும் போது ஸ்டேட்டஸ் பாரின் காலி இடத்தில் சிறிய “⚡ 3.9 W”",
        "Allow display over other apps" to "மற்ற ஆப்ஸின் மேல் காட்ட அனுமதி",
        "Scan hidden folders" to "மறைந்த போல்டர்களை ஸ்கேன் செய்",
        "Look inside folders starting with “.” (more junk, slightly slower)" to
            "“.” உடன் தொடங்கும் போல்டர்களுக்குள் தேடு (அதிக குப்பை, சற்று மெதுவாக)",
        "Duplicate detection minimum size" to "நகல் கண்டறியும் குறைந்த அளவு",
        "Large file threshold" to "பெரிய கோப்பு அளவு வரம்பு",
        "Old downloads age" to "பழைய பதிவிறக்க வயது",
        "Only flag APKs of installed apps" to "நிறுவிய ஆப்ஸின் APK மட்டும்",
        "Keeps installers for apps you haven't installed yet" to
            "இன்னும் நிறுவாத ஆப்ஸின் இன்ஸ்டாலர்களை வைத்திருக்கும்",
        "Protected folders" to "பாதுகாக்கப்பட்ட போல்டர்கள்",
        "These folders are never scanned. You can type a folder like “WhatsApp” or “DCIM/Camera”." to
            "இந்த போல்டர்கள் ஸ்கேன் செய்யப்படாது. “WhatsApp” அல்லது “DCIM/Camera” போல தட்டச்சு செய்யலாம்.",
        "e.g. WhatsApp" to "உ.ம். WhatsApp",
        "Language" to "மொழி",
        "Menu language" to "மெனுவின் மொழி",

        // -------------------------------------------------------- mobile screen
        "Mobile network" to "மொபைல் நெட்வொர்க்",
        "Signal quality" to "சிக்னல் தரம்",
        "Internet quality" to "இன்டர்நெட் தரம்",
        "Ping & jitter" to "பிங் & ஜிட்டர்",
        "Speed test" to "வேகப் பரிசோதனை",
        "Data usage" to "டேட்டா பயன்பாடு",
        "Today" to "இன்று",
        "Wi-Fi" to "Wi-Fi",
        "Mobile data" to "மொபைல் தரவு",
        "Ping now" to "இப்போது பிங் செய்",
        "Start test" to "பரிசோதனையைத் தொடங்கு",
        "Stop" to "நிறுத்து",
        "SIM & operator" to "SIM & ஆபரேட்டர்",
        "Cell towers" to "செல் டவர்கள்",
        "Public IP & ISP" to "பொது IP & ISP",
        "Test size" to "பரிசோதனை அளவு",
        "Data use is real — pick a size you can afford." to
            "இது உண்மையான டேட்டாவைப் பயன்படுத்தும் — பொருத்தமான அளவைத் தேர்ந்தெடுக்கவும்.",

        // ------------------------------------------------------------- about
        "Privacy" to "தனியுரிமை",
        "Installing & updating (Play Protect)" to "நிறுவல் & புதுப்பித்தல் (Play Protect)",
        "Why some things can't be cleaned" to "சிலவற்றை ஏன் சுத்தம் செய்ய முடியாது",
        "Suggested routine" to "பரிந்துரைக்கப்பட்ட வழக்கம்",
        "Contributors" to "பங்களிப்பாளர்கள்",
        "App name" to "ஆப் பெயர்",

        // ------------------------------------------------- mobile & data screen
        "Phone permission gives the real numbers" to "Phone அனுமதி உண்மையான எண்களைத் தரும்",
        "Allow and rescan" to "அனுமதித்து மீண்டும் படி",
        "Cell towers in reach" to "அருகில் உள்ள செல் டவர்கள்",
        "in use" to "பயன்பாட்டில்",
        "Check" to "சரிபார்",
        "Reading…" to "படிக்கிறது…",
        "Tap refresh" to "புதுப்பிக்கத் தொடவும்",
        "Asking the network…" to "நெட்வொர்க்கிடம் கேட்கிறது…",
        "no answer" to "பதில் இல்லை",
        "Measuring…" to "அளக்கிறது…",
        "Testing…" to "பரிசோதிக்கிறது…",
        "Download" to "பதிவிறக்கம்",
        "Upload" to "பதிவேற்றம்",
        "Downloading" to "பதிவிறக்குகிறது",
        "Uploading" to "பதிவேற்றுகிறது",
        "Starting the test" to "பரிசோதனை தொடங்குகிறது",
        "Starting the upload" to "பதிவேற்றம் தொடங்குகிறது",
        "The test failed" to "பரிசோதனை தோல்வி",
        "Upload test failed" to "பதிவேற்ற பரிசோதனை தோல்வி",
        "received" to "பெற்றது",
        "sent" to "அனுப்பியது",
        "Since the phone was switched on" to "போனை இயக்கியதிலிருந்து",
        "Total today" to "இன்றைய மொத்தம்",
        "Apps using the most data today" to "இன்று அதிக டேட்டா பயன்படுத்திய ஆப்ஸ்",
        "Usage access gives today's exact figures" to "Usage access தந்தால் இன்றைய துல்லியமான எண்கள்",
        "Also test upload" to "பதிவேற்றமும் சோதிக்க",
        "Sends the same amount again — off by default to save data." to
            "அதே அளவை மீண்டும் அனுப்பும் — டேட்டா சேமிக்க இயல்பாக ஆஃப்",
        "You are on mobile data — this test downloads about" to
            "நீங்கள் மொபைல் டேட்டாவில் உள்ளீர்கள் — இந்த பரிசோதனை பதிவிறக்குவது சுமார்",
        "twice (download + upload)" to "இரண்டு மடங்கு (பதிவிறக்கம் + பதிவேற்றம்)",
        "Unlimited 5G" to "அன்லிமிடெட் 5G",
        "Scan my network with AI" to "AI மூலம் நெட்வொர்க்கை ஸ்கேன் செய்க",
        "About" to "பற்றி",
        "Where it sits" to "எங்கே இருக்கும்",
        "Each tap moves it a little; “Auto” puts it back beside the camera." to
            "ஒவ்வொரு தொடுதலும் சிறிது நகர்த்தும்; “Auto” கேமரா அருகே திரும்ப வைக்கும்.",
        "The position is remembered, and the reading still only appears while charging." to
            "இடம் நினைவில் வைக்கப்படும்; சார்ஜ் செய்யும் போது மட்டுமே தெரியும்.",
        "Move it anywhere: phones put VoLTE, VPN, the carrier name or the battery " +
            "percentage in that strip, so the free space is different on every model." to
            "எங்கும் நகர்த்தலாம்: ஒவ்வொரு போனும் அந்தப் பட்டையில் VoLTE, VPN, ஆபரேட்டர் " +
            "பெயர் அல்லது பேட்டரி % எழுதும் — காலி இடம் ஒவ்வொரு போனிலும் வேறு.",
        "your phone writes VoLTE, VPN, the carrier name and the battery " +
            "percentage up there, so move the reading wherever it is free." to
            "உங்கள் போன் அங்கே VoLTE, VPN, ஆபரேட்டர் பெயர், பேட்டரி % எழுதும் — " +
            "காலியாக இருக்கும் இடத்திற்கு நகர்த்துங்கள்.",
        "Mobile data is ON." to "மொபைல் டேட்டா ஆன்.",
        // ---------------------------------------------------------------- v2.34 bug fixes
        "Status-bar data and watts" to "நிலைப் பட்டை டேட்டா மற்றும் வாட்ஸ்",
        "Watts only while connected; data and D/U lights return with background monitoring" to
            "சார்ஜர் இணைந்தால் வாட்ஸ் மட்டும்; பின்னணி கண்காணிப்பில் அகற்றியதும் டேட்டா மற்றும் D/U விளக்குகள் திரும்பும்",
        "Charger connected: watts only. Unplugged: data and D/U lights return with background monitoring. — W means the phone did not report power." to
            "சார்ஜர் இணைந்தால் வாட்ஸ் மட்டும். அகற்றியதும் பின்னணி கண்காணிப்பில் டேட்டா மற்றும் D/U விளக்குகள் திரும்பும். — W என்றால் மின்சக்தி அளவை போன் தெரிவிக்கவில்லை.",
        "Ready. Watts only while connected; normal data and D/U lights return after unplugging when background monitoring is enabled. Touch-through except while positioning it." to
            "தயார். சார்ஜர் இணைந்தால் வாட்ஸ் மட்டும்; அகற்றியதும் பின்னணி கண்காணிப்பில் வழக்கமான டேட்டா மற்றும் D/U விளக்குகள் திரும்பும். இடத்தை மாற்றும் நேரம் தவிர தொடுதல்களை மறிக்காது.",
        "The position is remembered in both charging and normal monitor views." to
            "சார்ஜ் மற்றும் வழக்கமான கண்காணிப்பு காட்சிகள் இரண்டிலும் இடம் நினைவில் இருக்கும்.",
        "Phone-specific security guide" to "இந்த போனுக்கான பாதுகாப்பு வழிகாட்டி",
        "Ask AI about all current issues" to "தற்போதைய அனைத்து சிக்கல்களையும் AI-யிடம் கேள்",
        "Choose the app to manage" to "நிர்வகிக்க வேண்டிய செயலியைத் தேர்ந்தெடு",
        "Manage" to "நிர்வகி",
        "Fix / review" to "சரி செய் / ஆய்வு செய்",
        "Return here after changing the setting; the security check refreshes automatically." to
            "அமைப்பை மாற்றிய பிறகு இங்கே திரும்புங்கள்; பாதுகாப்பு சோதனை தானாக புதுப்பிக்கும்.",
        "AI reviews a fresh security scan plus battery, storage and available scan results. Sharing follows your AI settings; no files, SMS or contacts are sent." to
            "புதிய பாதுகாப்பு சோதனை, பேட்டரி, சேமிப்பு மற்றும் கிடைக்கும் சோதனை முடிவுகளை AI ஆய்வு செய்கிறது. பகிர்வு AI அமைப்புகளைப் பின்பற்றும்; கோப்புகள், SMS அல்லது தொடர்புகள் அனுப்பப்படாது.",
        // ---------------------------------------------------------------- v2.7 additions
        "Mute voice replies" to "குரல் பதில்களை நிறுத்து",
        "Voice replies on" to "குரல் பதில் இயக்கத்தில்",
        "Live web lookup" to "நேரடி இணைய தேடல்",
        "Malware hash check" to "தீம்பொருள் சோதனை",
        "Check installed apps" to "நிறுவிய ஆப்களை சோதி",
        "Check this app for malware" to "இந்த ஆப்பில் தீம்பொருள் உள்ளதா என சோதி",
        "Permissions this app has right now" to "இந்த ஆப் இப்போது வைத்திருக்கும் அனுமதிகள்",
        "Removed — Android no longer grants these" to "நீக்கப்பட்டது — இவை இனி ஆப்க்கு இல்லை",
        "Last checked %s" to "கடைசியாக சோதித்தது %s",
        "Include preinstalled system apps" to "முன்பே நிறுவிய சிஸ்டம் ஆப்களையும் சேர்",
        "Open app settings" to "ஆப் அமைப்புகளைத் திற",
        "Remove" to "நீக்கு",
        "VirusTotal API key (optional)" to "VirusTotal API கீ (விருப்பம்)",

        "Last observed phone readings" to "கடைசியாகக் கவனித்த போன் அளவீடுகள்",
        // v2.35: truthful readings and local, opt-in history tools.
        "History & tools" to "வரலாறு மற்றும் கருவிகள்",
        "Open tools" to "கருவிகளைத் திற",
        "Action checklist" to "செயல் ஆய்வுப் பட்டியல்",
        "Self-test" to "சுய சோதனை",
        "Security timeline" to "பாதுகாப்பு மாற்ற வரலாறு",
        "Charging history" to "சார்ஜிங் வரலாறு",
        "Data budget" to "டேட்டா திட்டமிடல்",
        "Not checked" to "இன்னும் ஆய்வு செய்யவில்லை",
        "Not measured" to "இன்னும் அளவிடவில்லை",
        "Not reported" to "தகவல் கிடைக்கவில்லை",
        "Incomplete" to "முழுமையில்லை",
        "Incomplete check" to "ஆய்வு முழுமையில்லை",
        "Permission review" to "அனுமதி ஆய்வு",
        "Review findings" to "கண்டறிந்தவற்றைப் பார்",
        "Live Guard readings" to "லைவ் கார்டு அளவீடுகள்",
        "Device not read yet" to "போன் தகவல் இன்னும் பெறப்படவில்லை",
        "Storage free" to "காலி சேமிப்பு",
        "RAM free" to "காலி RAM",
        "Not a speed/safety score" to "வேகம் அல்லது பாதுகாப்பு மதிப்பெண் அல்ல",
        "Network probe" to "நெட்வொர்க் தாமத சோதனை",
        "Cellular" to "மொபைல் நெட்வொர்க்",
        "Other / offline" to "மற்றது / இணையம் இல்லை",
        "Quality not measured" to "தரம் இன்னும் அளவிடப்படவில்லை",
        "Very Good (measured probe)" to "அளவிடப்பட்ட தாமதம்: சிறந்த தரம்",
        "Medium (measured probe)" to "அளவிடப்பட்ட தாமதம்: மிதமான தரம்",
        "Poor latency / jitter" to "அதிக தாமதம் / தாமத வேறுபாடு",
        "Permission/settings review only — not proof that the phone is safe." to "இது அனுமதி/அமைப்பு ஆய்வு மட்டும்; போன் பாதுகாப்பானது என்பதற்குச் சான்றல்ல.",
        "Run a permission review. No safety verdict is available." to "அனுமதி ஆய்வை இயக்கவும். பாதுகாப்புச் சான்று எதுவும் இல்லை.",
        "Some checks were unavailable. Review the observed findings." to "சில ஆய்வுத் தகவல்கள் கிடைக்கவில்லை. கிடைத்தவற்றைப் பார்த்து ஆய்வு செய்யவும்.",
        "Quality uses a small active latency probe, not a speed test. Missing ping/jitter stays unknown." to "தரத்திற்கு சிறிய இணைய தாமத சோதனை பயன்படுகிறது; இது வேகச் சோதனை அல்ல. கிடைக்காத ping/jitter ஊகிக்கப்படாது.",
        "Phone self-test, security changes, charging history and data budgeting. The status pill stays simple." to "போன் சுய சோதனை, பாதுகாப்பு மாற்றங்கள், சார்ஜிங் வரலாறு மற்றும் டேட்டா திட்டமிடல். ஸ்டேட்டஸ் பில் எளிமையாகவே இருக்கும்.",
        "Preview diagnostic report" to "சோதனை அறிக்கையை முன்பார்",
        "Preview / share safe report" to "அறிக்கையை முன்பார் / பகிர்",
        "Share this report" to "இந்த அறிக்கையைப் பகிர்",
        "Only this preview is shared. No automatic upload or raw logs." to "இந்த முன்பார்வை மட்டும் பகிரப்படும். தானாக பதிவேற்றம் அல்லது முழு லாக் பகிர்வு இல்லை.",
        "Run self-test again" to "சுய சோதனையை மீண்டும் இயக்கு",
        "Open Live Guard app settings" to "லைவ் கார்டு ஆப் அமைப்புகளைத் திற",
        "Monitoring settings" to "கண்காணிப்பு அமைப்புகள்",
        "Reported / ready" to "தகவல் கிடைத்தது / தயார்",
        "Permission not granted" to "அனுமதி வழங்கப்படவில்லை",
        "Off / restricted" to "அணைக்கப்பட்டது / கட்டுப்படுத்தப்பட்டது",
        "Not reported / not yet tested" to "தகவல் இல்லை / இன்னும் சோதிக்கவில்லை",
        "Needs a device test" to "இந்த போனில் நேரடி சோதனை தேவை",
        "Record security changes locally" to "பாதுகாப்பு மாற்றங்களை இந்த போனில் சேமி",
        "Record charging history locally" to "சார்ஜிங் வரலாற்றை இந்த போனில் சேமி",
        "Keep daily mobile history locally" to "தினசரி மொபைல் டேட்டா வரலாற்றை இந்த போனில் சேமி",
        "Clear local history?" to "இந்த போனின் வரலாற்றை அழிக்கவா?",
        "Clear history" to "வரலாற்றை அழி",
        "This removes the local baseline, timeline and reviewed choices. No app permissions are changed." to "சேமித்த அடிப்படை ஆய்வு, மாற்ற வரலாறு மற்றும் பார்த்ததாகக் குறித்த தேர்வுகள் மட்டும் அழியும். ஆப் அனுமதிகள் மாறாது.",
        "This removes this tool's saved history. Recording switches and your data plan stay unchanged." to "இந்தக் கருவியின் சேமித்த வரலாறு மட்டும் அழியும். பதிவு சுவிட்ச் மற்றும் டேட்டா திட்டம் மாறாது.",
        "Run a fresh check" to "புதிய ஆய்வை இயக்கு",
        "Last recorded check %s" to "கடைசியாகப் பதிவு செய்த ஆய்வு %s",
        "Baseline established" to "அடிப்படை ஆய்வு பதிவு செய்யப்பட்டது",
        "App newly observed" to "ஆப் புதிதாகக் காணப்பட்டது",
        "App no longer listed" to "ஆப் பட்டியலில் இனி காணப்படவில்லை",
        "Finding newly observed" to "புதிய ஆய்வுக் குறிப்பு காணப்பட்டது",
        "Evidence changed" to "ஆய்வுத் தகவல் மாறியது",
        "No longer observed" to "இப்போது காணப்படவில்லை",
        "Reviewed / expected" to "பார்த்துவிட்டேன் / எதிர்பார்த்த அனுமதி",
        "Review acknowledgement removed" to "பார்த்ததாகக் குறித்த தேர்வு நீக்கப்பட்டது",
        "Recording observed session" to "கவனித்த சார்ஜிங் அமர்வு பதிவு நடக்கிறது",
        "Partial observed session" to "பகுதியாகக் கவனிக்கப்பட்ட சார்ஜிங் அமர்வு",
        "Observed charging session" to "கவனிக்கப்பட்ட சார்ஜிங் அமர்வு",
        "Sampled average / peak" to "அளவீட்டு சராசரி / அதிகபட்சம்",
        "Highest observed temperature" to "கவனித்த அதிகபட்ச வெப்பம்",
        "Power-sampled intervals %s of observed %s" to "பவர் அளவீடு கிடைத்த இடைவெளி %s; கவனித்த மொத்தம் %s",
        "Observed 20% → 80%" to "கவனித்த 20% → 80% நேரம்",
        "Not fully observed" to "முழுமையாகக் கவனிக்கப்படவில்லை",
        "Show charts" to "வரைபடங்களைக் காட்டு",
        "Hide charts" to "வரைபடங்களை மறை",
        "Battery-side watts" to "பேட்டரியின் சார்ஜ் சக்தி (W)",
        "Battery temperature" to "பேட்டரி வெப்பம்",
        "observed samples" to "கவனிக்கப்பட்ட அளவீடுகள்",
        "Not enough valid samples for a chart." to "வரைபடத்திற்கு போதுமான சரியான அளவீடுகள் இல்லை.",
        "Charging history & charts" to "சார்ஜிங் வரலாறு மற்றும் வரைபடங்கள்",
        "Data budget & history" to "டேட்டா திட்டமிடல் மற்றும் வரலாறு",
        "Configured quota" to "நீங்கள் அமைத்த டேட்டா அளவு",
        "Remaining estimate" to "மீதமுள்ளதற்கான மதிப்பீடு",
        "Today so far" to "இன்று இதுவரை",
        "Pack expiry / next reset date" to "பேக் முடிவு / அடுத்த புதுப்பிப்பு தேதி",
        "Choose date" to "தேதியைத் தேர்ந்தெடு",
        "Save date" to "தேதியைச் சேமி",
        "Remove date" to "தேதியை நீக்கு",
        "Days including today" to "இன்றையும் சேர்த்து மீதமுள்ள நாட்கள்",
        "Aim for at most today" to "இன்றைக்கு திட்டமிட்ட அதிகபட்சம்",
        "Average of complete recorded days" to "முழு தினங்களின் பதிவு சராசரி",
        "Estimated remaining days at that pace" to "அந்தப் பயன்பாட்டில் மீதமுள்ள நாள் மதிப்பீடு",
        "Not enough history" to "போதுமான வரலாறு இல்லை",
        "Based on %s complete past days (up to seven); forecasts need at least three. Unknown/partial days are not counted as zero." to "%s முழு கடந்த தினங்களை (அதிகபட்சம் 7) அடிப்படையாகக் கொண்டது. கணிப்பிற்கு குறைந்தது 3 தினங்கள் தேவை. தெரியாத/பகுதி தினங்கள் பூஜ்ஜியமாகக் கணக்கிடப்படாது.",
        "Reset local pack counter?" to "இந்த போனின் பேக் கணக்கை மீட்டமைக்கவா?",
        "Reset counter" to "கணக்கை மீட்டமை",
        "Recharge: reset local counter" to "ரீசார்ஜ்: உள்ளூர் கணக்கை மீட்டமை",
        "Confirm only when your pack/recharge has started. This changes local tracking, not your carrier plan. Set the new expiry separately." to "புதிய பேக்/ரீசார்ஜ் தொடங்கியிருந்தால் மட்டும் உறுதி செய்யவும். இது இந்த போனின் கணக்கை மட்டும் மாற்றும்; ஆபரேட்டர் திட்டத்தை மாற்றாது. புதிய முடிவு தேதியைத் தனியாக அமைக்கவும்.",
        "Open Usage access settings" to "பயன்பாட்டு அணுகல் அமைப்புகளைத் திற",
        "Recent mobile usage" to "சமீபத்திய மொபைல் டேட்டா பயன்பாடு",
        "Daily mobile usage; missing days are not zero" to "தினசரி மொபைல் டேட்டா; கிடைக்காத தினங்கள் பூஜ்ஜியம் அல்ல",
        "Reported full day" to "முழு தின அறிக்கை",
        "Partial / in progress" to "பகுதி / தினம் இன்னும் நடக்கிறது",
        "Clear saved daily history" to "சேமித்த தினசரி வரலாற்றை அழி",
        "Recheck current facts" to "தற்போதைய தகவல்களை மீண்டும் சோதி",
        "Do first" to "முதலில் கவனி",
        "Optional review" to "விருப்ப ஆய்வு",
        "Leave alone / expected" to "மாற்ற வேண்டாம் / எதிர்பார்த்தது",
        "Reviewed / expected — access unchanged" to "பார்த்துவிட்டேன் / எதிர்பார்த்தது — அனுமதி மாறவில்லை",
        "Not yet reviewed" to "இன்னும் பார்க்கவில்லை",
        "Local review steps" to "இந்த போனில் செய்யும் ஆய்வுப் படிகள்",
        "AI explanation — verify it" to "AI விளக்கம் — சரிபார்க்கவும்",
        "AI advice from %s" to "%s அன்று பெற்ற AI ஆலோசனை",
        "Review setting" to "அமைப்பைப் பார்த்து ஆய்வு செய்",
        "Open relevant tool" to "தொடர்புடைய கருவியைத் திற",
        "I reviewed this / expected access" to "இதைப் பார்த்தேன் / எதிர்பார்த்த அனுமதி",
        "Less detail" to "சுருக்கமாகக் காட்டு",
        "Review details" to "விவரங்களைப் பார்",
        "Turn advice into a review checklist" to "ஆலோசனையை ஆய்வுப் பட்டியலாகப் பார்",
        "Open action checklist" to "செயல் ஆய்வுப் பட்டியலைத் திற",
        "What changed? Security timeline" to "என்ன மாறியது? பாதுகாப்பு வரலாறு",
        "Some checks are unavailable. Missing observations are not proof of safety." to "சில ஆய்வுகள் கிடைக்கவில்லை. தகவல் இல்லாதது பாதுகாப்புக்கான சான்றல்ல.",
        "Permission grant state was not reported; do not infer granted or revoked access." to "அனுமதி நிலை கிடைக்கவில்லை. வழங்கப்பட்டது அல்லது நீக்கப்பட்டது என்று ஊகிக்க வேண்டாம்.",
        "Unlimited 5G mode • Counters hidden (user-set)" to "Unlimited 5G முறை • கணக்கு காட்சி மறைந்துள்ளது (உங்கள் தேர்வு)",
        "Android usage is an estimate, not the carrier's balance. Unlimited 5G is a user-set preference." to "ஆண்ட்ராய்டு பயன்பாடு ஒரு மதிப்பீடு; ஆபரேட்டரின் மீதமுள்ள அளவு அல்ல. Unlimited 5G என்பது உங்கள் தேர்வு.",
        "Partial observed mobile counter; full-period history may be unavailable." to "கவனித்த பகுதி மொபைல் கணக்கு; முழுக் கால வரலாறு கிடைக்காமல் இருக்கலாம்.",
        "No daily history available yet." to "தினசரி வரலாறு இன்னும் கிடைக்கவில்லை.",
        "Recharge / period start date" to "ரீசார்ஜ் / கணக்கின் தொடக்க தேதி",
        "Apply start date" to "தொடக்க தேதியை அமை",
        "Change local period start?" to "இந்த போனின் கணக்குத் தொடக்கத்தை மாற்றவா?",
        "Expiry day (inclusive)" to "முடிவு தினம் (அன்றையும் சேர்)",
        "Next reset day (exclusive)" to "அடுத்த தொடக்க தினம் (அன்றை சேர்க்காதே)",
        "Android history will use this date's midnight. Partial counter tracking restarts from now; earlier missing traffic is not guessed. Carrier billing and expiry are unchanged." to "ஆண்ட்ராய்டு வரலாறு இந்த தேதியின் நள்ளிரவிலிருந்து கணக்கிடும். பகுதி கணக்கு இப்போதிலிருந்து தொடங்கும்; விடுபட்ட பழைய டேட்டா ஊகிக்கப்படாது. ஆபரேட்டர் கட்டணம் மற்றும் முடிவு தேதி மாறாது.",
        "Diagnostic report copied" to "சோதனை அறிக்கை நகலெடுக்கப்பட்டது",
        "Could not copy; the preview remains available." to "நகலெடுக்க முடியவில்லை; முன்பார்வையில் அறிக்கையைப் படிக்கலாம்.",
        "No share activity available; use Copy or read the preview." to "பகிரும் ஆப் கிடைக்கவில்லை; நகலெடுக்கவும் அல்லது முன்பார்வையைப் படிக்கவும்.",
        "Recording began while connected; the earlier plug time and percentage are unknown." to "சார்ஜர் இணைந்திருக்கும்போதே பதிவு தொடங்கியது; அதற்கு முந்தைய இணைப்பு நேரம் மற்றும் சதவீதம் தெரியாது.",
        "Estimated phone age" to "போன் வயது மதிப்பீடு",
        "Calculating age estimate…" to "வயதை மதிப்பிடுகிறது…",
        "This is a coarse model/initial-Android-generation estimate. The reference months are approximate, not verified launch dates." to "இது போன் மாடல்/தொடக்க ஆண்ட்ராய்டு தலைமுறையின் தோராய மதிப்பீடு. மாதங்கள் தோராயமானவை; உறுதி செய்த வெளியீட்டுத் தேதிகள் அல்ல.",
        "Android does not reveal this handset's exact manufacture, purchase or first-use date. An Android upgrade, security patch or uptime does not prove phone age." to "இந்த போனின் சரியான தயாரிப்பு, வாங்கிய அல்லது முதலில் பயன்படுத்திய தேதியை ஆண்ட்ராய்டு காட்டாது. புதிய ஆண்ட்ராய்டு பதிப்பு, பாதுகாப்பு அப்டேட் அல்லது இயங்கிய நேரம் போனின் வயதுக்குச் சான்றல்ல.",
        "Estimate basis: model-family heuristic" to "மதிப்பீட்டின் அடிப்படை: மாடல் குடும்பம்",
        "Estimate basis: factory Android generation" to "மதிப்பீட்டின் அடிப்படை: தொழிற்சாலையின் ஆண்ட்ராய்டு தலைமுறை",
        "Not enough model/factory information to estimate age." to "வயதை மதிப்பிட போதுமான மாடல்/தொழிற்சாலைத் தகவல் இல்லை.",
        "Hide news strip" to "செய்திப் பட்டையை மறை",
        "Today's headlines" to "இன்றைய செய்திகள்",
        "Fetching today's headlines…" to "இன்றைய செய்திகளைப் பெறுகிறது…",
        "You're caught up with today's headlines. Tap to review." to "இன்றைய செய்திகளைப் பார்த்துவிட்டீர்கள். மீண்டும் பார்க்கத் தட்டவும்.",
        "Offline: no dated headlines from today are available." to "இணையம் இல்லை: இன்றைய தேதியுள்ள செய்திகள் கிடைக்கவில்லை.",
        "News feeds unavailable. No old news substituted." to "செய்தி ஊட்டங்கள் கிடைக்கவில்லை. பழைய செய்திகள் மாற்றாகக் காட்டப்படாது.",
        "No dated headlines published today are available yet." to "இன்று வெளியான தேதியுள்ள செய்திகள் இன்னும் கிடைக்கவில்லை.",
        "Open article" to "முழுச் செய்தியைத் திற",
        "Resume headlines" to "செய்திகளைத் தொடர்",
        "Pause headlines" to "செய்திகளை நிறுத்து",
        "Refresh today's headlines" to "இன்றைய செய்திகளைப் புதுப்பி",
        "Web excerpts (%s)" to "இணையப் பகுதிகள் (%s)",
        "Retrieved excerpts, not independent verification." to "பெறப்பட்ட இணையப் பகுதிகள்; தனியாக உறுதி செய்த தகவல் அல்ல.",
        "Runtime listeners (registration only)" to "நேரடி நிகழ்வு கவனிப்புகள் (பதிவு நிலை மட்டும்)",
        "No live listener owner; background events may be missed" to "நேரடி கவனிப்பு இயங்கவில்லை; பின்னணி நிகழ்வுகள் தவறலாம்",
        "Power listener: %s" to "சார்ஜர் நிகழ்வு கவனிப்பு: %s",
        "Battery listener: %s" to "பேட்டரி கவனிப்பு: %s",
        "Network listener: %s" to "நெட்வொர்க் கவனிப்பு: %s",
        "Registered; event delivery unverified" to "பதிவாகியுள்ளது; நிகழ்வு கிடைப்பது உறுதியாகவில்லை",
        "Failed/unavailable" to "தோல்வி/கிடைக்கவில்லை",
        "Not active" to "இயங்கவில்லை",
        "Live power/battery/network listeners run while the app is visible or its existing monitor is active. Without an active owner, Android8+ manifest power broadcasts may be missed or deferred. Jobs and hourly checks can be delayed; force-stop prevents delivery. No new persistent monitor is enabled." to "ஆப் திறந்திருக்கும் போது அல்லது ஏற்கனவே இயக்கிய கண்காணிப்பில் சார்ஜர்/பேட்டரி/நெட்வொர்க் கவனிப்புகள் இயங்கும். அது இல்லாமல் Android8+ பின்னணி சார்ஜர் நிகழ்வுகள் தவறலாம் அல்லது தாமதமாகலாம். பணிகளும் மணிநேரச் சோதனைகளும் தாமதமாகலாம்; கட்டாய நிறுத்தம் அறிவிப்புகளைத் தடுக்கும். புதிய நிரந்தர கண்காணிப்பு இயக்கப்படவில்லை.",
        "Voice delivery diagnostics" to "குரல் ஒலிப்பு சோதனை விவரங்கள்",
        "Last request: %s" to "கடைசி கோரிக்கை: %s",
        "Last automatic event: %s" to "கடைசி தானியங்கி நிகழ்வு: %s",
        "Not observed yet" to "இன்னும் கவனிக்கப்படவில்லை",
        "Tamil language reported available" to "தமிழ் குரல் கிடைப்பதாகக் காட்டப்படுகிறது",
        "Tamil language not available; install voice data" to "தமிழ் குரல் இல்லை; குரல் தரவை நிறுவவும்",
        "Media volume step: %s" to "மீடியா ஒலி அளவு: %s",
        "An enabled switch or queued request is not proof of sound. Engine completion cannot verify that you heard it; check media volume, Bluetooth route and Do Not Disturb." to "சுவிட்ச் இயக்கத்தில் இருப்பது அல்லது கோரிக்கை வரிசையில் இருப்பது ஒலிக்குச் சான்றல்ல. எஞ்சின் முடித்ததாகக் காட்டினாலும் நீங்கள் கேட்டதை உறுதி செய்யாது; மீடியா ஒலி, Bluetooth மற்றும் தொந்தரவு செய்யாதீர் முறையைப் பாருங்கள்.",
        "Test automatic charging voice" to "தானியங்கி சார்ஜ் குரலைச் சோதி",
        "Text-to-speech settings" to "வாசகத்தைக் குரலாக்கும் அமைப்புகள்",
        "The automatic test uses the real master/event/quiet-hour gates. It is not a charger connection. Actual early-unplug alerts only apply below 90% after your chosen hour." to "இந்தச் சோதனை பொது/நிகழ்வு/அமைதி நேர அமைப்புகளைப் பயன்படுத்தும்; சார்ஜர் இணைப்பு அல்ல. 90%-க்குக் கீழே, நீங்கள் தேர்ந்த நேரத்திற்குப் பிறகு கழற்றினால் மட்டுமே நினைவூட்டல் வரும்.",
        "Live battery/network alerts run while the app is visible or its existing monitor is active. Background power jobs and hourly checks may be delayed by Android/OEM restrictions; force-stop prevents delivery. No new persistent monitor is enabled." to "ஆப் திரையில் இருக்கும்போது அல்லது ஏற்கனவே உள்ள கண்காணிப்பு இயங்கும்போது பேட்டரி/நெட்வொர்க் அறிவிப்புகள் வரும். ஆண்ட்ராய்டு கட்டுப்பாடுகளால் பின்னணி வேலைகள் தாமதமாகலாம்; கட்டாய நிறுத்தத்தில் வராது. புதிய நிரந்தர கண்காணிப்பு இயக்கப்படாது.",
        "Uses full speech amplitude, but does not change Android media volume, override Do Not Disturb or force audio away from Bluetooth. A muted media stream stays muted." to "முழுக் குரல் ஒலியளவைப் பயன்படுத்தும்; ஆண்ட்ராய்டு மீடியா ஒலி, அமைதி முறை அல்லது Bluetooth பாதையை மாற்றாது. மீடியா மௌனமாக இருந்தால் மௌனமாகவே இருக்கும்.",
        "Uses an installed Tamil/English voice. If a network voice fails, an installed offline voice in the same language is tried once. Missing voice data or engine errors are shown in diagnostics; offline speech is not guaranteed. No API key is used." to "நிறுவிய தமிழ்/ஆங்கிலக் குரலைப் பயன்படுத்தும். இணையக் குரல் தோல்வியில் அதே மொழியின் நிறுவிய ஆஃப்லைன் குரல் ஒருமுறை முயற்சிக்கப்படும். குரல் தரவு/எஞ்சின் பிழை சோதனையில் தெரியும்; ஆஃப்லைன் குரல் உத்தரவாதமல்ல. API சாவி தேவையில்லை.",
        "A connected-but-paused reading can be normal adaptive charging; it does not prove a cable fault." to "இணைந்தும் இடைநிறுத்தப்பட்ட சார்ஜ் இயல்பான கட்டுப்படுத்தப்பட்ட சார்ஜிங்காக இருக்கலாம்; கேபிள் கோளாறுக்குச் சான்றல்ல.",
        "Announces observed Wi-Fi/mobile switches while the app or existing monitor is active; hourly checks are a delayed fallback." to "ஆப்/கண்காணிப்பு இயங்கும்போது கவனித்த Wi-Fi/மொபைல் மாற்றங்களை அறிவிக்கும்; மணிநேரச் சோதனை தாமதமான மாற்று வழி.",
        "No request observed in this process" to "இந்தச் செயல்பாட்டில் கோரிக்கை கவனிக்கப்படவில்லை",
        "Blocked: master voice switch is off" to "தடுக்கப்பட்டது: பொது குரல் சுவிட்ச் நிறுத்தத்தில்",
        "Blocked: this event's voice switch is off" to "தடுக்கப்பட்டது: நிகழ்வின் குரல் சுவிட்ச் நிறுத்தத்தில்",
        "Blocked: quiet hours" to "தடுக்கப்பட்டது: அமைதி நேரம்",
        "Queued, not yet confirmed playing" to "வரிசையில் உள்ளது; ஒலிப்பு இன்னும் உறுதியாகவில்லை",
        "Speech engine starting" to "குரல் எஞ்சின் தொடங்குகிறது",
        "Speech engine could not be created" to "குரல் எஞ்சினை உருவாக்க முடியவில்லை",
        "Speech engine initialization failed" to "குரல் எஞ்சின் தொடக்கம் தோல்வி",
        "Speech engine initialization timed out" to "குரல் எஞ்சின் தொடக்க நேரம் முடிந்தது",
        "Required language/offline voice data missing" to "தேவையான மொழி/ஆஃப்லைன் குரல் தரவு இல்லை",
        "Speech engine rejected the request" to "குரல் எஞ்சின் கோரிக்கையை மறுத்தது",
        "Speech engine reported playback started" to "குரல் ஒலிப்பு தொடங்கியதாக எஞ்சின் காட்டியது",
        "Speech engine reported completion; audibility unverified" to "குரல் முடிந்ததாக எஞ்சின் காட்டியது; கேட்டது உறுதி செய்யப்படவில்லை",
        "Speech engine reported an error" to "குரல் எஞ்சின் பிழை காட்டியது",
        "Speech delivery timed out" to "குரல் ஒலிப்பு நேரம் முடிந்தது",
        "Speech request cancelled/interrupted" to "குரல் கோரிக்கை ரத்து/இடைமறிப்பு",
        "Duplicate request skipped" to "மீண்டும் வந்த கோரிக்கை தவிர்க்கப்பட்டது",
        "Delayed/stale power event skipped" to "தாமதமான/பழைய சார்ஜ் நிகழ்வு தவிர்க்கப்பட்டது",
        "Voice request queued. Check delivery diagnostics for playback or a blocking reason." to "குரல் கோரிக்கை வரிசையில் உள்ளது. ஒலிப்பு அல்லது தடைக்கான காரணத்தைச் சோதனை விவரங்களில் பாருங்கள்.",
        "Voice request was blocked. Check the master switch and delivery diagnostics." to "குரல் கோரிக்கை தடுக்கப்பட்டது. பொது சுவிட்ச் மற்றும் சோதனை விவரங்களைப் பாருங்கள்.",
        "Search Android Settings for Text-to-speech output and installed Tamil voice data." to "ஆண்ட்ராய்டு அமைப்புகளில் வாசகத்தைக் குரலாக்குதல் மற்றும் நிறுவிய தமிழ் குரல் தரவைத் தேடுங்கள்.",
        // Unreleased: matching app/monitor/assistant review and selected-voice wording.
        "Live Guard assistant" to "லைவ் கார்டு உதவியாளர்",
        "Ask Live Guard…" to "லைவ் கார்டிடம் கேளுங்கள்…",
        "Previous review" to "முந்தைய ஆய்வு",
        "Saved review only. Run a fresh check for current findings." to "இது சேமித்த முந்தைய ஆய்வு மட்டுமே. தற்போதைய தகவல்களுக்கு மீண்டும் சோதிக்கவும்.",
        "Phone clock changed. Saved review is not a current verification." to "போனின் நேரம் மாறியுள்ளது. சேமித்த ஆய்வு தற்போதைய சரிபார்ப்பல்ல.",
        "Previous review incomplete" to "முந்தைய ஆய்வு முழுமையில்லை",
        "Tap to check again" to "மீண்டும் சோதிக்கத் தொடவும்",
        "Storage free: %s of %s." to "காலி சேமிப்பு: %s / மொத்தம் %s.",
        "Storage readings are not available yet." to "சேமிப்பு அளவீடுகள் இன்னும் கிடைக்கவில்லை.",
        "I can explain the available phone readings and guide you through cleaning. Try a suggestion or type a question." to "கிடைக்கும் போன் அளவீடுகளை விளக்கவும், சுத்தம் செய்ய வழிகாட்டவும் முடியும். பரிந்துரையைத் தேர்ந்தெடுக்கவும் அல்லது கேள்வியை எழுதவும்.",
        "Preparing answer…" to "பதிலைத் தயாரிக்கிறது…",
        "Searching live web…" to "இணையத்தில் தற்போதைய தகவலைத் தேடுகிறது…",
        "Waiting for %s…" to "%s சேவையின் பதிலுக்காகக் காத்திருக்கிறது…",
        "Elapsed: %s s · maximum: %s s" to "கடந்த நேரம்: %s வி · அதிகபட்சம்: %s வி",
        "No AI answer received" to "AI பதில் கிடைக்கவில்லை",
        "Request cancelled. You can retry or choose another provider." to "கோரிக்கை நிறுத்தப்பட்டது. மீண்டும் முயலலாம் அல்லது வேறு சேவையைத் தேர்ந்தெடுக்கலாம்.",
        "The AI request timed out. Retry or choose another provider." to "AI பதிலுக்கான நேர வரம்பு முடிந்தது. மீண்டும் முயலவும் அல்லது வேறு சேவையைத் தேர்ந்தெடுக்கவும்.",
        "The provider did not accept the saved key. Check AI settings." to "சேமித்த API விசையைச் சேவை ஏற்கவில்லை. AI அமைப்புகளைச் சரிபார்க்கவும்.",
        "The provider denied access. Check the selected model and account permissions." to "சேவை அணுகலை மறுத்தது. தேர்ந்தெடுத்த மாடல் மற்றும் கணக்கின் அனுமதிகளைச் சரிபார்க்கவும்.",
        "The provider is busy or its quota is exhausted. Retry later or choose another provider." to "சேவை பிஸியாக உள்ளது அல்லது அதன் பயன்பாட்டு வரம்பு முடிந்துள்ளது. பிறகு முயலவும் அல்லது வேறு சேவையைத் தேர்ந்தெடுக்கவும்.",
        "The selected model could not be used. Load available models in AI settings." to "தேர்ந்தெடுத்த மாடலைப் பயன்படுத்த முடியவில்லை. AI அமைப்புகளில் கிடைக்கும் மாடல்களின் பட்டியலை ஏற்றவும்.",
        "The provider is temporarily unavailable. Retry later or choose another provider." to "சேவை தற்காலிகமாகக் கிடைக்கவில்லை. பிறகு முயலவும் அல்லது வேறு சேவையைத் தேர்ந்தெடுக்கவும்.",
        "No key is saved for this provider. Open AI settings or select Free AI." to "இந்தச் சேவைக்கு API விசை சேமிக்கப்படவில்லை. AI அமைப்புகளைத் திறக்கவும் அல்லது இலவச AI-ஐத் தேர்ந்தெடுக்கவும்.",
        "The provider returned no final answer. Try another model or provider." to "சேவையிலிருந்து இறுதிப் பதில் வரவில்லை. வேறு மாடல் அல்லது சேவையை முயலவும்.",
        "Network tasks are busy. Cancel or retry shortly." to "இணையப் பணிகள் பிஸியாக உள்ளன. நிறுத்தவும் அல்லது சிறிது நேரத்தில் மீண்டும் முயலவும்.",
        "Could not get an AI answer. Check the connection, provider and model, then retry." to "AI பதிலைப் பெற முடியவில்லை. இணைய இணைப்பு, சேவை மற்றும் மாடலைச் சரிபார்த்து மீண்டும் முயலவும்.",
        "On-device settings answer · no API/search request" to "போனில் உள்ள அமைப்புகளின் பதில் · API/இணையத் தேடல் கோரிக்கை இல்லை",
        "Local welcome · no API/search request" to "உள்ளூர் வரவேற்பு · API/இணையத் தேடல் கோரிக்கை இல்லை",
        "On-device engine · tap to ask the AI" to "உள்ளூர் உதவியாளர் · AI-யிடம் கேட்கத் தொடவும்",
        "On-device engine · no AI/search request" to "உள்ளூர் உதவியாளர் · AI/இணையத் தேடல் கோரிக்கை இல்லை",
        "Web lookup off" to "இணையத் தேடல் முடக்கப்பட்டுள்ளது",
        "Retrieved web excerpts: %s · unverified" to "பெற்ற இணையத் தகவல் துணுக்குகள்: %s · சரிபார்க்கப்படவில்லை",
        "Web lookup timed out" to "இணையத் தேடலின் நேர வரம்பு முடிந்தது",
        "Web lookup unavailable" to "இணையத் தேடல் கிடைக்கவில்லை",
        "No relevant web evidence" to "தொடர்புடைய இணைய ஆதாரம் கிடைக்கவில்லை",
        "AI did not answer · %s" to "AI பதிலளிக்கவில்லை · %s",
        "Provider returned search citations · not independently verified" to "சேவை இணையத் தேடல் மேற்கோள்களைத் தந்தது · தனியாகச் சரிபார்க்கப்படவில்லை",
        "I could not verify a current price for %s. No usable exact-product price evidence was retrieved; I will not substitute another model or guess a price." to "%s இன் தற்போதைய விலையைச் சரிபார்க்க முடியவில்லை. அதே மாடலுக்கான பயன்படுத்தக்கூடிய விலை ஆதாரம் கிடைக்கவில்லை; வேறு மாடலை மாற்றிக் காட்டவோ விலையை ஊகிக்கவோ மாட்டேன்.",
        "Listing prices are not independently verified and may vary by storage variant, seller, region and offers. Confirm the current checkout price." to "பட்டியலிடப்பட்ட விலைகள் தனியாகச் சரிபார்க்கப்படவில்லை. சேமிப்புத் திறன் வகை, விற்பனையாளர், பகுதி மற்றும் சலுகைகளுக்கு ஏற்ப மாறலாம். வாங்கும்போது காட்டப்படும் தற்போதைய விலையை உறுதிப்படுத்தவும்.",
        "I could not retrieve today's dated headlines. I will not substitute old news or encyclopedia entries. Please retry later." to "இன்றைய வெளியீட்டுத் தேதியுள்ள செய்தித் தலைப்புகளைப் பெற முடியவில்லை. பழைய செய்தியையோ கலைக்களஞ்சியத் தகவலையோ மாற்றாகக் காட்ட மாட்டேன். பிறகு முயலவும்.",
        "Today's headlines (RSS publication dates; not independently verified):" to "இன்றைய செய்தித் தலைப்புகள் (RSS வெளியீட்டுத் தேதிகள்; தனியாகச் சரிபார்க்கப்படவில்லை):",
        "This question is outside the built-in local topics. Tap Ask the AI to send it and the allowed facts to your selected provider." to "இந்தக் கேள்வி உள்ளமைந்த உள்ளூர் தலைப்புகளுக்கு வெளியே உள்ளது. இதையும் அனுமதித்த தகவல்களையும் தேர்ந்தெடுத்த சேவைக்கு அனுப்ப ‘AI-யிடம் கேள்’ என்பதைத் தொடவும்.",
        "Online AI · %s" to "ஆன்லைன் AI · %s",
        "Online mode selected · provider setup needed" to "ஆன்லைன் முறை தேர்ந்தெடுக்கப்பட்டுள்ளது · சேவையை அமைக்க வேண்டும்",
        "Local assistant mode · no AI/search request" to "உள்ளூர் உதவியாளர் முறை · AI/இணையத் தேடல் கோரிக்கை இல்லை",
        "Checking the provider connection…" to "சேவையின் இணைப்பைச் சோதிக்கிறது…",
        "Online provider selected: %s. A connection check is not proof a model answered. Each reply shows its actual provider, model and elapsed time." to "தேர்ந்தெடுத்த ஆன்லைன் சேவை: %s. இணைப்புச் சோதனை மட்டும் மாடல் பதிலளித்ததற்குச் சான்றல்ல. ஒவ்வொரு பதிலும் உண்மையில் பயன்படுத்திய சேவை, மாடல் மற்றும் எடுத்த நேரத்தைக் காட்டும்.",
        "Online mode needs provider setup. Local help is still available." to "ஆன்லைன் முறைக்கு சேவையை அமைக்க வேண்டும். உள்ளூர் உதவி தொடர்ந்து கிடைக்கும்.",
        "The local assistant uses built-in rules and available phone readings. Online AI and web lookup require your choice." to "உள்ளூர் உதவியாளர் உள்ளமைந்த விதிகளையும் கிடைக்கும் போன் அளவீடுகளையும் பயன்படுத்தும். ஆன்லைன் AI மற்றும் இணையத் தேடல் உங்கள் தேர்வின் பேரில் மட்டுமே இயங்கும்.",
        "Which AI answers" to "எந்த AI பதிலளிக்கிறது",
        "Clear chat" to "உரையாடலை அழி",
        // 2.42: the "Choose and preview two voices…" error was removed on purpose — Voice A
        // and Voice B are now chosen automatically from the installed inventory, so that
        // message must never be shown again. Its Tamil pair was retired with it.
        "Alternating assistant voices" to "மாறி மாறிப் பேசும் உதவியாளர் குரல்கள்",
        "Voice A and Voice B alternate between answers. Each answer is read once, not twice. Manual Read aloud uses that answer's assigned voice." to "குரல் A மற்றும் குரல் B ஒவ்வொரு பதிலுக்கும் மாறி மாறிப் பயன்படுத்தப்படும். ஒரு பதில் ஒருமுறை மட்டுமே வாசிக்கப்படும்; இருமுறை அல்ல. ‘வாசித்துச் சொல்’ என்பதைத் தொடும்போதும் அந்தப் பதிலுக்கான குரலே பயன்படுத்தப்படும்.",
        "Android does not reliably label voice gender. Listen to installed-voice previews and choose the two voices you prefer. Choices are saved separately for Tamil and English." to "குரல் ஆணா பெண்ணா என்பதை Android நம்பகமாகக் குறிப்பிடுவதில்லை. கிடைக்கும் குரல்களை முன்கேட்டு விரும்பும் இரண்டு குரல்களைத் தேர்ந்தெடுக்கவும். தமிழ் மற்றும் ஆங்கிலத்திற்குத் தேர்வுகள் தனித்தனியாகச் சேமிக்கப்படும்.",
        "Loading installed voices…" to "போனில் கிடைக்கும் குரல்களை ஏற்றுகிறது…",
        "Fewer than two matching voices were reported by the engine. Two-voice alternation is unavailable until you install/select suitable voices." to "இந்த மொழிக்கு ஏற்ற இரண்டு குரல்கள் குரல் இயந்திரத்திலிருந்து கிடைக்கவில்லை. பொருத்தமான குரல்களை நிறுவி/தேர்ந்தெடுக்கும் வரை இரண்டு குரல்கள் மாறிப் பேச முடியாது.",
        "Online voices may send spoken text to the TTS provider. Preview completion is an engine callback, not proof you heard it. Save only after listening." to "ஆன்லைன் குரல்கள் வாசிக்கப்படும் உரையை TTS சேவைக்கு அனுப்பலாம். முன்னோட்டம் முடிந்தது என்ற இயந்திரச் செய்தி மட்டும் நீங்கள் கேட்டதற்குச் சான்றல்ல. கேட்ட பிறகே சேமிக்கவும்.",
        "Reload voices" to "குரல்களை மீண்டும் ஏற்று",
        "Android voice settings" to "Android குரல் அமைப்புகள்",
        "Voice A" to "குரல் A",
        "Voice B" to "குரல் B",
        "Choose installed voice" to "கிடைக்கும் குரலைத் தேர்ந்தெடு",
        "Preview" to "முன்கேள்",
        "Preview finished. Save this voice only if you heard it and prefer it." to "முன்னோட்டம் முடிந்தது. நீங்கள் கேட்டும் விரும்பியும் இருந்தால் மட்டுமே இந்தக் குரலைச் சேமிக்கவும்.",
        "Preview failed or was blocked. Check master voice, installed data, media volume and connection." to "முன்னோட்டம் தோல்வியடைந்தது அல்லது தடுக்கப்பட்டது. முதன்மைக் குரல் அமைப்பு, நிறுவிய குரல் தரவு, ஊடக ஒலியளவு மற்றும் இணைப்பைச் சரிபார்க்கவும்.",
        "Voice choice saved" to "குரல் தேர்வு சேமிக்கப்பட்டது",
        "Voice choice could not be saved. Preview it again and choose a distinct voice." to "குரல் தேர்வைச் சேமிக்க முடியவில்லை. மீண்டும் முன்கேட்டு மற்றொரு தனித்த குரலைத் தேர்ந்தெடுக்கவும்.",
        "Use as Voice A" to "குரல் A ஆகப் பயன்படுத்து",
        "Use as Voice B" to "குரல் B ஆகப் பயன்படுத்து",
        "Mobile data & battery status" to "மொபைல் தரவு & பேட்டரி நிலை",
        "Observed mobile data usage, configured quota and battery readings" to "காணப்பட்ட மொபைல் தரவுப் பயன்பாடு, அமைத்த வரம்பு மற்றும் பேட்டரி அளவீடுகள்",
        "Mobile usage unavailable · Battery %s" to "மொபைல் பயன்பாடு கிடைக்கவில்லை · பேட்டரி %s",
        "Mobile data today: %s · Pack left: %s" to "இன்றைய மொபைல் தரவு: %s · தொகுப்பில் மீதி: %s",
        "Unlimited 5G · user-set · Battery %s" to "வரம்பற்ற 5G · பயனர் தேர்வு · பேட்டரி %s",
        "Battery %s · %s" to "பேட்டரி %s · %s",
        "%s h %s min" to "%s மணி %s நிமிடம்",
        "%s min" to "%s நிமிடம்",
        "Estimated time to full: %s" to "நிறைவடைய மதிப்பிட்ட நேரம்: %s",
        "Estimated to 100% from recent charging: %s" to "சமீபத்திய சார்ஜ் வேகத்தைப் பொறுத்து 100% வரை: %s",
        "ETA %s" to "%s-இல் முழு சார்ஜ்",
        "Estimating" to "கணக்கிடுகிறது…",
        "Unavailable" to "கிடைக்கவில்லை",
        "Full — unplug when convenient" to "முழுமையாக சார்ஜ் ஆனது — வசதியானபோது துண்டிக்கவும்",
        "Estimated to 100%" to "100% வரை மதிப்பீடு",
        "Battery timing" to "பேட்டரி நேரக் கணிப்பு",
        "Connected · not charging" to "இணைக்கப்பட்டுள்ளது · சார்ஜ் ஆகவில்லை",
        "Time-to-empty estimate unavailable" to "பேட்டரி காலியாகும் நேர மதிப்பீடு கிடைக்கவில்லை",
        "Estimating charging time; more observations are needed." to "சார்ஜ் நேரம் கணக்கிடப்படுகிறது; மேலும் அளவீடுகள் தேவை.",
        "Charging time unavailable while the battery is not charging." to "பேட்டரி சார்ஜ் ஆகாததால் நிறைவடையும் நேரம் கிடைக்கவில்லை.",
        "Estimate uses recent in-memory battery-level observations while charging; it is unavailable until enough readings exist. It is not a measured watt reading." to "சார்ஜ் செய்யும் போது நினைவகத்தில் மட்டும் உள்ள சமீபத்திய பேட்டரி சதவீத அளவீடுகளிலிருந்து மதிப்பீடு செய்யப்படுகிறது. போதிய அளவீடுகள் இல்லையெனில் கிடைக்காது; இது அளவிடப்பட்ட வாட் மதிப்பு அல்ல.",
        "A time-to-empty estimate is not inferred from one current reading or a guessed battery capacity." to "ஒரு மின்னோட்ட அளவீட்டையோ ஊகிக்கப்பட்ட பேட்டரி கொள்ளளவையோ வைத்து காலியாகும் நேரம் கணிக்கப்படாது.",
        "%s h" to "%s மணி",
        "Battery voltage (Android broadcast): %s" to "பேட்டரி மின்னழுத்தம் (Android ஒலிபரப்பு): %s",
        "Battery temperature (Android broadcast): %s" to "பேட்டரி வெப்பநிலை (Android ஒலிபரப்பு): %s",
        "%s · measured quality: %s" to "%s · அளவிட்ட தரம்: %s",
        "Ping: %s ms" to "பிங் தாமதம்: %s மி.வி",
        " · Jitter: %s ms" to " · தாமத மாறுபாடு: %s மி.வி",
        "Live traffic: ↓ %s · ↑ %s" to "தற்போதைய தரவுப் பரிமாற்றம்: ↓ %s · ↑ %s",
        "Mobile statistics unavailable" to "மொபைல் தரவு புள்ளிவிவரங்கள் கிடைக்கவில்லை",
        "Pack used: %s / %s · left: %s" to "தொகுப்பில் பயன்படுத்தியது: %s / %s · மீதி: %s",
        "Unlimited 5G mode is user-set; quota display is hidden." to "வரம்பற்ற 5G என்பது பயனர் தேர்வு; பயன்பாட்டு வரம்பு காட்டப்படாது.",
        "Charger connected: %s" to "சார்ஜர் இணைக்கப்பட்டுள்ளது: %s",
        "Card updated: %s" to "அட்டை புதுப்பித்த நேரம்: %s",
        "Charging power is not reported by this phone. No wattage is guessed." to "இந்தப் போன் சார்ஜ் சக்தியை வழங்கவில்லை. வாட்ஸ் ஊகிக்கப்படாது.",
        "Watts are measured at the battery, not at the charger adapter." to "வாட்ஸ் பேட்டரியில் அளவிடப்படுகிறது; சார்ஜர் அடாப்டரின் சக்தி அல்ல.",
        "Mobile data statistics are unavailable; no usage or remaining quota is guessed." to "மொபைல் தரவு புள்ளிவிவரங்கள் கிடைக்கவில்லை; பயன்பாடோ மீதமுள்ள வரம்போ ஊகிக்கப்படாது.",
        "Partial observed mobile counter; gaps/reboots can miss traffic. Not the carrier's official balance. Wi-Fi excluded." to "காணப்பட்ட மொபைல் தரவு கணக்கு முழுமையற்றது; இடைவெளிகள்/மறுதொடக்கங்களில் தரவு விடுபடலாம். இது சேவை வழங்குநரின் அதிகாரப்பூர்வ இருப்பல்ல. Wi-Fi சேர்க்கப்படவில்லை.",
        "Mobile data usage excludes Wi-Fi and is not the carrier's official balance." to "மொபைல் தரவுப் பயன்பாட்டில் Wi-Fi சேராது; இது சேவை வழங்குநரின் அதிகாரப்பூர்வ இருப்பல்ல.",
        "Monitoring active. Open Live Guard for available phone readings." to "கண்காணிப்பு இயங்குகிறது. கிடைக்கும் போன் அளவீடுகளுக்கு லைவ் கார்டைத் திறக்கவும்.",
        "Battery-side charging power" to "பேட்டரியின் சார்ஜ் சக்தி",
        "Live Guard monitor" to "லைவ் கார்டு கண்காணிப்பு",
        "Battery reading unavailable. Monitoring is active; no value is guessed." to "பேட்டரி அளவீடு கிடைக்கவில்லை. கண்காணிப்பு இயங்குகிறது; மதிப்புகள் ஊகிக்கப்படாது.",
        "Wi-Fi network alerts" to "Wi-Fi நெட்வொர்க் எச்சரிக்கைகள்",
        "Alerts for newly observed Wi-Fi devices" to "புதிதாகக் காணப்படும் Wi-Fi சாதனங்களுக்கான எச்சரிக்கைகள்",
        "Live Guard Wi-Fi alert" to "லைவ் கார்டு Wi-Fi எச்சரிக்கை",
        "Charging" to "சார்ஜ் ஆகிறது",
        "Discharging" to "பேட்டரி சக்தி பயன்படுத்தப்படுகிறது",
        "Not charging" to "சார்ஜ் ஆகவில்லை",
        "Full" to "முழுமையாக சார்ஜ் ஆனது",
        "Unknown" to "தெரியவில்லை",
        "Model listed; generation not tested." to "மாடல் பட்டியலில் உள்ளது; பதில் உருவாக்குவது சோதிக்கப்படவில்லை.",
        "Provider answered: %s" to "சேவை பதிலளித்தது: %s",
        "Provider connection check failed" to "சேவையின் இணைப்புச் சோதனை தோல்வியடைந்தது",
        "The provider returned no models." to "சேவை மாடல் பட்டியலை வழங்கவில்லை.",
        "%s models available" to "%s மாடல்கள் கிடைக்கின்றன",
        "Online AI selected: %s" to "தேர்ந்தெடுத்த ஆன்லைன் AI: %s",
        "Reading saved review…" to "சேமித்த ஆய்வை ஏற்றுகிறது…",
        "Clear last-review summary" to "கடைசி ஆய்வுச் சுருக்கத்தை அழி",
        "Saved last review only; not current findings or a safety verdict." to "சேமித்த கடைசி ஆய்வு மட்டுமே; தற்போதைய கண்டுபிடிப்புகளோ பாதுகாப்புச் சான்றோ அல்ல.",
        "Phone clock changed; saved date may be ahead of now." to "போனின் நேரம் மாறியுள்ளது; சேமித்த தேதி தற்போதைய நேரத்தைவிட முன்னதாக இருக்கலாம்.",
        // Additional section audit: consistent bilingual labels and honest limits.
        "RSS headlines · no AI generation · publication dates are not independently verified" to "RSS செய்தித் தலைப்புகள் · AI பதில் உருவாக்கம் இல்லை · வெளியீட்டுத் தேதிகள் தனியாகச் சரிபார்க்கப்படவில்லை",
        "No usable live price evidence · no AI generation request" to "பயன்படுத்தக்கூடிய தற்போதைய விலை ஆதாரம் இல்லை · AI பதில் உருவாக்கக் கோரிக்கை இல்லை",
        "Home" to "முகப்பு",
        "Back" to "பின்செல்",
        "Refresh" to "புதுப்பி",
        "Analyse again" to "மீண்டும் பகுப்பாய்வு",
        "Refresh now" to "இப்போது புதுப்பி",
        "Send" to "அனுப்பு",
        "Voice" to "குரல்",
        "Ask" to "கேள்",
        "Inspect" to "ஆய்வு",
        "Reload" to "மீளேற்று",
        "Rescan" to "மீண்டும் ஸ்கேன் செய்",
        "Name device" to "சாதனத்திற்குப் பெயரிடு",
        "Known device" to "அறிந்த சாதனம்",
        "Expand" to "விரிவாக்கு",
        "Collapse" to "சுருக்கு",
        "Scan again" to "மீண்டும் ஸ்கேன் செய்",
        "Copy" to "நகலெடு",
        "Tamil" to "தமிழ்",
        "English" to "ஆங்கிலம்",
        "Free space, cache help — answers on this phone" to "காலி இடம், கேச் உதவி — இந்தப் போனில் உள்ளூர் பதில்கள்",
        "Ask any question…" to "எந்தக் கேள்வியையும் கேளுங்கள்…",
        "Mobile & Data Pack" to "மொபைல் தரவு & தரவுத் தொகுப்பு",
        "Today & Pack quota tracking" to "இன்றைய பயன்பாடு & தொகுப்பு வரம்புக் கணக்கு",
        "Unlimited 5G Pack" to "வரம்பற்ற 5G தொகுப்பு",
        "Hide daily limit & quota countdown on unlimited plans" to "வரம்பற்ற திட்டத்தில் தினசரி வரம்பு மற்றும் மீதிக் கணக்கை மறை",
        "Mobile data (Today)" to "இன்றைய மொபைல் தரவு",
        "Pack balance left" to "தொகுப்பில் மீதமுள்ள தரவு",
        "Pack Plan: " to "தரவுத் திட்டம்: ",
        "Not loaded" to "ஏற்றப்படவில்லை",
        "Strict Mobile Only" to "மொபைல் தரவு மட்டும்",
        "Set Pack Size" to "தொகுப்பின் அளவை அமை",
        "Reset Recharge" to "புதிய ரீசார்ஜ் காலத்தைத் தொடங்கு",
        "Unlimited 5G is a user setting, not a verified carrier entitlement. Quota countdown and pack warnings are hidden." to "வரம்பற்ற 5G என்பது பயனர் அமைப்பு; சேவை வழங்குநரின் உறுதிப்படுத்திய தகுதி அல்ல. மீதமுள்ள வரம்பின் கணக்கும் தொகுப்பு எச்சரிக்கைகளும் மறைக்கப்படும்.",
        "Reset Recharge Data Pack" to "புதிய ரீசார்ஜ் தரவுக் காலத்தைத் தொடங்கு",
        "Did you recharge a new data pack? Enter your pack size in GB (e.g. 1.5, 3, 6, 12, 25, 50 GB) and reset the counter to zero." to "புதிய தரவுத் தொகுப்பை ரீசார்ஜ் செய்தீர்களா? அதன் அளவை GB-இல் உள்ளிட்டு (எ.கா. 1.5, 3, 6, 12, 25, 50 GB) உள்ளூர் கணக்கை மீண்டும் தொடங்கவும்.",
        "Pack Size in GB" to "தொகுப்பு அளவு (GB)",
        "Reset to 0 MB" to "உள்ளூர் கணக்கை 0 MB-இல் தொடங்கு",
        "Change Data Pack Limit" to "தரவுத் தொகுப்பு வரம்பை மாற்று",
        "Update your total data quota in GB without resetting current usage counter:" to "தற்போதைய பயன்பாட்டுக் கணக்கை மாற்றாமல் மொத்தத் தரவு வரம்பை GB-இல் மாற்றவும்:",
        "Pack Quota (GB)" to "தொகுப்பு வரம்பு (GB)",
        "Save Limit" to "வரம்பைச் சேமி",
        "Run the self-test to see this phone's capabilities." to "இந்தப் போனின் ஆதரவை அறிய சுயசோதனையை இயக்கவும்.",
        "Last two observed 20% → 80% intervals: %s / %s" to "காணப்பட்ட கடைசி இரண்டு 20% → 80% இடைவெளிகள்: %s / %s",
        "Security last checked %s" to "கடைசி பாதுகாப்பு ஆய்வு: %s",
        "Ask AI" to "AI-யிடம் கேள்",
        "used" to "பயன்படுத்தியது",
        "total" to "மொத்தம்",
        "Checking…" to "சோதிக்கிறது…",
        "Tap to run a review" to "ஆய்வை இயக்கத் தொடவும்",
        "Live App Connections & Trackers" to "ஆப் இணைப்புகள் & கண்காணிப்புத் தடயங்கள்",
        "Inspect active destinations & track telemetry" to "காணப்படும் இணைப்பு இலக்குகள் மற்றும் தகவல் பரிமாற்றத் தடயங்களை ஆய்வு செய்",
        "Inspecting connections…" to "இணைப்புகளை ஆய்வு செய்கிறது…",
        "Inspect App Trackers" to "ஆப் கண்காணிப்புத் தடயங்களை ஆய்வு செய்",
        "%s apps with currently visible established TCP connections" to "தற்போது காணப்படும் செயலில் உள்ள TCP இணைப்புகள் கொண்ட ஆப்கள்: %s",
        "%s visible TCP sockets in this snapshot · %s" to "இந்தத் தருணப் பதிவில் %s TCP சாக்கெட்டுகள் தெரிகின்றன · %s",
        "%s heuristic tracker-labelled live sockets" to "ஊகத்தின் அடிப்படையில் கண்காணிப்புத் தடயமாகக் குறிக்கப்பட்ட நேரடி சாக்கெட்டுகள்: %s",
        "/proc TCP socket files were readable for this best-effort snapshot; Android may still hide per-app connections." to "/proc TCP சாக்கெட் கோப்புகளை முயன்ற அளவிலான இந்தத் தருணப் பதிவில் படிக்க முடிந்தது; Android இன்னும் ஆப் வாரியான இணைப்புகளை மறைக்கலாம்.",
        "Live TCP socket visibility is unavailable on this device; no current app count can be confirmed." to "இந்த சாதனத்தில் நேரடி TCP சாக்கெட் காட்சியளிப்பு கிடைக்கவில்லை; தற்போதைய ஆப் எண்ணிக்கையை உறுதிப்படுத்த முடியாது.",
        "No established TCP app sockets were visible in this read. This does not prove that no app is connected." to "இந்த வாசிப்பில் செயலில் உள்ள TCP ஆப் சாக்கெட்டுகள் எதுவும் தெரியவில்லை. எந்த ஆப்பும் இணைக்கப்படவில்லை என்று இதனால் உறுதியாகாது.",
        "NetworkStats is a separate historical byte counter: %s apps exceeded the display threshold in the last 24 hours. It is not a live-connection count." to "NetworkStats என்பது தனியான கடந்தகால பைட் கணக்கு: கடந்த 24 மணிநேரத்தில் %s ஆப்கள் காட்டும் வரம்பை மீறின. இது நேரடி இணைப்புகளின் எண்ணிக்கை அல்ல.",
        "Historical per-app NetworkStats is unavailable or restricted; it is not used in the live connection count." to "ஆப் வாரியான பழைய NetworkStats கிடைக்கவில்லை அல்லது கட்டுப்படுத்தப்பட்டுள்ளது; இது நேரடி இணைப்புகளின் எண்ணிக்கையில் பயன்படுத்தப்படாது.",
        "Company / provider label (heuristic): %s" to "நிறுவனம் / சேவை வழங்குநர் குறிப்பு (ஊகத்தின் அடிப்படையில்): %s",
        "Visible established TCP destination: %s:%s" to "தற்போது காணப்படும் செயலில் உள்ள TCP இலக்கு: %s:%s",
        "NetworkStats history: %s" to "NetworkStats வரலாறு: %s",
        "Open Android App Info" to "Android ஆப் தகவலைத் திற",
        "Use Android's Force stop control there if available. Live Guard cannot force-stop another app." to "அங்கு கிடைத்தால் Android-இன் கட்டாய நிறுத்து கட்டுப்பாட்டைப் பயன்படுத்துங்கள். Live Guard வேறு ஆப்பை நேரடியாக நிறுத்த முடியாது.",
        "Wi-Fi (Today)" to "இன்றைய Wi-Fi தரவு",
        "Unlimited 5G Data Plan" to "வரம்பற்ற 5G தரவுத் திட்டம்",
        "Turns off pack limit countdown & daily counters" to "தொகுப்பு வரம்பின் மீதிக் கணக்கையும் தினசரி கணக்குகளையும் மறைக்கும்",
        "Data Pack Plan" to "தரவுத் தொகுப்புத் திட்டம்",
        "Personal Hotspot Active" to "தனிப்பட்ட ஹாட்ஸ்பாட் இயங்குகிறது",
        "Mobile Data by App" to "ஆப் வாரியான மொபைல் தரவு",
        "Wi-Fi Data by App" to "ஆப் வாரியான Wi-Fi தரவு",
        "No cellular data used by apps yet today." to "இன்று ஆப்களின் மொபைல் தரவுப் பயன்பாடு பதிவாகவில்லை.",
        "No Wi-Fi data used by apps yet today." to "இன்று ஆப்களின் Wi-Fi தரவுப் பயன்பாடு பதிவாகவில்லை.",
        "Live Guard Permission Setup & Fix" to "லைவ் கார்டு அனுமதி அமைப்பு & வழிகாட்டல்",
        "Restricted settings may need manual approval" to "கட்டுப்படுத்தப்பட்ட அமைப்புகளுக்கு கைமுறை ஒப்புதல் தேவைப்படலாம்",
        "If a permission (Usage Access or Floating Overlay) appears greyed out or says “Restricted setting: For your security, this setting is unavailable”:" to "பயன்பாட்டு அணுகல் அல்லது மேலடுக்கு அனுமதி மங்கலாகத் தோன்றினால், அல்லது ‘பாதுகாப்பிற்காக இந்தக் கட்டுப்படுத்தப்பட்ட அமைப்பு கிடைக்கவில்லை’ என்று Android காட்டினால்:",
        "Open Live Guard App Info" to "லைவ் கார்டு ஆப் தகவலைத் திற",
        "App Permissions Hub (Turn ON / Turn OFF)" to "ஆப் அனுமதிகள் (இயக்கு / முடக்கு)",
        "Usage Access" to "பயன்பாட்டு அணுகல்",
        "Required for strictly separate Mobile Data vs Wi-Fi usage tracking per app" to "ஆப் வாரியாக மொபைல் தரவு மற்றும் Wi-Fi பயன்பாட்டைத் தனித்தனியாகப் பெறத் தேவை",
        "Display Over Other Apps" to "மற்ற ஆப்களின் மேல் காட்டு",
        "Draws the status bar pill and live illuminated D & U LED meter" to "நிலைப் பட்டை மிதக்கும் குறியையும் D & U ஒளிக் காட்டியையும் காட்டும்",
        "All Files Storage Access" to "அனைத்துக் கோப்புகள் அணுகல்",
        "Scans for residual cache, temp files, and bloatware junk" to "அணுகக்கூடிய கேச் எச்சங்கள், தற்காலிகக் கோப்புகள் மற்றும் தேவையற்ற மீதிகளை ஆய்வு செய்யும்",
        "App Notifications" to "ஆப் அறிவிப்புகள்",
        "Shows persistent data tracking and network quality alerts" to "தொடர் தரவுக் கண்காணிப்பு மற்றும் நெட்வொர்க் தர அறிவிப்புகளைக் காட்டும்",
        "%s findings across %s apps · %s high, %s medium" to "%s கண்டுபிடிப்புகள்; ஆய்வு செய்த ஆப்கள் %s · அதிக கவனம் %s, மிதமான கவனம் %s",
        "Device Name" to "போன் பெயர்",
        "Tailored instructions for your phone model and ROM" to "உங்கள் போன் மாடல் மற்றும் ROM-க்கு ஏற்ற வழிகாட்டல்",
        "Set up AI analysis" to "AI ஆய்வை அமை",
        "Auto-start after phone restart" to "போன் மறுதொடக்கத்திற்குப் பின் தானியங்கித் தொடக்கம்",
        "Some Xiaomi, Oppo and Vivo ROMs expose an Autostart setting. Availability varies; it may help after reboot but cannot guarantee background delivery or survive a force-stop." to "சில Xiaomi, Oppo மற்றும் Vivo ROM-களில் Autostart அமைப்பு இருக்கும். கிடைப்பது மாறுபடும்; மறுதொடக்கத்திற்குப் பின் உதவலாம், ஆனால் பின்னணி செயல்பாட்டை உறுதிப்படுத்தவோ கட்டாய நிறுத்தத்தை மீறவோ முடியாது.",
        "Open Autostart Settings" to "Autostart அமைப்புகளைத் திற",
        "Keeps low-priority background monitoring active with a persistent notification and an instant Stop button. Note: OEM power savers may restrict background execution, and no app survives an Android force-stop." to "நிலையான அறிவிப்பு மற்றும் உடனடி நிறுத்து பொத்தானுடன் குறைந்த முன்னுரிமைப் பின்னணிக் கண்காணிப்பை இயக்கும். தயாரிப்பாளரின் மின் சேமிப்பு கட்டுப்படுத்தலாம்; Android கட்டாய நிறுத்தத்தை எந்த ஆப்பும் மீற முடியாது.",
        "Status Bar Pill & LED Meter" to "நிலைப் பட்டை மிதக்கும் குறி & ஒளிக் காட்டி",
        "Hide mobile data when on Wi-Fi" to "Wi-Fi இணைப்பில் மொபைல் தரவைக் காட்டாதே",
        "Keeps only the animated D & U lights to save space" to "இடத்தைச் சேமிக்க D & U அசையும் ஒளிகளை மட்டும் காட்டும்",
        "Pill Size: %d%%" to "மிதக்கும் குறியின் அளவு: %d%%",
        "Nudge or drag to position beside camera cutout or status bar icons." to "கேமரா திறப்பு அல்லது நிலைப் பட்டை குறிகளுக்கு அருகில் வைக்க நகர்த்தவும் அல்லது இழுக்கவும்.",
        "Daily security warnings" to "தினசரி பாதுகாப்பு எச்சரிக்கைகள்",
        "Spoken Tamil warning only when High or Medium security risk settings/apps are detected." to "அதிக அல்லது மிதமான கவனம் தேவைப்படும் பாதுகாப்பு அமைப்புகள்/ஆப்கள் காணப்பட்டால் மட்டுமே தமிழில் குரல் எச்சரிக்கை.",
        "Daily security risk watch" to "தினசரி பாதுகாப்புக் கவனிப்பாய்வு",
        "Once a day CleanSweep checks security settings, permissions, battery and system health. Speaks out loud strictly when High or Medium risk items are found." to "ஒரு நாளுக்கு ஒருமுறை CleanSweep பாதுகாப்பு அமைப்புகள், அனுமதிகள், பேட்டரி மற்றும் போன் நிலையைச் சோதிக்கும். அதிக அல்லது மிதமான கவனம் தேவைப்படும் விஷயங்கள் காணப்பட்டால் மட்டுமே குரலில் எச்சரிக்கும்.",
        "Provider responded: %s" to "சேவை பதிலளித்தது: %s",
        "Not checked or unavailable" to "சோதிக்கப்படவில்லை அல்லது கிடைக்கவில்லை",
        "Connection checks do not prove generation. Test connection sends a small generation request." to "இணைப்புச் சோதனை மட்டும் பதில் உருவாக்கியதற்குச் சான்றல்ல. ‘இணைப்பைச் சோதி’ சிறிய பதில் உருவாக்கக் கோரிக்கையை அனுப்பும்.",
        "Choose a provider and save its key if required." to "சேவையைத் தேர்ந்தெடுத்து, தேவைப்பட்டால் அதன் API விசையைச் சேமிக்கவும்.",
        "API key configured for %s." to "%s-க்கான API விசை அமைக்கப்பட்டுள்ளது.",
        "No API key configured for %s." to "%s-க்கான API விசை அமைக்கப்படவில்லை.",
        "API key" to "API விசை",
        "Paste API key" to "API விசையை ஒட்டவும்",
        "Saved key is hidden. Enter a new key only to replace it." to "சேமித்த விசை மறைக்கப்பட்டுள்ளது. மாற்ற புதிய விசையை மட்டும் உள்ளிடவும்.",
        "Forget saved key" to "சேமித்த விசையை மறந்து நீக்கு",
        "Saved API keys are encrypted with Android Keystore before being written to CleanSweep's private storage. Each provider has its own key; use Forget key to remove it. The key is sent only to the provider you chose." to "சேமித்த API விசைகள் CleanSweep தனிப்பட்ட சேமிப்பில் எழுதுவதற்கு முன் Android Keystore மூலம் குறியாக்கம் செய்யப்படும். ஒவ்வொரு சேவைக்கும் தனி விசை உண்டு; நீக்க ‘சேமித்த விசையை மறந்து நீக்கு’ என்பதைப் பயன்படுத்தவும். தேர்ந்தெடுத்த சேவைக்கே விசை அனுப்பப்படும்.",
        "Secure key storage is currently unavailable. The key could not be saved encrypted; check the device and re-enter it only after secure storage is available." to "பாதுகாப்பான விசைச் சேமிப்பு இப்போது கிடைக்கவில்லை. குறியாக்கி விசையைச் சேமிக்க முடியவில்லை; சாதனத்தைச் சரிபார்த்து பாதுகாப்பான சேமிப்பு கிடைத்த பிறகே மீண்டும் உள்ளிடவும்.",
        "The API key could not be saved encrypted. It remains available only for this session; check secure storage and enter it again." to "API விசையை குறியாக்கி சேமிக்க முடியவில்லை. இந்த அமர்வில் மட்டும் பயன்படும்; பாதுகாப்பான சேமிப்பைச் சரிபார்த்து மீண்டும் உள்ளிடவும்.",
        "Saved %s key forgotten." to "%s-க்கான சேமித்த விசை நீக்கப்பட்டது.",
        "%s saved — checking it now…" to "%s சேமிக்கப்பட்டது — இப்போது சோதிக்கிறது…",
        "AI settings saved" to "AI அமைப்புகள் சேமிக்கப்பட்டன",
        "No key saved for %s yet." to "%s-க்கான விசை இன்னும் சேமிக்கப்படவில்லை.",
        "Free AI needs no key." to "இலவச AI-க்கு விசை தேவையில்லை.",
        "Could not remove the saved key. Please retry." to "சேமித்த விசையை நீக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
        "VirusTotal key could not be saved encrypted. Please check secure storage and try again." to "VirusTotal விசையை குறியாக்கி சேமிக்க முடியவில்லை. பாதுகாப்பான சேமிப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "VirusTotal key saved — flagged apps now get a second opinion." to "VirusTotal விசை சேமிக்கப்பட்டது — குறியிடப்பட்ட பயன்பாடுகளுக்கு இரண்டாம் கருத்து கிடைக்கும்.",
        "VirusTotal key removed — MalwareBazaar only." to "VirusTotal விசை நீக்கப்பட்டது — MalwareBazaar மட்டும்.",
        "Saved VirusTotal key cannot be read. Remove it before entering a new key." to "சேமித்த VirusTotal விசையைப் படிக்க முடியவில்லை. புதிய விசையை உள்ளிடுவதற்கு முன் இதை நீக்கவும்.",
        "Secure key storage is unavailable. This key is not confirmed encrypted at rest; remove it or retry after secure storage is available." to "பாதுகாப்பான விசைச் சேமிப்பு கிடைக்கவில்லை. இந்த விசை சேமிப்பில் குறியாக்கப்பட்டதா உறுதிப்படுத்தப்படவில்லை; அதை நீக்கவும் அல்லது பாதுகாப்பான சேமிப்பு கிடைத்தபின் மீண்டும் முயற்சிக்கவும்.",
        "Check now" to "இப்போது சோதி",
        "Choose a provider, save its key if required, then load available models. Requests go directly to that provider, not a developer server. Connection checks may run on app opening or settings changes; online questions and manual reports send their selected text and allowed facts." to "சேவையைத் தேர்ந்தெடுத்து, தேவைப்பட்டால் API விசையைச் சேமித்து, கிடைக்கும் மாடல்களை ஏற்றவும். கோரிக்கைகள் நேரடியாக அந்தச் சேவைக்குச் செல்கின்றன; உருவாக்குநரின் சேவையகத்திற்கு அல்ல. ஆப் திறப்பு/அமைப்பு மாற்றத்தில் இணைப்புச் சோதனைகள் நடக்கலாம்; ஆன்லைன் கேள்விகளும் கைமுறை அறிக்கைகளும் தேர்ந்தெடுத்த உரை மற்றும் அனுமதித்த தகவல்களை அனுப்பும்.",
        "Chat subscriptions and API access are separate. Availability, models, quotas and charges depend on the provider and account; check the provider dashboard. A configured model name is not proof it is usable." to "உரையாடல் சந்தாவும் API அணுகலும் தனித்தனி. கிடைப்பது, மாடல்கள், வரம்புகள் மற்றும் கட்டணங்கள் சேவை/கணக்கைப் பொறுத்தவை; சேவையின் கணக்குப் பலகையைச் சரிபார்க்கவும். மாடல் பெயரை அமைத்தது மட்டும் அது இயங்குவதற்குச் சான்றல்ல.",
        "Free AI needs no key and tries at most two endpoints within the same deadline. Shared endpoints may be busy or unavailable. Another provider is an explicit choice, not a silent cross-provider fallback." to "இலவச AI-க்கு API விசை தேவையில்லை; ஒரே நேர வரம்புக்குள் அதிகபட்சம் இரண்டு இணைப்பு முகவரிகளை முயலும். பகிரப்பட்ட சேவைகள் பிஸியாகவோ கிடைக்காமலோ இருக்கலாம். வேறு சேவையைப் பயன்படுத்துவது உங்கள் வெளிப்படையான தேர்வு; மறைமுக மாற்றம் அல்ல.",
        "Free endpoints use their configured models. The reply names the endpoint and model that actually returned an answer." to "இலவச இணைப்புகள் அவற்றுக்காக அமைத்த மாடல்களைப் பயன்படுத்தும். உண்மையில் பதிலளித்த சேவை மற்றும் மாடல் பதிலின் குறிப்பில் தெரியும்.",
        "Load models asks the provider for available model IDs. A listed model still needs a successful generation test; names, access and quotas can change." to "‘மாடல்களை ஏற்று’ சேவையிலிருந்து கிடைக்கும் மாடல் அடையாளங்களைப் பெறும். பட்டியலில் இருப்பதற்கும் வெற்றிகரமாகப் பதில் உருவாக்குவதற்கும் வேறுபாடு உண்டு; பெயர்கள், அணுகல் மற்றும் வரம்புகள் மாறலாம்.",
        "Fetch relevant web excerpts for assistant questions, with an 8-second lookup limit inside the 45-second chat deadline. Prices require exact-product seller evidence; news requires today's publication date. Excerpts and provider search citations are not independently verified." to "உதவியாளர் கேள்விகளுக்கு தொடர்புடைய இணையத் துணுக்குகள் பெறப்படும்; மொத்த 45 விநாடி உரையாடல் வரம்புக்குள் தேடலுக்கு 8 விநாடி. விலைக்கு அதே பொருளின் விற்பனை ஆதாரமும் செய்திக்கு இன்றைய வெளியீட்டுத் தேதியும் தேவை. துணுக்குகள் மற்றும் சேவையின் மேற்கோள்கள் தனியாகச் சரிபார்க்கப்படவில்லை.",
        "The app does not collect file contents, photos, messages or contacts for AI requests. Your typed question/report and allowed facts are sent to the selected provider. Its API key is sent only for authentication. Web lookup sends the question as a search query. Do not type private information you do not want to share." to "AI கோரிக்கைகளுக்காக கோப்பு உள்ளடக்கம், படங்கள், செய்திகள் அல்லது தொடர்புகளை இந்த ஆப் சேகரிக்காது. நீங்கள் எழுதும் கேள்வி/அறிக்கையும் அனுமதித்த தகவல்களும் தேர்ந்தெடுத்த சேவைக்கு அனுப்பப்படும். API விசை அடையாளச் சரிபார்ப்பிற்காக மட்டுமே அனுப்பப்படும். இணையத் தேடலில் கேள்வி தேடல் உரையாகச் செல்கிறது. பகிர விரும்பாத தனிப்பட்ட தகவலை எழுத வேண்டாம்.",
        "Base URL" to "சேவையின் அடிப்படை இணைய முகவரி",
        "Model (or type one)" to "மாடல் (அல்லது பெயரை எழுதவும்)",
        "Pick who analyses the report" to "அறிக்கையை ஆய்வு செய்யும் சேவையைத் தேர்ந்தெடு",
        "Choose the model" to "மாடலைத் தேர்ந்தெடு",
        "Socket visibility is best-effort and may be restricted by Android. Company and tracker labels are heuristics, not proof of identity or uploaded content." to "Android இணைப்புத் தகவலைக் கட்டுப்படுத்தலாம்; கிடைக்கும் தகவல் மட்டுமே காட்டப்படும். நிறுவனம்/கண்காணிப்பு அடையாளங்கள் ஊகக் குறிப்புகள்; உண்மையான அடையாளம் அல்லது அனுப்பிய உள்ளடக்கத்திற்குச் சான்றல்ல.",
        "No outward connections were visible in this read. Android may restrict access; this does not prove there are none." to "இந்த வாசிப்பில் வெளி இணைப்புகள் தெரியவில்லை. Android அணுகலைக் கட்டுப்படுத்தலாம்; இணைப்புகளே இல்லை என்பதற்குச் சான்றல்ல.",
        "Long answers may be shortened to fit speech limits." to "நீண்ட பதில்கள் குரல் வாசிப்பு வரம்புக்காகச் சுருக்கப்படலாம்.",
        "Daily phone brief" to "தினசரி போன் அறிக்கை",
        "Daily local readings and permission review" to "தினசரி உள்ளூர் அளவீடுகள் மற்றும் அனுமதி ஆய்வு",
        "Live Guard daily brief" to "லைவ் கார்டு தினசரி அறிக்கை",
        "Could not prepare the phone report. Retry or use the local action checklist." to "போன் அறிக்கையைத் தயாரிக்க முடியவில்லை. மீண்டும் முயலவும் அல்லது உள்ளூர் செயல் பட்டியலைப் பயன்படுத்தவும்.",
        "Requested model: %s" to "கோரிய மாடல்: %s",
        "Online provider selected: %s. A connection check is not proof a model answered. Replies show the responding service, requested model and elapsed time; a requested ID does not prove the underlying execution model." to "தேர்ந்தெடுத்த ஆன்லைன் சேவை: %s. இணைப்புச் சோதனை மட்டும் மாடல் பதிலளித்ததற்குச் சான்றல்ல. பதில் தந்த சேவை, கோரிய மாடல் மற்றும் எடுத்த நேரம் காட்டப்படும்; கோரிய அடையாளம் மட்டும் உண்மையில் இயங்கிய மாடலுக்குச் சான்றல்ல.",
        "Free endpoints use configured model IDs. Replies name the endpoint that returned an answer and its requested model; the underlying routed model may not be reported." to "இலவச இணைப்புகள் அமைத்த மாடல் அடையாளங்களைப் பயன்படுத்தும். பதிலளித்த சேவை முகவரியும் கோரிய மாடலும் காட்டப்படும்; உண்மையில் வழிமாற்றி பயன்படுத்திய மாடல் தெரியாமல் இருக்கலாம்.",

        // Reviewed setup, verified updates and core no-notification-reading notice.
        "Reviewed feature setup" to "வசதிகளைப் பரிசீலித்து அமைத்தல்",
        "%d features need setup or Android access" to "%d வசதிகளுக்கு அமைப்பு அல்லது Android அணுகல் தேவை",
        "Review feature setup" to "வசதி அமைப்பைப் பரிசீலி",
        // v2.40 core: the removed notification reader, stated plainly in both languages.
        "Core Live Guard does not read other apps' notifications or messages. It declares no notification-listener, SMS, Contacts, call-log, Accessibility or device-admin capability. Ordinary Live Guard alerts (battery, charging, storage, daily brief) remain." to "லைவ் கார்டின் மையப் பயன்பாடு மற்ற ஆப்களின் அறிவிப்புகளையோ செய்திகளையோ வாசிக்காது. அறிவிப்பு-கேட்பான் சேவை, SMS, தொடர்புகள், அழைப்புப் பதிவு, அணுகல் திறன் அல்லது சாதன நிர்வாக அனுமதி எதையும் இந்த ஆப் அறிவிக்காது. லைவ் கார்டின் சாதாரண எச்சரிக்கைகள் (பேட்டரி, சார்ஜிங், சேமிப்பு, தினசரி அறிக்கை) தொடர்ந்து இயங்கும்.",
        "Setup reviews only Live Guard's own functions: safe local readings and refresh defaults do not grant access or delete data. Core Live Guard does not read other apps' notifications or messages, so there is no private-message switch here. Existing OFF choices remain OFF unless you change them here." to "இந்த அமைப்பு லைவ் கார்டின் சொந்த வசதிகளை மட்டுமே பரிசீலிக்கிறது: பாதுகாப்பான உள்ளூர் அளவீடுகளும் புதுப்பிப்பு அமைப்புகளும் அனுமதி வழங்கவோ தரவை நீக்கவோ செய்யாது. லைவ் கார்டின் மையப் பயன்பாடு மற்ற ஆப்களின் அறிவிப்புகளையோ செய்திகளையோ வாசிக்காது; எனவே தனிப்பட்ட செய்திக்கான தேர்வு இங்கு இல்லை. நீங்கள் மாற்றாவிட்டால் ஏற்கனவே முடக்கிய தேர்வுகள் முடங்கியே இருக்கும்.",
        "Notification reader" to "அறிவிப்பு வாசிப்பு",
        "Review optional functions before enabling them. Saved OFF choices are respected." to "விருப்ப வசதிகளை இயக்கும் முன் பரிசீலிக்கவும். சேமித்த முடக்கத் தேர்வுகள் மதிக்கப்படும்.",
        "Your reviewed choices are saved. Optional OFF functions are not failures." to "நீங்கள் பரிசீலித்த தேர்வுகள் சேமிக்கப்பட்டன. விருப்ப வசதிகள் முடங்கியிருப்பது பிழை அல்ல.",
        "Safe local readings and refresh defaults do not grant access, delete data or silently enable private-message reading. Existing OFF choices remain OFF unless you change them here." to "பாதுகாப்பான உள்ளூர் அளவீடுகளும் புதுப்பிப்பு அமைப்புகளும் அனுமதி வழங்கவோ தரவை நீக்கவோ தனிப்பட்ட செய்தி வாசிப்பை மறைமுகமாக இயக்கவோ செய்யாது. இங்கு நீங்கள் மாற்றாவிட்டால் ஏற்கனவே முடக்கிய தேர்வுகள் முடங்கியே இருக்கும்.",
        "This checklist describes configuration, not proof of background delivery, audible speech or a successful AI response." to "இந்தப் பட்டியல் அமைப்பு நிலையை மட்டுமே காட்டும்; பின்னணி செயல்பாடு, கேட்ட குரல் அல்லது வெற்றிகரமான AI பதிலுக்குச் சான்றல்ல.",
        "Persistent background monitoring" to "தொடர் பின்னணி கண்காணிப்பு",
        "Voice alerts and answers" to "குரல் எச்சரிக்கைகளும் பதில்களும்",
        "Charging notification" to "சார்ஜ் அறிவிப்பு",
        "Daily local check" to "தினசரி உள்ளூர் சோதனை",
        "Optional setup reminder notifications" to "விருப்ப அமைப்பு நினைவூட்டல் அறிவிப்புகள்",
        "Optional automatic online daily summaries" to "விருப்ப தானியங்கி ஆன்லைன் தினசரி சுருக்கங்கள்",
        "Automatic online daily summaries send allowed phone-report facts to the selected provider and may use API quota. Off by default. This is separate from local checks and does not include notification messages." to "தானியங்கி ஆன்லைன் தினசரி சுருக்கங்கள் அனுமதித்த போன் அறிக்கைத் தகவல்களைத் தேர்ந்தெடுத்த சேவைக்கு அனுப்பும்; API வரம்பு பயன்படுத்தப்படலாம். இயல்பாக முடங்கியுள்ளது. இது உள்ளூர் சோதனையிலிருந்து தனியானது; அறிவிப்புச் செய்திகள் இதில் சேராது.",
        "Background and charging notification choices need Android notification permission. Grant it first, then apply the choices you want." to "பின்னணி மற்றும் சார்ஜ் அறிவிப்புத் தேர்வுகளுக்கு Android அறிவிப்பு அனுமதி தேவை. முதலில் அதை வழங்கிவிட்டு விரும்பும் தேர்வுகளைப் பயன்படுத்தவும்.",
        "Review Android notification permission" to "Android அறிவிப்பு அனுமதியைப் பரிசீலி",
        "Apply reviewed choices" to "பரிசீலித்த தேர்வுகளைப் பயன்படுத்து",
        "Choices saved where allowed. Background notification choices remain unchanged until you grant access and apply again." to "அனுமதி உள்ள தேர்வுகள் சேமிக்கப்பட்டன. அணுகலை வழங்கி மீண்டும் பயன்படுத்தும்வரை பின்னணி அறிவிப்புத் தேர்வுகள் மாறாது.",
        "Reviewed choices saved. No access was granted or data deleted automatically." to "பரிசீலித்த தேர்வுகள் சேமிக்கப்பட்டன. அனுமதியோ தரவு நீக்கமோ தானாகச் செய்யப்படவில்லை.",
        "Configured; operation still depends on Android and actual readings" to "அமைக்கப்பட்டுள்ளது; செயல்பாடு Android மற்றும் உண்மையான அளவீடுகளைப் பொறுத்தது",
        "Needs Android access" to "Android அணுகல் தேவை",
        "Not configured" to "அமைக்கப்படவில்லை",
        "Optional OFF or setup reminder skipped" to "விருப்ப வசதி முடக்கம் அல்லது அமைப்பு நினைவூட்டல் தவிர்க்கப்பட்டது",
        "Open setup / details" to "அமைப்பு / விவரங்களைத் திற",
        "Restore this setup reminder" to "இந்த அமைப்பு நினைவூட்டலை மீளமை",
        "Skip this setup reminder" to "இந்த அமைப்பு நினைவூட்டலைத் தவிர்",
        "How to add compact widgets" to "சிறிய விட்ஜெட்களைச் சேர்ப்பது எப்படி",
        "Long-press the launcher, choose Widgets, then Live Guard Battery, Data or Phone. Each has a View selector. Launcher cell sizes vary. Last unplug is saved only when an actual power-disconnect event is observed; missed events stay unknown, not invented." to "முகப்புத் திரையை நீண்ட நேரம் அழுத்தி விட்ஜெட்களைத் தேர்ந்தெடுத்து லைவ் கார்டு பேட்டரி, தரவு அல்லது போன் விட்ஜெட்டைச் சேர்க்கவும். ஒவ்வொன்றிலும் காட்சி மாற்றம் உள்ளது. முகப்புத் திரையின் கட்ட அளவுகள் மாறலாம். உண்மையான சார்ஜர் நீக்க நிகழ்வு கவனிக்கப்பட்டால் மட்டுமே கடைசி நீக்க நேரம் சேமிக்கப்படும்; தவறிய நிகழ்வுகள் தெரியாதவையாகவே இருக்கும்.",
        "Clear observed last unplug" to "கவனித்த கடைசி சார்ஜர் நீக்கத்தை அழி",
        "Observed last-unplug record cleared." to "கவனித்த கடைசி சார்ஜர் நீக்கப் பதிவு அழிக்கப்பட்டது.",
        "App updates" to "ஆப் புதுப்பிப்புகள்",
        "Live Guard update available" to "லைவ் கார்டு புதுப்பிப்பு உள்ளது",
        "Version %s is available (installed: %s)." to "பதிப்பு %s கிடைக்கிறது (நிறுவியது: %s).",
        "This stable APK is downloaded inside Live Guard and verified against its SHA-256, package, newer version and installed signing certificate." to "இந்த நிலையான APK லைவ் கார்டிற்குள் பதிவிறக்கப்பட்டு SHA-256, தொகுப்பு, புதிய பதிப்பு மற்றும் நிறுவிய கையொப்பச் சான்றிதழுடன் சரிபார்க்கப்படும்.",
        "Android requires per-app Install unknown apps access and shows its own confirmation. Play Protect remains enabled and is not bypassed." to "ஒவ்வொரு ஆப்பிற்கும் ‘தெரியாத ஆப்களை நிறுவு’ அணுகலை Android தேவைப்படுத்தி, தனியாக உறுதிப்படுத்தும். Play Protect இயக்கத்திலேயே இருக்கும்; தவிர்க்கப்படாது.",
        "Installation access is not granted. Android Settings will open; allow Live Guard only if you trust this update." to "நிறுவல் அணுகல் வழங்கப்படவில்லை. Android அமைப்புகள் திறக்கும்; இந்தப் புதுப்பிப்பை நம்பினால் மட்டுமே லைவ் கார்டை அனுமதிக்கவும்.",
        "What’s new" to "புதியவை",
        "Later" to "பிறகு",
        "Verified in-app download; Android confirms installation." to "ஆப்பிற்குள் சரிபார்த்த பதிவிறக்கம்; நிறுவலை Android உறுதிப்படுத்தும்.",
        "Checks RP-Android-Apps first; InternetRadio remains as a grace-period fallback." to "முதலில் RP-Android-Apps சரிபார்க்கப்படும்; இடைக்காலத்தில் InternetRadio மாற்று சேமிப்பகமாகத் தொடரும்.",
        "Release source" to "வெளியீட்டு மூலம்",
        "Downloading %s of %s" to "%s / %s பதிவிறங்குகிறது",
        "Installed version: %s" to "நிறுவிய பதிப்பு: %s",
        "Downloads only a stable published Live Guard APK from the official GitHub repository. SHA-256, package, higher version code and the installed signing certificate must match. No browser download or silent installation." to "அதிகாரப்பூர்வ GitHub சேமிப்பகத்திலிருந்து வெளியிடப்பட்ட நிலையான லைவ் கார்டு APK மட்டுமே பதிவிறங்கும். SHA-256, தொகுப்பு அடையாளம், உயர்ந்த பதிப்பு எண் மற்றும் நிறுவிய ஆப்பின் கையொப்பச் சான்றிதழ் பொருந்த வேண்டும். உலாவி பதிவிறக்கமோ மறைமுக நிறுவலோ இல்லை.",
        "Android may require Install unknown apps permission for Live Guard. Enable it only if you trust this update; you can turn it off afterward. Android installer confirmation is required. Play Protect and any scanning decisions belong to Android and cannot be forced or bypassed." to "லைவ் கார்டுக்கு ‘தெரியாத ஆப்களை நிறுவு’ அனுமதியை Android கேட்கலாம். இந்தப் புதுப்பிப்பை நம்பினால் மட்டுமே இயக்கவும்; பிறகு முடக்கலாம். Android நிறுவியின் உறுதிப்படுத்தல் அவசியம். Play Protect மற்றும் சோதனை முடிவுகளை Android தீர்மானிக்கும்; அவற்றைக் கட்டாயப்படுத்தவோ தவிர்க்கவோ முடியாது.",
        "Daily update check on app launch" to "ஆப் திறக்கும்போது தினசரி புதுப்பிப்புச் சோதனை",
        "Last update check: %s" to "கடைசி புதுப்பிப்புச் சோதனை: %s",
        "Published version: %s" to "வெளியிட்ட பதிப்பு: %s",
        "Published: %s" to "வெளியிட்ட நேரம்: %s",
        "Release notes are publisher text; not automatically translated." to "வெளியீட்டுக் குறிப்புகள் வெளியீட்டாளரின் உரை; தானாக மொழிபெயர்க்கப்படாது.",
        "No release notes were published." to "வெளியீட்டுக் குறிப்புகள் வெளியிடப்படவில்லை.",
        "Installation access is not granted. Tap below to open Android permission settings, then return and tap Install." to "நிறுவல் அணுகல் வழங்கப்படவில்லை. கீழே அழுத்தி Android அனுமதி அமைப்பைத் திறந்து, திரும்பி வந்து ‘நிறுவு’ என்பதை அழுத்தவும்.",
        "Download and verify APK" to "APK-ஐப் பதிவிறக்கிச் சரிபார்",
        "Check published updates" to "வெளியிட்ட புதுப்பிப்புகளைச் சரிபார்",
        "Update" to "புதுப்பிப்பு",
        "Verifying downloaded APK…" to "பதிவிறக்கிய APK சரிபார்க்கப்படுகிறது…",
        "Downloading APK inside Live Guard…" to "லைவ் கார்டிற்குள் APK பதிவிறங்குகிறது…",
        "Install with Android confirmation" to "Android உறுதிப்படுத்தலுடன் நிறுவு",
        "Allow installation for Live Guard" to "லைவ் கார்டிற்கு நிறுவல் அனுமதி வழங்கு",
        "No newer published, verifiable APK was found. Test builds are not releases." to "சரிபார்க்கக்கூடிய புதிய வெளியிட்ட APK கிடைக்கவில்லை. சோதனைக் கட்டமைப்புகள் வெளியீடுகள் அல்ல.",
        "Official update metadata verified. The APK is checked before installation." to "அதிகாரப்பூர்வ புதுப்பிப்புத் தகவல்கள் சரிபார்க்கப்பட்டன. நிறுவுவதற்கு முன் APK சரிபார்க்கப்படும்.",
        "Update check failed; check your connection and try again." to "புதுப்பிப்புச் சோதனை தோல்வி; இணைப்பைச் சரிபார்த்து மீண்டும் முயலவும்.",
        "Update operation cancelled. No installation was started." to "புதுப்பிப்புச் செயல் ரத்து செய்யப்பட்டது. நிறுவல் தொடங்கப்படவில்லை.",
        "APK digest, package, newer version and signing certificate match. Continue to Android installer." to "APK சரிபார்ப்பு மதிப்பு, தொகுப்பு, புதிய பதிப்பு மற்றும் கையொப்பச் சான்றிதழ் பொருந்துகின்றன. Android நிறுவிக்குச் செல்லவும்.",
        "Download or APK verification failed. Nothing will be installed. Try again or check update help." to "பதிவிறக்கம் அல்லது APK சரிபார்ப்பு தோல்வி. எதுவும் நிறுவப்படாது. மீண்டும் முயலவும் அல்லது புதுப்பிப்பு உதவியைப் பார்க்கவும்.",
        "Open Android Settings, Special app access, Install unknown apps, then allow Live Guard only if you trust this update." to "Android அமைப்பில் சிறப்பு ஆப் அணுகல், தெரியாத ஆப்களை நிறுவு என்பவற்றைத் திறந்து, இந்தப் புதுப்பிப்பை நம்பினால் மட்டுமே லைவ் கார்டை அனுமதிக்கவும்.",
        "Android installer opened. Confirm there; installation and Play Protect results are controlled by Android." to "Android நிறுவி திறக்கப்பட்டது. அங்கு உறுதிப்படுத்தவும்; நிறுவல் மற்றும் Play Protect முடிவுகளை Android கட்டுப்படுத்தும்.",
        "Android installer could not open or verification no longer matches. Check install permission and update help." to "Android நிறுவியைத் திறக்க முடியவில்லை அல்லது சரிபார்ப்பு இனி பொருந்தவில்லை. நிறுவல் அனுமதியையும் புதுப்பிப்பு உதவியையும் பார்க்கவும்.",
        "Selected notification reading" to "தேர்ந்தெடுத்த அறிவிப்புகளை வாசித்தல்",
        "Off by default. Choose apps and optional sender names. Sender name only is the default; full message reading is a separate choice for each app. No app name, time or notification count is spoken." to "இயல்பாக முடக்கம். ஆப்களையும் விரும்பினால் அனுப்புநர் பெயர்களையும் தேர்ந்தெடுக்கவும். இயல்பாக அனுப்புநர் பெயர் மட்டுமே வாசிக்கப்படும்; முழுச் செய்தி வாசிப்பு ஒவ்வொரு ஆப்பிற்கும் தனித் தேர்வு. ஆப் பெயர், நேரம் அல்லது அறிவிப்பு எண்ணிக்கை வாசிக்கப்படாது.",
        "Notification access lets Android deliver broad notification content to this service. Live Guard discards unselected apps before reading content. It does not read old notifications, upload or save messages, or request Contacts access. App-supplied sender names are not verified identities." to "அறிவிப்பு அணுகல் இந்தச் சேவைக்கு பரந்த அறிவிப்பு உள்ளடக்கத்தை Android வழங்க அனுமதிக்கும். தேர்ந்தெடுக்காத ஆப்களின் உள்ளடக்கத்தை வாசிக்கும் முன்பே லைவ் கார்டு புறக்கணிக்கும். பழைய அறிவிப்புகள் வாசிக்கப்படாது; செய்திகள் பதிவேற்றவோ சேமிக்கவோ செய்யப்படாது; தொடர்புகள் அனுமதி கேட்கப்படாது. ஆப் வழங்கும் அனுப்புநர் பெயர்கள் சரிபார்த்த அடையாளங்கள் அல்ல.",
        "Notification reading uses only an installed non-network TTS voice in the Live Guard language and on-device Tamil/English translation. Unknown language or missing translation model means sender name only. No private message is sent to an AI or online speech service by Live Guard." to "அறிவிப்பு வாசிப்பு லைவ் கார்டின் மொழியில் நிறுவிய இணையமற்ற TTS குரலையும் போனிலேயே தமிழ்/ஆங்கில மொழிபெயர்ப்பையும் மட்டுமே பயன்படுத்தும். மொழி தெரியாவிட்டாலோ மொழிபெயர்ப்பு மாடல் இல்லாவிட்டாலோ அனுப்புநர் பெயர் மட்டுமே வாசிக்கப்படும். தனிப்பட்ட செய்தியை லைவ் கார்டு AI அல்லது ஆன்லைன் குரல் சேவைக்கு அனுப்பாது.",
        "Your installed TTS engine implements its offline voice. Its own privacy policy still applies. Delivery depends on Android access, volume, quiet hours, the voice master switch and OEM background limits." to "நிறுவிய TTS இயந்திரமே அதன் இணையமற்ற குரலைச் செயல்படுத்தும்; அதன் தனியுரிமைக் கொள்கையும் பொருந்தும். செயல்பாடு Android அணுகல், ஒலி அளவு, அமைதி நேரம், மொத்தக் குரல் தேர்வு மற்றும் உற்பத்தியாளரின் பின்னணி வரம்புகளைப் பொறுத்தது.",
        "Enable selected notification reading" to "தேர்ந்தெடுத்த அறிவிப்பு வாசிப்பை இயக்கு",
        "Read only while screen is on and phone unlocked" to "திரை இயங்கியும் போன் திறந்தும் இருக்கும்போது மட்டும் வாசி",
        "Lock-screen reading can expose private sender names and messages to people nearby. Android may hide sensitive content regardless of this choice." to "பூட்டிய திரையில் வாசிப்பது தனிப்பட்ட அனுப்புநர் பெயர்களையும் செய்திகளையும் அருகிலுள்ளவர்களுக்கு வெளிப்படுத்தலாம். இந்தத் தேர்வைப் பொருட்படுத்தாமல் நுணுக்கமான உள்ளடக்கத்தை Android மறைக்கலாம்.",
        "Android notification access granted" to "Android அறிவிப்பு அணுகல் வழங்கப்பட்டுள்ளது",
        "Android notification access not granted" to "Android அறிவிப்பு அணுகல் வழங்கப்படவில்லை",
        "Listener connected; this does not prove speech was heard." to "அறிவிப்புச் சேவை இணைந்துள்ளது; குரல் கேட்கப்பட்டதற்குச் சான்றல்ல.",
        "Listener not connected. Android may restrict or suspend it." to "அறிவிப்புச் சேவை இணையவில்லை. Android அதைக் கட்டுப்படுத்தவோ நிறுத்தவோ செய்யலாம்.",
        "Open Android notification access" to "Android அறிவிப்பு அணுகலைத் திற",
        "If Android blocks access for a sideloaded app, review App info and Allow restricted settings only if you trust Live Guard. Android, not this app, grants access." to "வெளியிலிருந்து நிறுவிய ஆப்பின் அணுகலை Android தடுத்தால், ஆப் தகவலில் ‘கட்டுப்படுத்தப்பட்ட அமைப்புகளை அனுமதி’ என்பதை லைவ் கார்டை நம்பினால் மட்டுமே பரிசீலிக்கவும். இந்த ஆப் அல்ல, Android-தான் அணுகலை வழங்கும்.",
        "Voice master switch is off. Reading stays silent until you explicitly enable voice." to "மொத்தக் குரல் தேர்வு முடங்கியுள்ளது. நீங்கள் வெளிப்படையாக இயக்கும்வரை வாசிப்பு அமைதியாகவே இருக்கும்.",
        "Voice settings and language voices" to "குரல் அமைப்புகளும் மொழிக் குரல்களும்",
        "Select multiple apps" to "பல ஆப்களைத் தேர்ந்தெடு",
        "No apps selected: nothing will be read." to "ஆப்கள் தேர்ந்தெடுக்கப்படவில்லை; எதுவும் வாசிக்கப்படாது.",
        "Turn off and clear sender choices" to "முடக்கி அனுப்புநர் தேர்வுகளை அழி",
        "Read full message" to "முழுச் செய்தியை வாசி",
        "Sender plus translated content; suspected passwords and OTPs are omitted." to "அனுப்புநரும் மொழிபெயர்த்த உள்ளடக்கமும்; கடவுச்சொல் அல்லது OTP எனக் கருதப்படும் உள்ளடக்கம் தவிர்க்கப்படும்.",
        "Sender name only" to "அனுப்புநர் பெயர் மட்டும்",
        "Only selected senders" to "தேர்ந்தெடுத்த அனுப்புநர்கள் மட்டும்",
        "Names are app-scoped notification sender names, not verified contacts. Groups may use a group title. Empty selection reads nobody." to "பெயர்கள் அந்த ஆப்பின் அறிவிப்பு அனுப்புநர் பெயர்கள்; சரிபார்த்த தொடர்புகள் அல்ல. குழுக்களில் குழுப் பெயர் இருக்கலாம். தேர்வு காலியாக இருந்தால் யாரும் வாசிக்கப்படமாட்டார்கள்.",
        "Enable the reader and receive a new message to see sender choices. Existing messages are not replayed. You can also enter a name exactly as it appears." to "அனுப்புநர் தேர்வுகளைக் காண வாசிப்பை இயக்கிப் புதிய செய்தியைப் பெறவும். ஏற்கனவே உள்ள செய்திகள் மீண்டும் வாசிக்கப்படாது. பெயரை அறிவிப்பில் இருப்பதுபோலவும் எழுதலாம்.",
        "Sender name" to "அனுப்புநர் பெயர்",
        "Add sender choice" to "அனுப்புநர் தேர்வைச் சேர்",
        "Remove this app" to "இந்த ஆப்பை நீக்கு",
        "On-device translation models" to "போனிலேயே மொழிபெயர்க்கும் மாடல்கள்",
        "Tamil/English translation model is present; actual translation may still fail." to "தமிழ்/ஆங்கில மொழிபெயர்ப்பு மாடல் உள்ளது; உண்மையான மொழிபெயர்ப்பு தோல்வியடையலாம்.",
        "Translation model is missing or unavailable. Different-language content falls back to sender name only." to "மொழிபெயர்ப்பு மாடல் இல்லை அல்லது கிடைக்கவில்லை. வேறு மொழி உள்ளடக்கத்துக்குப் பதிலாக அனுப்புநர் பெயர் மட்டுமே வாசிக்கப்படும்.",
        "Download requires your approval and an internet connection. Model download traffic goes to Google's model service, not your private message text. Space and data costs vary. Models and app choices stay on this phone." to "பதிவிறக்கத்திற்கு உங்கள் ஒப்புதலும் இணைய இணைப்பும் தேவை. மாடல் பதிவிறக்கக் கோரிக்கை Google மாடல் சேவைக்குச் செல்லும்; தனிப்பட்ட செய்தி உரை செல்லாது. சேமிப்பு இடமும் தரவுச் செலவும் மாறும். மாடல்களும் ஆப் தேர்வுகளும் இந்தப் போனில் இருக்கும்.",
        "Downloading translation model…" to "மொழிபெயர்ப்பு மாடல் பதிவிறங்குகிறது…",
        "Review and download local models" to "பரிசீலித்து உள்ளூர் மாடல்களைப் பதிவிறக்கு",
        "Search apps" to "ஆப்களைத் தேடு",
        "Download local translation models?" to "உள்ளூர் மொழிபெயர்ப்பு மாடல்களைப் பதிவிறக்கவா?",
        "Only model files are downloaded. No message is sent for translation. Android/ML Kit may finish a consented download after you leave this page." to "மாடல் கோப்புகள் மட்டுமே பதிவிறங்கும். மொழிபெயர்ப்பிற்காகச் செய்தி அனுப்பப்படாது. இந்தப் பக்கத்தை விட்டு சென்றாலும் ஒப்புதல் அளித்த பதிவிறக்கத்தை Android/ML Kit முடிக்கலாம்.",
        "Wi-Fi only (recommended)" to "வைஃபை மட்டும் (பரிந்துரை)",
        "Download models" to "மாடல்களைப் பதிவிறக்கு",
        "Local translation model downloaded. No message text was uploaded." to "உள்ளூர் மொழிபெயர்ப்பு மாடல் பதிவிறங்கியது. செய்தி உரை பதிவேற்றப்படவில்லை.",
        "Model download not ready. Keep sender-only reading and retry on Wi-Fi." to "மாடல் பதிவிறக்கம் தயாராகவில்லை. அனுப்புநர் பெயர் மட்டும் வாசிப்பை வைத்திருந்து வைஃபையில் மீண்டும் முயலவும்.",
        "Model download failed. Private messages were not uploaded." to "மாடல் பதிவிறக்கம் தோல்வி. தனிப்பட்ட செய்திகள் பதிவேற்றப்படவில்லை.",
        "Open Android Settings and grant Notification access to Live Guard if you trust it." to "லைவ் கார்டை நம்பினால் Android அமைப்பைத் திறந்து அறிவிப்பு அணுகலை வழங்கவும்.",
        "Notification listener connected" to "அறிவிப்புச் சேவை இணைந்துள்ளது",
        "Notification listener disconnected" to "அறிவிப்புச் சேவை துண்டிக்கப்பட்டது",
        "Translation unavailable; sender name only" to "மொழிபெயர்ப்பு கிடைக்கவில்லை; அனுப்புநர் பெயர் மட்டும்",
        "Notification reading is off" to "அறிவிப்பு வாசிப்பு முடங்கியுள்ளது",
        "Include system apps" to "சிஸ்டம் ஆப்களையும் சேர்",
        "Included for review only; no automatic selection, permission grant or deletion." to "ஆய்வுக்கு மட்டுமே சேர்க்கப்படும்; தானியங்கி தேர்வு, அனுமதி வழங்கல் அல்லது நீக்கம் இல்லை.",
        "Optional VirusTotal hash checks use your account quota only for flagged or manually selected apps. No APK is uploaded. Check current account terms before enabling." to "விருப்ப VirusTotal ஹாஷ் சோதனை குறித்துக் காட்டிய அல்லது கைமுறையாகத் தேர்ந்தெடுத்த ஆப்களுக்கு மட்டும் கணக்கு வரம்பைப் பயன்படுத்தும். APK பதிவேற்றப்படாது. இயக்கும் முன் தற்போதைய கணக்கு விதிகளைப் பார்க்கவும்.",
        "Help and voice guide" to "உதவியும் குரல் வழிகாட்டியும்",
        "Setup guide" to "அமைப்பு வழிகாட்டி",
        "Read guide aloud" to "வழிகாட்டியைச் சத்தமாக வாசி",
        "Stop voice" to "குரலை நிறுத்து",
        "VirusTotal API access is optional. Availability, quotas and terms depend on your account; no permanent free quota is promised." to "VirusTotal API அணுகல் விருப்பமானது. கிடைப்பது, வரம்புகள் மற்றும் விதிகள் கணக்கைப் பொறுத்தவை; நிரந்தர இலவச வரம்பு உறுதியளிக்கப்படாது.",
        "Get VirusTotal API key — official site" to "VirusTotal API விசையைப் பெறு — அதிகாரப்பூர்வ தளம்",
        "VirusTotal key help" to "VirusTotal விசை உதவி",
        "Get API key — official provider" to "API விசையைப் பெறு — அதிகாரப்பூர்வ சேவை",
        "Open official endpoint help" to "அதிகாரப்பூர்வ சேவை உதவியைத் திற",
        "Open the official provider link and sign in. If your account allows API access, create a key, paste it here, save, and run the generation test. Never share the key. Chat subscriptions and API billing are separate; limits or charges may apply." to "அதிகாரப்பூர்வ சேவை இணைப்பைத் திறந்து உள்நுழையவும். கணக்கில் API அணுகல் இருந்தால் விசையை உருவாக்கி இங்கே ஒட்டி சேமித்துப் பதில் உருவாக்கச் சோதிக்கவும். விசையைப் பகிர வேண்டாம். உரையாடல் சந்தாவும் API கட்டணமும் தனியானவை; வரம்புகளோ கட்டணங்களோ இருக்கலாம்.",
        "This shared endpoint mode does not need your key. Availability and quotas can change; free access is not guaranteed. You must explicitly choose another provider if this one is unavailable." to "இந்தப் பகிரப்பட்ட சேவை முறைக்கு உங்கள் விசை தேவையில்லை. கிடைப்பதும் வரம்புகளும் மாறலாம்; இலவச அணுகல் உறுதியில்லை. இந்தச் சேவை கிடைக்காவிட்டால் வேறு சேவையை நீங்களே தேர்ந்தெடுக்க வேண்டும்.",
        "Use only a trusted OpenAI-compatible endpoint and its own API key instructions. There is no universal signup link for custom servers. Check who operates it before sending questions or reports. Never share your key." to "நம்பகமான OpenAI-க்கு இணங்கும் சேவையையும் அதன் API விசை வழிமுறைகளையும் மட்டுமே பயன்படுத்தவும். தனிப்பயன் சேவைகளுக்குப் பொதுவான பதிவு இணைப்பு இல்லை. கேள்வி அல்லது அறிக்கையை அனுப்பும் முன் இயக்குபவரைச் சரிபார்க்கவும். விசையைப் பகிர வேண்டாம்.",
        "Open the official VirusTotal site, sign in, and find API key in your account menu. Paste it in Live Guard only if you choose the second-opinion hash check. Quotas and terms vary. Live Guard sends a file hash, not the APK file. Never share the key." to "அதிகாரப்பூர்வ VirusTotal தளத்தைத் திறந்து உள்நுழைந்து கணக்கு மெனுவில் API விசையைப் பெறவும். கூடுதல் ஹாஷ் சோதனையைத் தேர்ந்தெடுத்தால் மட்டுமே லைவ் கார்டில் ஒட்டவும். வரம்புகளும் விதிகளும் மாறும். கோப்பின் ஹாஷ் மட்டுமே அனுப்பப்படும்; APK அனுப்பப்படாது. விசையைப் பகிர வேண்டாம்.",
        "Choose your apps, then optional sender names. Sender name only is the default. Full messages use local Tamil or English translation and a non-network installed voice. Grant Android notification access only if you trust Live Guard. Missing models mean sender name only. No message is uploaded by this app." to "ஆப்களைத் தேர்ந்தெடுத்து விரும்பினால் அனுப்புநர் பெயர்களையும் தேர்ந்தெடுக்கவும். இயல்பாக அனுப்புநர் பெயர் மட்டும் வாசிக்கப்படும். முழுச் செய்திகள் உள்ளூர் தமிழ்/ஆங்கில மொழிபெயர்ப்பையும் நிறுவிய இணையமற்ற குரலையும் பயன்படுத்தும். லைவ் கார்டை நம்பினால் மட்டுமே Android அறிவிப்பு அணுகலை வழங்கவும். மாடல் இல்லாவிட்டால் பெயர் மட்டும் வாசிக்கப்படும். இந்த ஆப் செய்தியைப் பதிவேற்றாது.",
        "Review each optional function before enabling it. Background monitoring, voice and sensitive Android access are your choices. Grant only the permissions you need. The app keeps your saved off choices and cannot bypass Android confirmation or power restrictions." to "ஒவ்வொரு விருப்ப வசதியையும் இயக்கும்முன் பரிசீலிக்கவும். பின்னணி கண்காணிப்பு, குரல் மற்றும் நுணுக்கமான Android அணுகல் உங்கள் தேர்வுகள். தேவையான அனுமதிகளை மட்டும் வழங்கவும். சேமித்த முடக்கத் தேர்வுகள் பாதுகாக்கப்படும்; Android உறுதிப்படுத்தலையோ மின் கட்டுப்பாடுகளையோ ஆப் தவிர்க்க முடியாது.",
        "File cleanup" to "கோப்பு சுத்தம்",
        "Persistent monitoring" to "தொடர் கண்காணிப்பு",
        "Voice alerts and answer voices" to "குரல் எச்சரிக்கைகளும் பதில் குரல்களும்",
        "Online assistant" to "ஆன்லைன் உதவியாளர்",
        "Data plan and compact widgets" to "தரவுத் திட்டமும் சிறிய விட்ஜெட்களும்",
        "In-app APK updates" to "ஆப்பிற்குள் APK புதுப்பிப்புகள்",
        "Local battery and phone readings need no special access. Missing sensor values remain unavailable." to "உள்ளூர் பேட்டரி மற்றும் போன் அளவீடுகளுக்குச் சிறப்பு அணுகல் தேவையில்லை. சென்சார் தெரிவிக்காத மதிப்புகள் தெரியாதவையாகவே இருக்கும்.",
        "System apps are included by default for inspection only. No permission is granted or app removed automatically." to "சிஸ்டம் ஆப்கள் இயல்பாக ஆய்வுக்கு மட்டும் சேர்க்கப்படும். அனுமதியோ ஆப் நீக்கமோ தானாகச் செய்யப்படாது.",
        "File cleanup requires Android storage access. You review and select items; nothing is deleted by setup." to "கோப்பு சுத்தத்திற்கு Android சேமிப்பு அணுகல் தேவை. நீங்கள் பரிசீலித்து உருப்படிகளைத் தேர்ந்தெடுப்பீர்கள்; அமைப்பு எதையும் நீக்காது.",
        "Usage access is needed for cache sizes. The list refreshes when opened; system apps are visible by default but not auto-selected." to "கேச் அளவுகளுக்குப் பயன்பாட்டு அணுகல் தேவை. திறக்கும்போது பட்டியல் புதுப்பிக்கப்படும்; சிஸ்டம் ஆப்கள் இயல்பாகத் தெரியும், தானாகத் தேர்ந்தெடுக்கப்படாது.",
        "Daily local checks and notifications follow your switches. Online daily summaries require a separate explicit choice." to "தினசரி உள்ளூர் சோதனைகளும் அறிவிப்புகளும் உங்கள் தேர்வுகளின்படி இயங்கும். ஆன்லைன் தினசரி சுருக்கங்களுக்குத் தனியான வெளிப்படையான தேர்வு தேவை.",
        "Optional background service uses an ongoing notification and Stop control. Android force-stop and OEM restrictions still apply." to "விருப்ப பின்னணிச் சேவை தொடரும் அறிவிப்பையும் நிறுத்தும் பொத்தானையும் பயன்படுத்தும். Android கட்டாய நிறுத்தமும் உற்பத்தியாளர் கட்டுப்பாடுகளும் பொருந்தும்.",
        "Enable voice explicitly and preview actual installed language voices. Quiet hours and media volume can suppress speech; callbacks do not prove you heard it." to "குரலை வெளிப்படையாக இயக்கி நிறுவிய மொழிக் குரல்களை முன்னோட்டம் கேட்கவும். அமைதி நேரமும் ஊடக ஒலி அளவும் குரலைத் தடுக்கலாம்; இயந்திர முடிவு மட்டும் நீங்கள் கேட்டதற்குச் சான்றல்ல.",
        "Optional overlay access is required. The floating pill keeps its existing charging-watts behavior." to "விருப்ப மேலடுக்கு அணுகல் தேவை. மிதக்கும் சிறு பட்டையின் சார்ஜ் வாட் காட்சி மாறாது.",
        "Opt-in selected apps and sender names. Sender-only default, local translation and non-network speech; broad Android notification access needs your review." to "ஆப்களையும் அனுப்புநர் பெயர்களையும் ஒப்புதலுடன் தேர்ந்தெடுக்கவும். இயல்பாக பெயர் மட்டும்; உள்ளூர் மொழிபெயர்ப்பும் இணையமற்ற குரலும். பரந்த Android அறிவிப்பு அணுகலைப் பரிசீலிக்க வேண்டும்.",
        "Optional provider setup sends only your approved questions and allowed report facts. An API key or model name is not proof a model ran." to "விருப்ப சேவை அமைப்பு நீங்கள் ஒப்புக்கொண்ட கேள்விகளையும் அனுமதித்த அறிக்கைத் தகவல்களையும் மட்டுமே அனுப்பும். API விசையோ மாடல் பெயரோ மாடல் இயங்கியதற்குச் சான்றல்ல.",
        "Choose battery, data or phone widgets. Metered plans show available usage; Wi-Fi or user-set unlimited 5G shows useful phone details instead." to "பேட்டரி, தரவு அல்லது போன் விட்ஜெட்டைத் தேர்ந்தெடுக்கவும். வரம்புள்ள திட்டங்களில் கிடைக்கும் பயன்பாடும், வைஃபை அல்லது பயனர் தேர்ந்தெடுத்த வரம்பற்ற 5G-இல் பயனுள்ள போன் விவரங்களும் காட்டப்படும்.",
        "Checks only official published releases. Download verification and Android installer confirmation are mandatory; Play Protect cannot be forced or bypassed." to "அதிகாரப்பூர்வ வெளியீடுகள் மட்டுமே சோதிக்கப்படும். பதிவிறக்கச் சரிபார்ப்பும் Android நிறுவி உறுதிப்படுத்தலும் அவசியம்; Play Protect-ஐக் கட்டாயப்படுத்தவோ தவிர்க்கவோ முடியாது.",
        "Optional feature setup reminders" to "விருப்ப வசதி அமைப்பு நினைவூட்டல்கள்",
        "%d selected or core features need setup. Off-by-choice features are not treated as failures." to "%d தேர்ந்தெடுத்த அல்லது அடிப்படை வசதிகளுக்கு அமைப்பு தேவை. விருப்பமாக முடக்கிய வசதிகள் பிழைகளாகக் கருதப்படாது.",
        "Review Live Guard setup" to "லைவ் கார்டு அமைப்பைப் பரிசீலி",
        "Cache refresh failed. Previous readings have not been replaced." to "கேச் புதுப்பிப்பு தோல்வி. முந்தைய அளவீடுகள் மாற்றப்படவில்லை.",
        "Responding service: %s" to "பதிலளித்த சேவை: %s",
        "Selected service; not a completed response: %s" to "தேர்ந்தெடுத்த சேவை; பதில் முடிந்ததல்ல: %s",
        "Waiting for the selected provider…" to "தேர்ந்தெடுத்த சேவைக்காகக் காத்திருக்கிறது…",
        "%d s elapsed; whole-operation limit %d s" to "%d விநாடி கடந்தது; மொத்தச் செயலின் வரம்பு %d விநாடி",
        "Reading installed apps and permissions…" to "நிறுவிய ஆப்களும் அனுமதிகளும் வாசிக்கப்படுகின்றன…",
        "Preparing allowed report facts…" to "அனுமதித்த அறிக்கைத் தகவல்கள் தயாராகின்றன…",
        "Reading local phone observations…" to "உள்ளூர் போன் அளவீடுகள் வாசிக்கப்படுகின்றன…",
        "Report request cancelled. Late results will not be shown." to "அறிக்கைக் கோரிக்கை ரத்து செய்யப்பட்டது. தாமதமாக வரும் முடிவுகள் காட்டப்படாது.",
        "Report operation reached its 90-second limit. Retry or use the local action checklist." to "அறிக்கைச் செயலின் 90 விநாடி வரம்பு முடிந்தது. மீண்டும் முயலவும் அல்லது உள்ளூர் செயல் பட்டியலைப் பயன்படுத்தவும்.",
        "Device AI request cancelled. Late results will not be shown." to "சாதன AI கோரிக்கை ரத்து செய்யப்பட்டது. தாமதமாக வரும் முடிவுகள் காட்டப்படாது.",
        "Device AI operation reached its 90-second limit. Try again." to "சாதன AI செயலின் 90 விநாடி வரம்பு முடிந்தது. மீண்டும் முயலவும்.",
        "Device AI request failed. Check setup or try again." to "சாதன AI கோரிக்கை தோல்வி. அமைப்பைச் சரிபார்க்கவும் அல்லது மீண்டும் முயலவும்.",
        "Set up an AI provider first." to "முதலில் AI சேவையை அமைக்கவும்.",
        "Scan the network first." to "முதலில் வலையமைப்பை ஆய்வு செய்யவும்.",
        "Network sharing is off. Review AI settings before sending device facts." to "வலையமைப்பைப் பகிர்வது முடங்கியுள்ளது. சாதனத் தகவல்களை அனுப்புமுன் AI அமைப்பைப் பரிசீலிக்கவும்.",
        "Turning reading off stops speech but does not revoke Android notification access. Revoke access in Android settings if you no longer want this service connected." to "வாசிப்பை முடக்குவது குரலை நிறுத்தும்; Android அறிவிப்பு அணுகலை நீக்காது. இந்தச் சேவை இணைந்திருக்க வேண்டாம் என்றால் Android அமைப்பில் அணுகலை நீக்கவும்.",
        "No installed non-network voice for this language. Install one in Android TTS settings." to "இந்த மொழிக்கான நிறுவிய இணையமற்ற குரல் இல்லை. Android TTS அமைப்பில் ஒன்றை நிறுவவும்.",
        "Offline preview callback completed. Confirm you heard it yourself; reading was not enabled by preview." to "இணையமற்ற முன்னோட்டத்தின் இயந்திர முடிவு வந்தது. நீங்கள் கேட்டீர்களா என்பதை உறுதிப்படுத்தவும்; முன்னோட்டம் வாசிப்பை இயக்கவில்லை.",
        "Offline voice preview failed. Reading remains subject to your existing choices." to "இணையமற்ற குரல் முன்னோட்டம் தோல்வி. வாசிப்பு உங்கள் தற்போதைய தேர்வுகளின்படியே இருக்கும்.",
        "Preview installed offline voice" to "நிறுவிய இணையமற்ற குரலைக் கேள்",
        "Local network scan unavailable. No device facts were sent to AI." to "உள்ளூர் வலையமைப்பு ஆய்வு கிடைக்கவில்லை. சாதனத் தகவல் AI-க்கு அனுப்பப்படவில்லை.",
        "Local scan complete. Review provider setup and network-sharing consent before AI identification." to "உள்ளூர் ஆய்வு முடிந்தது. AI அடையாள ஆய்வுக்கு முன் சேவை அமைப்பையும் வலையமைப்பைப் பகிரும் ஒப்புதலையும் பரிசீலிக்கவும்.",
        "Optional device AI analysis" to "விருப்ப சாதன AI ஆய்வு",
        "Sends displayed device IPs, hostnames, vendors and ports to your chosen provider only with network sharing enabled. Wi-Fi network name is omitted. AI guesses are not verified identities or saved contact names." to "வலையமைப்பைப் பகிர்வதை இயக்கினால் மட்டுமே காட்டிய சாதன IP, கணினிப் பெயர், நிறுவனக் குறிப்பு மற்றும் போர்ட் தகவல்கள் தேர்ந்தெடுத்த சேவைக்கு அனுப்பப்படும். வைஃபை வலையமைப்புப் பெயர் அனுப்பப்படாது. AI ஊகங்கள் சரிபார்த்த அடையாளங்களோ சேமித்த தொடர்புப் பெயர்களோ அல்ல.",
        "Send current device facts to AI" to "தற்போதைய சாதனத் தகவல்களை AI-க்கு அனுப்பு",
        "Scanning the network locally…" to "வலையமைப்பு உள்ளூரில் ஆய்வு செய்யப்படுகிறது…",
        "Scanning with AI…" to "AI உடன் ஆய்வு செய்கிறது…",
        // Live Guard 2.41 — honest charging numbers, the screen-on/off note and the 44 °C notice.
        "Charging power measured at the battery: %s" to "பேட்டரியில் அளக்கப்பட்ட சார்ஜிங் திறன்: %s",
        "Battery-side current: %s" to "பேட்டரிப் பக்க மின்னோட்டம்: %s",
        "Battery voltage: %s" to "பேட்டரி மின்னழுத்தம்: %s",
        "Battery temperature: %s" to "பேட்டரி வெப்பநிலை: %s",
        "battery-side %s" to "பேட்டரிப் பக்கம் %s",
        "Screen is on, so the phone itself is drawing power. The charging figure is measured at the battery and can read lower than with the screen off — it is not the charger's output." to "திரை இயக்கத்தில் உள்ளது, எனவே ஃபோனே மின்சாரத்தைப் பயன்படுத்துகிறது. சார்ஜிங் எண் பேட்டரியில் அளக்கப்படுகிறது; திரை அணைந்திருக்கும்போது கிடைப்பதைவிடக் குறைவாகத் தெரியலாம் — இது சார்ஜரின் வெளியீடு அல்ல.",
        "Screen is off, so the phone is drawing little. The charging figure is measured at the battery, not the charger's output." to "திரை அணைந்துள்ளது, எனவே ஃபோன் மிகக் குறைவாகவே மின்சாரம் எடுக்கிறது. சார்ஜிங் எண் பேட்டரியில் அளக்கப்படுகிறது; சார்ஜரின் வெளியீடு அல்ல.",
        "Battery temperature is at or above 44 °C (%s). Unusually warm — review charging conditions; this is a temperature notice, not a diagnosis." to "பேட்டரி வெப்பநிலை 44 °C அல்லது அதற்கு மேல் உள்ளது (%s). வழக்கத்திற்கு மாறாக வெப்பம் — சார்ஜ் செய்யும் நிலைமைகளைப் பரிசீலிக்கவும்; இது ஒரு வெப்பநிலை அறிவிப்பு, நோய் கண்டறிதல் அல்ல.",
        "A heat sensor is at or above its warning level (%s). Let the phone cool before heavy use; this is a temperature notice, not a diagnosis." to "ஒரு வெப்ப உணரி அதன் எச்சரிக்கை அளவை எட்டியுள்ளது (%s). கனமான பயன்பாட்டுக்கு முன் ஃபோனைக் குளிரவிடவும்; இது ஒரு வெப்பநிலை அறிவிப்பு, நோய் கண்டறிதல் அல்ல.",
        "Each reply uses a bounded output budget. When a request's own time limit is nearly spent the answer budget shrinks instead of letting one question run into a minute-long wait." to "ஒவ்வொரு பதிலும் வரையறுக்கப்பட்ட வெளியீட்டு நிலைநிதியைப் பயன்படுத்துகிறது. ஒரு கோரிக்கையின் நேர வரம்பு முடியும் தருவாயில், ஒரு கேள்வி நீண்ட நிமிடக் காத்தலாக மாறாமல் பதில் நிலைநிதி குறைக்கப்படுகிறது.",
        // Live Guard 2.42 — battery-current source, automatic answer voices, default-on
        // monitors, permission routing and one home-screen spacing scale.
        "Read from the battery broadcast: this phone's current counter property reports no value." to "பேட்டரி அறிவிப்பிலிருந்து பெறப்பட்டது: இந்த ஃபோனின் மின்னோட்ட எண்ணிடும் பண்பு எந்த மதிப்பையும் தெரிவிக்கவில்லை.",
        "Voice A and Voice B are chosen automatically from your installed voices. Change or preview them here any time." to "குரல் A மற்றும் குரல் B உங்கள் நிறுவப்பட்ட குரல்களிலிருந்து தானாகவே தேர்ந்தெடுக்கப்படுகின்றன. இங்கே எப்போது வேண்டுமானாலும் அவற்றை மாற்றலாம் அல்லது கேட்கலாம்.",
        "The engine reported no matching voice for this language, so answers cannot be spoken in it yet. Install a voice in Android TTS settings." to "இந்த மொழிக்குப் பொருந்தும் எந்தக் குரலையும் இயந்திரம் தெரிவிக்கவில்லை, எனவே இன்னும் அந்த மொழியில் பதில்களைப் பேச முடியாது. Android TTS அமைப்பில் ஒரு குரலை நிறுவவும்.",
        "Only one matching voice was reported, so both slots use it and the two voices cannot alternate yet." to "ஒரே ஒரு பொருந்தும் குரல் மட்டுமே தெரிவிக்கப்பட்டுள்ளது, எனவே இரண்டு இடங்களிலும் அதுவே பயன்படுத்தப்படும்; இரண்டு குரல்கள் மாறி மாறி வர இயலாது.",
        "On by default. Turning one off is your choice and is saved; it is not a failure." to "இயல்பாக இயக்கத்தில் உள்ளது. ஒன்றை முடக்குவது உங்கள் தேர்வு; அது சேமிக்கப்படும். இது ஒரு தோல்வி அல்ல.",
        "Project development contributor: Ramesh prathap .R" to "திட்ட உருவாக்கப் பங்களிப்பாளர்: Ramesh prathap .R",
        "AI software-assistance contributor: Arena.ai Agent Mode" to "AI மென்பொருள் உதவிப் பங்களிப்பாளர்: Arena.ai Agent Mode",
        "App releases" to "பயன்பாட்டு வெளியீடுகள்",
        "Check for updates on app launch" to "பயன்பாட்டைத் திறக்கும்போது புதுப்பிப்புகளைச் சரிபார்",
        "Live Guard — GitHub release" to "Live Guard — GitHub வெளியீடு",
        "Ramesh Radio — GitHub release" to "Ramesh Radio — GitHub வெளியீடு",
        "PulseEQ — GitHub release" to "PulseEQ — GitHub வெளியீடு",
        "RP VOCAL REMOVER STUDIO — GitHub release" to "RP VOCAL REMOVER STUDIO — GitHub வெளியீடு",
    )
}

/** Convenience for UI code: tr("Save") → the label in the chosen language. */
fun tr(text: String, vararg args: Any): String = Lang.t(text, *args)
