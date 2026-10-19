package com.rpideas.secretmessage.preview.settings

import android.content.Context
import com.rpideas.secretmessage.preview.audio.BuiltInNotificationSound
import com.rpideas.secretmessage.preview.chat.ChatTimerPolicy
import com.rpideas.secretmessage.preview.chat.DEFAULT_VIEW_SECONDS

/** Non-content preferences only. Message text, drawings and keys are never stored. */
data class PreviewSettings(
    val viewSeconds: Int = DEFAULT_VIEW_SECONDS,
    val toneId: Int = BuiltInNotificationSound.DEFAULT.id,
    val appLockEnabled: Boolean = false,
) {
    val tone: BuiltInNotificationSound
        get() = BuiltInNotificationSound.fromId(toneId)

    fun sanitized(): PreviewSettings = copy(
        viewSeconds = ChatTimerPolicy.snapViewSeconds(viewSeconds),
        toneId = BuiltInNotificationSound.fromId(toneId).id,
    )
}

/** App-private preferences; backup and device transfer are excluded in the manifest rules. */
class PreviewSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): PreviewSettings = PreviewSettings(
        viewSeconds = preferences.getInt(KEY_VIEW_SECONDS, DEFAULT_VIEW_SECONDS),
        toneId = preferences.getInt(KEY_TONE_ID, BuiltInNotificationSound.DEFAULT.id),
        appLockEnabled = preferences.getBoolean(KEY_APP_LOCK, false),
    ).sanitized()

    fun save(settings: PreviewSettings) {
        val clean = settings.sanitized()
        preferences.edit()
            .putInt(KEY_VIEW_SECONDS, clean.viewSeconds)
            .putInt(KEY_TONE_ID, clean.toneId)
            .putBoolean(KEY_APP_LOCK, clean.appLockEnabled)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "secretmessage_preview_settings"
        const val KEY_VIEW_SECONDS = "view_seconds"
        const val KEY_TONE_ID = "tone_id"
        const val KEY_APP_LOCK = "app_lock_enabled"
    }
}
