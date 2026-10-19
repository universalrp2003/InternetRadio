package com.universalrp.cleansweep.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.universalrp.cleansweep.BuildConfig
import com.universalrp.cleansweep.net.BoundedHttp
import com.universalrp.updates.ReleaseRepositorySources
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import kotlin.coroutines.coroutineContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Live Guard updater. The other three apps use the shared updater; no silent install, browser download or CI asset. */
object LiveGuardUpdates {
    data class Release(val version: String, val notes: String, val tag: String, val assetUrl: String, val bytes: Long,
        val sha256: String, val publishedAt: Long, val repository: String)
    data class State(val busy: Boolean = false, val downloading: Boolean = false, val bytes: Long = 0,
        val release: Release? = null, val verifiedApk: File? = null, val checkedAt: Long? = null,
        val message: String? = null)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var revision = 0L
    private fun prefs(context: Context) = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    fun automatic(context: Context) = prefs(context).getBoolean("automatic", true) // Preserve the existing saved OFF choice.
    fun setAutomatic(context: Context, value: Boolean) { prefs(context).edit().putBoolean("automatic", value).apply() }
    fun isDismissed(context: Context, version: String): Boolean =
        prefs(context).getString("liveguard_dismissed_version", null) == version
    fun dismiss(context: Context, version: String) {
        prefs(context).edit().putString("liveguard_dismissed_version", version).apply()
    }
    fun checkOnLaunch(context: Context) {
        if (!automatic(context)) return
        // The owner asked for a launch check, not a daily cache window. The prompt itself is
        // suppressed for a version the user already dismissed until a newer APK is published.
        check(context)
    }
    fun check(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val mine = ++revision
        mutable.value = mutable.value.copy(busy = true, message = null)
        job = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    var best: Release? = null
                    var reachableRepositories = 0
                    var lastFailure: Exception? = null
                    val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "LiveGuard-update-check")

                    fun isBetter(candidate: Release, current: Release): Boolean {
                        if (UpdatePolicy.newer(candidate.version, current.version)) return true
                        if (UpdatePolicy.newer(current.version, candidate.version)) return false
                        val candidateRank = ReleaseRepositorySources.ordered.indexOfFirst { it.slug == candidate.repository }
                        val currentRank = ReleaseRepositorySources.ordered.indexOfFirst { it.slug == current.repository }
                        return if (candidateRank != currentRank) candidateRank < currentRank
                        else candidate.publishedAt > current.publishedAt
                    }

                    for (source in ReleaseRepositorySources.ordered) {
                        for (page in 1..ReleaseRepositorySources.MAX_RELEASE_PAGES) {
                            coroutineContext.ensureActive()
                            val reply = try {
                                BoundedHttp.request(source.releasesPage(page), 15_000, headers = headers)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                lastFailure = failure
                                break
                            }
                            if (reply.code != 200) {
                                lastFailure = java.io.IOException("${source.slug} release check returned HTTP ${reply.code}")
                                break
                            }
                            if (page == 1) reachableRepositories++
                            val releases = JSONArray(reply.body)
                            for (i in 0 until releases.length()) {
                                coroutineContext.ensureActive()
                                val release = releases.getJSONObject(i)
                                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                                val tag = release.optString("tag_name")
                                    .takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,120}")) } ?: continue
                                if (!ReleaseRepositorySources.isOfficialReleasePage(release.optString("html_url"), source, tag)) continue
                                val published = runCatching {
                                    Instant.parse(release.optString("published_at")).toEpochMilli()
                                }.getOrNull() ?: continue
                                val assets = release.optJSONArray("assets") ?: continue
                                for (j in 0 until assets.length()) {
                                    val asset = assets.getJSONObject(j)
                                    val name = asset.optString("name")
                                    val version = Regex("^CleanSweep-v([0-9]+(?:\\.[0-9]+){1,3})\\.apk$")
                                        .matchEntire(name)?.groupValues?.get(1) ?: continue
                                    if (asset.optString("state") != "uploaded" ||
                                        !UpdatePolicy.officialAsset(asset.optString("browser_download_url"), name, tag, source) ||
                                        !UpdatePolicy.validSize(asset.optLong("size", -1L))
                                    ) continue
                                    val hash = UpdatePolicy.digest(asset.optString("digest")) ?: continue
                                    if (!UpdatePolicy.newer(version, BuildConfig.VERSION_NAME)) continue
                                    val candidate = Release(
                                        version = version,
                                        notes = release.optString("body").take(12_000),
                                        tag = tag,
                                        assetUrl = asset.optString("browser_download_url"),
                                        bytes = asset.optLong("size", -1L),
                                        sha256 = hash,
                                        publishedAt = published,
                                        repository = source.slug,
                                    )
                                    val old = best
                                    if (old == null || isBetter(candidate, old)) best = candidate
                                }
                            }
                            if (releases.length() < ReleaseRepositorySources.PAGE_SIZE) break
                        }
                    }
                    if (reachableRepositories == 0) {
                        throw java.io.IOException("Could not check either official release repository", lastFailure)
                    }
                    best
                }
                if (mine != revision) return@launch
                val now = System.currentTimeMillis()
                prefs(app).edit().putLong("liveguard_checked", now).apply()
                val same = found?.sha256 == mutable.value.release?.sha256
                if (!same) mutable.value.verifiedApk?.delete()
                mutable.value = State(release = found, checkedAt = now, verifiedApk = mutable.value.verifiedApk.takeIf { same },
                    message = if (found == null) "No newer published, verifiable APK was found. Test builds are not releases." else "Official update metadata verified. The APK is checked before installation.")
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (mine == revision) mutable.value = mutable.value.copy(message = "Update check failed; check your connection and try again.") } finally { if (mine == revision) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun cancel() {
        ++revision; job?.cancel(); job = null
        mutable.value = mutable.value.copy(busy = false, downloading = false, bytes = 0, message = "Update operation cancelled. No installation was started.")
    }
    fun download(context: Context) {
        val release = mutable.value.release ?: return
        if (job?.isActive == true) return
        val app = context.applicationContext; val mine = ++revision
        val file = File(File(app.cacheDir, "verified-updates"), "update-${UUID.randomUUID()}.apk")
        mutable.value = mutable.value.copy(busy = true, downloading = true, bytes = 0, verifiedApk = null, message = null)
        job = scope.launch {
            var accepted = false
            try {
                VerifiedApkDownload.download(release.assetUrl, file, release.bytes, release.sha256) { bytes ->
                    scope.launch { if (mine == revision) mutable.value = mutable.value.copy(bytes = bytes) }
                }
                verify(app, file, release)
                ensureActive()
                if (mine != revision) return@launch
                accepted = true
                mutable.value = mutable.value.copy(verifiedApk = file, message = "APK digest, package, newer version and signing certificate match. Continue to Android installer.")
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (mine == revision) mutable.value = mutable.value.copy(message = "Download or APK verification failed. Nothing will be installed. Try again or check update help.") } finally {
                if (!accepted) file.delete()
                if (mine == revision) mutable.value = mutable.value.copy(busy = false, downloading = false)
            }
        }
    }
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()
    fun requestInstallPermission(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { mutable.value = mutable.value.copy(message = "Open Android Settings, Special app access, Install unknown apps, then allow Live Guard only if you trust this update.") }
    }
    fun install(context: Context) {
        val release = mutable.value.release ?: return; val file = mutable.value.verifiedApk ?: return
        if (job?.isActive == true) return
        if (!canInstall(context)) { requestInstallPermission(context); return }
        val mine = ++revision; val app = context.applicationContext
        mutable.value = mutable.value.copy(busy = true)
        job = scope.launch {
            try {
                verify(app, file, release) // Recheck private-cache bytes/installed version immediately before handing off.
                ensureActive(); if (mine != revision) return@launch
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates.files", file)
                app.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                mutable.value = mutable.value.copy(message = "Android installer opened. Confirm there; installation and Play Protect results are controlled by Android.")
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (mine == revision) mutable.value = mutable.value.copy(message = "Android installer could not open or verification no longer matches. Check install permission and update help.") } finally { if (mine == revision) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    @Suppress("DEPRECATION")
    private suspend fun verify(context: Context, file: File, release: Release) = withContext(Dispatchers.IO) {
        check(context.packageName == UpdatePolicy.PACKAGE && file.isFile && file.length() == release.bytes)
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        check(hash.digest().joinToString("") { "%02x".format(it) } == release.sha256)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val apk = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APK package not readable")
        fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        check(apk.packageName == UpdatePolicy.PACKAGE && apk.versionName == release.version && code(apk) > code(installed))
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } }.toSet()
        }
        check(UpdatePolicy.trustedSigner(signers(apk), signers(installed)))
    }
}
