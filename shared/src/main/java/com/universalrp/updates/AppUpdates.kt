package com.universalrp.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Checks published stable releases, not uninstalled CI artifacts. No automatic APK installation. */
object AppUpdates {
    private const val REPO = "https://github.com/universalrp2003/InternetRadio"
    data class Release(val version: String, val notes: String, val page: String)
    fun newer(candidate: String, installed: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: return false }
        val b = installed.split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val delta = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (delta != 0) return delta > 0
        }
        return false
    }
    private suspend fun fetch(prefix: String, installed: String): Release? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/universalrp2003/InternetRadio/releases?per_page=30").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12000; c.readTimeout = 12000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "$prefix-update-check")
            check(c.responseCode == 200) { "Update service unavailable (${c.responseCode}). Try again later." }
            val releases = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            var best: Release? = null
            val pattern = Regex("^${Regex.escape(prefix)}-v([0-9]+(?:\\.[0-9]+)*)\\.apk$")
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val version = pattern.matchEntire(assets.getJSONObject(j).optString("name"))?.groupValues?.get(1) ?: continue
                    val page = release.optString("html_url")
                    if (!page.startsWith("$REPO/releases/")) continue
                    if (newer(version, installed) && (best == null || newer(version, best!!.version)))
                        best = Release(version, release.optString("body").ifBlank { "No release notes were published." }, page)
                }
            }
            best
        } finally { c.disconnect() }
    }

    @Composable
    fun Control(prefix: String, automatic: Boolean = false) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("app_updates", 0) }
        var enabled by remember { mutableStateOf(prefs.getBoolean("automatic", true)) }
        var requested by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var update by remember { mutableStateOf<Release?>(null) }
        var show by remember { mutableStateOf(false) }
        val installed = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" }
        LaunchedEffect(requested) {
            val manual = requested > 0
            if (!manual && (!automatic || !prefs.getBoolean("automatic", true) ||
                    System.currentTimeMillis() - prefs.getLong("checked", 0) < 24L * 60 * 60 * 1000)) return@LaunchedEffect
            busy = true
            val result = runCatching { fetch(prefix, installed) }
            busy = false
            if (result.isSuccess) {
                prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                update = result.getOrNull()
                message = if (update == null) "No newer published release was found for this app. Installed: $installed. Test builds may be newer than published releases." else "Version ${update!!.version} is available (installed: $installed)."
                show = manual || update != null
            } else if (manual) {
                message = "Could not check for updates. Check your connection and try again."
                show = true
            }
        }
        if (!automatic) Column {
            TextButton(enabled = !busy, onClick = { requested++ }) { Text(if (busy) "Checking…" else "Check for updates") }
            Text("Checks GitHub releases. Shows what’s new before you choose to download; never installs automatically.")
            TextButton(onClick = { enabled = !enabled; prefs.edit().putBoolean("automatic", enabled).apply() }) {
                Text("Daily check on app launch: ${if (enabled) "On" else "Off"}")
            }
        }
        if (show) AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(if (update != null) "Update available" else "App updates") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(message.orEmpty())
                update?.let { Text("\nWhat’s new\n${it.notes.take(12000)}") }
            } },
            confirmButton = { TextButton(onClick = {
                update?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.page))) } }
                show = false
            }) { Text(if (update == null) "OK" else "Open release / download") } },
            dismissButton = { if (update != null) TextButton(onClick = { show = false }) { Text("Later") } },
        )
    }
}
