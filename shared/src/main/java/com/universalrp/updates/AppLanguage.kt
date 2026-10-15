package com.universalrp.updates

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tamil-first interface language shared by the RP Ideas Android apps. */
enum class AppLanguage(val preferenceValue: String) {
    TAMIL("ta"),
    ENGLISH("en");

    companion object {
        fun from(value: String?): AppLanguage = entries.firstOrNull { it.preferenceValue == value } ?: TAMIL
    }
}

object AppLanguageState {
    private const val PREFS = "app_interface_language"
    private const val KEY = "language"

    /** The apps are separate Android processes, so one observable choice per process is enough. */
    var current: AppLanguage by mutableStateOf(AppLanguage.TAMIL)
        private set

    fun initialize(context: Context) {
        current = AppLanguage.from(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null),
        )
    }

    fun set(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.preferenceValue)
            .apply()
        current = language
    }

    fun toggle(context: Context) = set(
        context,
        if (current == AppLanguage.TAMIL) AppLanguage.ENGLISH else AppLanguage.TAMIL,
    )
}

/** A visible in-app language setting. The button names the language it will switch to. */
@Composable
fun AppLanguageToggle() {
    val context = LocalContext.current
    val targetLanguage = if (AppLanguageState.current == AppLanguage.TAMIL) "English" else "தமிழ்"
    TextButton(
        onClick = { AppLanguageState.toggle(context) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(targetLanguage, fontSize = 12.sp)
    }
}

/**
 * Static interface copy used by the shared-app Compose Text adapters and updater UI.
 * Missing entries intentionally remain readable English rather than becoming blank or guessed.
 */
object AppStrings {
    fun translate(text: String, language: AppLanguage = AppLanguageState.current): String {
        if (language != AppLanguage.TAMIL) return text
        TAMIL[text]?.let { return it }
        return translateDynamic(text) ?: text
    }

    private fun translateDynamic(text: String): String? {
        val voteLabels = Regex("\\b(\\d+) votes\\b").replace(text) { "${it.groupValues[1]} வாக்குகள்" }
        if (voteLabels != text) return voteLabels
        Regex("^The saved audio location is missing\\.$").matchEntire(text)
            ?.let { return "சேமித்த ஒலிக் கோப்பு இருப்பிடம் இல்லை." }
        Regex("^Android could not play this audio \\(player error (\\d+)/(\\d+)\\)\\.$").matchEntire(text)
            ?.let { return "Android இந்த ஒலியை இயக்க முடியவில்லை (பிளேயர் பிழை ${it.groupValues[1]}/${it.groupValues[2]})." }
        Regex("^Could not play the audio: (.+)$").matchEntire(text)
            ?.let { return "இந்த ஒலியை இயக்க முடியவில்லை: ${it.groupValues[1]}" }
        Regex("^Could not play the saved audio\\.$").matchEntire(text)
            ?.let { return "சேமித்த ஒலியை இயக்க முடியவில்லை." }
        Regex("^No newer published release was found for this app\\. Installed: (.+)\\.$").matchEntire(text)
            ?.let { return "இந்தப் பயன்பாட்டிற்கான புதிய வெளியீடு கிடைக்கவில்லை. நிறுவிய பதிப்பு: ${it.groupValues[1]}." }
        Regex("^Update available · (.+) \\(installed: (.+)\\)\\.$").matchEntire(text)
            ?.let { return "புதுப்பிப்பு உள்ளது · ${it.groupValues[1]} (நிறுவியது: ${it.groupValues[2]})." }
        Regex("^(\\d+) min$").matchEntire(text)?.let { return "${it.groupValues[1]} நிமிடம்" }
        Regex("^(\\d+) min left$").matchEntire(text)?.let { return "${it.groupValues[1]} நிமிடம் மீதம்" }
        Regex("^(\\d+) songs found$").matchEntire(text)?.let { return "${it.groupValues[1]} பாடல்கள் கண்டறியப்பட்டன" }
        Regex("^(\\d+) saved$").matchEntire(text)?.let { return "${it.groupValues[1]} சேமிக்கப்பட்டது" }
        Regex("^(\\d+) matches$").matchEntire(text)?.let { return "${it.groupValues[1]} பொருத்தங்கள்" }
        Regex("^(\\d+) stations in this list$").matchEntire(text)?.let { return "இந்தப் பட்டியலில் ${it.groupValues[1]} வானொலி நிலையங்கள்" }
        Regex("^(\\d+) stations$").matchEntire(text)?.let { return "${it.groupValues[1]} வானொலி நிலையங்கள்" }
        Regex("^⌛ (\\d+) queued$").matchEntire(text)?.let { return "⌛ ${it.groupValues[1]} வரிசையில்" }
        Regex("^(\\d+) processing or paused · (\\d+) waiting · (\\d+) done · (\\d+) failed$")
            .matchEntire(text)?.let { return "${it.groupValues[1]} செயலாக்கம் அல்லது இடைநிறுத்தம் · ${it.groupValues[2]} காத்திருக்கிறது · ${it.groupValues[3]} முடிந்தது · ${it.groupValues[4]} தோல்வி" }
        Regex("^Waiting · (.+) profile$").matchEntire(text)
            ?.let { return "காத்திருக்கிறது · ${translate(it.groupValues[1])} முறை" }
        Regex("^Saved chunks (\\d+) / (\\d+) · (.+) profile$").matchEntire(text)
            ?.let { return "சேமித்த பகுதிகள் ${it.groupValues[1]} / ${it.groupValues[2]} · ${translate(it.groupValues[3])} முறை" }
        Regex("^⚙ (.+)$").matchEntire(text)?.let { return "⚙ ${translate(it.groupValues[1])}" }
        Regex("^Reading local PCM WAV · (\\d+)%$").matchEntire(text)?.let { return "உள்ளூர் PCM WAV-ஐப் படிக்கிறது · ${it.groupValues[1]}%" }
        Regex("^Decoding locally · (\\d+)%$").matchEntire(text)?.let { return "சாதனத்தில் குறியாக்க நீக்கம் · ${it.groupValues[1]}%" }
        Regex("^Copying WAV to Music · (\\d+)%$").matchEntire(text)?.let { return "WAV-ஐ Music-இல் சேமிக்கிறது · ${it.groupValues[1]}%" }
        Regex("^Encoding high-quality MP3 · (\\d+)%$").matchEntire(text)?.let { return "உயர்தர MP3 உருவாக்கம் · ${it.groupValues[1]}%" }
        Regex("^(.+) · (\\d+)%$").matchEntire(text)?.let { return "${translate(it.groupValues[1])} · ${it.groupValues[2]}%" }
        Regex("^(\\d+(?:\\.\\d+)? MB) of (\\d+(?:\\.\\d+)? MB) downloaded$").matchEntire(text)
            ?.let { return "${it.groupValues[1]} / ${it.groupValues[2]} பதிவிறக்கம்" }
        Regex("^The second pass needs about (.+) free to decode the saved WAV and keep a 96 MiB reserve; only (.+) is available\\. Free about (.+) more, then resume\\.$").matchEntire(text)
            ?.let { return "சேமித்த WAV-ஐத் திறக்கவும் 96 MiB பாதுகாப்பு இடத்தை வைத்திருக்கவும் ${it.groupValues[1]} காலி இடம் தேவை; ${it.groupValues[2]} மட்டுமே உள்ளது. மேலும் ${it.groupValues[3]} காலி செய்து மீண்டும் தொடரவும்." }
        Regex("^The 320 kb/s MP3 export needs about (.+) free including a safety reserve; only (.+) is available\\. Free about (.+) more, then resume\\.$").matchEntire(text)
            ?.let { return "பாதுகாப்பு இடத்துடன் 320 kb/s MP3 ஏற்றுமதிக்கு ${it.groupValues[1]} காலி இடம் தேவை; ${it.groupValues[2]} மட்டுமே உள்ளது. மேலும் ${it.groupValues[3]} காலி செய்து மீண்டும் தொடரவும்." }
        Regex("^Downloading the 63\\.7 MiB model while keeping a 96 MiB safety reserve needs about (.+) free; only (.+) is available\\. Free about (.+) more, then resume\\.$").matchEntire(text)
            ?.let { return "96 MiB பாதுகாப்பு இடத்தை வைத்துக் கொண்டு 63.7 MiB மாடலைப் பதிவிறக்க ${it.groupValues[1]} காலி இடம் தேவை; ${it.groupValues[2]} மட்டுமே உள்ளது. மேலும் ${it.groupValues[3]} காலி செய்து மீண்டும் தொடரவும்." }
        Regex("^Copying the selected song while keeping a 96 MiB safety reserve needs about (.+) free; only (.+) is available\\. Free about (.+) more, then resume\\.$").matchEntire(text)
            ?.let { return "தேர்ந்தெடுத்த பாடலை நகலெடுத்து 96 MiB பாதுகாப்பு இடத்தை வைத்திருக்க ${it.groupValues[1]} காலி இடம் தேவை; ${it.groupValues[2]} மட்டுமே உள்ளது. மேலும் ${it.groupValues[3]} காலி செய்து மீண்டும் தொடரவும்." }
        Regex("^Low RAM; saved chunks are safe\\. Will auto-resume after Android reports at least (.+) free for (\\d+) seconds, once other safety checks are clear\\.$").matchEntire(text)
            ?.let { return "RAM குறைவு; சேமித்த பகுதிகள் பாதுகாப்பாக உள்ளன. Android குறைந்தது ${it.groupValues[1]} காலி RAM-ஐ ${it.groupValues[2]} விநாடிகள் அறிக்கையிட்டு மற்ற பாதுகாப்புச் சோதனைகள் சரியானதும் தானாகத் தொடரும்." }
        Regex("^(.+) · (\\d+) waiting$").matchEntire(text)
            ?.let { return "${translate(it.groupValues[1])} · ${it.groupValues[2]} காத்திருக்கிறது" }
        Regex("^Download failed: (.+)\\. Check your internet connection, then retry\\.$").matchEntire(text)
            ?.let { return "பதிவிறக்கம் தோல்வி: ${it.groupValues[1]}. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." }
        Regex("^Could not control the background queue: (.+)$").matchEntire(text)
            ?.let { return "பின்னணி வரிசையைக் கட்டுப்படுத்த முடியவில்லை: ${it.groupValues[1]}" }
        Regex("^(\\d+) MP3s? added to the background queue\\.(.*)$").matchEntire(text)?.let { match ->
            val count = match.groupValues[1]
            val suffix = match.groupValues[2]
            return buildString {
                append("$count MP3 கோப்புகள் பின்னணி வரிசையில் சேர்க்கப்பட்டன.")
                Regex(" (\\d+) duplicate files? (?:was|were) already queued\\.").find(suffix)?.let {
                    append(" ${it.groupValues[1]} நகல் கோப்பு ஏற்கெனவே வரிசையில் உள்ளது.")
                }
                Regex(" (\\d+) skipped: the queue supports up to 100 pending songs\\.").find(suffix)?.let {
                    append(" ${it.groupValues[1]} தவிர்க்கப்பட்டது: வரிசையில் அதிகபட்சம் 100 நிலுவைப் பாடல்கள் அனுமதிக்கப்படும்.")
                }
                Regex(" (\\d+) files? could not be kept accessible after restart\\.").find(suffix)?.let {
                    append(" மறுதொடக்கத்திற்குப் பிறகும் அணுகக்கூடியதாக ${it.groupValues[1]} கோப்பை வைத்திருக்க முடியவில்லை.")
                }
            }
        }
        Regex("^Preamp (-?[0-9.]+) dB$").matchEntire(text)?.let { return "முன்பெருக்கம் ${it.groupValues[1]} dB" }
        Regex("^preamp (-?[0-9.]+) dB$").matchEntire(text)?.let { return "முன்பெருக்கம் ${it.groupValues[1]} dB" }
        Regex("^Volume boost: ([0-9.]+) dB$").matchEntire(text)?.let { return "ஒலி உயர்வு: ${it.groupValues[1]} dB" }
        Regex("^Version (.+) • Tamil FM, world news, home-screen widget and a 10-band equalizer$")
            .matchEntire(text)?.let { return "பதிப்பு ${it.groupValues[1]} • தமிழ் FM, உலகச் செய்திகள், முகப்புத் திரை விட்ஜெட், 10-பட்டை சமநிலையாக்கி" }
        Regex("^PulseEQ (.+) • 20-band equalizer$").matchEntire(text)
            ?.let { return "PulseEQ ${it.groupValues[1]} • 20-பட்டை சமநிலையாக்கி" }
        Regex("^Your current curve: (.+)\\. Once you move a fader it becomes “Custom” and keeps its own shape — presets only change the 20 gains, never your LED or background settings\\.$")
            .matchEntire(text)?.let { return "தற்போதைய வளைவு: ${it.groupValues[1]}. ஒரு கட்டுப்பாட்டை நகர்த்தினால் “தனிப்பயன்” ஆகும்; அதன் வடிவம் சேமிக்கப்படும். முன்னமைவுகள் 20 ஒலி அளவுகளை மட்டுமே மாற்றும்; LED அல்லது பின்னணி அமைப்புகளை அல்ல." }
        return null
    }

    private val TAMIL = mapOf(
        // Shared language, navigation, update and About labels.
        "App language" to "பயன்பாட்டு மொழி",
        "Tamil" to "தமிழ்",
        "English" to "ஆங்கிலம்",
        "About" to "பற்றி",
        "About PulseEQ" to "PulseEQ பற்றி",
        "Audio processing stays on your phone. GitHub update checks can be turned off below." to "ஒலி செயலாக்கம் உங்கள் போனிலேயே நடைபெறும். GitHub புதுப்பிப்புச் சோதனைகளை கீழே முடக்கலாம்.",
        "About Ramesh Radio" to "ரமேஷ் ரேடியோ பற்றி",
        "Source code · GNU GPL v3" to "மூலக் குறியீடு · GNU GPL v3",
        "Free software under the GNU GPL v3" to "GNU GPL v3 உரிமத்தின் கீழ் இலவச மென்பொருள்",
        "Free software under the GNU GPL v3\nApp: Ramesh Radio (Tamilnadu FM Radio)" to "GNU GPL v3 உரிமத்தின் கீழ் இலவச மென்பொருள்\nபயன்பாடு: ரமேஷ் ரேடியோ (தமிழ்நாடு FM வானொலி)",
        "Ramesh Radio" to "ரமேஷ் ரேடியோ",
        "Contributors" to "பங்களிப்பாளர்கள்",
        "Project development" to "திட்ட உருவாக்கம்",
        "AI software assistance" to "AI மென்பொருள் உதவி",
        "App updates" to "பயன்பாட்டு புதுப்பிப்புகள்",
        "Verified GitHub APK updates" to "சரிபார்க்கப்பட்ட GitHub APK புதுப்பிப்புகள்",
        "Checks RP-Android-Apps first; InternetRadio remains as a grace-period fallback." to "முதலில் RP-Android-Apps சரிபார்க்கப்படும்; இடைக்காலத்தில் InternetRadio மாற்று சேமிப்பகமாகத் தொடரும்.",
        "Release source" to "வெளியீட்டு மூலம்",
        "Verified streams stay first; the all-stations list prioritizes Tamil, and Radio Browser votes sort each group." to "சரிபார்க்கப்பட்ட ஒளிபரப்புகள் முதலில்; அனைத்து நிலையப் பட்டியலில் தமிழ் முன்னுரிமை பெறும், ஒவ்வொரு குழுவிலும் Radio Browser வாக்குகள் வரிசைப்படுத்தும்.",
        "This raises the radio app's audio-session gain by up to 10 dB. Android's device volume limit still applies; lower the boost if you hear distortion." to "இது ரேடியோ ஒலியின் அமர்வு அளவை அதிகபட்சம் 10 dB வரை உயர்த்தும். Android சாதனத்தின் ஒலி வரம்பு தொடரும்; ஒலி சிதைந்தால் Boost-ஐக் குறைக்கவும்.",
        "Check for updates" to "புதுப்பிப்புகளைச் சரிபார்",
        "Checking…" to "சரிபார்க்கிறது…",
        "Update available" to "புதுப்பிப்பு உள்ளது",
        "Download and verify APK" to "APK-ஐப் பதிவிறக்கி சரிபார்",
        "Install with Android confirmation" to "Android உறுதிப்படுத்தலுடன் நிறுவு",
        "Allow installation for this app" to "இந்தப் பயன்பாட்டிற்கு நிறுவலை அனுமதி",
        "Later" to "பிறகு",
        "Close" to "மூடு",
        "Cancel" to "ரத்து செய்",
        "Resume" to "தொடர்",
        "Retry" to "மீண்டும் முயற்சி செய்",
        "OK" to "சரி",
        "What’s new" to "புதியவை",
        "Daily check on app launch: On" to "பயன்பாட்டைத் திறக்கும்போது தினசரி சரிபார்ப்பு: இயக்கத்தில்",
        "Daily check on app launch: Off" to "பயன்பாட்டைத் திறக்கும்போது தினசரி சரிபார்ப்பு: முடக்கத்தில்",
        "Daily update check on app launch" to "பயன்பாட்டைத் திறக்கும்போது தினசரி புதுப்பிப்பு சரிபார்ப்பு",
        "Check for updates on app launch" to "பயன்பாட்டைத் திறக்கும்போது புதுப்பிப்புகளைச் சரிபார்",
        "App releases" to "பயன்பாட்டு வெளியீடுகள்",
        "Project development: Ramesh prathap .R" to "திட்ட உருவாக்கம்: Ramesh prathap .R",
        "Project development: Ramesh prathap .R · universalrp2003@gmail.com" to "திட்ட உருவாக்கம்: Ramesh prathap .R · universalrp2003@gmail.com",
        "● Ready" to "● தயார்",
        "AI software-assistance contributor: Arena.ai Agent Mode" to "AI மென்பொருள் உதவிப் பங்களிப்பாளர்: Arena.ai Agent Mode", 
        "No newer published release was found for this app." to "இந்தப் பயன்பாட்டிற்கான புதிய வெளியீடு எதுவும் கிடைக்கவில்லை.",
        "Stable releases are checked against the app package, version, SHA-256 and installed signing certificate. Downloads stay in the app; Android shows its installer and confirmation." to "நிலையான வெளியீடுகள் பயன்பாட்டுத் தொகுப்பு, பதிப்பு, SHA-256 மற்றும் நிறுவிய கையொப்பச் சான்றிதழுடன் ஒப்பிடப்படும். பதிவிறக்கம் பயன்பாட்டிற்குள் இருக்கும்; நிறுவியையும் உறுதிப்படுத்தலையும் Android காட்டும்.",
        "Downloading and verifying APK…" to "APK-ஐப் பதிவிறக்கி சரிபார்க்கிறது…",
        "SHA-256, package name, newer version and signing certificate are checked before Android opens the installer." to "Android நிறுவியைத் திறப்பதற்கு முன் SHA-256, தொகுப்புப் பெயர், புதிய பதிப்பு மற்றும் கையொப்பச் சான்றிதழ் சரிபார்க்கப்படும்.",
        "Android requires Install unknown apps permission for this app and its own confirmation for each install. Play Protect is not bypassed." to "இந்தப் பயன்பாட்டிற்கு ‘தெரியாத பயன்பாடுகளை நிறுவு’ அனுமதியும் ஒவ்வொரு நிறுவலுக்கும் Android உறுதிப்படுத்தலும் தேவை. Play Protect தவிர்க்கப்படாது.",
        "Installed: %s · APK: %s MiB" to "நிறுவிய பதிப்பு: %s · APK: %s MiB",
        "Version %s · %s MiB" to "பதிப்பு %s · %s MiB",
        "Could not check for updates. Check your connection and try again." to "புதுப்பிப்புகளைச் சரிபார்க்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "The downloaded APK is verified. Android will show its installer and confirmation; this app cannot install silently or bypass Play Protect." to "பதிவிறக்கப்பட்ட APK சரிபார்க்கப்பட்டது. Android நிறுவி மற்றும் உறுதிப்படுத்தலைக் காட்டும்; இந்தப் பயன்பாடு அமைதியாக நிறுவவோ Play Protect-ஐத் தவிர்க்கவோ முடியாது.",
        "Android needs per-app Install unknown apps access before the installer can open. Grant it in Settings only if you trust this update." to "நிறுவி திறப்பதற்கு முன் Android-இல் இந்தப் பயன்பாட்டிற்கான ‘தெரியாத பயன்பாடுகளை நிறுவு’ அனுமதி தேவை. இந்தப் புதுப்பிப்பை நம்பினால் மட்டுமே அமைப்புகளில் அனுமதி அளிக்கவும்.",
        "Android installer opened. Confirm installation there; Android and Play Protect control the result." to "Android நிறுவி திறக்கப்பட்டது. அங்கே நிறுவலை உறுதிப்படுத்தவும்; முடிவை Android மற்றும் Play Protect தீர்மானிக்கும்.",
        "The update could not be verified, so it will not be installed." to "புதுப்பிப்பைச் சரிபார்க்க முடியவில்லை; எனவே அது நிறுவப்படாது.",

        // Ramesh Radio navigation and local audio.
        "Radio" to "வானொலி",
        "News" to "செய்திகள்",
        "Local" to "உள்ளூர்",
        "More" to "மேலும்",
        "Local audio" to "உள்ளூர் ஒலி",
        "Songs on this phone, played through the same 10-band equalizer." to "இந்தப் போனிலுள்ள பாடல்கள் இதே 10-பட்டை சமநிலையாக்கி வழியாக ஒலிக்கும்.",
        "Choose folder" to "கோப்புறையைத் தேர்ந்தெடு",
        "Open a file" to "கோப்பைத் திற",
        "Reading your music folder…" to "உங்கள் இசைக் கோப்புறையைப் படிக்கிறது…",
        "Only audio in your chosen folder and subfolders is listed." to "நீங்கள் தேர்ந்தெடுத்த கோப்புறையிலும் அதன் துணைக் கோப்புறைகளிலும் உள்ள ஒலி கோப்புகள் மட்டுமே பட்டியலிடப்படும்.",
        "Choose the folder containing your songs, not your ringtones folder. Or open a single audio file. Folder access is remembered; no whole-device scan is needed." to "ரிங்டோன் கோப்புறையை அல்ல, பாடல்கள் உள்ள கோப்புறையைத் தேர்ந்தெடுக்கவும். அல்லது ஒரு ஒலி கோப்பைத் திறக்கவும். கோப்புறை அனுமதி நினைவில் வைக்கப்படும்; முழுச் சாதனத்தையும் ஸ்கேன் செய்ய வேண்டியதில்லை.",
        "Choose a folder" to "கோப்புறையைத் தேர்ந்தெடு",
        "Refresh folder" to "கோப்புறையைப் புதுப்பி",
        "Unknown artist" to "தெரியாத கலைஞர்",
        "Play" to "இயக்கு",
        "Pause" to "இடைநிறுத்து",
        "Stop" to "நிறுத்து",
        "Playing" to "ஒலிக்கிறது",
        "Paused" to "இடைநிறுத்தப்பட்டது",
        "Nothing playing" to "எதுவும் ஒலிக்கவில்லை",
        "From your phone" to "உங்கள் போனிலிருந்து",
        "Playing from your phone's storage" to "உங்கள் போன் சேமிப்பிலிருந்து ஒலிக்கிறது",
        "Pick a station to start" to "தொடங்க ஒரு வானொலி நிலையத்தைத் தேர்ந்தெடுக்கவும்",
        "Tamil FM • News • Local files" to "தமிழ் FM • செய்திகள் • உள்ளூர் கோப்புகள்",
        "LIVE" to "நேரலை",
        "PAUSED" to "இடைநிறுத்தம்",
        "Connecting…" to "இணைக்கிறது…",
        "Previous" to "முந்தையது",
        "Next" to "அடுத்தது",
        "Previous song" to "முந்தைய பாடல்",
        "Next song" to "அடுத்த பாடல்",
        "Play or pause" to "இயக்கு அல்லது இடைநிறுத்து",
        "Dismiss" to "மூடு",
        "Favourite" to "பிடித்தது",
        "Add favourite" to "பிடித்தவற்றில் சேர்",
        "Remove favourite" to "பிடித்தவற்றிலிருந்து நீக்கு",
        "Search the directory" to "நிலையகக் களஞ்சியத்தில் தேடு",
        "Station, city or language" to "நிலையம், நகரம் அல்லது மொழி",
        "Live directory" to "நேரடி நிலையப் பட்டியல்",
        "Directory" to "நிலையப் பட்டியல்",
        "My stations" to "என் நிலையங்கள்",
        "Station name" to "நிலையத்தின் பெயர்",
        "Stream link (HTTPS preferred; legacy HTTP supported)" to "HTTPS விரும்பத்தக்கது; பழைய HTTP ஒலிபரப்புகளும் ஆதரிக்கப்படும்.",
        "Prefer HTTPS when a station offers it. Legacy HTTP streams remain supported but are not encrypted in transit." to "நிலையம் வழங்கினால் HTTPS-ஐப் பயன்படுத்தவும். பழைய HTTP ஒலிபரப்புகளும் இயங்கும்; ஆனால் அனுப்பும் போது அவை குறியாக்கப்படாது.",
        "This station uses unencrypted HTTP. Others on the network may be able to observe or alter the stream. Prefer its HTTPS stream if available; HTTP is kept for legacy stations." to "இந்த நிலையம் குறியாக்கமற்ற HTTP-ஐப் பயன்படுத்துகிறது. வலையமைப்பில் உள்ளவர்கள் ஒலியைப் பார்க்கவோ மாற்றவோ முடியும். HTTPS பதிப்பு இருந்தால் அதைப் பயன்படுத்தவும்; பழைய நிலையங்களுக்காக HTTP ஆதரிக்கப்படுகிறது.",
        "Add station" to "நிலையத்தைச் சேர்",
        "Remove" to "நீக்கு",
        "Save" to "சேமி",
        "Add any stream you like. Stream links usually end in .mp3, .m3u8 or /stream — copy one from a station's website." to "நீங்கள் விரும்பும் ஒலிபரப்பைச் சேர்க்கலாம். இணைப்புகள் பொதுவாக .mp3, .m3u8 அல்லது /stream என முடியும்; நிலையத்தின் இணையதளத்திலிருந்து நகலெடுக்கவும்.",
        "Nothing here yet." to "இங்கே இன்னும் எதுவும் இல்லை.",
        "No news stations in this category yet. Tap one of the refresh buttons above to pull live results from the directory." to "இந்தப் பிரிவில் இன்னும் செய்தி நிலையங்கள் இல்லை. நிலையப் பட்டியலிலிருந்து நேரடி முடிவுகளைப் பெற மேலுள்ள புதுப்பிப்பு பொத்தானைத் தட்டவும்.",
        "Try another category, clear the search box, or look in the live directory — it has far more stations than the shipped list, including the town and city stations." to "வேறு பிரிவை முயற்சிக்கவும், தேடலை அழிக்கவும் அல்லது நேரடி நிலையப் பட்டியலைப் பார்க்கவும். அதில் தொகுப்புடன் வந்த பட்டியலைவிட, ஊர் மற்றும் நகர நிலையங்கள் உட்பட பல நிலையங்கள் உள்ளன.",
        "The shipped list has world and English news channels. Ask the directory for more — it is community maintained and much larger than any list an app can carry." to "தொகுப்புடன் வந்த பட்டியலில் உலக மற்றும் ஆங்கிலச் செய்தி நிலையங்கள் உள்ளன. சமூகத்தால் பராமரிக்கப்படும் விரிவான பட்டியலுக்கு நிலையக் களஞ்சியத்தில் தேடவும்.",
        "World news (English)" to "உலகச் செய்திகள் (ஆங்கிலம்)",
        "Tamil news" to "தமிழ்ச் செய்திகள்",
        "Indian news" to "இந்தியச் செய்திகள்",
        "News radio" to "செய்தி வானொலி",
        "Live news stations" to "நேரடி செய்தி நிலையங்கள்",
        "Live stream" to "நேரடி ஒலிபரப்பு",
        "Enter a name and a stream link starting with http" to "பெயரையும் http எனத் தொடங்கும் ஒலிபரப்பு இணைப்பையும் உள்ளிடவும்",
        "My station" to "என் நிலையம்",
        "Station added to My stations" to "என் நிலையங்களில் நிலையம் சேர்க்கப்பட்டது",
        "No stations came back. Check your connection and try again." to "நிலையங்கள் கிடைக்கவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "Player is still starting — tap again in a second." to "பிளேயர் இன்னும் தொடங்குகிறது — ஒரு நொடிக்குப் பிறகு மீண்டும் தட்டவும்.",
        "Player is connecting. Please try again shortly." to "பிளேயர் இணைகிறது. சிறிது நேரத்தில் மீண்டும் முயற்சிக்கவும்.",
        "Audio file" to "ஒலி கோப்பு",
        "No audio files in this folder. Choose the folder containing your songs." to "இந்தக் கோப்புறையில் ஒலி கோப்புகள் இல்லை. உங்கள் பாடல்கள் உள்ள கோப்புறையைத் தேர்ந்தெடுக்கவும்.",
        "Cannot read music folder. Select it again to grant access." to "இசைக் கோப்புறையைப் படிக்க முடியவில்லை. அணுகலை வழங்க அதை மீண்டும் தேர்ந்தெடுக்கவும்.",
        "Unknown track" to "தெரியாத பாடல்",
        "File is not readable" to "கோப்பைப் படிக்க முடியவில்லை",
        "Could not open that file. Download it to your phone if it is cloud-only, then select it again and allow access." to "கோப்பைத் திறக்க முடியவில்லை. மேகத்தில் மட்டுமே இருந்தால் போனில் பதிவிறக்கி, மீண்டும் தேர்ந்தெடுத்து அணுகலை அனுமதிக்கவும்.",
        "Sleep timer off" to "தூக்க நேரக் கணிப்பான் முடக்கம்",
        "Sleep timer finished — playback paused" to "தூக்க நேரம் முடிந்தது — ஒலிப்பு இடைநிறுத்தப்பட்டது",
        "Share station" to "நிலையத்தைப் பகிர்",
        "Nothing available to share with." to "பகிர்வதற்கான பயன்பாடு இல்லை.",
        "Playback runs in the background — you can close the app." to "பின்னணியில் ஒலிக்கும் — பயன்பாட்டை மூடலாம்.",
        "No browser available to open the link." to "இணைப்பைத் திறக்க உலாவி இல்லை.",
        "Folder too large; select a smaller music folder" to "கோப்புறை மிகப் பெரியது; சிறிய இசைக் கோப்புறையைத் தேர்ந்தெடுக்கவும்",
        "Provider could not read folder" to "சேமிப்புச் சேவை கோப்புறையைப் படிக்க முடியவில்லை",
        "Unknown station" to "தெரியாத நிலையம்",
        "The stream did not start within 30 seconds. Check the connection and tap Play to retry, or choose another station." to "30 விநாடிகளில் ஒலிபரப்பு தொடங்கவில்லை. இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்க இயக்கு என்பதைத் தட்டவும் அல்லது வேறு நிலையத்தைத் தேர்ந்தெடுக்கவும்.",
        "Playback failed" to "ஒலிப்பு தோல்வியடைந்தது",
        "Boosting bands can clip. The preamp pulls the whole output down to keep it clean — it is set automatically when you change a band." to "ஒலி பட்டைகளை உயர்த்துவது சிதைவைக் கொண்டுவரலாம். சிதைவைத் தவிர்க்க முன்பெருக்கம் முழு வெளியீட்டையும் குறைக்கும் — பட்டையை மாற்றும்போது இது தானாக அமைக்கப்படும்.",
        "Gentle multiband levelling + a limiter, so ads and jingles stop distorting and quiet parts stay audible." to "மென்மையான பல-பட்டை ஒலி சமநிலையும் வரம்பாக்கியும் விளம்பரங்களும் ஜிங்கிள்களும் சிதையாமல், மெதுவான பகுதிகளும் கேட்குமாறு செய்கின்றன.",
        "Rolls off the sub-bass below 62 Hz — removes the boom from traffic noise, footsteps and wind that phone mics pick up." to "62 Hz-க்குக் கீழுள்ள மிகக் குறைந்த பேஸ் ஒலியைக் குறைத்து, போன் மைக்கில் பதிவாகும் போக்குவரத்து, காலடி மற்றும் காற்றின் இரைச்சலைத் தணிக்கிறது.",
        "Trims the top octave where low-bitrate streams put their hiss. Turn it on if a station sounds fizzy." to "குறைந்த பிட் வீத ஒலிபரப்பில் வரும் உயர் சீறு ஒலியைக் குறைக்கிறது. நிலைய ஒலி கூர்மையாக இருந்தால் இயக்கவும்.",
        "What this equalizer can and cannot do" to "இந்த ஒலி சமநிலையாக்கி செய்யக்கூடியதும் முடியாததும்",
        "It shapes the sound this app plays. Android gives no app a way to reshape another app's audio without root or a screen-capture consent dialog, so YouTube and other players are out of reach — that is a platform limit, not a missing feature. Everything here is a standard Android audio effect: no root, no hidden APIs, nothing that fakes quality." to "இந்தப் பயன்பாடு ஒலிக்கும் ஒலியை இது வடிவமைக்கும். Root அல்லது திரைப் பதிவு அனுமதி இல்லாமல் மற்ற பயன்பாட்டின் ஒலியை மாற்ற Android எந்த ஆப்புக்கும் வழி வழங்காது. எனவே YouTube மற்றும் பிற பிளேயர்களை மாற்ற முடியாது — இது தள வரம்பு; விடுபட்ட வசதி அல்ல. இங்கு அனைத்தும் Android-இன் நிலையான ஒலி விளைவுகள்; root, மறைக்கப்பட்ட API அல்லது போலியான தரம் எதுவும் இல்லை.",
        "Tamil FM" to "தமிழ் FM",
        "Warm low end with clear voices — a good default for Tamil FM" to "தெளிவான குரல்களுடன் மென்மையான பேஸ் — தமிழ் FM-க்கான நல்ல இயல்புநிலை",
        "Kind to older recordings — soft top end, no harshness" to "பழைய பதிவுகளுக்கு ஏற்றது — மென்மையான உயர் ஒலி, கூர்மை இல்லை",
        "Smile curve: full bass, open highs" to "புன்னகை வளைவு: நிறைந்த பேஸ், திறந்த உயர் ஒலி",
        "Cinema punch with dialogue kept forward" to "உரையாடலை முன்னிலைப்படுத்தும் திரைப்பட ஒலி",
        "Removes rumble, lifts voices — best for news and talk" to "குறைந்த இரைச்சலை நீக்கி குரல்களை உயர்த்தும் — செய்தி மற்றும் பேச்சுக்கு சிறந்தது",
        "Speech-forward but easier on the ears than News" to "செய்தி முறையைவிட காதுக்கு மென்மையான பேச்சு ஒலி",
        "Pulls a singer in front of the band" to "பாடகரின் குரலை இசைக்குழுவுக்கு முன்னால் கொண்டுவரும்",
        "Deep low end; the limiter keeps it from breaking up" to "ஆழமான பேஸ்; சிதையாமல் வரம்பாக்கி காக்கும்",
        "Detail and air without touching the bass" to "பேஸை மாற்றாமல் நுணுக்கமும் திறந்த உயர் ஒலியும்",
        "Gentle and even for bhajans and temple music" to "பஜனை மற்றும் கோயில் இசைக்கான மென்மையான சமநிலை",
        "Guitars and kick up, mid-range cleaned" to "கிட்டார் மற்றும் தாள ஒலி உயர்வு; நடுத்தர அதிர்வெண் தெளிவு",
        "Bright and radio-like" to "பிரகாசமான வானொலி பாணி ஒலி",
        "Natural, close to flat with a little sparkle" to "இயல்பான, கிட்டத்தட்ட சமமான ஒலியில் சிறு மினுமினுப்பு",
        "Club curve: sub, kick and a crisp top" to "கிளப் வளைவு: ஆழ் பேஸ், தாளம், தெளிவான உயர் ஒலி",
        "Even loudness at low volume, no harsh treble" to "குறைந்த ஒலியளவிலும் சமமான ஒலி; கூர்மையான உயர் ஒலி இல்லை",
        "Global mix not requested" to "முழு வெளியீட்டு கலவை கோரப்படவில்லை",
        "Other-app equalizer" to "பிற பயன்பாடுகளுக்கான ஒலி சமநிலையாக்கி",
        "Stop EQ" to "ஒலி சமநிலையை நிறுத்து",
        "Android blocked the background EQ service" to "பின்னணி ஒலி சமநிலைச் சேவையை Android தடுத்தது",
        "Global mix attached; Android may still bypass it for some outputs" to "முழு வெளியீடு இணைக்கப்பட்டது; சில வெளியீடுகளில் Android இதைத் தவிர்க்கலாம்",
        "This phone blocks global output effects; compatible sessions only" to "இந்தப் போன் முழு வெளியீட்டு விளைவுகளைத் தடுக்கிறது; இணக்கமான அமர்வுகள் மட்டுமே",
        "Global mix paused while Radio plays to avoid double EQ" to "இரட்டை ஒலி சமநிலையைத் தவிர்க்க Radio ஒலிக்கும்போது முழு வெளியீடு இடைநிறுத்தப்படுகிறது",
        "Uses the radio EQ curve; device band count may differ." to "Radio-வின் ஒலி வளைவைப் பயன்படுத்தும்; சாதனத்தின் பட்டை எண்ணிக்கை மாறலாம்.",
        "EQ curve is switched off." to "ஒலி சமநிலை வளைவு முடக்கப்பட்டுள்ளது.",
        "Other-app EQ stopped" to "பிற பயன்பாட்டு ஒலி சமநிலை நிறுத்தப்பட்டது",
        "Off — other players must expose compatible audio sessions" to "முடக்கம் — பிற பிளேயர்கள் இணக்கமான ஒலி அமர்வுகளை வழங்க வேண்டும்",
        "World news in English and Tamil news, around the clock." to "ஆங்கிலத்தில் உலகச் செய்திகளும் தமிழ்ச் செய்திகளும் — 24 மணி நேரமும்.",
        "Tamil FM from Tamil Nadu and around the world — live." to "தமிழ்நாட்டிலிருந்தும் உலகெங்குமிருந்தும் தமிழ் FM — நேரலையில்.",
        "Sleep timer" to "தூக்க நேரக் கணிப்பான்",
        "Sleep timer, your own stations, and about this app." to "தூக்க நேரக் கணிப்பான், உங்கள் நிலையங்கள், இந்தப் பயன்பாட்டைப் பற்றி.",
        "Handy for falling asleep to a station — playback pauses on its own." to "வானொலியைக் கேட்டபடி தூங்க உதவும் — நேரம் முடிந்ததும் ஒலி தானாக நிற்கும்.",
        "Continue listening" to "தொடர்ந்து கேள்",
        "Off" to "முடக்கு",
        "Equalizer" to "ஒலி சமநிலையாக்கி",
        "EQ" to "ஒலி சமநிலை",
        "10 bands, 31 Hz to 16 kHz, plus Clear sound. Applies to everything this app plays — online stations and local files." to "31 Hz முதல் 16 kHz வரை 10 ஒலி பட்டைகள் மற்றும் தெளிவான ஒலி. இந்தப் பயன்பாட்டில் ஒலிக்கும் இணைய நிலையங்களுக்கும் உள்ளூர் கோப்புகளுக்கும் பொருந்தும்.",
        "Left / right balance" to "இடது / வலது ஒலி சமநிலை",
        "Left ← Centre → Right. Reduces the louder side without boosting or clipping. Applies to stereo audio played here, not other apps." to "இடது ← மையம் → வலது. அதிக ஒலி உள்ள பக்கத்தை உயர்த்தாமல் அல்லது சிதைக்காமல் குறைக்கும். இங்கு ஒலிக்கும் ஸ்டீரியோவுக்கு மட்டுமே பொருந்தும்; பிற பயன்பாடுகளுக்கு அல்ல.",
        "Centre / reset" to "மையம் / மீட்டமை",
        "Clear sound" to "தெளிவான ஒலி",
        "Bass boost" to "பேஸ் ஒலி உயர்வு",
        "Treble boost" to "உயர் அதிர்வெண் ஒலி உயர்வு",
        "Hiss cut" to "சீறு ஒலியைக் குறை",
        "Rumble cut" to "குறைந்த அதிர்வு ஒலியைக் குறை",
        "Dance" to "நடனம்",
        "Movies" to "திரைப்படங்கள்",
        "Flat" to "சம ஒலி",
        "Vocal" to "குரல்",
        "Quick sound" to "விரைவு ஒலி",
        "Presets" to "முன்னமைவுகள்",
        "Preamp" to "முன்பெருக்கம்",
        "Equalizer off" to "சமநிலையாக்கி முடக்கப்பட்டுள்ளது",
        "Equalizer on" to "சமநிலையாக்கி இயக்கப்பட்டுள்ளது",
        "Enable other-app EQ" to "பிற பயன்பாடுகளுக்கான ஒலி சமநிலையை இயக்கு",
        "Stop other-app EQ" to "பிற பயன்பாடுகளுக்கான ஒலி சமநிலையை நிறுத்து",
        "Other apps / system-wide EQ" to "பிற பயன்பாடுகள் / முழுச் சாதன ஒலி சமநிலை",
        "Built-in PulseEQ-style session control. Turn off the separate PulseEQ app to avoid effects competing. Start this before playback in the other app. No recording, no root; not every player is supported." to "உள்ளமைந்த PulseEQ பாணி அமர்வு கட்டுப்பாடு. ஒலி விளைவுகள் மோதாமல் இருக்க தனி PulseEQ பயன்பாட்டை முடக்கவும். மற்ற பயன்பாட்டில் ஒலியைத் தொடங்குவதற்கு முன் இதை இயக்கவும். பதிவு இல்லை, root தேவையில்லை; எல்லா பிளேயர்களும் ஆதரிக்கப்படாது.",
        "Try global mix (device dependent)" to "முழு வெளியீட்டை முயற்சி செய் (சாதனத்தைப் பொறுத்தது)",
        "Could not start background EQ. Check app battery/background restrictions." to "பின்னணி ஒலி சமநிலையைத் தொடங்க முடியவில்லை. பேட்டரி மற்றும் பின்னணி கட்டுப்பாடுகளைச் சரிபார்க்கவும்.",
        "Attaches when playback starts" to "ஒலி தொடங்கும்போது இணையும்",
        "Share app" to "பயன்பாட்டைப் பகிர்",
        "Share" to "பகிர்",
        "Source code" to "மூலக் குறியீடு",
        "Privacy & permissions" to "தனியுரிமை & அனுமதிகள்",
        "Internet access is needed for live streams and directory searches. Local audio is selected on your phone. Notifications and the foreground playback service keep audio controls available while listening. Favourites and settings stay on this device. Stream and directory servers receive your requests; offline station listings do not make streams playable offline. HTTPS is preferred when a station offers it. Some legacy stations use unencrypted HTTP, which can expose or allow changes to stream traffic on the network." to "நேரடி ஒலிபரப்புக்கும் நிலையத் தேடலுக்கும் இணையம் தேவை. உள்ளூர் ஒலியை உங்கள் போனிலிருந்து தேர்ந்தெடுக்கிறீர்கள். கேட்கும்போது அறிவிப்புகளும் முன்னணி ஒலிச் சேவையும் கட்டுப்பாடுகளை வைத்திருக்கும். பிடித்த நிலையங்களும் அமைப்புகளும் இந்தச் சாதனத்திலேயே இருக்கும். ஒலிபரப்பு மற்றும் நிலைய சேவையகங்களுக்கு உங்கள் கோரிக்கைகள் செல்லும்; இணையமில்லா நிலையப் பட்டியல் நேரடி ஒலியைக் கேட்கச் செய்யாது. நிலையம் வழங்கினால் HTTPS முன்னுரிமை பெறும். சில பழைய நிலையங்கள் குறியாக்கமற்ற HTTP-ஐப் பயன்படுத்துகின்றன; இதனால் வலையமைப்பில் ஒலிப் போக்குவரத்தைப் பார்க்கவோ மாற்றவோ முடியும்.",
        "radio-browser.info • community run, no account needed" to "radio-browser.info • சமூகத்தால் பராமரிக்கப்படுகிறது; கணக்கு தேவையில்லை",
        "What is inside: thousands of Tamil FM stations from India, Sri Lanka, Malaysia and Singapore plus the Tamil diaspora, Tamil and English news stations from around the world, a local file player, favourites, recently played, a sleep timer, and a 10-band equalizer with Clear sound, rumble cut and hiss cut." to "இந்தியாவில் இருந்து ஆயிரக்கணக்கான தமிழ் FM நிலையங்கள், இலங்கை, மலேசியா, சிங்கப்பூர் மற்றும் உலகத் தமிழ் நிலையங்கள்; உலகச் செய்திகள்; உள்ளூர் கோப்பு பிளேயர்; பிடித்தவை; சமீபத்தில் கேட்டவை; தூக்க நேரக் கணிப்பான்; தெளிவான ஒலி, குறைந்த அதிர்வு மற்றும் சீறு ஒலி குறைப்புடன் 10-பட்டை சமநிலையாக்கி.",
        "Station lists come from the free, open Radio-Browser directory. The app ships a list so it works offline-on-first-launch, and can search the live directory any time. Directory entries are community maintained: a station can go offline without notice, and a few links are unchecked — those are marked. No ads, no account, no tracking; nothing leaves the phone except the stream you chose and the directory search you asked for." to "நிலையப் பட்டியல் இலவச, திறந்த Radio-Browser களஞ்சியத்திலிருந்து வருகிறது. முதல் தொடக்கத்தில் இணையமில்லாமலும் பார்க்க பட்டியல் பயன்பாட்டுடன் வருகிறது; விரும்பினால் நேரடி களஞ்சியத்தில் தேடலாம். பதிவுகள் சமூகத்தால் பராமரிக்கப்படுவதால் நிலையங்கள் முன்னறிவிப்பின்றி நிற்கலாம்; சரிபார்க்கப்படாத சில இணைப்புகள் குறிக்கப்படும். விளம்பரம், கணக்கு, கண்காணிப்பு இல்லை; நீங்கள் தேர்ந்தெடுத்த ஒலிபரப்பும் நீங்கள் கேட்ட தேடலும் மட்டுமே இணையத்துக்குச் செல்லும்.",

        // PulseEQ navigation, player, settings and About.
        "Bands" to "ஒலி பட்டைகள்",
        "20 bands" to "20 ஒலி பட்டைகள்",
        "20-band sound equalizer" to "20-பட்டை ஒலி சமநிலையாக்கி",
        "Player" to "பிளேயர்",
        "Background & coverage" to "பின்னணி & செயல்பாடு",
        "How PulseEQ keeps equalizing after you leave the app" to "பயன்பாட்டை விட்டு வெளியேறிய பிறகும் PulseEQ எவ்வாறு ஒலி சமநிலையைத் தொடர்கிறது",
        "Run in the background" to "பின்னணியில் இயக்கு",
        "Keeps the DSP and the system effect alive with a small ongoing notification. This is the switch that stops the equalizer from closing." to "சிறிய தொடர்ச்சியான அறிவிப்புடன் DSP மற்றும் கணினி ஒலி விளைவை இயங்க வைத்திருக்கும். சமநிலையாக்கி மூடப்படாமல் இருக்க இந்த அமைப்பை இயக்கவும்.",
        "Try system-wide effect" to "முழுச் சாதன ஒலி விளைவை முயற்சி செய்",
        "Attach one equalizer to the phone's whole output mix. Google deprecated this and many Android 11+ ROMs refuse it for normal apps — PulseEQ reports honestly whether your phone allowed it." to "போனின் முழு வெளியீட்டுக்கும் ஒரு சமநிலையாக்கியை இணைக்க முயலும். Google இதைத் திரும்பப் பெற்றுவிட்டது; Android 11+ பல பதிப்புகள் சாதாரண பயன்பாடுகளை அனுமதிக்காது. உங்கள் போன் அனுமதித்ததா என்பதை PulseEQ நேர்மையாகக் காட்டும்.",
        "Equalize cooperating players" to "ஒத்துழைக்கும் பிளேயர்களுக்கு ஒலி சமநிலை செய்",
        "Players such as Poweramp, VLC or Musicolet announce their audio session. PulseEQ shapes those sessions directly — no capture, no root." to "Poweramp, VLC அல்லது Musicolet போன்ற பிளேயர்கள் தங்கள் ஒலி அமர்வை அறிவிக்கும். PulseEQ அந்த அமர்வுகளின் ஒலியை நேரடியாகச் சரிசெய்யும் — பதிவு இல்லை, root தேவையில்லை.",
        "Battery optimisation" to "பேட்டரி மேம்படுத்தல்",
        "PulseEQ is still optimised — Android may stop the equalizer when the screen is off for a long time." to "PulseEQ இன்னும் பேட்டரி மேம்பாட்டில் உள்ளது — திரை நீண்ட நேரம் அணைந்திருந்தால் Android சமநிலையாக்கியை நிறுத்தலாம்.",
        "PulseEQ is exempt — the equalizer survives Doze." to "PulseEQ-க்கு விலக்கு உள்ளது — Doze நிலையிலும் சமநிலையாக்கி இயங்கும்.",
        "Allow unrestricted battery use" to "கட்டுப்பாடற்ற பேட்டரி பயன்பாட்டை அனுமதி",
        "LED brightness" to "LED ஒளிர்வு",
        "Idle bars dim down automatically; this sets how bright the lit segments are." to "செயலற்ற பட்டைகள் தானாக மங்கும்; ஒளிரும் பகுதிகளின் பிரகாசத்தை இங்கு அமைக்கலாம்.",
        "What it can and cannot equalize — the honest list" to "எதைச் சமநிலைப்படுத்த முடியும், எதை முடியாது — நேர்மையான விளக்கம்",
        "PulseEQ player" to "PulseEQ பிளேயர்",
        "The only player where the full 20-band engine is guaranteed: samples pass through PulseEQ's own DSP before they reach the speaker" to "முழு 20-பட்டை இயந்திரம் உறுதியாகச் செயல்படும் பிளேயர் இதுவே: ஒலி ஸ்பீக்கரை அடைவதற்கு முன் PulseEQ-இன் DSP வழியாகச் செல்கிறது.",
        "Finished" to "முடிந்தது",
        "Could not play that file" to "அந்தக் கோப்பை இயக்க முடியவில்லை",
        "Nothing loaded" to "எதுவும் ஏற்றப்படவில்லை",
        "Choose an audio file that is saved on your phone" to "உங்கள் போனில் சேமிக்கப்பட்ட ஒலி கோப்பைத் தேர்ந்தெடுக்கவும்",
        "Play / pause" to "இயக்கு / இடைநிறுத்து",
        "Choose audio file" to "ஒலி கோப்பைத் தேர்ந்தெடு",
        "The picker only shows audio files already on your phone. If the last file is still available, “Play / pause” resumes it without asking again." to "தேர்வியில் உங்கள் போனில் ஏற்கெனவே உள்ள ஒலி கோப்புகள் மட்டுமே காட்டப்படும். கடைசிக் கோப்பு இன்னும் இருந்தால், “இயக்கு / இடைநிறுத்து” அதை மீண்டும் தேர்வு கேட்காமல் தொடரும்.",
        "What this player does not do" to "இந்தப் பிளேயர் செய்யாதவை",
        "It plays local files — streaming is not supported. Internet access is only for update checks, not online music. For YouTube, Spotify and other apps, use the Background/Settings options: the system-wide effect or the cooperating-player sessions." to "இது உள்ளூர் கோப்புகளை இயக்கும் — நேரடி ஒலிபரப்பு ஆதரிக்கப்படாது. இணையம் புதுப்பிப்பு சரிபார்ப்புக்கு மட்டுமே; இணைய இசைக்காக அல்ல. YouTube, Spotify மற்றும் பிற பயன்பாடுகளுக்கு பின்னணி/அமைப்புகள் விருப்பங்களில் உள்ள முழுச் சாதன ஒலி விளைவு அல்லது ஒத்துழைக்கும் பிளேயர் அமர்வுகளை முயற்சிக்கவும்.",
        "Want the presets here?" to "முன்னமைவுகளை இங்கே பார்க்கவா?",
        "Open presets" to "முன்னமைவுகளைத் திற",
        "What PulseEQ is" to "PulseEQ என்றால் என்ன",
        "A 20-band equalizer with real DSP of its own — 20 peaking filters per channel, a preamp and a soft limiter — plus an LED spectrum that lights up to the real audio energy of each band, ready-made presets for music, movies, speech and more, and a foreground service that keeps it all running in the background." to "தனித்த DSP கொண்ட 20-பட்டை சமநிலையாக்கி — ஒவ்வொரு ஒலி வழிக்கும் 20 உச்ச வடிகட்டிகள், முன்பெருக்கி, மென்மையான வரம்பாக்கி; ஒவ்வொரு பட்டையின் உண்மையான ஒலி ஆற்றலைக் காட்டும் LED அலைவரிசை; இசை, திரைப்படம், பேச்சு போன்றவற்றுக்கான முன்னமைவுகள்; பின்னணியில் இயங்க வைக்கும் முன்னணி சேவை.",
        "Where the sound is equalized — honestly" to "ஒலி எங்கு சமநிலைப்படுத்தப்படுகிறது — நேர்மையாக",
        "✅ PulseEQ player — guaranteed. Your file is decoded, filtered by our 20-band engine and played back. The LEDs are live here.\n\n✅ Cooperating players — Poweramp, VLC, Musicolet and others announce their audio session, and PulseEQ shapes it through Android's effect API. No capture, no root.\n\n⚠️ System-wide effect — PulseEQ tries to attach one equalizer to the whole output mix. Google deprecated this and many Android 11+ ROMs refuse it for normal apps; the app tells you whether yours allowed it. When it works, every app is equalized.\n\n❌ Apps that neither use our player nor announce a session — e.g. YouTube or Spotify — cannot be equalized without root. Android simply does not hand out that access, and PulseEQ will not pretend otherwise. (Apps that do this use internal audio capture plus privileged tricks, and Spotify blocks capture outright.)" to "✅ PulseEQ பிளேயர் — உறுதியாகச் செயல்படும். உங்கள் கோப்பு திறக்கப்பட்டு 20-பட்டை இயந்திரத்தில் வடிகட்டப்பட்டு ஒலிக்கும். LED-கள் இங்கு நேரடியாகக் காட்டும்.\n\n✅ ஒத்துழைக்கும் பிளேயர்கள் — Poweramp, VLC, Musicolet போன்றவை ஒலி அமர்வை அறிவித்தால், Android விளைவு API வழியாக PulseEQ ஒலியைச் சரிசெய்யும். பதிவு இல்லை, root இல்லை.\n\n⚠️ முழுச் சாதன ஒலி விளைவு — முழு வெளியீட்டையும் இணைக்க PulseEQ முயலும். Google இதைத் திரும்பப் பெற்றுவிட்டது; Android 11+ பல பதிப்புகள் சாதாரண பயன்பாடுகளை மறுக்கும். உங்கள் சாதனம் அனுமதித்ததா என்பதைப் பயன்பாடு தெரிவிக்கும். செயல்பட்டால் எல்லா பயன்பாடுகளுக்கும் பொருந்தும்.\n\n❌ எங்கள் பிளேயரைப் பயன்படுத்தாததும் அமர்வை அறிவிக்காததுமான பயன்பாடுகள் — YouTube அல்லது Spotify போன்றவை — root இல்லாமல் சமநிலைப்படுத்த முடியாது. Android அந்த அணுகலை வழங்காது; PulseEQ வேறுவிதமாகக் கூறாது. சில பயன்பாடுகள் உள் ஒலி பதிவையும் சிறப்பு அணுகலையும் பயன்படுத்துகின்றன; Spotify பதிவை முற்றிலும் தடுக்கிறது.",
        "Privacy" to "தனியுரிமை",
        "• Internet permission is used only for GitHub release checks; no audio is uploaded.\n• No microphone permission: the LED spectrum reads the audio you are already playing, not the room.\n• No accounts, no ads, no analytics.\n• Your settings live in the app's own private storage." to "• GitHub வெளியீடுகளைச் சரிபார்க்க மட்டுமே இணைய அனுமதி பயன்படும்; ஒலி பதிவேற்றப்படாது.\n• மைக்ரோஃபோன் அனுமதி இல்லை: LED அலைவரிசை அறை ஒலியை அல்ல, நீங்கள் இயக்கும் ஒலியையே அளக்கும்.\n• கணக்குகள், விளம்பரங்கள், பகுப்பாய்வு இல்லை.\n• உங்கள் அமைப்புகள் பயன்பாட்டின் தனிப்பட்ட சேமிப்பில் இருக்கும்.",
        "Diagnostics" to "கண்டறிதல் தகவல்",
        "Copy info" to "தகவலை நகலெடு",
        "Refresh" to "புதுப்பி",
        "Listening safety" to "கேட்கும் பாதுகாப்பு",
        "Boosting bands adds real energy — that is why the preamp drops automatically and a soft limiter sits on the output. Still, keep phone volume at a sane level, especially with headphones, and give your ears breaks." to "ஒலி பட்டைகளை உயர்த்துவது உண்மையான ஆற்றலைச் சேர்க்கும் — அதனால் முன்பெருக்கி தானாகக் குறைக்கப்படுகிறது; வெளியீட்டில் மென்மையான வரம்பாக்கியும் உள்ளது. இருந்தாலும், குறிப்பாக காதொலியுடன், போன் ஒலியை மிதமாக வைத்துக் கொண்டு காதுகளுக்கு இடைவெளி கொடுக்கவும்.",
        "Reset to flat" to "சம நிலைக்கு மீட்டமை",
        "Bypass" to "வழிமாற்று",
        "Brightness" to "ஒளிர்வு",
        "LED spectrum" to "LED அலைவரிசை",
        "Pulls the whole curve down so big boosts can't clip. It follows your boosts automatically, but you can trim it by hand here." to "அதிக ஒலி உயர்வு சிதையாமல் முழு வளைவையும் குறைக்கும். உங்கள் உயர்வுகளைப் பின்பற்றி தானாக மாறும்; இங்கே கைமுறையாகவும் மாற்றலாம்.",
        "Bars follow the real audio energy in each band" to "ஒவ்வொரு பட்டையின் உண்மையான ஒலி ஆற்றலைப் பட்டைகள் காட்டும்",
        "Bars show your EQ curve instead of live audio" to "நேரடி ஒலிக்குப் பதிலாக உங்கள் EQ வளைவைப் பட்டைகள் காட்டும்",
        "Currently equalizing %s player session(s)." to "%s பிளேயர் அமர்வுகளுக்கு தற்போது ஒலி சமநிலை செய்யப்படுகிறது.",
        "Tip: PulseEQ's own 20-band engine runs inside the built-in player (Player tab) so the curve is exact. The system-wide effect and cooperating players use the device's own equalizer engine, which usually has fewer bands — PulseEQ maps your curve onto it." to "குறிப்பு: உள்ளமைந்த பிளேயரில் (பிளேயர் தாவல்) PulseEQ-இன் 20-பட்டை இயந்திரம் இயங்குவதால் வளைவு துல்லியமாக இருக்கும். முழுச் சாதன விளைவும் ஒத்துழைக்கும் பிளேயர்களும் சாதனத்தின் சமநிலையாக்கியைப் பயன்படுத்தும்; அதில் பட்டைகள் குறைவாக இருக்கலாம் — PulseEQ உங்கள் வளைவை அதற்கு மாற்றும்.",
        "Louder +2 dB" to "அதிக ஒலி +2 dB",
        "Quieter −2 dB" to "குறைந்த ஒலி −2 dB",
        "Shift the whole curve" to "முழு வளைவையும் மாற்று",
        "drag to shape • tap label to zero" to "வடிவமைக்க இழுக்கவும் • பூஜ்ஜியமாக்க பெயரைத் தட்டவும்",
        "SYSTEM-WIDE" to "முழுச் சாதனம்",
        "BACKGROUND OFF" to "பின்னணி முடக்கம்",
        "BACKGROUND ON" to "பின்னணி இயக்கம்",
        "PLAYER ONLY" to "பிளேயர் மட்டும்",
        "BYPASSED" to "தவிர்க்கப்படுகிறது",
        "Ready-made curves for music, movies and speech — apply one, then fine-tune it by hand" to "இசை, திரைப்படம், பேச்சுக்கான தயாரான வளைவுகள் — ஒன்றைத் தேர்ந்தெடுத்து கைமுறையாகச் சரிசெய்யவும்",
        "Not started yet." to "இன்னும் தொடங்கவில்லை.",

        // RP Vocal Remover Studio home, queue, playback and output browser.
        "Studio" to "ஸ்டுடியோ",
        "MODEL READY" to "மாடல் தயார்",
        "MODEL NEEDED" to "மாடல் தேவை",
        "FIRST PASS · WAV" to "முதல் செயலாக்கம் · WAV",
        "Keep the music.\nLose the lead vocals." to "இசையை வைத்துக்கொள்ளுங்கள்.\nமுன்னணி குரலை நீக்குங்கள்.",
        "First pass saves a separate instrumental WAV. Your original stays unchanged." to "முதல் செயலாக்கம் கருவியிசை கொண்ட தனி WAV-ஐச் சேமிக்கும். அசல் கோப்பு மாற்றப்படாது.",
        "Browse saved WAV / MP3" to "சேமித்த WAV / MP3-ஐ உலாவு",
        "Music/Vocal Remover Studio" to "Music/Vocal Remover Studio",
        "Processing speed" to "செயலாக்க வேகம்",
        "CHOOSE ONE" to "ஒன்றைத் தேர்ந்தெடு",
        "JOB PROFILE" to "பணியின் முறை",
        "Slow" to "மெதுவாக",
        "Medium" to "நடுத்தரம்",
        "Fast" to "வேகமாக",
        "coolest" to "குறைந்த வெப்பம்",
        "recommended" to "பரிந்துரை",
        "more cores" to "கூடுதல் கோர்கள்",
        "Keep screen awake" to "திரையை விழித்திருக்க வை",
        "While this app is open during a job or model download · uses more battery" to "பணி அல்லது மாடல் பதிவிறக்கத்தின் போது பயன்பாடு திறந்திருக்கும் வரை · கூடுதல் மின்சாரம் தேவை",
        "Model ready" to "மாடல் தயார்",
        "Download model" to "மாடலைப் பதிவிறக்கு",
        "On-device model is ready" to "சாதனத்திலுள்ள மாடல் தயார்",
        "Download vocal-focused model" to "குரல் பிரிப்புக்கான மாடலைப் பதிவிறக்கு",
        "UVR MDX-Net Voc FT · stored privately on this phone · 63.7 MiB" to "UVR MDX-Net Voc FT · இந்தப் போனில் தனிப்பட்ட முறையில் சேமிக்கப்பட்டது · 63.7 MiB",
        "UVR MDX-Net Voc FT · 63.7 MiB over HTTPS · checksum verified" to "UVR MDX-Net Voc FT · HTTPS வழியாக 63.7 MiB · சரிபார்ப்பு மதிப்பு உறுதிசெய்யப்பட்டது",
        "Model credits & terms" to "மாடல் நன்றிகள் மற்றும் விதிமுறைகள்",
        "Download complete · verifying checksum…" to "பதிவிறக்கம் முடிந்தது · சரிபார்ப்பு மதிப்பு உறுதிசெய்யப்படுகிறது…",
        "Connecting securely to the model host…" to "மாடல் சேவையகத்துடன் பாதுகாப்பாக இணைகிறது…",
        "Retry model download" to "மாடல் பதிவிறக்கத்தை மீண்டும் முயற்சி செய்",
        "Download model · 63.7 MiB" to "மாடலைப் பதிவிறக்கு · 63.7 MiB",
        "Background queue" to "பின்னணி வரிசை",
        "Clear finished" to "முடிந்தவற்றை அழி",
        "Processing on device" to "சாதனத்தில் செயலாக்கம்",
        "Paused · resume from the current job card" to "இடைநிறுத்தம் · தற்போதைய பணிப் பலகையிலிருந்து தொடரவும்",
        "Instrumental WAV saved" to "கருவியிசை WAV சேமிக்கப்பட்டது",
        "Failed · retry available" to "தோல்வி · மீண்டும் முயற்சி செய்யலாம்",
        "Waiting" to "காத்திருக்கிறது",
        "Processing needs attention" to "செயலாக்கத்திற்கு கவனம் தேவை",
        "Job canceled" to "பணி ரத்து செய்யப்பட்டது",
        "Paused safely" to "பாதுகாப்பாக இடைநிறுத்தப்பட்டது",
        "Pausing at checkpoint" to "பாதுகாப்புச் சோதனை இடத்தில் இடைநிறுத்துகிறது",
        "Working on your song" to "உங்கள் பாடலைச் செயலாக்குகிறது",
        "WAV and MP3 ready" to "WAV மற்றும் MP3 தயார்",
        "Choosing MP3 files…" to "MP3 கோப்புகளைத் தேர்ந்தெடுக்கிறது…",
        "Add multiple MP3s to queue" to "பல MP3-களை வரிசையில் சேர்",
        "Resume queued songs" to "வரிசைப் பாடல்களைத் தொடர்",
        "Choose an MP3…" to "MP3-ஐத் தேர்ந்தெடு…",
        "Choose an MP3" to "MP3-ஐத் தேர்ந்தெடு",
        "Model downloading…" to "மாடல் பதிவிறங்குகிறது…",
        "Try extra vocal reduction?" to "கூடுதல் குரல் குறைப்பை முயற்சிக்கவா?",
        "This runs the saved instrumental through the separator once more, entirely on this phone. It may reduce leftover singing, but can also soften or change instruments and may take about as long as the first pass, using more processing time, storage, and heat. A separate WAV will be saved; your source and first WAV stay unchanged. Listen to the first WAV before choosing." to "சேமித்த கருவியிசையை இந்தப் போனிலேயே மீண்டும் பிரிப்பான் வழியாகச் செயலாக்கும். மீதமுள்ள பாடலைக் குறைக்கலாம்; ஆனால் கருவியிசையை மங்கச் செய்யவோ மாற்றவோ கூடும். முதல் செயலாக்கம் அளவுக்கு நேரமும் சேமிப்பும் வெப்பமும் தேவைப்படலாம். தனி WAV சேமிக்கப்படும்; அசலும் முதல் WAV-மும் மாற்றமின்றி இருக்கும். தேர்வு செய்வதற்கு முன் முதல் WAV-ஐக் கேளுங்கள்.",
        "Not now" to "இப்போது வேண்டாம்",
        "Start second pass" to "இரண்டாவது செயலாக்கத்தைத் தொடங்கு",
        "Create a high-quality MP3?" to "உயர்தர MP3 உருவாக்கவா?",
        "MP3 is a lossy format, so no WAV-to-MP3 conversion can be mathematically lossless. This uses a high-quality 320 kb/s CBR LAME encode to minimize the extra loss. It creates a separate MP3 in Music/Vocal Remover Studio; the WAV remains unchanged." to "MP3 தர இழப்புள்ள வடிவம்; WAV-ஐ MP3-ஆக மாற்றுவது கணித ரீதியாக இழப்பற்றதாக இருக்க முடியாது. கூடுதல் இழப்பைக் குறைக்க உயர்தர 320 kb/s CBR LAME குறியாக்கம் பயன்படும். Music/Vocal Remover Studio-வில் தனி MP3 உருவாகும்; WAV மாற்றமின்றி இருக்கும்.",
        "Keep WAV only" to "WAV மட்டும் வைத்திரு",
        "Create 320 kb/s MP3" to "320 kb/s MP3 உருவாக்கு",
        "Download was interrupted. Tap Retry to try again." to "பதிவிறக்கம் தடைப்பட்டது. மீண்டும் முயற்சி என்பதைத் தட்டவும்.",
        "Allow notifications so the long-running foreground queue stays visible and controllable." to "நீண்ட நேர முன்னணி வரிசையைப் பார்க்கவும் கட்டுப்படுத்தவும் அறிவிப்புகளை அனுமதிக்கவும்.",
        "Model downloaded and checksum verified. Choose an MP3 to begin." to "மாடல் பதிவிறக்கப்பட்டு சரிபார்ப்பு மதிப்பு உறுதிசெய்யப்பட்டது. தொடங்க MP3-ஐத் தேர்ந்தெடுக்கவும்.",
        "Model download failed. Check your connection and try again." to "மாடல் பதிவிறக்கம் தோல்வியடைந்தது. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "Wait until the queue is idle before opening an earlier result." to "முந்தைய முடிவைத் திறப்பதற்கு முன் வரிசை ஓய்வாகும் வரை காத்திருக்கவும்.",
        "Wait for the active queue to finish before opening an earlier result." to "முந்தைய முடிவைத் திறப்பதற்கு முன் செயல்படும் வரிசை முடியும் வரை காத்திருக்கவும்.",
        "Could not open the link." to "இணைப்பைத் திறக்க முடியவில்லை.",
        "Stopped" to "நிறுத்தப்பட்டது",
        "Use" to "பயன்படுத்து",
        "Extra reduction ready" to "கூடுதல் குரல் குறைப்பு தயார்",
        "Instrumental ready" to "கருவியிசை தயார்",
        "Saved chunks" to "சேமித்த பகுதிகள்",
        "Pausing…" to "இடைநிறுத்துகிறது…",
        "Stop queue" to "வரிசையை நிறுத்து",
        "Retry saved work" to "சேமித்த பணியை மீண்டும் முயற்சி செய்",
        "Discard" to "நீக்கு",
        "Play instrumental" to "கருவியிசையை இயக்கு",
        "Add songs" to "பாடல்களைச் சேர்",
        "Cancel playback" to "ஒலிப்பை ரத்து செய்",
        "Playback volume" to "ஒலிப்பின் அளவு",
        "Listen to this WAV first. If singing remains, you can choose one extra on-device reduction pass; it may also change instruments." to "முதலில் இந்த WAV-ஐக் கேளுங்கள். குரல் மீந்தால், சாதனத்தில் ஒரு கூடுதல் குறைப்பு செயலாக்கத்தைத் தேர்ந்தெடுக்கலாம்; அது கருவியிசையையும் மாற்றக்கூடும்.",
        "Optional: reduce vocals again" to "விருப்பம்: குரலை மீண்டும் குறை",
        "Play first-pass WAV for comparison" to "ஒப்பிட முதல் WAV-ஐ இயக்கு",
        "Play saved MP3 · 320 kb/s" to "சேமித்த MP3-ஐ இயக்கு · 320 kb/s",
        "Export MP3 · 320 kb/s" to "MP3 ஆக ஏற்று · 320 kb/s",
        "MP3 is lossy; this high-bitrate export minimizes extra loss but is not lossless. Your WAV stays unchanged." to "MP3 என்பது தர இழப்புள்ள வடிவம்; உயர்ந்த பிட் வீத ஏற்றுமதி கூடுதல் இழப்பைக் குறைக்கும், ஆனால் இழப்பற்றதல்ல. WAV மாற்றமின்றி இருக்கும்.",
        "Add more songs" to "மேலும் பாடல்களைச் சேர்",
        "System watch" to "சாதனக் கண்காணிப்பு",
        "DEVICE STATUS" to "சாதன நிலை",
        "Reading…" to "படிக்கிறது…",
        "Android thermal status not available" to "Android வெப்ப நிலை கிடைக்கவில்லை",
        "Thermal status not reported" to "வெப்ப நிலை தெரிவிக்கப்படவில்லை",
        "Normal" to "இயல்பு",
        "Light" to "லேசானது",
        "Moderate" to "மிதமானது",
        "Severe" to "கடுமையானது",
        "Critical" to "மிகக் கடுமையானது",
        "Emergency" to "அவசர நிலை",
        "Ready" to "தயார்",
        "🌡 Thermal" to "🌡 வெப்ப நிலை",
        "▦ Free RAM" to "▦ காலி RAM",
        "♨ CPU temp" to "♨ CPU வெப்பம்",
        "🔋 Battery" to "🔋 பேட்டரி",
        "▣ App RAM" to "▣ பயன்பாட்டு RAM",
        "◌ CPU load" to "◌ CPU சுமை",
        "◈ Native heap" to "◈ நேட்டிவ் ஹீப்",
        "▧ Storage free" to "▧ காலி சேமிப்பு",
        "Not reported" to "தகவல் இல்லை",
        "Saved WAV and MP3" to "சேமித்த WAV மற்றும் MP3",
        "Refreshing…" to "புதுப்பிக்கிறது…",
        "Browse in Files" to "Files-இல் உலாவு",
        "Loading saved audio…" to "சேமித்த ஒலியை ஏற்றுகிறது…",
        "No WAV or MP3 outputs found yet." to "இன்னும் WAV அல்லது MP3 வெளியீடுகள் இல்லை.",
        "Preparing playback…" to "ஒலிப்பைத் தயாரிக்கிறது…",
        "Open" to "திற",
        "About RP VOCAL REMOVER STUDIO" to "RP VOCAL REMOVER STUDIO பற்றி",
        "Processing and privacy" to "செயலாக்கமும் தனியுரிமையும்",
        "Selected audio is processed on this device and is not uploaded. Internet access is used to download the separation model and check app updates. The original file is preserved. The first pass saves a separate instrumental stereo WAV; the second reduction pass is optional and starts only when you tap it. A separate 320 kb/s CBR MP3 export is available; MP3 is lossy and the WAV is retained. Separation quality varies by mix; residual vocals or musical artifacts are possible." to "தேர்ந்தெடுத்த ஒலி இந்தச் சாதனத்தில் செயலாக்கப்படும்; பதிவேற்றப்படாது. பிரிப்பு மாடலைப் பதிவிறக்கவும் பயன்பாட்டு புதுப்பிப்புகளைச் சரிபார்க்கவும் மட்டுமே இணையம் பயன்படும். அசல் கோப்பு பாதுகாக்கப்படும். முதல் செயலாக்கம் கருவியிசை கொண்ட தனி ஸ்டீரியோ WAV-ஐச் சேமிக்கும்; இரண்டாவது குரல் குறைப்பு விருப்பமானது, நீங்கள் தொடங்கினால் மட்டுமே இயங்கும். 320 kb/s CBR MP3-ஐ தனியாக ஏற்றுமதி செய்யலாம்; MP3 தர இழப்புடையது, WAV வைத்திருக்கும். கலவையைப் பொறுத்து தரம் மாறும்; குரல் எச்சங்களோ இசை மாற்றங்களோ இருக்கலாம்.",
        "Performance and sensors" to "செயல்திறனும் உணரிகளும்",
        "Low-memory pauses may resume automatically after memory recovers and safety checks clear. User, thermal, storage and other safety pauses require a user action. Temperature readings depend on Android and OEM sensor availability; ‘Not reported’ means no usable reading was exposed. CPU load and RAM values are system estimates, not precision instrumentation. Slow uses one inference thread and longer cool-downs; Medium balances CPU use; Fast can use up to four threads while Android reports normal thermal status." to "நினைவகம் குறைவதால் இடைநிறுத்தப்பட்ட பணி, நினைவகம் மீண்டு பாதுகாப்புச் சோதனைகள் சரியான பிறகு தானாகத் தொடரலாம். பயனர், வெப்பம், சேமிப்பு மற்றும் பிற பாதுகாப்பு இடைநிறுத்தங்களுக்கு பயனர் செயல் தேவை. வெப்ப அளவீடுகள் Android மற்றும் சாதன உற்பத்தியாளர் உணரிகளைப் பொறுத்தவை; ‘தகவல் இல்லை’ என்றால் பயன்படக்கூடிய அளவீடு கிடைக்கவில்லை. CPU சுமை மற்றும் RAM மதிப்புகள் துல்லியமான கருவி அளவீடுகள் அல்ல; அமைப்பின் மதிப்பீடுகள். மெதுவான முறை ஒரு inference thread மற்றும் நீண்ட குளிர்விப்பு இடைவெளிகளைப் பயன்படுத்தும்; நடுத்தர முறை CPU பயன்பாட்டை சமப்படுத்தும்; Android வெப்ப நிலை இயல்பாக இருக்கும்போது வேகமான முறை நான்கு thread வரை பயன்படுத்தலாம்.",
        "Queue and playback" to "வரிசையும் ஒலிப்பும்",
        "Songs in the queue run one at a time; queue state and work checkpoints are saved across restarts. Playback is in-app and stops when the app is left. Android or OEM restrictions can interrupt long work. Minimum supported Android version: Android 10." to "வரிசையிலுள்ள பாடல்கள் ஒவ்வொன்றாகச் செயலாக்கப்படும்; வரிசை நிலையும் பணிச் சோதனைப் புள்ளிகளும் மறுதொடக்கத்திற்குப் பிறகும் சேமிக்கப்படும். ஒலிப்பு பயன்பாட்டிற்குள் நடைபெறும்; பயன்பாட்டை விட்டு வெளியேறும்போது நிற்கும். Android அல்லது சாதன உற்பத்தியாளர் கட்டுப்பாடுகள் நீண்ட பணியைத் தடுக்கலாம். குறைந்தபட்ச ஆதரவு Android பதிப்பு: Android 10.",
        "Model credits, licences and notices" to "மாடல் நன்றிகள், உரிமங்கள் மற்றும் அறிவிப்புகள்",
        "RP Vocal Remover Studio uses the larger, vocal-focused UVR MDX-Net Voc FT model, downloaded on request from the sherpa-onnx source-separation-models release. Its SHA-256 is checked before installation." to "RP Vocal Remover Studio, குரலுக்காக வடிவமைக்கப்பட்ட UVR MDX-Net Voc FT மாடலை sherpa-onnx source-separation-models வெளியீட்டிலிருந்து தேவைக்கேற்பப் பதிவிறக்குகிறது. நிறுவுவதற்கு முன் அதன் SHA-256 சரிபார்க்கப்படுகிறது.",
        "Attribution: UVR (Anjok07 and aufr33), UVR MDX-Net Voc FT. The UVR project asks third-party apps using its models to credit UVR and its developers. The sherpa-onnx runtime is Apache-2.0; model weights are a separate asset." to "நன்றி: UVR (Anjok07 மற்றும் aufr33), UVR MDX-Net Voc FT. அதன் மாடல்களைப் பயன்படுத்தும் மூன்றாம் தரப்பு பயன்பாடுகள் UVR மற்றும் உருவாக்குநர்களுக்கு நன்றி தெரிவிக்க வேண்டும் என UVR திட்டம் கோருகிறது. sherpa-onnx இயக்கநேரம் Apache-2.0 உரிமம்; மாடல் எடைகள் தனி சொத்து.",
        "Official UVR notice" to "அதிகாரப்பூர்வ UVR அறிவிப்பு",
        "Bundled notices" to "சேர்க்கப்பட்ட அறிவிப்புகள்",
        "Third-party notices" to "மூன்றாம் தரப்பு அறிவிப்புகள்",
        "Third-party notices are unavailable in this build." to "இந்தக் கட்டமைப்பில் மூன்றாம் தரப்பு அறிவிப்புகள் கிடைக்கவில்லை.",
        "Model credits & licences" to "மாடல் நன்றிகளும் உரிமங்களும்",
        "Got it" to "புரிந்தது",
        "Could not open the release page." to "வெளியீட்டுப் பக்கத்தைத் திறக்க முடியவில்லை.",
        "No installed audio app could open this file." to "நிறுவிய ஒலி பயன்பாட்டால் இந்தக் கோப்பைத் திறக்க முடியவில்லை.",

        // Vocal Remover processing phases and service status.
        "Could not start processing" to "செயலாக்கத்தைத் தொடங்க முடியவில்லை",
        "Waiting to start" to "தொடங்கக் காத்திருக்கிறது",
        "Processing on this device" to "இந்தச் சாதனத்தில் செயலாக்கம்",
        "Resuming from the last completed checkpoint." to "கடைசியாக முடித்த சோதனைப் புள்ளியிலிருந்து தொடர்கிறது.",
        "Resuming this song" to "இந்தப் பாடலைத் தொடர்கிறது",
        "Starting optional extra vocal reduction" to "விருப்பமான கூடுதல் குரல் குறைப்பைத் தொடங்குகிறது",
        "The saved first WAV and selected original will stay unchanged. This second on-device pass may reduce more vocals, but can also alter instruments and takes longer." to "சேமித்த முதல் WAV மற்றும் தேர்ந்தெடுத்த அசல் மாற்றமின்றி இருக்கும். சாதனத்தில் நடைபெறும் இரண்டாவது செயலாக்கம் மேலும் குரலைக் குறைக்கலாம்; கருவியிசையை மாற்றவும் அதிக நேரம் எடுக்கவும் கூடும்.",
        "Running the optional second separation pass" to "விருப்பமான இரண்டாவது பிரிப்பு செயலாக்கம் நடைபெறுகிறது",
        "Starting high-quality MP3 export" to "உயர்தர MP3 ஏற்றுமதியைத் தொடங்குகிறது",
        "Encoding a separate 320 kb/s MP3 locally. MP3 is lossy; the saved WAV will remain unchanged." to "சாதனத்தில் தனி 320 kb/s MP3 உருவாக்கப்படுகிறது. MP3 தர இழப்புடையது; சேமித்த WAV மாற்றமின்றி இருக்கும்.",
        "Creating a separate MP3 export" to "தனி MP3 ஏற்றுமதி உருவாக்கப்படுகிறது",
        "Stopping the processing queue at the next safe checkpoint." to "அடுத்த பாதுகாப்பான சோதனைப் புள்ளியில் செயலாக்க வரிசை நிறுத்தப்படும்.",
        "Retrying from saved audio chunks." to "சேமித்த ஒலி பகுதிகளிலிருந்து மீண்டும் முயற்சிக்கிறது.",
        "Retrying this song on the device" to "இந்தப் பாடலைச் சாதனத்தில் மீண்டும் முயற்சிக்கிறது",
        "Processing stopped unexpectedly. The selected original is unchanged." to "செயலாக்கம் எதிர்பாராத விதமாக நின்றது. தேர்ந்தெடுத்த அசல் மாற்றமின்றி உள்ளது.",
        "Could not finish" to "முடிக்க முடியவில்லை",
        "Preparing extra vocal reduction" to "கூடுதல் குரல் குறைப்பைத் தயாரிக்கிறது",
        "Preparing on-device processing" to "சாதனச் செயலாக்கத்தைத் தயாரிக்கிறது",
        "Starting safely; the original song will not be changed." to "பாதுகாப்பாகத் தொடங்குகிறது; அசல் பாடல் மாற்றப்படாது.",
        "Downloading separation model" to "பிரிப்பு மாடலைப் பதிவிறக்குகிறது",
        "Downloading the 63.7 MiB vocal-focused model over HTTPS; song audio is not uploaded." to "HTTPS வழியாக 63.7 MiB குரல் பிரிப்பு மாடல் பதிவிறங்குகிறது; பாடல் ஒலி பதிவேற்றப்படாது.",
        "Preparing second-pass audio" to "இரண்டாவது செயலாக்க ஒலியைத் தயாரிக்கிறது",
        "Preparing MP3 decoder" to "MP3 குறியாக்க நீக்கியைத் தயாரிக்கிறது",
        "Reading first-pass WAV" to "முதல் செயலாக்க WAV-ஐப் படிக்கிறது",
        "Decoding MP3" to "MP3 குறியாக்க நீக்கம்",
        "Saving extra-reduction WAV" to "கூடுதல் குறைப்பு WAV-ஐச் சேமிக்கிறது",
        "Saving WAV" to "WAV-ஐச் சேமிக்கிறது",
        "Saving the optional second-pass WAV separately. The first-pass WAV stays intact." to "விருப்பமான இரண்டாவது செயலாக்க WAV தனியாகச் சேமிக்கப்படுகிறது. முதல் WAV மாற்றமின்றி இருக்கும்.",
        "Saving the instrumental as a stereo PCM WAV in Music/Vocal Remover Studio." to "கருவியிசை ஸ்டீரியோ PCM WAV ஆக Music/Vocal Remover Studio-வில் சேமிக்கப்படுகிறது.",
        "Extra vocal reduction ready" to "கூடுதல் குரல் குறைப்பு தயார்",
        "Saved the optional extra-reduction WAV. The first-pass WAV and source song were left unchanged." to "விருப்பமான கூடுதல் குறைப்பு WAV சேமிக்கப்பட்டது. முதல் WAV மற்றும் அசல் பாடல் மாற்றப்படவில்லை.",
        "Saved to Music/Vocal Remover Studio. The source song was left unchanged." to "Music/Vocal Remover Studio-வில் சேமிக்கப்பட்டது. அசல் பாடல் மாற்றப்படவில்லை.",
        "Converting WAV to MP3" to "WAV-ஐ MP3-ஆக மாற்றுகிறது",
        "Encoding a separate 320 kb/s MP3 on this phone. MP3 is lossy; the WAV will remain unchanged." to "இந்தப் போனில் தனி 320 kb/s MP3 உருவாக்கப்படுகிறது. MP3 தர இழப்புடையது; WAV மாற்றமின்றி இருக்கும்.",
        "LAME high-quality 320 kb/s CBR encoding · WAV stays untouched." to "LAME உயர்தர 320 kb/s CBR குறியாக்கம் · WAV மாற்றமின்றி இருக்கும்.",
        "MP3 export ready" to "MP3 ஏற்றுமதி தயார்",
        "Saved a separate 320 kb/s MP3 in Music/Vocal Remover Studio. MP3 is lossy; the WAV and source remain unchanged." to "தனி 320 kb/s MP3 Music/Vocal Remover Studio-வில் சேமிக்கப்பட்டது. MP3 தர இழப்புடையது; WAV மற்றும் அசல் மாற்றமின்றி இருக்கும்.",
        "The saved WAV size could not be measured safely; check device storage, then retry." to "சேமித்த WAV அளவைப் பாதுகாப்பாக அளவிட முடியவில்லை; சாதனச் சேமிப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "Free app storage could not be measured safely; check device storage, then retry." to "பயன்பாட்டிற்கான காலி சேமிப்பை அளவிட முடியவில்லை; சாதனச் சேமிப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "Free storage could not be measured safely; check device storage, then retry." to "காலி சேமிப்பை அளவிட முடியவில்லை; சாதனச் சேமிப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்.",
        "The first-pass WAV location is missing." to "முதல் WAV இருப்பிடம் இல்லை.",
        "Android could not read the saved first-pass WAV" to "சேமித்த முதல் WAV-ஐ Android படிக்க முடியவில்லை",
        "The selected MP3 could not be opened" to "தேர்ந்தெடுத்த MP3-ஐத் திறக்க முடியவில்லை",
        "Android could not read the selected MP3" to "தேர்ந்தெடுத்த MP3-ஐ Android படிக்க முடியவில்லை",
        "Copying song to app storage" to "பாடலைப் பயன்பாட்டு சேமிப்பிற்கு நகலெடுக்கிறது",
        "Copying a private working copy; the selected original is not modified." to "தனிப்பட்ட வேலை நகல் உருவாக்கப்படுகிறது; தேர்ந்தெடுத்த அசல் மாற்றப்படாது.",
        "Selected original is unchanged" to "தேர்ந்தெடுத்த அசல் மாற்றமின்றி உள்ளது",
        "Canceled safely. Your selected original song was not changed." to "பாதுகாப்பாக ரத்து செய்யப்பட்டது. தேர்ந்தெடுத்த அசல் பாடல் மாற்றப்படவில்லை.",
        "MP3 export canceled. The saved WAV and selected original were left unchanged." to "MP3 ஏற்றுமதி ரத்து செய்யப்பட்டது. சேமித்த WAV மற்றும் தேர்ந்தெடுத்த அசல் மாற்றப்படவில்லை.",
        "The optional second pass was canceled. The first-pass WAV and selected original remain unchanged." to "விருப்பமான இரண்டாவது செயலாக்கம் ரத்து செய்யப்பட்டது. முதல் WAV மற்றும் தேர்ந்தெடுத்த அசல் மாற்றமின்றி உள்ளன.",
        "Android reports severe thermal status. Let the device cool, then resume." to "Android கடுமையான வெப்ப நிலையை அறிவிக்கிறது. சாதனம் குளிர்ந்ததும் தொடரவும்.",
        "Free storage is low. Make space, then resume." to "காலி சேமிப்பு குறைவு. இடத்தை உருவாக்கி மீண்டும் தொடரவும்.",
        "Processing paused for device safety." to "சாதனப் பாதுகாப்பிற்காக செயலாக்கம் இடைநிறுத்தப்பட்டது.",
        "Android's foreground-processing time allowance ended. Your work is saved; resume when Android allows." to "Android-இன் முன்னணி செயலாக்க நேர வரம்பு முடிந்தது. உங்கள் பணி சேமிக்கப்பட்டுள்ளது; Android அனுமதிக்கும்போது தொடரவும்.",
        "Paused by you. Resume when ready." to "நீங்கள் இடைநிறுத்தினீர்கள். தயாரானதும் தொடரவும்.",
        "Paused by you. The current short audio chunk will finish first." to "நீங்கள் இடைநிறுத்தினீர்கள். தற்போதைய குறுகிய ஒலி பகுதி முதலில் முடியும்.",
        "Paused at a safe checkpoint." to "பாதுகாப்பான சோதனைப் புள்ளியில் இடைநிறுத்தப்பட்டது.",
        "Resumed from the last safe checkpoint." to "கடைசி பாதுகாப்புச் சோதனைப் புள்ளியிலிருந்து தொடரப்பட்டது.",
        "Finishing the current short audio chunk before pausing" to "இடைநிறுத்துவதற்கு முன் தற்போதைய குறுகிய ஒலி பகுதியை முடிக்கிறது",
        "Canceled; original unchanged" to "ரத்து செய்யப்பட்டது; அசல் மாற்றமில்லை",
        "WAV and 320 kb/s MP3 saved; original unchanged" to "WAV மற்றும் 320 kb/s MP3 சேமிக்கப்பட்டது; அசல் மாற்றமில்லை",
        "Extra-reduction WAV saved; first WAV retained" to "கூடுதல் குறைப்பு WAV சேமிக்கப்பட்டது; முதல் WAV பாதுகாக்கப்பட்டது",
        "Instrumental WAV saved; original unchanged" to "கருவியிசை WAV சேமிக்கப்பட்டது; அசல் மாற்றமில்லை",
        "Vocal separation" to "குரல் பிரிப்பு",
        "Visible progress and pause/resume controls for local song processing" to "உள்ளூர் பாடல் செயலாக்கத்தின் முன்னேற்றம் மற்றும் இடைநிறுத்தம்/தொடர்வு கட்டுப்பாடுகள்",

        // Shared project credits, shown in each maintained app's About section.
        "Ramesh prathap .R" to "Ramesh prathap .R",
        "Arena.ai Agent Mode" to "Arena.ai Agent Mode",
        "Development contributor" to "உருவாக்கப் பங்களிப்பாளர்",
        "GitHub releases" to "GitHub வெளியீடுகள்",
        "Live Guard — GitHub release" to "Live Guard — GitHub வெளியீடு",
        "Ramesh Radio — GitHub release" to "Ramesh Radio — GitHub வெளியீடு",
        "PulseEQ — GitHub release" to "PulseEQ — GitHub வெளியீடு",
        "RP VOCAL REMOVER STUDIO — GitHub release" to "RP VOCAL REMOVER STUDIO — GitHub வெளியீடு",
        "universalrp2003@gmail.com" to "universalrp2003@gmail.com",
    )
}
