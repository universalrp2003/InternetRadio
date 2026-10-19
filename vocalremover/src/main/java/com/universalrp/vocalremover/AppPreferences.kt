package com.universalrp.vocalremover

import android.content.Context

object AppPreferences {
    private const val PREFS = "vocal_remover_preferences"
    private const val KEEP_SCREEN_AWAKE = "keep_screen_awake"

    fun keepScreenAwake(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEEP_SCREEN_AWAKE, false)

    fun setKeepScreenAwake(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEEP_SCREEN_AWAKE, enabled).apply()
    }
}
