// Forked for Secret Message 1.1.0 from shared/src/main/java/com/universalrp/updates (the shared
// updater pins the RP Ideas signing certificate; Secret Message has its own release key).
package com.rpideas.secretmessage.wifitest.updates

import com.rpideas.secretmessage.wifitest.Tr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Verified GitHub Releases updater for the RP Ideas Android apps, including Oor Arivippu.
 * It never installs automatically: Android's per-app sideload permission, package installer
 * confirmation and Play Protect remain in control.
 */
object AppUpdates {
    private const val MAX_APK_BYTES = 128L * 1024 * 1024
    private const val EXPECTED_SIGNER_SHA256 = "58e370e417cf6f33234689acd5bf379474f5a8c55f79181d9e892a321bc04f08"
    private val DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    private data class AppIdentity(val prefix: String, val packageName: String) {
        val assetPattern = Regex("^${Regex.escape(prefix)}-v([0-9]+(?:\\.[0-9]+){1,3})\\.apk$")
    }

    data class Release(
        val version: String,
        val notes: String,
        val tag: String,
        val assetName: String,
        val assetUrl: String,
        val size: Long,
        val sha256: String,
        val publishedAt: Long,
        val repository: String,
    )

    private fun identity(prefix: String): AppIdentity = when (prefix) {
        "Secret-Message" -> AppIdentity(prefix, "com.rpideas.secretmessage")
        else -> throw IllegalArgumentException("No updater configuration exists for this app")
    }

    fun newer(candidate: String, installed: String): Boolean {
        fun parts(value: String): List<Int>? {
            if (!value.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}"))) return null
            return value.split('.').map { it.toIntOrNull() ?: return null }
        }
        val a = parts(candidate) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val delta = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (delta != 0) return delta > 0
        }
        return false
    }

    private fun digest(value: String?): String? = value
        ?.removePrefix("sha256:")
        ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }

    private fun validSize(size: Long): Boolean = size in 1..MAX_APK_BYTES

    private fun localizedReleaseNotes(notes: String, tamil: Boolean): String {
        val marker = "## தமிழ்"
        return if (tamil && marker in notes) {
            notes.substringAfter(marker).trim()
        } else {
            notes.substringBefore(marker).trim()
        }
    }

    private fun isOfficialAsset(
        url: String,
        source: ReleaseRepositorySources.Source,
        tag: String,
        name: String,
    ): Boolean = ReleaseRepositorySources.isOfficialAsset(url, source, tag, name)

    private suspend fun fetch(prefix: String, installedVersion: String): Release? = withContext(Dispatchers.IO) {
        val app = identity(prefix)
        var best: Release? = null
        var reachableRepositories = 0
        var lastFailure: Exception? = null

        fun isBetter(candidate: Release, current: Release): Boolean {
            if (newer(candidate.version, current.version)) return true
            if (newer(current.version, candidate.version)) return false
            val candidateRank = ReleaseRepositorySources.ordered.indexOfFirst { it.slug == candidate.repository }
            val currentRank = ReleaseRepositorySources.ordered.indexOfFirst { it.slug == current.repository }
            return if (candidateRank != currentRank) candidateRank < currentRank
            else candidate.publishedAt > current.publishedAt
        }

        // Check both official repositories. A missing/not-yet-created destination is an
        // expected migration state; it must not prevent updates from the still-live old repo.
        for (source in ReleaseRepositorySources.ordered) {
            for (page in 1..ReleaseRepositorySources.MAX_RELEASE_PAGES) {
                coroutineContext.ensureActive()
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(source.releasesPage(page)).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "$prefix-verified-updater")
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        lastFailure = IOException("${source.slug} release check returned HTTP $responseCode")
                        break
                    }
                    if (page == 1) reachableRepositories++
                    val raw = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 4 * 1024 * 1024) { "Release metadata is too large" }
                            output.write(buffer, 0, count)
                        }
                        String(output.toByteArray(), Charsets.UTF_8)
                    }
                    val releases = JSONArray(raw)
                    for (i in 0 until releases.length()) {
                        coroutineContext.ensureActive()
                        val release = releases.getJSONObject(i)
                        if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                        val tag = release.optString("tag_name")
                        if (!tag.matches(Regex("[A-Za-z0-9._-]{1,120}"))) continue
                        if (!ReleaseRepositorySources.isOfficialReleasePage(release.optString("html_url"), source, tag)) continue
                        val publishedAt = runCatching {
                            Instant.parse(release.optString("published_at")).toEpochMilli()
                        }.getOrNull() ?: 0L
                        val assets = release.optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            if (asset.optString("state") != "uploaded") continue
                            val name = asset.optString("name")
                            val version = app.assetPattern.matchEntire(name)?.groupValues?.get(1) ?: continue
                            if (!newer(version, installedVersion)) continue
                            val hash = digest(asset.optString("digest")) ?: continue
                            val size = asset.optLong("size", -1L)
                            val url = asset.optString("browser_download_url")
                            if (!validSize(size) || !isOfficialAsset(url, source, tag, name)) continue
                            val candidate = Release(
                                version = version,
                                notes = release.optString("body").take(12_000),
                                tag = tag,
                                assetName = name,
                                assetUrl = url,
                                size = size,
                                sha256 = hash,
                                publishedAt = publishedAt,
                                repository = source.slug,
                            )
                            val old = best
                            if (old == null || isBetter(candidate, old)) best = candidate
                        }
                    }
                    if (releases.length() < ReleaseRepositorySources.PAGE_SIZE) break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    lastFailure = failure
                    break
                } finally {
                    connection?.disconnect()
                }
            }
        }
        if (reachableRepositories == 0) {
            throw IOException("Could not check either official release repository", lastFailure)
        }
        best
    }

    private suspend fun download(release: Release, destination: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        check(validSize(release.size) && digest(release.sha256) != null)
        var location = URL(release.assetUrl)
        var activeConnection: HttpURLConnection? = null
        val hash = MessageDigest.getInstance("SHA-256")
        var complete = false
        try {
            for (hop in 0..4) {
                ensureActive()
                check(location.protocol == "https" && location.host.lowercase() in DOWNLOAD_HOSTS &&
                    location.userInfo == null && location.port in setOf(-1, 443)) {
                    "Update download redirected to an untrusted host"
                }
                val connection = location.openConnection() as HttpURLConnection
                activeConnection = connection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "RP-Android-verified-updater")
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308 -> {
                        val next = connection.getHeaderField("Location") ?: throw IOException("Update redirect has no destination")
                        location = URL(location, next)
                        connection.disconnect()
                        activeConnection = null
                        check(hop < 4) { "Too many update redirects" }
                    }
                    HttpURLConnection.HTTP_OK -> break
                    else -> throw IOException("Update download returned HTTP $status")
                }
            }
            val response = activeConnection ?: throw IOException("Update download did not start")
            val responseLength = response.contentLengthLong
            check(responseLength <= 0L || responseLength == release.size) { "Downloaded size differs from release metadata" }
            destination.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
            var bytes = 0L
            response.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes += count
                        check(bytes <= release.size) { "APK is larger than release metadata" }
                        output.write(buffer, 0, count)
                        hash.update(buffer, 0, count)
                        onProgress(bytes)
                    }
                }
            }
            val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
            check(bytes == release.size && actualHash == release.sha256) { "APK size or SHA-256 verification failed" }
            complete = true
            destination
        } finally {
            activeConnection?.disconnect()
            if (!complete) destination.delete()
        }
    }

    private suspend fun ensureApkMatches(context: Context, file: File, identity: AppIdentity, release: Release) = withContext(Dispatchers.IO) {
        check(context.packageName == identity.packageName) { "Updater app does not match the requested package" }
        check(file.isFile && file.length() == release.size) { "Verified update file is missing or changed" }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        check(hash.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "Update digest no longer matches" }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun signerDigests(info: PackageInfo): Set<String> {
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.toSet()
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded APK package metadata cannot be read")
        check(candidate.packageName == identity.packageName && candidate.versionName == release.version &&
            versionCode(candidate) > versionCode(installed)) { "APK package or newer version does not match" }
        val expected = setOf(EXPECTED_SIGNER_SHA256)
        check(signerDigests(candidate) == expected && signerDigests(installed) == expected) {
            "APK signing certificate does not match the installed app"
        }
    }

    private fun canInstall(context: Context): Boolean = runCatching {
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    private fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            // The UI continues to explain the exact Android Settings route.
        }
    }

    private fun launchInstaller(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates.files", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    @Composable
    fun Control(prefix: String, automatic: Boolean = false) {
        val context = LocalContext.current
        val app = remember(prefix) { identity(prefix) }
        val prefs = remember(context) { context.getSharedPreferences("app_updates", Context.MODE_PRIVATE) }
        val scope = rememberCoroutineScope()
        var installed by remember(prefix) {
            mutableStateOf(
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0",
            )
        }
        var enabled by remember { mutableStateOf(prefs.getBoolean("automatic", true)) }
        var requested by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(false) }
        var bytes by remember { mutableLongStateOf(0L) }
        var message by remember { mutableStateOf<String?>(null) }
        var update by remember { mutableStateOf<Release?>(null) }
        var verifiedApk by remember { mutableStateOf<File?>(null) }
        var showPrompt by remember { mutableStateOf(false) }
        var installAllowed by remember { mutableStateOf(canInstall(context)) }
        var operation by remember { mutableStateOf<Job?>(null) }
        val tamil = Tr.tamil
        fun text(value: String) = Tr.tr(value)

        LaunchedEffect(prefix, automatic, enabled, requested, installed) {
            val manual = requested > 0
            if (!manual && (!automatic || !enabled)) return@LaunchedEffect
            busy = true
            message = null
            val result = runCatching { fetch(prefix, installed) }
            busy = false
            if (result.isSuccess) {
                val found = result.getOrNull()
                prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                val sameVerifiedRelease = found != null && found.sha256 == update?.sha256
                if (!sameVerifiedRelease) verifiedApk?.delete()
                verifiedApk = verifiedApk?.takeIf { sameVerifiedRelease }
                update = found
                message = if (found == null) {
                    "No newer published release was found for this app. Installed: $installed."
                } else {
                    "Update available · ${found.version} (installed: $installed)."
                }
                val dismissed = prefs.getString("dismissed_version_$prefix", null)
                showPrompt = (manual && found != null) ||
                    (!manual && found != null && dismissed != found.version)
            } else if (manual) {
                message = text("Could not check for updates. Check your connection and try again.")
                showPrompt = true
            }
        }

        fun beginDownload() {
            val release = update ?: return
            if (busy) return
            operation?.cancel()
            busy = true
            downloading = true
            bytes = 0L
            verifiedApk?.delete()
            verifiedApk = null
            message = null
            val file = File(File(context.cacheDir, "verified-updates"), "${prefix.lowercase()}-${UUID.randomUUID()}.apk")
            operation = scope.launch {
                var complete = false
                try {
                    withContext(Dispatchers.IO) {
                        download(release, file) { current ->
                            scope.launch { bytes = current }
                        }
                        ensureApkMatches(context, file, app, release)
                    }
                    verifiedApk = file
                    complete = true
                    message = "The downloaded APK is verified. Android will show its installer and confirmation; this app cannot install silently or bypass Play Protect."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = text("The update could not be verified, so it will not be installed.")
                } finally {
                    if (!complete) file.delete()
                    downloading = false
                    busy = false
                }
            }
        }

        fun installUpdate() {
            val release = update ?: return
            val file = verifiedApk ?: return
            if (!canInstall(context)) {
                installAllowed = false
                message = text("Android needs per-app Install unknown apps access before the installer can open. Grant it in Settings only if you trust this update.")
                openInstallPermission(context)
                return
            }
            if (busy) return
            busy = true
            operation = scope.launch {
                try {
                    ensureApkMatches(context, file, app, release)
                    launchInstaller(context, file)
                    message = text("Android installer opened. Confirm installation there; Android and Play Protect control the result.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = text("The update could not be verified, so it will not be installed.")
                } finally {
                    busy = false
                }
            }
        }

        if (!automatic) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text("Verified GitHub APK updates"))
                Text(text("Stable releases are checked against the app package, version, SHA-256 and installed signing certificate. Downloads stay in the app; Android shows its installer and confirmation."))
                Text(text("Checks RP-Android-Apps first; InternetRadio remains as a grace-period fallback."))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(text("Check for updates on app launch"), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        prefs.edit().putBoolean("automatic", it).apply()
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { requested++ }) { Text(text(if (busy) "Checking…" else "Check for updates")) }
                    if (busy) TextButton(onClick = { operation?.cancel(); operation = null; busy = false; downloading = false; message = text("The update could not be verified, so it will not be installed.") }) {
                        Text(text("Cancel"))
                    }
                }
                message?.let {
                    val releaseDetails = update?.let { release ->
                        if (tamil) "\nபதிப்பு ${release.version} · ${release.size / (1024 * 1024)} MiB"
                        else "\nVersion ${release.version} · ${release.size / (1024 * 1024)} MiB"
                    } ?: ""
                    Text(text(it) + releaseDetails)
                }
                update?.let { release ->
                    Text(text("Release source") + ": ${release.repository}")
                    Text(text("What’s new") + "\n" + localizedReleaseNotes(release.notes, tamil))
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { (bytes.toDouble() / release.size.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    when {
                        verifiedApk != null -> Button(enabled = !busy, onClick = { installUpdate() }) {
                            Text(text(if (installAllowed) "Install with Android confirmation" else "Allow installation for this app"))
                        }
                        !downloading -> Button(enabled = !busy, onClick = { beginDownload() }) { Text(text("Download and verify APK")) }
                    }
                }
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, context) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    installAllowed = canInstall(context)
                    val currentVersion = runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "0"
                    if (currentVersion != installed) installed = currentVersion
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (automatic && showPrompt && update != null) {
            val release = update!!
            AlertDialog(
                onDismissRequest = {
                    prefs.edit().putString("dismissed_version_$prefix", release.version).apply()
                    showPrompt = false
                },
                title = { Text(text("Update available") + " · ${release.version}") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val installedSize = release.size / (1024 * 1024)
                        Text(if (tamil) "நிறுவிய பதிப்பு: $installed · APK: $installedSize MiB" else "Installed: $installed · APK: $installedSize MiB")
                        Text(text("Release source") + ": ${release.repository}")
                        message?.let { Text(text(it)) }
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { (bytes.toDouble() / release.size.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(text("Downloading and verifying APK…"))
                        } else if (verifiedApk == null) {
                            Text(text("SHA-256, package name, newer version and signing certificate are checked before Android opens the installer."))
                            Text(text("Android requires Install unknown apps permission for this app and its own confirmation for each install. Play Protect is not bypassed."))
                            Text(text("What’s new") + "\n" + localizedReleaseNotes(release.notes, tamil))
                        }
                    }
                },
                confirmButton = {
                    when {
                        verifiedApk != null -> TextButton(enabled = !busy, onClick = { installUpdate() }) {
                            Text(text(if (installAllowed) "Install with Android confirmation" else "Allow installation for this app"))
                        }
                        downloading -> TextButton(enabled = !busy, onClick = { operation?.cancel(); operation = null }) { Text(text("Cancel")) }
                        else -> TextButton(enabled = !busy, onClick = { beginDownload() }) { Text(text("Download and verify APK")) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prefs.edit().putString("dismissed_version_$prefix", release.version).apply()
                        showPrompt = false
                    }) { Text(text("Later")) }
                },
            )
        }
    }
}
