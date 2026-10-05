package com.universalrp.cleansweep

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalrp.cleansweep.ai.Assistant
import com.universalrp.cleansweep.ai.AssistantAction
import com.universalrp.cleansweep.ai.AssistantContext
import com.universalrp.cleansweep.ai.AssistantMessage
import com.universalrp.cleansweep.data.AppCacheInfo
import com.universalrp.cleansweep.data.AppCacheRepo
import com.universalrp.cleansweep.data.JunkDeleter
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.ScanEngine
import com.universalrp.cleansweep.data.ScanProgress
import com.universalrp.cleansweep.data.ScanReport
import com.universalrp.cleansweep.data.ScanSettings
import com.universalrp.cleansweep.data.SettingsRepo
import com.universalrp.cleansweep.data.StorageAccess
import com.universalrp.cleansweep.data.StorageInfo
import com.universalrp.cleansweep.data.StorageInfoProvider
import com.universalrp.cleansweep.data.hasAllFilesAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.os.Environment

enum class Screen { HOME, SCANNING, RESULTS, APP_CACHE, ASSISTANT, SETTINGS, ABOUT }

data class CleanStats(val atMs: Long, val freedBytes: Long, val items: Int)

data class UiState(
    val screen: Screen = Screen.HOME,
    val revision: Long = 0, // bumped on every mutation so in-place list edits recompose
    val hasAllFilesAccess: Boolean = true,
    val storage: StorageInfo? = null,
    val progress: ScanProgress? = null,
    val report: ScanReport? = null,
    val settings: ScanSettings = ScanSettings(),
    val soundsEnabled: Boolean = true,
    val assistantVerbose: Boolean = true,
    val cleaning: Boolean = false,
    val cleanDone: Int = 0,
    val cleanTotal: Int = 0,
    val lastClean: CleanStats? = null,
    val freedDialogBytes: Long? = null,
    val freedDialogItems: Int = 0,
    val message: String? = null,
    // App cache screen
    val usageAccess: Boolean = false,
    val appsLoading: Boolean = false,
    val includeSystemApps: Boolean = false,
    val appCaches: List<AppCacheInfo> = emptyList(),
    val pendingManualApps: Int = 0,
    // On-device assistant
    val assistantMessages: List<AssistantMessage> = emptyList(),
    val assistantTyping: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    val settingsRepo = SettingsRepo(ctx)
    private val engine = ScanEngine(ctx)
    private val deleter = JunkDeleter(ctx)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var scanJob: Job? = null
    private var currentSettings = ScanSettings()
    private val manualQueue = ArrayDeque<String>()

    private val prefs = ctx.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch {
            settingsRepo.scanSettings.collect { s ->
                currentSettings = s
                mutate { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            settingsRepo.soundsEnabled.collect { on ->
                com.universalrp.cleansweep.data.SoundFx.enabled = on
                mutate { it.copy(soundsEnabled = on) }
            }
        }
        viewModelScope.launch {
            settingsRepo.assistantVerbose.collect { on ->
                mutate { it.copy(assistantVerbose = on) }
            }
        }
        val lastMs = prefs.getLong("last_clean_ms", 0L)
        if (lastMs > 0L) {
            val stats = CleanStats(
                atMs = lastMs,
                freedBytes = prefs.getLong("last_clean_bytes", 0L),
                items = prefs.getInt("last_clean_items", 0),
            )
            mutate { it.copy(lastClean = stats) }
        }
        refresh()
    }

    private fun mutate(block: (UiState) -> UiState) {
        _state.update { s -> block(s).let { it.copy(revision = it.revision + 1) } }
    }

    // ------------------------------------------------------------------ basics

    fun refresh() {
        mutate {
            it.copy(
                hasAllFilesAccess = hasAllFilesAccess(),
                storage = runCatching { StorageInfoProvider.read() }.getOrNull(),
                usageAccess = AppCacheRepo.hasUsageAccess(ctx),
            )
        }
        if (_state.value.screen == Screen.APP_CACHE && _state.value.usageAccess) {
            loadAppCaches()
        }
    }

    fun navigate(screen: Screen) = mutate { it.copy(screen = screen) }

    fun requestAllFilesAccess() = StorageAccess.requestAllFilesAccess(ctx)

    fun dismissMessage() = mutate { it.copy(message = null) }

    // ------------------------------------------------------------------ scan

    fun startScan() {
        if (!hasAllFilesAccess()) {
            StorageAccess.requestAllFilesAccess(ctx)
            mutate { it.copy(message = "Please allow “All files access” for CleanSweep, then tap Scan again.") }
            return
        }
        com.universalrp.cleansweep.data.SoundFx.play(ctx, com.universalrp.cleansweep.R.raw.sound_scan)
        mutate { it.copy(screen = Screen.SCANNING, progress = null) }
        val root = Environment.getExternalStorageDirectory()
        val cfg = currentSettings
        scanJob = viewModelScope.launch {
            try {
                val report = engine.scan(root, cfg) { p ->
                    mutate { it.copy(progress = p) }
                }
                mutate { it.copy(screen = Screen.RESULTS, report = report, progress = null) }
            } catch (e: CancellationException) {
                mutate { it.copy(screen = Screen.HOME, progress = null) }
            } catch (e: Exception) {
                mutate {
                    it.copy(
                        screen = Screen.HOME,
                        progress = null,
                        message = "Scan failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        mutate { it.copy(screen = Screen.HOME, progress = null) }
    }

    // ------------------------------------------------------- selection edits

    fun toggleFile(path: String) {
        val report = _state.value.report ?: return
        report.categories.forEach { c ->
            c.files.forEach { f -> if (f.path == path) f.selected = !f.selected }
        }
        mutate { it }
    }

    fun toggleCategory(kind: JunkKind, selected: Boolean) {
        _state.value.report?.categories?.firstOrNull { it.kind == kind }?.setAll(selected)
        mutate { it }
    }

    // ------------------------------------------------------------------ clean

    fun cleanSelected() {
        val report = _state.value.report ?: return
        val paths = report.selectedPaths()
        if (paths.isEmpty() || _state.value.cleaning) return
        mutate { it.copy(cleaning = true, cleanDone = 0, cleanTotal = paths.size) }
        viewModelScope.launch {
            val result = try {
                deleter.delete(paths) { done, _, _ ->
                    mutate { it.copy(cleanDone = done) }
                }
            } catch (e: Exception) {
                null
            }
            if (result == null) {
                mutate { it.copy(cleaning = false, message = "Cleaning was interrupted.") }
                return@launch
            }
            val failedSet = result.failed.toHashSet()
            report.categories.forEach { c ->
                c.files.removeAll { f -> f.path !in failedSet }
            }
            prefs.edit()
                .putLong("last_clean_ms", System.currentTimeMillis())
                .putLong("last_clean_bytes", result.freedBytes)
                .putInt("last_clean_items", result.deleted)
                .apply()
            com.universalrp.cleansweep.data.SoundFx.play(ctx, com.universalrp.cleansweep.R.raw.sound_success)
            mutate {
                it.copy(
                    cleaning = false,
                    cleanDone = 0,
                    cleanTotal = 0,
                    freedDialogBytes = result.freedBytes,
                    freedDialogItems = result.deleted,
                    storage = runCatching { StorageInfoProvider.read() }.getOrNull(),
                    lastClean = CleanStats(System.currentTimeMillis(), result.freedBytes, result.deleted),
                )
            }
        }
    }

    fun dismissFreedDialog() = mutate { it.copy(freedDialogBytes = null) }

    // -------------------------------------------------------------- app cache

    fun loadAppCaches() {
        if (!AppCacheRepo.hasUsageAccess(ctx)) {
            mutate { it.copy(usageAccess = false) }
            return
        }
        mutate { it.copy(usageAccess = true, appsLoading = true) }
        viewModelScope.launch {
            val list = try {
                AppCacheRepo.load(ctx, _state.value.includeSystemApps)
            } catch (e: Exception) {
                emptyList()
            }
            mutate { it.copy(appsLoading = false, appCaches = list) }
        }
    }

    fun requestUsageAccess() = AppCacheRepo.openUsageAccessSettings(ctx)

    fun toggleApp(pkg: String) {
        mutate { s ->
            s.copy(appCaches = s.appCaches.map {
                if (it.pkg == pkg) it.copy(selected = !it.selected) else it
            })
        }
    }

    fun toggleAllApps(selected: Boolean) {
        mutate { s ->
            s.copy(appCaches = s.appCaches.map { it.copy(selected = selected) })
        }
    }

    fun setIncludeSystemApps(value: Boolean) {
        mutate { it.copy(includeSystemApps = value) }
        loadAppCaches()
    }

    /**
     * v1.3: no Accessibility automation. CleanSweep walks the user through the
     * official two-tap path (open app storage page → user taps "Clear cache").
     */
    fun cleanSelectedApps() {
        val pkgs = _state.value.appCaches
            .filter { it.selected && it.cacheBytes > 0 }
            .map { it.pkg }
        if (pkgs.isEmpty()) {
            mutate { it.copy(message = "Select at least one app with cache to clean.") }
            return
        }
        manualQueue.clear()
        manualQueue.addAll(pkgs)
        openNextManualApp()
    }

    fun openNextManualApp() {
        val pkg = manualQueue.removeFirstOrNull()
        if (pkg == null) {
            mutate { it.copy(pendingManualApps = 0, message = "All done — refreshing cache sizes…") }
            loadAppCaches()
            return
        }
        StorageAccess.openAppStorage(ctx, pkg)
        mutate {
            it.copy(
                pendingManualApps = manualQueue.size,
                message = "Tap “Clear cache” on that screen, come back, then tap “Next app”."
            )
        }
    }

    // --------------------------------------------------------- assistant (v1.3)

    /** Opens the chat and seeds the greeting bubble on first use. */
    fun openAssistant() {
        mutate { it.copy(screen = Screen.ASSISTANT) }
        if (_state.value.assistantMessages.isEmpty()) {
            val msg = Assistant.welcome(assistantContext(), _state.value.assistantVerbose)
            mutate { it.copy(assistantMessages = it.assistantMessages + msg) }
        }
    }

    fun assistantSuggestions(): List<String> = Assistant.suggestionChips(assistantContext())

    fun askAssistant(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.assistantTyping) return
        val userMsg = AssistantMessage(fromUser = true, text = q)
        mutate { it.copy(assistantMessages = it.assistantMessages + userMsg, assistantTyping = true) }
        viewModelScope.launch {
            // A short beat so the reply feels like a considered answer rather than
            // a canned string — the whole engine still runs locally and instantly.
            delay(420)
            val s = _state.value
            val reply = Assistant.answer(q, assistantContext(), s.assistantVerbose)
            mutate { it.copy(assistantMessages = it.assistantMessages + reply, assistantTyping = false) }
        }
    }

    fun runAssistantAction(action: AssistantAction) {
        when (action) {
            AssistantAction.SCAN -> startScan()
            AssistantAction.OPEN_APP_CACHE -> navigate(Screen.APP_CACHE)
            AssistantAction.OPEN_RESULTS -> {
                if (_state.value.report != null) navigate(Screen.RESULTS)
                else mutate { it.copy(message = "Scan first and I'll show you the results here.") }
            }
            AssistantAction.OPEN_SETTINGS -> navigate(Screen.SETTINGS)
            AssistantAction.NONE -> Unit
        }
    }

    fun clearAssistant() {
        val msg = Assistant.welcome(assistantContext(), _state.value.assistantVerbose)
        mutate { it.copy(assistantMessages = listOf(msg), assistantTyping = false) }
    }

    private fun assistantContext(): AssistantContext {
        val s = _state.value
        return AssistantContext(
            storage = s.storage,
            report = s.report,
            lastCleanBytes = s.lastClean?.freedBytes ?: 0L,
            lastCleanItems = s.lastClean?.items ?: 0,
            hasAllFilesAccess = s.hasAllFilesAccess,
            usageAccess = s.usageAccess,
            appCacheCount = s.appCaches.size,
            totalAppCacheBytes = s.appCaches.sumOf { it.cacheBytes }.coerceAtLeast(0L),
            topAppCache = s.appCaches.maxByOrNull { it.cacheBytes },
        )
    }

    // --------------------------------------------------------------- settings

    fun setAssistantVerbose(v: Boolean) =
        viewModelScope.launch { settingsRepo.setAssistantVerbose(v) }

    fun setSoundsEnabled(v: Boolean) =
        viewModelScope.launch { settingsRepo.setSoundsEnabled(v) }

    fun setIncludeHidden(v: Boolean) =
        viewModelScope.launch { settingsRepo.setIncludeHidden(v) }

    fun setDupMinKb(v: Int) =
        viewModelScope.launch { settingsRepo.setDupMinKb(v) }

    fun setLargeMb(v: Int) =
        viewModelScope.launch { settingsRepo.setLargeMb(v) }

    fun setOldDays(v: Int) =
        viewModelScope.launch { settingsRepo.setOldDays(v) }

    fun setApkInstalledOnly(v: Boolean) =
        viewModelScope.launch { settingsRepo.setApkInstalledOnly(v) }

    fun addExclusion(raw: String) =
        viewModelScope.launch { settingsRepo.addExclusion(raw) }

    fun removeExclusion(path: String) =
        viewModelScope.launch { settingsRepo.removeExclusion(path) }
}
