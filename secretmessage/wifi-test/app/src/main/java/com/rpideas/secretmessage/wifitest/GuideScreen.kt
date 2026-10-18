package com.rpideas.secretmessage.wifitest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.ui.Ink

/** The guide in Tamil and English (owner request for 1.1.0); it follows the app's language. */
@Composable
internal fun GuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val sections = if (Tr.tamil) TAMIL_GUIDE else ENGLISH_GUIDE
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Guide"), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            LanguageToggle()
        }
        sections.forEach { (title, body) ->
            AppSection(title) { AppNote(body) }
        }
        TextButton(onClick = onBack) { Text(tr("Back to settings")) }
        Spacer(Modifier.height(12.dp))
    }
}

private val ENGLISH_GUIDE = listOf(
    "1. Start a chat with a new phone" to
        "Tap \"Start a chat\" and send the invite by SMS or WhatsApp. On the other phone, tap the link (or copy it " +
        "and tap \"Paste invite and join\"). Both people compare the 20-digit safety code. Tap \"Codes match\" " +
        "only if every digit is the same on both phones.",
    "2. Call a remembered phone" to
        "After the first chat the phone appears under \"Remembered phones\". Tap its name to give it a name. Tap " +
        "\"Chat\": the other phone gets a notification, \"Someone you remember wants to chat\", and the app asks there " +
        "too. Tap it or \"Answer\": the chat opens on both phones without comparing the code again, because the key " +
        "is unchanged. If both phones tap Chat at the same time, they connect by themselves. A green dot " +
        "means the phone is ready now; grey means it is not reachable now. Both phones need Secret Message 1.1.0 " +
        "or newer and must have chatted once with it.",
    "3. Messages" to
        "Type with the built-in English keyboard, or open the drawing pad to draw a picture (the default) or letters " +
        "in any language. Messages disappear from both phones after they are viewed and their timer ends (60 to 300 " +
        "seconds, set in Settings). Undelivered messages are removed after 30 seconds. Nothing is saved.",
    "4. Keep Secret Message ready for chats" to
        "In Settings keep \"Ready for chats\" on, tap \"Allow background use\" and allow it. Keep the \"Ready for " +
        "chats\" notification; it says nothing else. Xiaomi, Redmi and POCO: Settings > Apps > Manage apps > Secret " +
        "Message > Autostart: on, and Battery saver: No restrictions. Samsung: Settings > Battery > Background usage " +
        "limits: do not put Secret Message in sleeping apps. If a call says \"Not reachable\", open Secret Message " +
        "on the other phone once.",
    "5. Safety" to
        "If the app shows \"WARNING: the key ... has changed\", compare every digit, and tap \"Codes differ\" if you " +
        "are unsure. Reset key and Forget remembered phones are in Settings. The relay forwards only encrypted data " +
        "and keeps nothing; chat requests are encrypted too. This app has not been independently security-reviewed.",
    "6. Updates" to
        "Settings > About > Check for updates. A download is checked (signing certificate, version and SHA-256) " +
        "before Android asks you to install it.",
)

private val TAMIL_GUIDE = listOf(
    "1. புதிய தொலைபேசியுடன் உரையாடலைத் தொடங்க" to
        "“உரையாடலைத் தொடங்கு” என்பதைத் தொட்டு, அழைப்பிதழை SMS அல்லது WhatsApp மூலம் அனுப்புங்கள். மற்ற " +
        "தொலைபேசியில் அந்த இணைப்பைத் தொடவும் (அல்லது அதை நகலெடுத்து “அழைப்பிதழை ஒட்டி இணை” என்பதைத் " +
        "தொடவும்). இருவரும் 20 இலக்கப் பாதுகாப்புக் குறியீட்டை ஒப்பிடுங்கள். இரண்டு தொலைபேசிகளிலும் ஒவ்வொரு " +
        "இலக்கமும் ஒன்றாக இருந்தால் மட்டுமே “குறியீடுகள் பொருந்துகின்றன” என்பதைத் தொடவும்.",
    "2. நினைவில் உள்ள தொலைபேசியை அழைக்க" to
        "முதல் உரையாடலுக்குப் பிறகு அந்தத் தொலைபேசி “நினைவில் உள்ள தொலைபேசிகள்” பட்டியலில் வரும். பெயர் வைக்க " +
        "அதன் பெயரைத் தொடவும். “உரையாடு” என்பதைத் தொட்டால், மற்ற தொலைபேசிக்கு “நீங்கள் நினைவில் வைத்த " +
        "ஒருவர் உரையாட அழைக்கிறார்” என்ற அறிவிப்பு வரும்; செயலியிலும் கேட்கும். அதை அல்லது “பதிலளி” என்பதைத் " +
        "தொட்டதும், சாவி மாறாததால் குறியீட்டை மீண்டும் ஒப்பிடாமலே இரண்டு தொலைபேசிகளிலும் உரையாடல் திறக்கும். " +
        "இரண்டு தொலைபேசிகளும் ஒரே நேரத்தில் “உரையாடு” என்பதைத் தொட்டால், அவை தானாகவே இணையும். பச்சைப் புள்ளி: " +
        "இப்போது தயார். சாம்பல் புள்ளி: " +
        "இப்போது தொடர்பு கொள்ள முடியாது. இரண்டு தொலைபேசிகளிலும் Secret Message 1.1.0 அல்லது புதிய பதிப்பு " +
        "இருக்க வேண்டும்; அதில் ஒருமுறை உரையாடியிருக்க வேண்டும்.",
    "3. செய்திகள்" to
        "உள்ளமைந்த ஆங்கில விசைப்பலகையில் தட்டச்சு செய்யலாம், அல்லது வரைபலகையைத் திறந்து படம் (இயல்பு) அல்லது " +
        "எந்த மொழியிலும் எழுத்துகளை வரையலாம். செய்திகள் பார்க்கப்பட்டு அவற்றின் நேரம் முடிந்ததும் இரண்டு " +
        "தொலைபேசிகளிலிருந்தும் மறைந்துவிடும் (60 முதல் 300 வினாடிகள், அமைப்புகளில் மாற்றலாம்). சேராத செய்திகள் " +
        "30 வினாடிகளில் நீக்கப்படும். எதுவும் சேமிக்கப்படுவதில்லை.",
    "4. Secret Message-ஐ உரையாடலுக்குத் தயாராக வைத்திருக்க" to
        "அமைப்புகளில் “உரையாடலுக்குத் தயார்” என்பதை இயக்கத்தில் வைத்து, “பின்னணியில் இயங்க அனுமதி” என்பதைத் " +
        "தொட்டு அனுமதியுங்கள். “உரையாடலுக்குத் தயார்” அறிவிப்பை அணைக்க வேண்டாம்; அதில் வேறு எதுவும் இருக்காது. " +
        "Xiaomi, Redmi, POCO: Settings > Apps > Manage apps > Secret Message > Autostart இயக்கவும்; Battery saver: " +
        "No restrictions. Samsung: Settings > Battery > Background usage limits-இல் Secret Message-ஐ sleeping apps " +
        "பட்டியலில் சேர்க்க வேண்டாம். அழைப்பில் “இப்போது தொடர்பு கொள்ள முடியாது” என்று வந்தால், மற்ற " +
        "தொலைபேசியில் Secret Message-ஐ ஒருமுறை திறக்கவும்.",
    "5. பாதுகாப்பு" to
        "“எச்சரிக்கை: சாவி மாறியுள்ளது” என்று வந்தால் ஒவ்வொரு இலக்கத்தையும் ஒப்பிடுங்கள்; சந்தேகம் இருந்தால் " +
        "“குறியீடுகள் வேறுபடுகின்றன” என்பதைத் தொடவும். சாவியை மீட்டமைத்தல், நினைவில் உள்ள தொலைபேசிகளை மறத்தல் " +
        "ஆகியவை அமைப்புகளில் உள்ளன. இணைப்புச் சேவையகம் (relay) குறியாக்கப்பட்ட தரவை மட்டுமே அனுப்புகிறது; " +
        "எதையும் சேமிப்பதில்லை. உரையாடல் அழைப்புகளும் குறியாக்கப்பட்டவை. இந்தச் செயலி சுயாதீனப் பாதுகாப்பு " +
        "மதிப்பாய்வு செய்யப்படவில்லை.",
    "6. புதுப்பிப்புகள்" to
        "அமைப்புகள் > செயலி விவரம் (About) > “புதுப்பிப்புகளைச் சரிபார்”. பதிவிறக்கம் கையொப்பச் சான்றிதழ், பதிப்பு, " +
        "SHA-256 ஆகியவற்றுடன் சரிபார்க்கப்பட்ட பிறகே Android நிறுவக் கேட்கும்.",
)
