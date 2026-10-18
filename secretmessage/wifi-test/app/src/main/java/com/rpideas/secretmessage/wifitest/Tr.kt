package com.rpideas.secretmessage.wifitest

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpideas.secretmessage.preview.ui.Accent
import com.rpideas.secretmessage.preview.ui.Ink
import com.rpideas.secretmessage.preview.ui.KeyBorder
import com.rpideas.secretmessage.preview.ui.Night
import com.rpideas.secretmessage.preview.ui.ProductText

/**
 * The app's language: Tamil first, English on request (owner request for 1.1.0), like the other RP
 * Ideas apps. Only the interface changes: the chat keyboard stays English, and the copied report
 * stays English so it can be read the same way from every phone.
 *
 * The choice is Compose state, so every screen that calls [tr] while composing follows a change at
 * once. Notifications read it when they are posted.
 */
internal object Tr {
    private const val PREFS = "secretmessage_language"
    private const val KEY_TAMIL = "tamil"

    private var tamilState by mutableStateOf(true)

    @Volatile
    private var loaded = false

    val tamil: Boolean get() = tamilState

    /** Loads the saved choice once per process and connects the product screens to it. */
    fun init(context: Context) {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    tamilState = context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean(KEY_TAMIL, true)
                    loaded = true
                }
            }
        }
        ProductText.translate = { english -> tr(english) }
    }

    fun set(context: Context, tamil: Boolean) {
        tamilState = tamil
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TAMIL, tamil).apply()
    }

    /** [english] in the chosen language; text without a Tamil entry stays in English. */
    fun tr(english: String): String {
        if (!tamilState) return english
        return TamilText.words[english] ?: TamilText.patterned(english) ?: english
    }

    /** [english] in the chosen language, then `{0}`, `{1}`… replaced by [args]. */
    fun tr(english: String, vararg args: Any): String {
        var text = tr(english)
        args.forEachIndexed { index, value -> text = text.replace("{$index}", value.toString()) }
        return text
    }
}

/** Shorthand used by the app's screens. */
internal fun tr(english: String): String = Tr.tr(english)

internal fun tr(english: String, vararg args: Any): String = Tr.tr(english, *args)

/** Two small pills, "தமிழ்" and "English", for the start screen and Settings. */
@Composable
internal fun LanguageToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LanguagePill("தமிழ்", selected = Tr.tamil) { Tr.set(context, true) }
        LanguagePill("English", selected = !Tr.tamil) { Tr.set(context, false) }
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Accent else Color.Transparent)
            .border(1.dp, if (selected) Accent else KeyBorder, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Night else Ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
