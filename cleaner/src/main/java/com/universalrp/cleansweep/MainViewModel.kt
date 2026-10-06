package com.universalrp.cleansweep

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalrp.cleansweep.ai.AiClient
import com.universalrp.cleansweep.ai.AiConfig
import com.universalrp.cleansweep.ai.AiProvider
import com.universalrp.cleansweep.ai.AiReport
import com.universalrp.cleansweep.ai.AiSettings
import com.universalrp.cleansweep.ai.Assistant
import com.universalrp.cleansweep.ai.AssistantAction
import com.universalrp.cleansweep.ai.AssistantContext
import com.universalrp.cleansweep.ai.AssistantMessage
import com.universalrp.cleansweep.data.AppCacheInfo
import com.universalrp.cleansweep.data.AppCacheRepo
import com.universalrp.cleansweep.data.AppInventoryLoader
import com.universalrp.cleansweep.data.AppInventoryReport
import com.universalrp.cleansweep.data.AppRow
import com.universalrp.cleansweep.data.DeviceHealthReader
import com.universalrp.cleansweep.data.HealthSnapshot
import com.universalrp.cleansweep.data.NetworkReport
import com.universalrp.cleansweep.data.NetworkScanner
import com.universalrp.cleansweep.data.SecurityReport
import com.universalrp.cleansweep.data.SecurityScanner
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
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment

enum class Screen {
    HOME, SCANNING, RESULTS, APP_CACHE, ASSISTANT, SETTINGS, ABOUT,
    HEALTH, APPS, SECURITY, NETWORK, AI_SETTINGS, AI_REPORT
}

/** Filter ids for the installed-apps screen. */
object AppFilter {
    const val ALL = "all"
    const val BLOAT = "bloat"
    const val UNUSED = "unused"
    const val PREINSTALLED = "preinstalled"
    const val SIDELOADED = "sideloaded"
    const val RISKY = "risky"
}

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
    // Phone health (battery, thermals, CPU, memory)
    val health: HealthSnapshot? = null,
    // Installed apps
    val appInventory: AppInventoryReport? = null,
    val appInventoryBusy: Boolean = false,
    val appFilter: String = AppFilter.ALL,
    val appQuery: String = "",
    // Security check
    val securityReport: SecurityReport? = null,
    val securityBusy: Boolean = false,
    // Network
    val networkReport: NetworkReport? = null,
    val networkBusy: Boolean = false,
    val networkProgress: Pair<Int, Int>? = null,
    val locationPermission: Boolean = false,
    // Android 8/9/10 storage permission (the "0 MB cleaned" bug)
    val legacyStorageOk: Boolean = true,
    // AI analysis
    val aiConfig: AiConfig = AiConfig(),
    val aiBusy: Boolean = false,
    val aiAnswer: String? = null,
    val aiError: String? = null,
    val aiPromptPreview: String = "",
    val aiUsedProvider: String = "",
    val aiTestBusy: Boolean = false,
    val aiTestResult: String? = null,
    // Deletion failures (so the app can never say "0 MB cleaned" and mean success)
    val deleteFailureCount: Int = 0,
    val deleteFailureReason: String? = null,
    // AI model list loaded from the provider (so nobody has to guess a model name)
    val aiModels: List<String> = emptyList(),
    val aiModelsBusy: Boolean = false,
    val aiModelsError: String? = null,
    // "Who is on my network" identified through the AI
    val aiDeviceBusy: Boolean = false,
    val aiDeviceResult: String? = null,
    // Assistant mode + charging notification
    val assistantOnline: Boolean = false,
    val chargeMonitor: Boolean = true,
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
        viewModelScope.launch {
            settingsRepo.assistantOnline.collect { on ->
                mutate { it.copy(assistantOnline = on) }
            }
        }
        viewModelScope.launch {
            settingsRepo.chargeMonitor.collect { on ->
                mutate { it.copy(chargeMonitor = on) }
                com.universalrp.cleansweep.notify.ChargeMonitorService.sync(ctx, on)
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
        mutate { it.copy(aiConfig = AiSettings.load(ctx)) }
        refresh()
        refreshHealth()
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
                legacyStorageOk = legacyStorageGranted(),
                locationPermission = locationGranted(),
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
        if (!legacyStorageGranted()) {
            // Android 8/9/10: without WRITE_EXTERNAL_STORAGE the scan finds the junk and
            // every delete then fails, which used to end as "0 MB cleaned".
            mutate {
                it.copy(
                    message = "Allow storage access first — on Android 9 and older CleanSweep " +
                        "cannot delete what it finds without it."
                )
            }
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
            if (result.freedBytes <= 0L && result.failed.isNotEmpty()) {
                // Nothing was actually removed. Say so instead of showing a 0 MB "success".
                mutate {
                    it.copy(
                        cleaning = false,
                        cleanDone = 0,
                        cleanTotal = 0,
                        deleteFailureCount = result.failed.size,
                        deleteFailureReason = result.firstFailure ?: "the system refused the delete",
                        message = "Nothing was deleted — storage permission is missing.",
                    )
                }
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

    fun dismissDeleteFailure() = mutate {
        it.copy(deleteFailureCount = 0, deleteFailureReason = null)
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            StorageAccess.requestAllFilesAccess(ctx)
        } else {
            // The UI launches the runtime dialog; this is the fallback path.
            StorageAccess.openAppInfo(ctx, ctx.packageName)
        }
    }

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
            val state = _state.value
            if (state.assistantOnline && state.aiConfig.ready) {
                // Online mode: your provider answers, with the same on-device numbers as
                // context so it cannot drift away from reality.
                val context = assistantContext()
                val facts = buildString {
                    appendLine("Storage: free ${context.storage?.free ?: 0} of ${context.storage?.total ?: 0} bytes")
                    context.report?.let { report ->
                        appendLine("Last scan: ${report.totalCount} junk items, ${report.totalBytes} bytes")
                    }
                    appendLine("App cache measured: ${context.appCacheCount} apps, ${context.totalAppCacheBytes} bytes")
                    context.topAppCache?.let { appendLine("Biggest cache: ${it.label}") }
                    appendLine("All-files access: ${context.hasAllFilesAccess}, usage access: ${context.usageAccess}")
                    _state.value.health?.let { health ->
                        appendLine(
                            "Battery ${health.battery.percent}% ${health.battery.statusLabel}, " +
                                "%.1f C, ${health.battery.powerW ?: 0} W; " +
                                "CPU ${health.cpuTempC ?: 0} C; " +
                                "RAM free ${health.device.availableRamBytes} of ${health.device.totalRamBytes}"
                        )
                    }
                }
                val result = AiClient.ask(
                    config = state.aiConfig,
                    systemPrompt = "You are CleanSweep's helper inside an Android cleaning app. " +
                        "Answer in under 200 words, plain language, using only the phone facts given.",
                    userPrompt = "$facts\nQuestion from the user: $q",
                )
                val reply = AssistantMessage(
                    fromUser = false,
                    text = if (result.ok) {
                        result.text
                    } else {
                        "I could not reach the AI provider (${result.error ?: "unknown error"}). " +
                            "Here is the on-device answer instead:\n\n" +
                            Assistant.answer(q, context, state.assistantVerbose).text
                    },
                )
                mutate { it.copy(assistantMessages = it.assistantMessages + reply, assistantTyping = false) }
                return@launch
            }
            // A short beat so the reply feels like a considered answer rather than
            // a canned string — the whole engine still runs locally and instantly.
            delay(420)
            val reply = Assistant.answer(q, assistantContext(), _state.value.assistantVerbose)
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


    // ------------------------------------------------------------ permissions

    /** Android 8/9/10 (API 26-29): WRITE_EXTERNAL_STORAGE is needed to delete files. */
    fun legacyStorageGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return true
        val read = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun legacyStoragePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    fun locationGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Wi-Fi details need Location below Android 13 and Nearby-devices from 13 up. */
    fun networkPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun requestNetworkPermission() {
        // The screen launches the runtime dialog; if the user denied twice we can only
        // send them to the app's settings page.
        StorageAccess.openAppInfo(ctx, ctx.packageName)
    }

    // ---------------------------------------------------------------- health

    fun refreshHealth() {
        val snapshot = runCatching { DeviceHealthReader.read(ctx) }.getOrNull() ?: return
        mutate { it.copy(health = snapshot) }
        // Keep the charging card in the status bar in step with reality: it appears when
        // the charger is connected and removes itself when the cable comes out.
        com.universalrp.cleansweep.notify.ChargeMonitorService.sync(ctx, _state.value.chargeMonitor)
    }

    fun setChargeMonitor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setChargeMonitor(enabled) }
        com.universalrp.cleansweep.notify.ChargeMonitorService.sync(ctx, enabled)
    }

    fun setAssistantOnline(enabled: Boolean) {
        if (enabled && !_state.value.aiConfig.ready) {
            mutate {
                it.copy(message = "Add an AI provider in AI settings first — then Online AI can answer.")
            }
            return
        }
        viewModelScope.launch { settingsRepo.setAssistantOnline(enabled) }
    }

    // ------------------------------------------------------------ app inventory

    fun loadAppInventory(includeSystem: Boolean = true) {
        if (_state.value.appInventoryBusy) return
        mutate { it.copy(appInventoryBusy = true, screen = Screen.APPS) }
        viewModelScope.launch {
            val report = runCatching { AppInventoryLoader.load(ctx, includeSystem) }.getOrNull()
            mutate {
                it.copy(
                    appInventoryBusy = false,
                    appInventory = report ?: it.appInventory,
                    message = if (report == null) "Could not read the app list." else null,
                )
            }
        }
    }

    fun setAppFilter(filter: String) = mutate { it.copy(appFilter = filter) }

    fun setAppQuery(query: String) = mutate { it.copy(appQuery = query) }

    fun appFilters(): List<Pair<String, String>> = listOf(
        AppFilter.ALL to "All",
        AppFilter.BLOAT to "Bloatware",
        AppFilter.UNUSED to "Unused",
        AppFilter.PREINSTALLED to "Preinstalled",
        AppFilter.SIDELOADED to "Not from a store",
        AppFilter.RISKY to "Sensitive permissions",
    )

    fun filteredApps(): List<AppRow> {
        val s = _state.value
        val report = s.appInventory ?: return emptyList()
        val query = s.appQuery.trim().lowercase()
        return report.rows.filter { row ->
            val matchesQuery = query.isEmpty() ||
                row.label.lowercase().contains(query) ||
                row.pkg.lowercase().contains(query)
            val matchesFilter = when (s.appFilter) {
                AppFilter.BLOAT -> row.isKnownBloatStub || (row.isPreinstalled && row.labelsBloat())
                AppFilter.UNUSED -> row.isUnused && !row.isSystem
                AppFilter.PREINSTALLED -> row.isPreinstalled
                AppFilter.SIDELOADED -> row.isSideloaded
                AppFilter.RISKY -> row.riskyPermissions.isNotEmpty()
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    /** A preinstalled app counts as bloatware when it is unused *and* sizable. */
    private fun AppRow.labelsBloat(): Boolean =
        isUnused && (riskyPermissions.isNotEmpty() || totalBytes > 20L * 1024 * 1024)

    fun openAppInfo(pkg: String) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$pkg")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            mutate { it.copy(message = "Android would not open that app's settings.") }
        }
    }

    // ---------------------------------------------------------------- security

    fun loadSecurity() {
        if (_state.value.securityBusy) return
        mutate { it.copy(securityBusy = true, screen = Screen.SECURITY) }
        viewModelScope.launch {
            val apps = _state.value.appInventory?.rows
                ?: runCatching { AppInventoryLoader.load(ctx, includeSystem = true) }
                    .getOrNull()?.rows.orEmpty()
            val report = runCatching { SecurityScanner.scan(ctx, apps) }.getOrNull()
            mutate {
                it.copy(
                    securityBusy = false,
                    securityReport = report ?: it.securityReport,
                    message = if (report == null) "Security check could not run." else null,
                )
            }
        }
    }

    // ----------------------------------------------------------------- network

    fun refreshNetworkDetails() {
        mutate { it.copy(locationPermission = locationGranted()) }
        viewModelScope.launch {
            val details = runCatching { NetworkScanner.details(ctx) }.getOrNull()
            val current = _state.value.networkReport
            val updated = when {
                details == null -> current
                current == null -> NetworkReport(
                    wifi = details,
                    devices = emptyList(),
                    scannedAtMs = System.currentTimeMillis(),
                    probedHosts = 0,
                    note = "Tap “Scan my network” to look for the phones, computers and smart " +
                        "devices sharing your Wi-Fi.",
                )
                else -> current.copy(wifi = details)
            }
            mutate { it.copy(networkReport = updated) }
        }
    }

    fun scanNetwork() {
        if (_state.value.networkBusy) return
        mutate {
            it.copy(
                networkBusy = true,
                networkProgress = 0 to 0,
                screen = Screen.NETWORK,
            )
        }
        viewModelScope.launch {
            val report = runCatching {
                NetworkScanner.sweep(ctx) { done, total ->
                    mutate { it.copy(networkProgress = done to total) }
                }
            }.getOrNull()
            mutate {
                it.copy(
                    networkBusy = false,
                    networkProgress = null,
                    networkReport = report ?: it.networkReport,
                    message = when {
                        report == null -> "The network scan stopped unexpectedly."
                        report.devices.isEmpty() -> "No devices answered. Some routers block " +
                            "device-to-device traffic; phones also stop answering while asleep."
                        else -> null
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------------- AI

    fun selectAiProvider(provider: AiProvider) {
        val updated = _state.value.aiConfig.copy(provider = provider)
        AiSettings.save(ctx, updated)
        mutate { it.copy(aiConfig = updated, aiTestResult = null) }
    }

    fun setAiIncludeAppNames(value: Boolean) {
        val updated = _state.value.aiConfig.copy(includeAppNames = value)
        AiSettings.save(ctx, updated)
        mutate { it.copy(aiConfig = updated) }
    }

    fun setAiIncludeNetwork(value: Boolean) {
        val updated = _state.value.aiConfig.copy(includeNetwork = value)
        AiSettings.save(ctx, updated)
        mutate { it.copy(aiConfig = updated) }
    }

    fun saveAiConfig(apiKey: String, model: String, baseUrl: String) {
        val updated = _state.value.aiConfig.copy(
            apiKey = apiKey.trim(),
            model = model.trim(),
            baseUrl = baseUrl.trim(),
        )
        AiSettings.save(ctx, updated)
        mutate { it.copy(aiConfig = updated, message = "AI settings saved") }
    }

    fun testAiConnection() {
        val config = _state.value.aiConfig
        mutate { it.copy(aiTestBusy = true, aiTestResult = null) }
        viewModelScope.launch {
            val result = AiClient.test(config)
            mutate {
                it.copy(
                    aiTestBusy = false,
                    aiTestResult = if (result.ok) {
                        "OK — ${result.providerLabel} answered."
                    } else {
                        result.error ?: "No answer."
                    },
                )
            }
        }
    }

    /** Builds the report and asks the chosen provider. Everything local happens first. */
    fun runAiAnalysis() {
        val s = _state.value
        if (s.aiBusy) return
        mutate {
            it.copy(
                aiBusy = true,
                aiError = null,
                aiAnswer = null,
                screen = Screen.AI_REPORT,
            )
        }
        viewModelScope.launch {
            // Fill in whatever the report needs but has not been collected yet.
            val health = _state.value.health ?: runCatching { DeviceHealthReader.read(ctx) }
                .getOrNull()?.also { snapshot -> mutate { it.copy(health = snapshot) } }
            val apps = _state.value.appInventory
                ?: runCatching { AppInventoryLoader.load(ctx, includeSystem = true) }
                    .getOrNull()?.also { report -> mutate { it.copy(appInventory = report) } }
            val security = _state.value.securityReport
                ?: runCatching { SecurityScanner.scan(ctx, apps?.rows.orEmpty()) }
                    .getOrNull()?.also { report -> mutate { it.copy(securityReport = report) } }

            val config = _state.value.aiConfig
            val prompt = AiReport.build(
                config = config,
                health = health,
                apps = apps,
                security = security,
                network = _state.value.networkReport,
                storage = _state.value.storage,
            )
            mutate { it.copy(aiPromptPreview = prompt) }

            val result = AiClient.ask(
                config = config,
                systemPrompt = AiReport.systemPrompt(),
                userPrompt = prompt,
            )
            mutate {
                it.copy(
                    aiBusy = false,
                    aiAnswer = if (result.ok) result.text else null,
                    aiError = if (result.ok) null else result.error,
                    aiUsedProvider = result.providerLabel,
                )
            }
        }
    }

    fun openAiSettings() = mutate { it.copy(screen = Screen.AI_SETTINGS) }

    /** Asks the provider for the model list ("Load models" button). */
    fun loadAiModels() {
        if (_state.value.aiModelsBusy) return
        mutate { it.copy(aiModelsBusy = true, aiModelsError = null) }
        viewModelScope.launch {
            val config = _state.value.aiConfig
            val (models, error) = AiClient.listModels(config)
            mutate {
                it.copy(
                    aiModelsBusy = false,
                    aiModels = models.distinct().sorted(),
                    aiModelsError = error ?: if (models.isEmpty()) "The provider returned no models." else null,
                    message = if (models.isNotEmpty()) "${models.size} models available" else null,
                )
            }
        }
    }

    /**
     * Second opinion on the network list: the AI gets the same facts the screen shows
     * (ports, names, MAC vendors) and is asked what each device probably is.
     */
    fun identifyDevicesWithAi() {
        val report = _state.value.networkReport ?: return
        val config = _state.value.aiConfig
        if (!config.ready) {
            mutate { it.copy(message = "Set up an AI provider first.") }
            return
        }
        if (report.devices.isEmpty()) {
            mutate { it.copy(message = "Scan the network first.") }
            return
        }
        mutate { it.copy(aiDeviceBusy = true, aiDeviceResult = null) }
        viewModelScope.launch {
            val listing = buildString {
                appendLine("Wi-Fi network: \"${report.wifi.ssid ?: "name hidden"}\"")
                appendLine("Devices that answered (ip | hostname | MAC vendor | open ports | CleanSweep guess):")
                report.devices.forEach { device ->
                    appendLine(
                        "- ${device.ip}" +
                            (if (device.isSelf) " (this phone)" else "") +
                            (if (device.isGateway) " (router)" else "") +
                            " | ${device.hostname ?: "-"} | ${device.vendor ?: "-"} | " +
                            "${device.openPorts.joinToString(",").ifBlank { "-" }} | " +
                            device.identity.type
                    )
                }
            }
            val result = AiClient.ask(
                config = config,
                systemPrompt = """
                    You identify devices on a home Wi-Fi network from the evidence given.
                    For each line say what the device probably is (phone, laptop, desktop, tablet,
                    smart TV, IP camera, printer, router, NAS, smart-home device) and how confident
                    you are, and what else would confirm it. Never invent a brand the evidence does
                    not support. Then add a short section on anything that looks unusual for a home
                    network. Keep it under 300 words, bullet points, plain language.
                """.trimIndent(),
                userPrompt = listing,
            )
            mutate {
                it.copy(
                    aiDeviceBusy = false,
                    aiDeviceResult = if (result.ok) result.text else result.error,
                )
            }
        }
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
