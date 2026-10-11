package com.universalrp.cleansweep.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/** Aggregate snapshot only. History stays opt-in; reads never refresh the actual scan date. */
object PermissionReviewStore {
    private const val PREFS = "cleansweep_last_permission_review"
    fun load(context: Context): PermissionReviewSummary? = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        PermissionReviewSummary(p.getLong("at", 0), p.getInt("apps", -1), p.getInt("high", -1),
            p.getInt("medium", -1), p.getBoolean("incomplete", true)).takeIf { it.valid() }
    }.getOrNull()

    /** Observe the one saved aggregate; collection never performs a security scan. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(context: Context): Flow<PermissionReviewSummary?> = callbackFlow {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "at" || key == null) trySend(load(app)) }
        p.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(app))
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun record(context: Context, report: SecurityReport) {
        val item = PermissionReviewSummary.from(report)
        if (!item.valid()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", item.atMs).putInt("apps", item.appsChecked).putInt("high", item.high)
            .putInt("medium", item.medium).putBoolean("incomplete", item.incomplete).apply()
    }

    fun clear(context: Context) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
}
