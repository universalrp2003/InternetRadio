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
            entries.firstOrNull { it.id == id } ?: EN
    }
}

object Lang {

    /** Prefix for the language mirror that non-Compose code reads. */
    const val PREFS = "cleansweep_state"
    const val KEY = "app_lang"

    /** Where the Tamil name of the app lives, so every screen can show it consistently. */
    private const val APP_NAME_EN = "CleanSweep"
    private const val APP_NAME_TA = "சுத்தம் செய்பவர்"

    @Volatile
    var current: AppLang = AppLang.EN
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
        AppLang.EN
    }

    fun appName(): String = if (isTamil) APP_NAME_TA else APP_NAME_EN

    /** The tagline under the app title. */
    fun tagline(): String = if (isTamil) {
        "எந்த ஆண்ட்ராய்டு போனுக்கும் க்ளீனர், பேட்டரி ஆரோக்கியம் & பாதுகாப்பு"
    } else {
        "Cleaner, battery health & security for any Android phone"
    }

    /** Translates one label, with optional %s values. */
    fun t(text: String, vararg args: Any): String {
        if (current == AppLang.EN) return if (args.isEmpty()) text else safeFormat(text, args)
        val translated = TAMIL[text] ?: return if (args.isEmpty()) text else safeFormat(text, args)
        return if (args.isEmpty()) translated else safeFormat(translated, args)
    }

    private fun safeFormat(pattern: String, args: Array<out Any>): String =
        try {
            String.format(pattern, *args)
        } catch (e: Exception) {
            pattern
        }

    /** Shown in Settings and About. */
    val languageNote: String get() = if (isTamil) {
        "மெனுவின் மொழி. தமிழ் தேர்ந்தெடுத்தால் பயன்பாட்டின் பெயர் “சுத்தம் செய்பவர்” ஆகும்."
    } else {
        "Menu language. Tamil also renames the app to “சுத்தம் செய்பவர்” — “the cleaner”. " +
            "The launcher label follows a Tamil phone too."
    }

    /**
     * English text → Tamil. Kept flat on purpose: one table, easy to extend, and a missing
     * entry is harmless (English shows through).
     */
    private val TAMIL: Map<String, String> = mapOf(
        // ------------------------------------------------------------- app chrome
        "CleanSweep" to "சுத்தம் செய்பவர்",
        "About CleanSweep" to "சுத்தம் செய்பவர் பற்றி",
        "CleanSweep v2.10" to "சுத்தம் செய்பவர் v2.10",
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
        "CleanSweep Assistant" to "சுத்தம் செய்பவர் உதவியாளர்",
        "Detailed answers" to "விரிவான பதில்கள்",
        "Detailed assistant answers" to "விரிவான உதவியாளர் பதில்கள்",
        "Off = the on-device assistant replies in one short line" to
            "ஆஃப் = உதவியாளர் ஒரு சிறு வரியில் பதில் தரும்",
        "Ask about your storage…" to "உங்கள் சேமிப்பைப் பற்றிக் கேளுங்கள்…",
        "CleanSweep AI" to "சுத்தம் செய்பவர் AI",
        "Thinking on this device…" to "இந்த போனிலேயே யோசிக்கிறது…",

        // ----------------------------------------------------------- health screen
        "Full analysis with AI" to "AI மூலம் முழு பரிசோதனை",
        "Charging watts beside the clock" to "கடிகாரம் அருகே சார்ஜ் வாட்",
        "Allow overlay" to "ஓவர்லே யை அனுமதி",
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
        "Mobile data" to "மொபைல் டேட்டா",
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
        "Author" to "எழுத்தாளர்",
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
    )
}

/** Convenience for UI code: tr("Save") → the label in the chosen language. */
fun tr(text: String, vararg args: Any): String = Lang.t(text, *args)
