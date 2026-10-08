package com.universalrp.cleansweep

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
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
import com.universalrp.cleansweep.ai.WebLookup
import com.universalrp.cleansweep.data.AppCacheInfo
import com.universalrp.cleansweep.data.AppCacheRepo
import com.universalrp.cleansweep.data.AppInventoryLoader
import com.universalrp.cleansweep.data.AppInventoryReport
import com.universalrp.cleansweep.data.AppRow
import com.universalrp.cleansweep.data.BazaarVerdict
import com.universalrp.cleansweep.data.DeviceHealthReader
import com.universalrp.cleansweep.data.MalwareCheck
import com.universalrp.cleansweep.data.MalwareHit
import com.universalrp.cleansweep.data.MalwareReport
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.DataUsageReport
import com.universalrp.cleansweep.data.HealthSnapshot
import com.universalrp.cleansweep.data.IpInfo
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.MobileNet
import com.universalrp.cleansweep.data.MobileSnapshot
import com.universalrp.cleansweep.data.PingStats
import com.universalrp.cleansweep.data.SpeedMeter
import com.universalrp.cleansweep.data.SpeedProgress
import com.universalrp.cleansweep.data.SpeedResult
import com.universalrp.cleansweep.data.UsageStats
import com.universalrp.cleansweep.data.tr
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
import com.universalrp.cleansweep.notify.ChargeMonitorService
import com.universalrp.cleansweep.notify.StatusPill
import com.universalrp.cleansweep.voice.Announcer
import com.universalrp.cleansweep.widget.CleanSweepWidget
import com.universalrp.cleansweep.work.HealthWatchWorker
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    HEALTH, APPS, SECURITY, NETWORK, MOBILE, AI_SETTINGS, AI_REPORT, VOICE
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
    // Status-bar watt reading: CleanSweep draws it itself (needs "Display over other apps")
    val statusPill: Boolean = false,
    val statusPillAllowed: Boolean = false,
    // True while the user is placing the status-bar reading with their finger
    val pillDragging: Boolean = false,
    // Wi-Fi permission (Nearby-devices on Android 13+, Location below)
    val wifiPermission: Boolean = false,
    // Language (English / Tamil) — the whole menu follows it
    val lang: AppLang = AppLang.EN,
    // Mobile network, latency, speed test and data usage
    val mobile: MobileSnapshot? = null,
    val mobileBusy: Boolean = false,
    val pingStats: List<PingStats> = emptyList(),
    val pingBusy: Boolean = false,
    val pingProgress: Pair<Int, Int>? = null,
    val ipInfo: IpInfo? = null,
    val ipBusy: Boolean = false,
    val speedBusy: Boolean = false,
    val speedProgress: SpeedProgress? = null,
    val speedResult: SpeedResult? = null,
    val speedUploadResult: SpeedResult? = null,
    // 0 = the user has not picked a size yet; the screen asks instead of guessing
    val speedSizeMb: Int = 0,
    val speedIncludeUpload: Boolean = false,
    val persistentMonitor: Boolean = false,
    val speedError: String? = null,
    val usage: DataUsageReport? = null,
    val usageBusy: Boolean = false,
    // Which attempt the AI is on, so a busy provider is explained instead of looking frozen
    val aiAttempt: Int = 0,
    val aiAttempts: Int = 0,
    // Which category a quick action is scanning, so screens can say "Duplicates only"
    val scanScope: String? = null,
    // Which AI answers right now, and whether it really is reachable
    val aiEngineLabel: String = "",
    val aiStatusText: String? = null,
    val aiStatusOk: Boolean = false,
    val aiStatusChecking: Boolean = false,
    val aiFallbackOffered: Boolean = false,
    val aiFallbackNote: String? = null,
    val aiUsedModel: String = "",
    val aiUsedMs: Long = 0L,
    // ---------------------------------------------------------------- voice & daily watch
    /** The master voice switch (Settings → Voice). */
    val voiceOn: Boolean = true,
    val voiceLouder: Boolean = false,
    /** v2.6: use the phone's natural cloud voice instead of the robotic offline one. */
    val onlineVoice: Boolean = true,
    val voiceQuietStart: Int = Announcer.DEFAULT_QUIET_START,
    val voiceQuietEnd: Int = Announcer.DEFAULT_QUIET_END,
    val voiceBatteryLow: Boolean = true,
    val voiceBatteryFull: Boolean = true,
    val voiceOverheat: Boolean = true,
    val voiceCharging: Boolean = true,
    val voiceDaily: Boolean = true,
    val voiceAnswers: Boolean = true,
    val voiceStorageLow: Boolean = true,
    val voiceUnplugged: Boolean = true,
    val voiceChargerIdle: Boolean = true,
    val voiceBatteryHealth: Boolean = true,
    /** Off by default: a phone that hops between Wi-Fi and data must not narrate it. */
    val voiceNetworkChange: Boolean = false,
    val voiceNewDevice: Boolean = true,
    val voiceUnplugStart: Int = Announcer.DEFAULT_UNPLUG_START,
    /** MACs seen on the last network scan, so a genuinely new device can be announced. */
    val knownNetworkMacs: Set<String> = emptySet(),
    val dailyScanOn: Boolean = true,
    val dailyHour: Int = Announcer.DEFAULT_DAILY_HOUR,
    /** What the last daily brief said — shown on the Home screen. */
    val lastBrief: String = "",
    /** "auto" | "en" | "ta" — the language the AI answers in. */
    val aiAnswerLanguage: String = "auto",
    /** The last crash, kept on the phone and shown in About (v2.6). Empty when clean. */
    val lastCrash: String = "",
    /** The last thing the offline engine could not answer, kept for "Ask the AI". */
    val assistantPendingQuestion: String = "",
    /** v2.7: the assistant fetches live web snippets before asking the model. */
    val aiWebLookup: Boolean = true,
    // v2.7: file-hash malware check (MalwareBazaar always, VirusTotal with a free key)
    val malwareBusy: Boolean = false,
    val malwareProgress: Pair<Int, Int>? = null,
    val malwareReport: MalwareReport? = null,
    val malwareIncludeSystem: Boolean = false,
    val vtKeySaved: Boolean = false,
    val tamilInfoItems: List<com.universalrp.cleansweep.data.TamilInfoStripRepo.Item> = emptyList(),
    val tamilInfoVisible: Boolean = true,
    val tamilInfoPaused: Boolean = false,
    val autoWifiScanOnOpen: Boolean = true,
    val bgWifiScanEnabled: Boolean = true,
    val verifiedWifiMacs: Set<String> = emptySet(),
    val customDeviceNames: Map<String, String> = emptyMap(),
    val appTrackerReport: com.universalrp.cleansweep.data.AppNetworkTracker.TrackerReport? = null,
    val appTrackerBusy: Boolean = false,
    val appTrackerVpnActive: Boolean = false,
    val appTrackerVpnMode: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    val settingsRepo = SettingsRepo(ctx)
    private val engine = ScanEngine(ctx)
    private val deleter = JunkDeleter(ctx)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var scanJob: Job? = null
    private var speedJob: Job? = null
    private var malwareJob: Job? = null
    private var currentSettings = ScanSettings()
    /** Screens the user actually visited, so the back button can walk them again. */
    private val navStack = ArrayDeque<Screen>()
    /**
     * v2.7: the Home list's scroll position lives here, not in the composable — the Home
     * screen leaves the composition while a scan runs, which used to drop the user back
     * at the top on return (\"back from Temp & junk goes to the top menu\"). The ViewModel
     * outlives the screen switch (and a rotation), so the position survives both.
     */
    val homeListState = LazyListState()
    private var lastAiCheckMs = 0L
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
                // Mirrored into plain prefs for ChargingWatcher / the service.
                prefs.edit().putBoolean(ChargeMonitorService.CARD_KEY, on).apply()
                ChargeMonitorService.sync(ctx, on)
            }
        }
        viewModelScope.launch {
            settingsRepo.persistentMonitor.collect { on ->
                mutate { it.copy(persistentMonitor = on) }
                prefs.edit().putBoolean(ChargeMonitorService.PERSISTENT_KEY, on).apply()
                ChargeMonitorService.sync(ctx, _state.value.chargeMonitor)
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
        viewModelScope.launch {
            settingsRepo.statusPill.collect { on ->
                // The foreground service reads this mirror, so keep it in plain prefs too.
                prefs.edit().putBoolean(ChargeMonitorService.PILL_KEY, on).apply()
                mutate { it.copy(statusPill = on, statusPillAllowed = StatusPill.canDraw(ctx)) }
                if (!on) StatusPill.remove()
                ChargeMonitorService.sync(ctx, _state.value.chargeMonitor)
            }
        }
        // A key that is already saved is enough: fill in its provider and model so the
        // app can use it without another trip to the AI settings screen.
        viewModelScope.launch {
            settingsRepo.lang.collect { id ->
                val lang = AppLang.fromId(id)
                Lang.set(lang)
                prefs.edit().putString(Lang.KEY, lang.id).apply()
                mutate { it.copy(lang = lang) }
            }
        }
        // The speed test remembers the size the user chose last time and nothing else.
        viewModelScope.launch {
            settingsRepo.speedSizeMb.collect { mb ->
                if (mb > 0 && mb != _state.value.speedSizeMb) {
                    mutate { it.copy(speedSizeMb = mb) }
                }
            }
        }
        val loaded = AiSettings.load(ctx)
        val completed = AiSettings.autoComplete(loaded)
        if (completed != loaded) AiSettings.save(ctx, completed)
        mutate {
            it.copy(
                aiConfig = completed,
                aiEngineLabel = completed.engineLabel,
                aiWebLookup = AiSettings.webLookup(ctx),
                vtKeySaved = AiSettings.vtKey(ctx).isNotBlank(),
            )
        }
        // Voice + daily watch: read the switches, and make sure the hourly worker is alive
        // whenever the user wants warnings or the daily brief.
        loadVoiceSettings()
        if (Announcer.enabled(ctx) || Announcer.dailyScanOn(ctx)) HealthWatchWorker.schedule(ctx)
        val savedVerified = prefs.getStringSet("verified_wifi_macs", emptySet()) ?: emptySet()
        val autoWifiScan = prefs.getBoolean("auto_wifi_scan_open", true)
        val bgWifiScan = prefs.getBoolean(com.universalrp.cleansweep.work.WifiScanWorker.KEY_BG_WIFI_SCAN, true)
        val savedNamesRaw = prefs.getString("custom_device_names_json", "{}") ?: "{}"
        val namesMap = try {
            val json = org.json.JSONObject(savedNamesRaw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k -> map[k] = json.getString(k) }
            map
        } catch (e: Exception) {
            emptyMap()
        }
        val vpnPref = prefs.getBoolean("tracker_vpn_mode_pref", false)

        if (bgWifiScan) {
            com.universalrp.cleansweep.work.WifiScanWorker.schedule(ctx)
        }

        mutate {
            it.copy(
                verifiedWifiMacs = savedVerified,
                customDeviceNames = namesMap,
                autoWifiScanOnOpen = autoWifiScan,
                bgWifiScanEnabled = bgWifiScan,
                appTrackerVpnMode = vpnPref,
                appTrackerVpnActive = com.universalrp.cleansweep.vpn.CleanSweepVpnService.isVpnRunning,
            )
        }
        refresh()
        refreshHealth()
        // "Every time you open the app, check that the AI can actually answer."
        refreshAiStatus()
        // Mobile readings are cheap: read them once at start so the card is never empty.
        loadMobile()
        loadTamilInfoStrip()
        if (autoWifiScan && NetworkScanner.wifiPermissionGranted(ctx)) {
            scanNetwork()
        }
    }

    private var widgetTick = 0

    private fun mutate(block: (UiState) -> UiState) {
        val next = _state.updateAndGet { s -> block(s).let { it.copy(revision = it.revision + 1) } }
        // Keep the home-screen widget in step, but not on every single keystroke of state:
        // one refresh every few mutations is plenty for a battery/storage readout.
        //
        // Never while a scan is running, though: the scan reports progress from an IO thread
        // several times a second, and pushing a widget update (binder call + battery and
        // storage reads) into that loop stole the thread the scan was walking the disk on.
        // The widget shows the same numbers, so refreshing it while the app is idle is enough.
        widgetTick++
        if (widgetTick % 8 == 0 && next.progress == null) {
            try {
                CleanSweepWidget.refresh(ctx)
            } catch (t: Throwable) {
                // A missing widget host must never be able to kill the app.
            }
        }
    }

    // ------------------------------------------------------------------ basics

    fun refresh() {
        mutate {
            it.copy(
                hasAllFilesAccess = hasAllFilesAccess(),
                lastBrief = Announcer.lastBrief(ctx),
                aiAnswerLanguage = AiSettings.answerLanguage(ctx),
                lastCrash = CrashLog.last(ctx),
                storage = runCatching { StorageInfoProvider.read() }.getOrNull(),
                usageAccess = AppCacheRepo.hasUsageAccess(ctx),
                legacyStorageOk = legacyStorageGranted(),
                locationPermission = locationGranted(),
                wifiPermission = NetworkScanner.wifiPermissionGranted(ctx),
                statusPillAllowed = StatusPill.canDraw(ctx),
            )
        }
        // Coming back from the AI settings screen (or any other) re-tests the provider,
        // but at most every 15 minutes so the app is not chatty.
        if (System.currentTimeMillis() - lastAiCheckMs > 15 * 60_000L) refreshAiStatus()
        if (_state.value.screen == Screen.APP_CACHE && _state.value.usageAccess) {
            loadAppCaches()
        }
        // v2.7: coming back from Android's app-info page (where permissions are revoked)
        // must re-read the app list and the security findings — the old build kept showing
        // the revoked permission as held until the screen was opened again by hand.
        // refresh() runs on every ON_RESUME, and both loaders no-op while already busy.
        if (_state.value.screen == Screen.APPS) {
            loadAppInventory()
        }
        if (_state.value.screen == Screen.SECURITY) {
            loadSecurity()
        }
    }

    /**
     * Opens the scanner straight from the home-screen widget's "Clean" button. Android does
     * not allow a widget tap to start the scan itself, so the app does it the moment it is
     * in front — otherwise the screen sat at "Starting…" with nothing running, which is
     * exactly what the user saw on the phone.
     */
    fun openScanner() {
        if (scanJob?.isActive == true) {
            mutate { it.copy(screen = Screen.SCANNING) }
            return
        }
        startScan()
    }

    fun navigate(screen: Screen) {
        val current = _state.value.screen
        if (screen != current && current != Screen.HOME && current != Screen.SCANNING) {
            navStack.addLast(current)
            while (navStack.size > 12) navStack.removeFirst()
        }
        mutate { it.copy(screen = screen) }
    }

    /**
     * The system back button / gesture. The build the user tested closed the whole app from
     * every screen; now back walks the screens that were actually visited and only leaves
     * the app from Home. Back from a running scan cancels the scan.
     */
    fun goBack() {
        val current = _state.value.screen
        if (current == Screen.SCANNING) {
            cancelScan()
            return
        }
        if (current == Screen.HOME) return
        while (navStack.isNotEmpty()) {
            val previous = navStack.removeLast()
            if (previous == Screen.SCANNING) continue
            if (previous == Screen.RESULTS && _state.value.report == null) continue
            mutate { it.copy(screen = previous) }
            return
        }
        // Screens reached straight from an action (AI report from Phone health, the AI
        // settings from a card) still get a sensible parent instead of exiting the app.
        val parent = when (current) {
            Screen.AI_REPORT -> Screen.HEALTH
            Screen.AI_SETTINGS -> Screen.SETTINGS
            Screen.VOICE -> Screen.SETTINGS
            Screen.MOBILE -> Screen.HOME
            else -> Screen.HOME
        }
        mutate { it.copy(screen = parent) }
    }

    fun requestAllFilesAccess() = StorageAccess.requestAllFilesAccess(ctx)

    fun dismissMessage() = mutate { it.copy(message = null) }

    /** Clears the stored crash report once the user has read it. */
    fun clearCrashReport() {
        CrashLog.clear(ctx)
        mutate { it.copy(lastCrash = "") }
    }

    /** Shows a one-line message in the snackbar — used by the voice buttons. */
    fun notifyMessage(text: String) = mutate { it.copy(message = text) }

    // ------------------------------------------------------------------ scan

    /**
     * @param kinds when set, only this category is hunted for. The quick tiles pass their
     *   own category so "Duplicates" scans for duplicates instead of everything.
     */
    fun startScan(kinds: Set<JunkKind>? = null, scopeLabel: String? = null) {
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
        mutate { it.copy(screen = Screen.SCANNING, progress = null, scanScope = scopeLabel) }
        val root = Environment.getExternalStorageDirectory()
        val cfg = currentSettings
        scanJob = viewModelScope.launch {
            try {
                val report = engine.scan(root, cfg, kinds) { p ->
                    try {
                        mutate { it.copy(progress = p) }
                    } catch (t: Throwable) {
                        // A repaint problem is not a reason to abandon a scan.
                    }
                }
                mutate { it.copy(screen = Screen.RESULTS, report = report, progress = null) }
            } catch (e: CancellationException) {
                mutate { it.copy(screen = Screen.HOME, progress = null) }
            } catch (t: Throwable) {
                // Catching Throwable and not just Exception is deliberate: on a very full
                // phone a scan used to end with an out-of-memory error, which is an Error and
                // not an Exception — so it escaped this handler and MIUI showed "CleanSweep
                // keeps stopping". Now the app stays open and says what happened.
                val reason = when (t) {
                    is OutOfMemoryError ->
                        "The phone ran out of memory while reading the storage. Close a few " +
                            "apps and scan again — nothing was deleted."
                    else -> "Scan failed: ${t.message ?: t.javaClass.simpleName}"
                }
                mutate {
                    it.copy(screen = Screen.HOME, progress = null, message = reason)
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        mutate { it.copy(screen = Screen.HOME, progress = null, scanScope = null) }
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

    /** "Select all" / "Clear" for the whole result list. */
    fun toggleAll(selected: Boolean) {
        _state.value.report?.categories?.forEach { it.setAll(selected) }
        mutate { it }
    }

    /** How many items are ticked right now (used by the Select all button). */
    fun allSelected(): Boolean {
        val report = _state.value.report ?: return false
        val files = report.categories.flatMap { it.files }
        return files.isNotEmpty() && files.all { it.selected }
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

    fun askAssistant(question: String, forceAi: Boolean = false) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.assistantTyping) return
        val userMsg = AssistantMessage(fromUser = true, text = q)
        mutate { it.copy(assistantMessages = it.assistantMessages + userMsg, assistantTyping = true) }
        viewModelScope.launch {
            val state = _state.value
            if (state.aiConfig.ready && (forceAi || state.assistantOnline)) {
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
                // v2.7: live web grounding. Providers other than Google cannot browse by
                // themselves, so without this a question about today is answered from stale
                // training memory. The lookup below fetches fresh keyless snippets first and
                // they travel with the question; the model is told to quote them (with the
                // source) for anything time-sensitive instead of guessing.
                val today = try {
                    java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH)
                        .format(java.util.Date())
                } catch (e: Exception) {
                    ""
                }
                var liveMeta = ""
                var liveSources = emptyList<String>()
                var officeText = ""
                var fetchedSnippets = emptyList<WebLookup.Snippet>()
                var liveBlock = if (today.isNotBlank()) "Today's date is $today." else ""
                val isGreeting = WebLookup.isGreetingOrSmallTalk(q)
                if (state.aiWebLookup && !isGreeting) {
                    val lookup = runCatching { WebLookup.lookup(q) }.getOrNull()
                    val snippets = lookup?.snippets.orEmpty()
                    fetchedSnippets = snippets
                    // v2.11: the office lead doubles as the verification source.
                    officeText = lookup?.office?.text.orEmpty()
                    if (snippets.isNotEmpty()) {
                        liveBlock = "Live web results, fetched just now" +
                            (if (today.isNotBlank()) " ($today)" else "") +
                            " — for anything time-sensitive, base the answer on these and " +
                            "name the source:\n" + snippets.joinToString("\n") { snippet ->
                            "- (${snippet.source}) ${snippet.text}" +
                                (if (snippet.url.isNotBlank()) " [${snippet.url}]" else "")
                        }
                        liveMeta = " · live web (${snippets.size})"
                        // v2.8: the user sees the same evidence the model saw, so a wrong
                        // answer can be checked instead of trusted.
                        liveSources = snippets.take(3).map { snippet ->
                            "• (${snippet.source}) ${ellipsize(snippet.text, 220)}" +
                                shortSourceUrl(snippet.url)
                        }
                    } else if (today.isNotBlank()) {
                        liveBlock = "Today's date is $today. No live web results were found " +
                            "for this question, so say in one short sentence when an answer " +
                            "may be out of date instead of stating it as current fact."
                    }
                }
                val (result, fallbackNote, elapsedMs) = askWithFallback(
                    // v2.4: the assistant answers *any* question — general knowledge, how-to,
                    // maths, whatever the user types. The phone facts below are context it may
                    // use, not a fence: the user asked for an assistant that is not restricted.
                    systemPrompt = "You are the assistant inside CleanSweep, an Android phone-care " +
                        "app. Answer any question the user asks, on any topic, helpfully and " +
                        "accurately. Use the phone facts provided when the question is about " +
                        "this phone, storage, battery or the app; otherwise just answer the " +
                        "question on its own merits. " +
                        "If you have a web-search tool available, use it for anything that " +
                        "changes from day to day — today's news, prices, scores, weather, " +
                        "election results, what version of something is current — and say where " +
                        "the information came from. If you have no way to look something up, " +
                        "say that in one short sentence and then give the best answer you have " +
                        "from what you know; never refuse the question and never say you are " +
                        "not allowed to discuss a topic. " +
                        "Live web results may be attached to the question. They are fresher " +
                        "than your own memory, so for anything time-sensitive treat them as " +
                        "the authority: when they settle the question, quote the decisive " +
                        "sentence briefly and name its source; when they do not settle it, " +
                        "say \"the live lookup did not settle this\" in one short sentence " +
                        "and mark the rest as possibly out of date. \"Current\" and " +
                        "\"incumbent\" always mean as of today's date above. If the " +
                        "evidence says someone \"served\" from one year to another year " +
                        "that has already passed, that person is FORMER, not current — " +
                        "never call them the current holder. Never present " +
                        "an answer from memory as if it came from the live results. " +
                        answerLanguageRule() +
                        " Keep it under 250 words, plain language, no markdown headings.",
                    userPrompt = "$facts\n$liveBlock\nQuestion from the user: $q",
                    allowSearch = true,
                )
                // Apply the same evidence gate to provider responses and offline fallbacks.
                val rawAnswer = if (result.ok) result.text else
                    "I could not reach the AI provider (${result.error ?: "unknown error"}).\n\n" +
                        Assistant.answer(q, context, state.assistantVerbose).text
                val answerText = if (com.universalrp.cleansweep.ai.NewsLookup.applies(q))
                    com.universalrp.cleansweep.ai.NewsLookup.answer(fetchedSnippets)
                else com.universalrp.cleansweep.ai.OfficeEvidence.answer(q, rawAnswer, officeText)
                val reply = AssistantMessage(
                    fromUser = false,
                    text = answerText,
                    // The bubble says who answered and which model — no mystery AI.
                    meta = if (result.ok) {
                        answerMeta(result, elapsedMs, fallbackNote) + liveMeta
                    } else {
                        "On-device engine · ${state.aiConfig.engineLabel} did not answer"
                    },
                    // Evidence is retained even when the provider failed: the office
                    // gate can still answer from a successfully fetched article.
                    sources = liveSources,
                )
                mutate { it.copy(assistantMessages = it.assistantMessages + reply, assistantTyping = false) }
                if (result.ok) speakAnswer(answerText)
                return@launch
            }
            // The offline engine first. When it cannot answer *and* the user has an AI
            // instead of a dead end the bubble offers to ask the real AI (v2.4).
            val local = Assistant.answer(q, assistantContext(), _state.value.assistantVerbose)
            if (local.meta == null && local.action == AssistantAction.NONE &&
                _state.value.aiConfig.ready
            ) {
                mutate { it.copy(assistantPendingQuestion = q, screen = Screen.ASSISTANT) }
                val prompt = Assistant.offlineFallback(q)
                    .copy(meta = "On-device engine · tap to ask the AI")
                mutate {
                    it.copy(assistantMessages = it.assistantMessages + prompt, assistantTyping = false)
                }
                return@launch
            }
            // A short beat so the reply feels like a considered answer rather than
            // a canned string — the whole engine still runs locally and instantly.
            delay(420)
            val reply = local.copy(meta = "On-device engine · works with no internet")
            mutate {
                it.copy(
                    assistantMessages = it.assistantMessages + reply,
                    assistantTyping = false,
                    assistantPendingQuestion = "",
                )
            }
            speakAnswer(reply.text)
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
            AssistantAction.ASK_AI -> askPendingWithAi()
            AssistantAction.NONE -> Unit
        }
    }

    fun clearAssistant() {
        val msg = Assistant.welcome(assistantContext(), _state.value.assistantVerbose)
        mutate { it.copy(assistantMessages = listOf(msg), assistantTyping = false) }
    }

    // ================================================================ voice & daily watch

    /**
     * Speaks an assistant answer when the user asked for spoken replies. Long answers are cut
     * to the useful part — nobody wants two minutes of bullet points read to them.
     *
     * @param forced the per-answer "Read aloud" tap: an explicit request, honoured even when
     *   automatic voice replies are muted (only the master voice switch still gates it).
     */
    fun speakAnswer(text: String, forced: Boolean = false) {
        val s = _state.value
        if (!s.voiceOn) return
        if (!forced && !s.voiceAnswers) return
        val short = Announcer.shorten(text, 420)
        if (short.isBlank()) return
        // Read it in the language it is actually written in, not the menu language: an
        // English answer should not be handed to a Tamil voice.
        Announcer.speakNow(ctx, short, languageOf(short))
    }

    /** True when the text is mostly Tamil script. */
    private fun isTamilText(text: String): Boolean {
        var tamil = 0
        var latin = 0
        for (ch in text) {
            when {
                ch.code in 0x0B80..0x0BFF -> tamil++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        return tamil > 0 && tamil >= latin
    }

    private fun languageOf(text: String): AppLang = if (isTamilText(text)) AppLang.TA else AppLang.EN

    /** The sentence handed to the model that decides the answer language. */
    private fun answerLanguageRule(): String = when (AiSettings.answerLanguage(ctx)) {
        "ta" -> "Always reply in Tamil, whatever language the question is written in."
        "en" -> "Always reply in English, whatever language the question is written in."
        else -> "Reply in the language the user wrote in."
    }

    /**
     * A source URL shortened for the bubble (\" (en.wikipedia.org/wiki/…)\"),
     * or an empty string when there is nothing worth showing.
     */
    private fun shortSourceUrl(url: String): String {
        val short = url.removePrefix("https://").removePrefix("http://")
            .substringBefore("#").trim().take(90)
        return if (short.isBlank()) "" else " ($short)"
    }

    /** Cuts text at a word boundary instead of mid-word ("...aft (en.wiki...)"). */
    private fun ellipsize(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.take(max).substringBeforeLast(' ')
        val kept = if (cut.length < max - 40) text.take(max) else cut
        return kept.trimEnd(',', ';', ':', ' ') + "…"
    }

    fun setAnswerLanguage(value: String) {
        AiSettings.setAnswerLanguage(ctx, value)
        mutate { it.copy(aiAnswerLanguage = value) }
    }

    /** The "hear the voice" button in Settings: always allowed, ignores quiet hours. */
    fun testVoice() {
        val tamil = Lang.current == AppLang.TA
        val english = "Hello. This is CleanSweep. I will tell you when your battery is low or full, " +
            "when your phone is running hot, and I will read out the daily brief."
        val tamilText = "வணக்கம். இது சுத்தம் செய்பவர். பேட்டரி குறையும்போது, முழுவதும் சார்ஜ் ஆகும்போது, " +
            "போன் சூடாகும்போது நான் சொல்வேன்; தினசரி அறிக்கையையும் படித்துக் காட்டுவேன்."
        val text = if (tamil && Announcer.canSpeak(ctx)) tamilText else english
        val spoke = Announcer.speakNow(ctx, text)
        mutate {
            it.copy(
                message = if (spoke) {
                    tr("Speaking… if you hear nothing, check that a text-to-speech engine is installed.")
                } else {
                    tr("No text-to-speech engine is available on this phone.")
                },
            )
        }
    }

    /** The switch for the natural (network) voice: see Announcer.setOnlineVoice. */
    fun setOnlineVoice(on: Boolean) {
        Announcer.setOnlineVoice(ctx, on)
        mutate { it.copy(onlineVoice = on) }
    }

    fun setLouderVoice(on: Boolean) {
        Announcer.setLouderVoice(ctx, on)
        mutate { it.copy(voiceLouder = on) }
    }

    fun setVoiceOn(on: Boolean) {
        Announcer.setEnabled(ctx, on)
        if (on) HealthWatchWorker.schedule(ctx) else HealthWatchWorker.cancel(ctx)
        mutate { it.copy(voiceOn = on) }
    }

    fun setVoiceEvent(event: Announcer.Event, on: Boolean) {
        Announcer.setAllows(ctx, event, on)
        // v2.7: muting the answers is also the Stop button — anything being read out halts
        // at once instead of finishing the sentence.
        if (event == Announcer.Event.ANSWER && !on) Announcer.stop()
        // Turning any voice event on means the hourly watch must be alive; the charging
        // announcement additionally needs the charging monitor, which reads the same switches.
        if (on) HealthWatchWorker.schedule(ctx)
        mutate {
            it.copy(
                voiceBatteryLow = Announcer.allows(ctx, Announcer.Event.BATTERY_LOW),
                voiceBatteryFull = Announcer.allows(ctx, Announcer.Event.BATTERY_FULL),
                voiceOverheat = Announcer.allows(ctx, Announcer.Event.OVERHEAT),
                voiceCharging = Announcer.allows(ctx, Announcer.Event.CHARGING),
                voiceDaily = Announcer.allows(ctx, Announcer.Event.DAILY),
                voiceAnswers = Announcer.allows(ctx, Announcer.Event.ANSWER),
                voiceStorageLow = Announcer.allows(ctx, Announcer.Event.STORAGE_LOW),
                voiceUnplugged = Announcer.allows(ctx, Announcer.Event.UNPLUGGED_EARLY),
                voiceChargerIdle = Announcer.allows(ctx, Announcer.Event.CHARGER_IDLE),
                voiceBatteryHealth = Announcer.allows(ctx, Announcer.Event.BATTERY_HEALTH),
                voiceNetworkChange = Announcer.allows(ctx, Announcer.Event.NETWORK_CHANGE),
                voiceNewDevice = Announcer.allows(ctx, Announcer.Event.NEW_DEVICE),
            )
        }
    }

    fun setUnplugStartHour(hour: Int) {
        Announcer.setUnplugStartHour(ctx, hour)
        mutate { it.copy(voiceUnplugStart = Announcer.unplugStartHour(ctx)) }
    }

    /**
     * The AI answer language: "auto" follows what the user types, "en"/"ta" force one.
     * Deliberately separate from the menu language — the menu may be English while the
     * answers are wanted in Tamil.
     */
    fun answerLanguage(): String = AiSettings.answerLanguage(ctx)

    fun setQuietHours(start: Int, end: Int) {
        Announcer.setQuietHours(ctx, start, end)
        mutate { it.copy(voiceQuietStart = Announcer.quietStart(ctx), voiceQuietEnd = Announcer.quietEnd(ctx)) }
    }

    fun setDailyScanOn(on: Boolean) {
        Announcer.setDailyScanOn(ctx, on)
        if (on) HealthWatchWorker.schedule(ctx) else HealthWatchWorker.cancel(ctx)
        mutate { it.copy(dailyScanOn = on) }
    }

    fun setDailyHour(hour: Int) {
        Announcer.setDailyHour(ctx, hour)
        mutate { it.copy(dailyHour = Announcer.dailyHour(ctx)) }
    }

    /** Runs the daily brief right now, so the user can hear it without waiting for 8 pm. */
    fun runDailyBriefNow() {
        mutate { it.copy(message = tr("Taking today's reading…")) }
        HealthWatchWorker.schedule(ctx)
        viewModelScope.launch {
            // The worker waits for the brief (and possibly a slow AI answer) — never on the
            // main thread, or the app would look frozen while it works.
            val ran = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                HealthWatchWorker.runNow(ctx)
            }
            mutate {
                it.copy(
                    lastBrief = Announcer.lastBrief(ctx),
                    message = if (ran) null else tr("The daily check could not run right now."),
                )
            }
        }
    }

    /** Re-reads everything the voice settings screen shows. */
    private fun loadVoiceSettings() {
        mutate {
            it.copy(
                voiceOn = Announcer.enabled(ctx),
                voiceLouder = Announcer.louderVoice(ctx),
                onlineVoice = Announcer.onlineVoice(ctx),
                voiceQuietStart = Announcer.quietStart(ctx),
                voiceQuietEnd = Announcer.quietEnd(ctx),
                voiceBatteryLow = Announcer.allows(ctx, Announcer.Event.BATTERY_LOW),
                voiceBatteryFull = Announcer.allows(ctx, Announcer.Event.BATTERY_FULL),
                voiceOverheat = Announcer.allows(ctx, Announcer.Event.OVERHEAT),
                voiceCharging = Announcer.allows(ctx, Announcer.Event.CHARGING),
                voiceDaily = Announcer.allows(ctx, Announcer.Event.DAILY),
                voiceAnswers = Announcer.allows(ctx, Announcer.Event.ANSWER),
                voiceStorageLow = Announcer.allows(ctx, Announcer.Event.STORAGE_LOW),
                voiceUnplugged = Announcer.allows(ctx, Announcer.Event.UNPLUGGED_EARLY),
                voiceChargerIdle = Announcer.allows(ctx, Announcer.Event.CHARGER_IDLE),
                voiceBatteryHealth = Announcer.allows(ctx, Announcer.Event.BATTERY_HEALTH),
                voiceNetworkChange = Announcer.allows(ctx, Announcer.Event.NETWORK_CHANGE),
                voiceNewDevice = Announcer.allows(ctx, Announcer.Event.NEW_DEVICE),
                voiceUnplugStart = Announcer.unplugStartHour(ctx),
                dailyScanOn = Announcer.dailyScanOn(ctx),
                dailyHour = Announcer.dailyHour(ctx),
                lastBrief = Announcer.lastBrief(ctx),
            )
        }
    }

    /**
     * The voice button in the assistant. Android's own recogniser turns speech into text —
     * CleanSweep never records or stores audio, it only receives the words the user chose to
     * dictate, exactly like typing them.
     */
    fun voiceInput(text: String) {
        val said = text.trim()
        if (said.isEmpty()) return
        askAssistant(said)
    }

    /** "Ask the AI" on a bubble the offline engine could not answer. */
    fun askPendingWithAi() {
        val q = _state.value.assistantPendingQuestion.ifBlank {
            _state.value.assistantMessages.lastOrNull { it.fromUser }?.text.orEmpty()
        }
        if (q.isBlank()) return
        if (!_state.value.aiConfig.ready) {
            mutate { it.copy(screen = Screen.AI_SETTINGS, message = tr("Add an API key (or pick the free AI) and ask again.")) }
            return
        }
        askAssistant(q, forceAi = true)
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

    /** Android 13+ also accepts Nearby-devices for the Wi-Fi name. */
    fun wifiPermissionGranted(): Boolean = NetworkScanner.wifiPermissionGranted(ctx)

    /** Wi-Fi details need Location below Android 13 and Nearby-devices from 13 up. */
    fun networkPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Fallback for when Android refuses to show the permission dialog again (the user said
     * "don't allow" twice): the app's own settings page is the only place left.
     */
    fun requestNetworkPermission() {
        StorageAccess.openAppInfo(ctx, ctx.packageName)
    }

    /** Re-reads the Wi-Fi name after the permission dialog closes. */
    fun onWifiPermissionResult() {
        mutate { it.copy(wifiPermission = NetworkScanner.wifiPermissionGranted(ctx)) }
        refreshNetworkDetails()
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
        // Mirrored so ChargingWatcher knows whether to start the monitor on its own.
        prefs.edit().putBoolean(ChargeMonitorService.CARD_KEY, enabled).apply()
        ChargeMonitorService.sync(ctx, enabled)
    }

    fun setPersistentMonitor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setPersistentMonitor(enabled) }
        prefs.edit().putBoolean(ChargeMonitorService.PERSISTENT_KEY, enabled).apply()
        ChargeMonitorService.sync(ctx, _state.value.chargeMonitor)
        if (enabled) {
            mutate { it.copy(message = "Persistent battery monitor on. Use notification 'Stop' to end.") }
        } else {
            mutate { it.copy(message = "Persistent battery monitor off.") }
        }
    }

    /**
     * The watt reading in the status bar. Android will not let any app write there without
     * "Display over other apps", so switching this on for the first time opens that page.
     */
    fun setStatusPill(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setStatusPill(enabled) }
        prefs.edit().putBoolean(ChargeMonitorService.PILL_KEY, enabled).apply()
        if (!enabled) StatusPill.remove()
        ChargeMonitorService.sync(ctx, _state.value.chargeMonitor)
        if (enabled && !StatusPill.canDraw(ctx)) {
            mutate {
                it.copy(
                    message = "Allow “Display over other apps” and the watt reading will " +
                        "appear beside the clock while charging.",
                )
            }
            openOverlaySettings()
        } else if (enabled) {
            mutate { it.copy(message = "Watt reading on — it appears while the charger is connected.") }
        }
    }

    fun openOverlaySettings() {
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            StorageAccess.openAppInfo(ctx, ctx.packageName)
        }
    }

    fun refreshStatusPillPermission() =
        mutate { it.copy(statusPillAllowed = StatusPill.canDraw(ctx)) }

    fun setAssistantOnline(enabled: Boolean) {
        if (enabled && !_state.value.aiConfig.ready) {
            mutate {
                it.copy(
                    message = "Online AI needs a provider first — add a free key, or use the " +
                        "free no-key option in AI settings.",
                )
            }
            return
        }
        viewModelScope.launch { settingsRepo.setAssistantOnline(enabled) }
        if (enabled) {
            mutate { it.copy(message = "Online AI on — answers come from ${it.aiConfig.engineLabel}") }
        }
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
            // v2.7: always re-read the installed apps first. The old build reused the cached
            // inventory when it had one, so a permission revoked in Settings kept showing as
            // held until the app was restarted. The fresh inventory is shared with the apps
            // screen, so both always agree.
            val inventory = runCatching { AppInventoryLoader.load(ctx, includeSystem = true) }.getOrNull()
            if (inventory != null) mutate { it.copy(appInventory = inventory) }
            val apps = inventory?.rows ?: _state.value.appInventory?.rows.orEmpty()
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

    // ------------------------------------------------- malware hash check (v2.7)

    /**
     * Hashes each installed APK (SHA-256, on the phone) and looks the hash up in
     * MalwareBazaar — free, no key, and that database holds only confirmed malware, so a
     * hit is a real red flag. A saved VirusTotal key buys a second opinion, but only for
     * apps MalwareBazaar already flagged: the free VirusTotal quota (4/minute, 500/day)
     * would never survive a blind sweep of the whole list.
     *
     * By default only user-installed apps are checked — preinstalled system apps live on
     * the read-only partition and cannot be removed anyway — with a switch for the full
     * sweep. Cancellable; progress is shown per app.
     */
    fun runMalwareScan() {
        if (_state.value.malwareBusy) return
        mutate { it.copy(malwareBusy = true, malwareProgress = 0 to 0, malwareReport = null) }
        malwareJob = viewModelScope.launch {
            try {
                val inventory = _state.value.appInventory
                    ?: runCatching { AppInventoryLoader.load(ctx, includeSystem = true) }.getOrNull()
                if (inventory != null && _state.value.appInventory == null) {
                    mutate { it.copy(appInventory = inventory) }
                }
                val includeSystem = _state.value.malwareIncludeSystem
                val targets = inventory?.rows.orEmpty().filter { includeSystem || !it.isSystem }
                if (targets.isEmpty()) {
                    mutate {
                        it.copy(
                            malwareBusy = false,
                            malwareProgress = null,
                            message = "No apps to check — open Installed apps first.",
                        )
                    }
                    return@launch
                }
                val vtKey = AiSettings.vtKey(ctx).trim()
                val hits = mutableListOf<MalwareHit>()
                var checked = 0
                var skipped = 0
                var vtLookups = 0
                for ((index, app) in targets.withIndex()) {
                    coroutineContext.ensureActive()
                    mutate { it.copy(malwareProgress = index to targets.size) }
                    val hash = app.apkPath?.let { MalwareCheck.sha256OfFile(it) }
                    if (hash == null) {
                        skipped++
                        continue
                    }
                    when (val verdict = MalwareCheck.queryBazaar(hash)) {
                        is BazaarVerdict.Hit -> {
                            var source = "MalwareBazaar"
                            var detail = verdict.signature +
                                (if (verdict.tags.isNotBlank()) " (${verdict.tags})" else "")
                            // Second opinion, spent only where it matters (see the KDoc above).
                            if (vtKey.isNotBlank()) {
                                val vt = MalwareCheck.queryVirusTotal(vtKey, hash)
                                if (vt != null) {
                                    vtLookups++
                                    source = "MalwareBazaar + VirusTotal"
                                    detail += " · VirusTotal: ${vt.malicious}/${vt.total} engines flagged it"
                                }
                            }
                            hits.add(MalwareHit(app.pkg, app.label, source, detail, hash))
                        }
                        else -> Unit
                    }
                    checked++
                }
                coroutineContext.ensureActive()
                val done = MalwareReport(
                    checked = checked,
                    hits = hits.toList(),
                    skipped = skipped,
                    vtLookups = vtLookups,
                    scannedAtMs = System.currentTimeMillis(),
                    // v2.9: the result line states its own scope, so a scan run
                    // before the system-apps toggle was switched on cannot confuse.
                    includeSystem = includeSystem,
                )
                mutate {
                    it.copy(
                        malwareBusy = false,
                        malwareProgress = null,
                        malwareReport = done,
                        message = if (hits.isEmpty()) {
                            "No known malware: ${done.checked} apps checked against MalwareBazaar."
                        } else {
                            "${hits.size} app(s) flagged — details are listed in the card above."
                        },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                mutate { it.copy(malwareBusy = false, malwareProgress = null) }
            }
        }
    }

    fun cancelMalwareScan() {
        malwareJob?.cancel()
        malwareJob = null
        mutate { it.copy(malwareBusy = false, malwareProgress = null) }
    }

    fun setMalwareIncludeSystem(on: Boolean) = mutate { it.copy(malwareIncludeSystem = on) }

    /** Saves the user's own free VirusTotal key (blank clears it). Stored like the AI keys. */
    fun saveVtKey(key: String) {
        AiSettings.setVtKey(ctx, key.trim())
        mutate {
            it.copy(
                vtKeySaved = key.trim().isNotBlank(),
                message = if (key.trim().isNotBlank()) {
                    "VirusTotal key saved — flagged apps now get a second opinion."
                } else {
                    "VirusTotal key removed — MalwareBazaar only."
                },
            )
        }
    }

    /**
     * \"Check this app for malware\" from the app details: MalwareBazaar first, VirusTotal
     * second when a key is saved. The answer comes back as a message, whatever it is.
     */
    fun checkOneApp(pkg: String) {
        viewModelScope.launch {
            val inventory = _state.value.appInventory
                ?: runCatching { AppInventoryLoader.load(ctx, includeSystem = true) }.getOrNull()
            if (inventory != null && _state.value.appInventory == null) {
                mutate { it.copy(appInventory = inventory) }
            }
            val app = inventory?.rows?.firstOrNull { it.pkg == pkg }
            if (app == null) {
                mutate { it.copy(message = "That app is no longer installed.") }
                return@launch
            }
            mutate { it.copy(message = "Checking ${app.label}…") }
            val hash = app.apkPath?.let { MalwareCheck.sha256OfFile(it) }
            if (hash == null) {
                mutate { it.copy(message = "Could not read ${app.label}'s file to hash it.") }
                return@launch
            }
            when (val verdict = MalwareCheck.queryBazaar(hash)) {
                is BazaarVerdict.Hit -> {
                    var detail = "MalwareBazaar flags ${app.label}: ${verdict.signature}"
                    val vtKey = AiSettings.vtKey(ctx).trim()
                    if (vtKey.isNotBlank()) {
                        MalwareCheck.queryVirusTotal(vtKey, hash)?.let { vt ->
                            detail += " (VirusTotal: ${vt.malicious}/${vt.total} engines)"
                        }
                    }
                    mutate { it.copy(message = "$detail — consider uninstalling it.") }
                }
                is BazaarVerdict.Clean -> {
                    mutate { it.copy(message = "${app.label}: no match in MalwareBazaar — not known malware.") }
                }
                is BazaarVerdict.Unknown -> {
                    mutate { it.copy(message = "Could not check ${app.label} (${verdict.reason}).") }
                }
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

            val verified = _state.value.verifiedWifiMacs
            val customNames = _state.value.customDeviceNames
            val updatedReport = report?.let { r ->
                r.copy(devices = r.devices.map { d ->
                    val isV = d.mac != null && d.mac in verified
                    val cName = d.mac?.let { customNames[it] }
                    d.copy(isVerifiedKnown = isV, customName = cName)
                })
            }

            mutate {
                it.copy(
                    networkBusy = false,
                    networkProgress = null,
                    networkReport = updatedReport ?: it.networkReport,
                    message = when {
                        report == null -> "The network scan stopped unexpectedly."
                        report.devices.isEmpty() -> "No devices answered. Some routers block " +
                            "device-to-device traffic; phones also stop answering while asleep."
                        else -> null
                    },
                )
            }
            // New device on the network: the scan just listed them, so anything that was not
            // in the previous scan or verified list is genuinely new. Only spoken when the switch allows it.
            if (report != null && report.devices.isNotEmpty()) {
                val known = _state.value.knownNetworkMacs + _state.value.verifiedWifiMacs
                val fresh = report.devices.filter { it.mac != null && it.mac !in known && !it.isSelf && !it.isGateway }
                if (known.isNotEmpty() && fresh.isNotEmpty()) {
                    Announcer.speakTamil(
                        ctx,
                        if (fresh.size == 1) {
                            "எச்சரிக்கை! உங்கள் வைஃபை நெட்வொர்க்கில் ஒரு புதிய சாதனம் இணைந்துள்ளது."
                        } else {
                            "எச்சரிக்கை! உங்கள் வைஃபை நெட்வொர்க்கில் ${fresh.size} புதிய சாதனங்கள் இணைந்துள்ளன."
                        },
                        Announcer.Event.NEW_DEVICE,
                    )
                }
                mutate {
                    it.copy(
                        knownNetworkMacs = known + report.devices.mapNotNull { device -> device.mac },
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------- AI

    /**
     * Switching provider now restores whatever was saved for that provider — key and model —
     * so moving between Gemini and Groq (or back to the free option) never asks for the same
     * key twice, which is what the user hit in v2.2.
     */
    fun selectAiProvider(provider: AiProvider) {
        val updated = AiSettings.applySaved(ctx, provider)
        AiSettings.save(ctx, updated)
        mutate {
            it.copy(
                aiConfig = updated,
                aiEngineLabel = updated.engineLabel,
                aiTestResult = null,
                aiStatusText = if (updated.apiKey.isNotBlank()) {
                    "Saved ${provider.shortLabel} key restored."
                } else if (provider.needsKey) {
                    "No key saved for ${provider.shortLabel} yet."
                } else {
                    "Free AI needs no key."
                },
                aiStatusOk = false,
            )
        }
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

    /** v2.7: live web snippets before the model answers (DuckDuckGo + Wikipedia, no key). */
    fun setAiWebLookup(on: Boolean) {
        AiSettings.setWebLookup(ctx, on)
        mutate { it.copy(aiWebLookup = on) }
    }

    fun saveAiConfig(apiKey: String, model: String, baseUrl: String) {
        // Per-provider copy first, so the next switch finds it even if the app is closed now.
        AiSettings.saveFor(ctx, _state.value.aiConfig.provider, apiKey.trim(), model.trim())
        val typed = _state.value.aiConfig.copy(
            apiKey = apiKey.trim(),
            model = model.trim(),
            baseUrl = baseUrl.trim(),
        )
        // A pasted key carries its own provider prefix and a model id that works, so the
        // user never has to guess either one — and the assistant switches to it at once.
        val updated = AiSettings.autoComplete(typed)
        AiSettings.save(ctx, updated)
        mutate {
            it.copy(
                aiConfig = updated,
                aiEngineLabel = updated.engineLabel,
                message = if (updated.hasSavedKey) {
                    "${updated.provider.shortLabel} saved — checking it now…"
                } else {
                    "AI settings saved"
                },
            )
        }
        if (updated.ready) {
            if (!_state.value.assistantOnline) setAssistantOnline(true)
            refreshAiStatus(announce = true)
        }
    }

    /**
     * "Is my AI ready?" — the check the app runs every time it opens, as the user asked for.
     * For the free lane it tries the keyless endpoints in turn and remembers the one that
     * answered; for a key it asks the provider for its model list, so a retired model or a
     * refused key is reported in plain words before a question is ever asked.
     */
    fun refreshAiStatus(announce: Boolean = false) {
        val config = _state.value.aiConfig
        lastAiCheckMs = System.currentTimeMillis()
        val label = config.engineLabel
        if (!config.ready) {
            mutate {
                it.copy(
                    aiEngineLabel = label,
                    aiStatusChecking = false,
                    aiStatusOk = false,
                    aiStatusText = "No AI set up yet — add a free key, or tap “Use the free AI”.",
                    aiFallbackOffered = true,
                )
            }
            return
        }
        if (_state.value.aiStatusChecking) return
        mutate { it.copy(aiEngineLabel = label, aiStatusChecking = true, aiStatusText = null) }
        AiClient.attemptListener = { attempt, of, _ ->
            if (of > 1) mutate { it.copy(aiAttempt = attempt, aiAttempts = of) }
        }
        viewModelScope.launch {
            val result = AiClient.ping(config)
            val answered = result.endpointUrl?.let { url ->
                config.copy(freeEndpointUrl = url).engineLabel
            } ?: label
            mutate {
                it.copy(
                    aiStatusChecking = false,
                    aiStatusOk = result.ok,
                    aiStatusText = result.error,
                    aiFallbackOffered = !result.ok,
                    aiEngineLabel = answered,
                    message = when {
                        !announce -> it.message
                        result.ok -> "${result.providerLabel} is ready"
                        else -> null
                    },
                )
            }
            if (result.ok) rememberEndpoint(result)
        }
    }

    /**
     * "If the AI you picked does not work, give me another free one." Switches to the
     * keyless lane, forgets the endpoint that failed and tests again immediately. The saved
     * key stays put, so switching back to Gemini later needs no re-typing.
     */
    fun useAnotherFreeAi() {
        val current = _state.value.aiConfig
        val updated = current.copy(
            provider = AiProvider.FREE,
            model = "",
            freeEndpointUrl = "",
        )
        AiSettings.save(ctx, updated)
        mutate {
            it.copy(
                aiConfig = updated,
                aiEngineLabel = updated.engineLabel,
                aiStatusText = "Trying the free AI lane…",
                aiStatusOk = false,
                message = "Trying the free AI lane (no key needed)…",
            )
        }
        if (!_state.value.assistantOnline) setAssistantOnline(true)
        lastAiCheckMs = 0L
        refreshAiStatus(announce = true)
    }

    /**
     * One place that asks the AI and, if the chosen provider does not answer, quietly tries
     * the free keyless lane before giving up. The caller is told who answered and what went
     * wrong first, so the UI can be honest about it instead of pretending.
     */
    private suspend fun askWithFallback(
        systemPrompt: String,
        userPrompt: String,
        /** Assistant questions may need live information; reports never do. */
        allowSearch: Boolean = false,
    ): Triple<AiClient.Result, String?, Long> {
        val started = System.currentTimeMillis()
        val config = _state.value.aiConfig
        val first = AiClient.ask(config, systemPrompt, userPrompt, allowSearch = allowSearch)
        if (first.ok) {
            rememberEndpoint(first)
            return Triple(first, null, System.currentTimeMillis() - started)
        }
        if (config.provider != AiProvider.FREE) {
            val freeLane = AiConfig(
                provider = AiProvider.FREE,
                freeEndpointUrl = config.freeEndpointUrl,
                includeAppNames = config.includeAppNames,
                includeNetwork = config.includeNetwork,
            )
            val second = AiClient.ask(freeLane, systemPrompt, userPrompt, allowSearch = false)
            if (second.ok) {
                rememberEndpoint(second)
                return Triple(second, first.error, System.currentTimeMillis() - started)
            }
        }
        return Triple(first, null, System.currentTimeMillis() - started)
    }

    /** "Gemini · gemini-2.5-flash · 1.4 s" — printed under every AI answer. */
    private fun answerMeta(
        result: AiClient.Result,
        elapsedMs: Long,
        fallbackNote: String?,
    ): String = buildString {
        append(result.providerLabel)
        if (result.model.isNotBlank()) append(" · ").append(result.model)
        if (elapsedMs > 0L) append(" · ").append("%.1f s".format(elapsedMs / 1000.0))
        if (fallbackNote != null) {
            append("\nYour provider did not answer first (").append(fallbackNote.take(120)).append(")")
        }
    }

    /** Remembers the keyless endpoint that answered so the next request starts there. */
    private fun rememberEndpoint(result: AiClient.Result) {
        val url = result.endpointUrl ?: return
        val current = _state.value.aiConfig
        if (current.freeEndpointUrl == url && current.engineLabel.isNotBlank()) return
        val updated = current.copy(freeEndpointUrl = url)
        AiSettings.save(ctx, updated)
        mutate { it.copy(aiConfig = updated, aiEngineLabel = updated.engineLabel) }
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
        AiClient.attemptListener = { attempt, of, _ ->
            if (of > 1) mutate { it.copy(aiAttempt = attempt, aiAttempts = of) }
        }
        mutate {
            it.copy(
                aiBusy = true,
                aiError = null,
                aiAnswer = null,
                aiFallbackNote = null,
                aiUsedMs = 0L,
                aiAttempt = 1,
                aiAttempts = 1,
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

            val (result, fallbackNote, elapsedMs) = askWithFallback(
                systemPrompt = AiReport.systemPrompt(),
                userPrompt = prompt,
            )
            mutate {
                it.copy(
                    aiBusy = false,
                    aiAnswer = if (result.ok) result.text else null,
                    aiError = if (result.ok) null else result.error,
                    aiUsedProvider = result.providerLabel,
                    aiUsedModel = result.model,
                    aiUsedMs = elapsedMs,
                    aiFallbackNote = fallbackNote,
                    aiStatusOk = result.ok,
                    aiAttempt = 0,
                    aiAttempts = 0,
                )
            }
        }
    }

    fun openAiSettings() = mutate { it.copy(screen = Screen.AI_SETTINGS) }

    // -------------------------------------------------------------- language

    /** English or Tamil. Applied at once: every screen re-reads the table on recomposition. */
    fun setLanguage(lang: AppLang) {
        Lang.set(lang)
        prefs.edit().putString(Lang.KEY, lang.id).apply()
        mutate { it.copy(lang = lang, message = if (lang == AppLang.TA) {
            "மொழி: தமிழ் — ஆப் பெயர் “சுத்தம் செய்பவர்”"
        } else {
            "Language: English"
        }) }
        viewModelScope.launch { settingsRepo.setLang(lang) }
    }

    // ------------------------------------------------- status-bar reading position

    /**
     * Moves the charging-watt reading. Every phone blocks that corner with something
     * different (VoLTE, VPN, carrier name, battery %), so the user decides where it sits.
     */
    fun moveStatusPill(dx: Int, dy: Int) = StatusPill.moveBy(ctx, dx, dy)

    fun resetStatusPill() {
        StatusPill.resetPosition(ctx)
        mutate { it.copy(message = tr("Reading back at the automatic spot (beside the camera).")) }
    }

    /**
     * Finger placement. With it on, the reading follows the finger across the whole screen, so
     * the user can drop it exactly where their phone leaves a gap; with it off the overlay is
     * touch-through again and can never swallow a tap meant for the status bar.
     */
    fun togglePillDrag() {
        val on = !_state.value.pillDragging
        StatusPill.setDraggable(ctx, on)
        mutate {
            it.copy(
                pillDragging = on,
                message = if (on) {
                    tr("Drag mode on — slide the watt reading where you want it, then tap Done.")
                } else {
                    tr("Saved. The reading gets its place back every time you charge.")
                },
            )
        }
    }

    // ------------------------------------------------------ mobile & data (v2.3)

    fun loadMobile() {
        if (_state.value.mobileBusy) return
        mutate { it.copy(mobileBusy = true, screen = _state.value.screen) }
        viewModelScope.launch {
            val snapshot = runCatching { MobileNet.snapshot(ctx) }.getOrNull()
            val usage = runCatching { UsageStats.read(ctx) }.getOrNull()
            mutate {
                it.copy(
                    mobileBusy = false,
                    mobile = snapshot ?: it.mobile,
                    usage = usage ?: it.usage,
                )
            }
        }
    }

    fun phonePermissionGranted(): Boolean = MobileNet.hasPhonePermission(ctx)

    fun phonePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun onPhonePermissionResult() {
        loadMobile()
        refreshNetworkDetails()
    }

    fun openUsageAccess() = AppCacheRepo.openUsageAccessSettings(ctx)

    /** Latency and jitter to three public endpoints — a few kilobytes, never a speed test. */
    fun runPing() {
        if (_state.value.pingBusy) return
        mutate { it.copy(pingBusy = true, pingStats = emptyList(), pingProgress = 0 to 0) }
        viewModelScope.launch {
            val stats = runCatching {
                MobileNet.ping { done, total -> mutate { it.copy(pingProgress = done to total) } }
            }.getOrNull().orEmpty()
            mutate {
                it.copy(
                    pingBusy = false,
                    pingProgress = null,
                    pingStats = stats,
                    message = if (stats.all { s -> s.received == 0 }) {
                        "No ping answers — check the connection."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** Public IP, ISP and city: one small request, only when the button is tapped. */
    fun loadPublicIp() {
        if (_state.value.ipBusy) return
        mutate { it.copy(ipBusy = true) }
        viewModelScope.launch {
            val info = runCatching { MobileNet.publicIp() }.getOrNull()
            mutate { it.copy(ipBusy = false, ipInfo = info) }
        }
    }

    fun setSpeedSize(mb: Int) {
        mutate { it.copy(speedSizeMb = mb, speedResult = null, speedUploadResult = null) }
        viewModelScope.launch { settingsRepo.setSpeedSizeMb(mb) }
    }

    fun setSpeedIncludeUpload(on: Boolean) = mutate { it.copy(speedIncludeUpload = on) }

    /**
     * The real speed test. It downloads exactly the number of megabytes the user chose
     * (1 MB … 100 MB) from Cloudflare's public speed host, so on a metered plan the small
     * sizes are the honest choice and on an unlimited 5G plan 100 MB is what shows the true
     * top speed. The screen warns about data before starting; nothing runs on its own.
     */
    fun startSpeedTest() {
        val state = _state.value
        if (state.speedBusy) return
        if (state.speedSizeMb <= 0) {
            // No size has been chosen yet: ask, never guess — this costs the user's data.
            mutate { it.copy(message = tr("Pick a test size first — that is how much data the test uses.")) }
            return
        }
        val bytes = state.speedSizeMb.toLong() * 1024L * 1024L
        val withUpload = state.speedIncludeUpload
        mutate {
            it.copy(
                speedBusy = true,
                speedError = null,
                speedResult = null,
                speedUploadResult = null,
                speedProgress = SpeedProgress("Starting", 0L, bytes, 0.0, 0L),
            )
        }
        speedJob = viewModelScope.launch {
            val download = SpeedMeter.download(bytes) { progress ->
                mutate { it.copy(speedProgress = progress) }
            }
            mutate {
                it.copy(
                    speedResult = download,
                    speedError = if (download.ok) null else download.error,
                    speedProgress = null,
                )
            }
            if (!download.ok || !withUpload) {
                mutate { it.copy(speedBusy = false) }
                return@launch
            }
            mutate {
                it.copy(
                    speedProgress = SpeedProgress("Starting upload", 0L, bytes, 0.0, 0L),
                )
            }
            val upload = SpeedMeter.upload(bytes) { progress ->
                mutate { it.copy(speedProgress = progress) }
            }
            mutate {
                it.copy(
                    speedBusy = false,
                    speedUploadResult = upload,
                    speedProgress = null,
                    message = if (upload.ok) null else upload.error,
                )
            }
        }
    }

    /** Stops a running speed test (the connection is closed and nothing more is downloaded). */
    fun cancelSpeedTest() {
        speedJob?.cancel()
        speedJob = null
        mutate {
            it.copy(
                speedBusy = false,
                speedProgress = null,
                speedError = "Test stopped — whatever was downloaded is already counted by your plan.",
            )
        }
    }

    fun loadUsage() {
        if (_state.value.usageBusy) return
        mutate { it.copy(usageBusy = true) }
        viewModelScope.launch {
            val usage = runCatching { UsageStats.read(ctx) }.getOrNull()
            mutate { it.copy(usageBusy = false, usage = usage ?: it.usage) }
        }
    }

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
                // The network name is deliberately left out: it is the one detail the user
                // asked never to show, and it is not needed to identify a device.
                appendLine("Wi-Fi network name: not sent (CleanSweep never shares it)")
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
            val (result, fallbackNote, elapsedMs) = askWithFallback(
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
                    aiDeviceResult = if (result.ok) {
                        result.text + "\n\n— " + answerMeta(result, elapsedMs, fallbackNote)
                    } else {
                        result.error
                    },
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

    /** Which categories come pre-ticked after a scan (none, by default). */
    fun setDefaultSelected(kind: JunkKind, on: Boolean) =
        viewModelScope.launch { settingsRepo.setDefaultSelected(kind, on) }

    fun addExclusion(raw: String) =
        viewModelScope.launch { settingsRepo.addExclusion(raw) }

    fun removeExclusion(path: String) =
        viewModelScope.launch { settingsRepo.removeExclusion(path) }

    // -------------------------------------------------------- Tamil info strip

    fun loadTamilInfoStrip(force: Boolean = false) {
        viewModelScope.launch {
            val items = com.universalrp.cleansweep.data.TamilInfoStripRepo.getItems(ctx, force)
            mutate { it.copy(tamilInfoItems = items) }
        }
    }

    fun toggleTamilInfoPause() {
        mutate { it.copy(tamilInfoPaused = !it.tamilInfoPaused) }
    }

    fun toggleTamilInfoVisibility() {
        mutate { it.copy(tamilInfoVisible = !it.tamilInfoVisible) }
    }

    fun speakTamilText(text: String) {
        if (text.isNotBlank()) {
            Announcer.speakTamil(ctx, text, Announcer.Event.TEST)
        }
    }

    // ---------------------------------------------------- Wi-Fi Verified Devices & Auto-Scan

    fun toggleDeviceVerified(mac: String) {
        val current = _state.value.verifiedWifiMacs.toMutableSet()
        if (mac in current) current.remove(mac) else current.add(mac)
        prefs.edit().putStringSet("verified_wifi_macs", current).apply()
        mutate { state ->
            val updatedReport = state.networkReport?.let { r ->
                r.copy(devices = r.devices.map { d ->
                    if (d.mac == mac) d.copy(isVerifiedKnown = mac in current) else d
                })
            }
            state.copy(verifiedWifiMacs = current, networkReport = updatedReport)
        }
    }

    fun setCustomDeviceName(mac: String, name: String) {
        val trimmed = name.trim()
        val current = _state.value.customDeviceNames.toMutableMap()
        if (trimmed.isBlank()) {
            current.remove(mac)
        } else {
            current[mac] = trimmed
        }
        val json = org.json.JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("custom_device_names_json", json.toString()).apply()

        mutate { state ->
            val updatedReport = state.networkReport?.let { r ->
                r.copy(devices = r.devices.map { d ->
                    if (d.mac == mac) d.copy(customName = trimmed.ifBlank { null }) else d
                })
            }
            state.copy(customDeviceNames = current, networkReport = updatedReport)
        }
    }

    fun setAutoWifiScanOnOpen(enabled: Boolean) {
        prefs.edit().putBoolean("auto_wifi_scan_open", enabled).apply()
        mutate { it.copy(autoWifiScanOnOpen = enabled) }
    }

    fun setBgWifiScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(com.universalrp.cleansweep.work.WifiScanWorker.KEY_BG_WIFI_SCAN, enabled).apply()
        if (enabled) {
            com.universalrp.cleansweep.work.WifiScanWorker.schedule(ctx)
        } else {
            com.universalrp.cleansweep.work.WifiScanWorker.cancel(ctx)
        }
        mutate { it.copy(bgWifiScanEnabled = enabled) }
    }

    fun setTrackerVpnMode(preferVpn: Boolean) {
        prefs.edit().putBoolean("tracker_vpn_mode_pref", preferVpn).apply()
        mutate { it.copy(appTrackerVpnMode = preferVpn) }
        if (preferVpn) {
            startTrackerVpn()
        } else {
            stopTrackerVpn()
        }
    }

    fun startTrackerVpn() {
        val intent = android.content.Intent(ctx, com.universalrp.cleansweep.vpn.CleanSweepVpnService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        mutate { it.copy(appTrackerVpnActive = true) }
    }

    fun stopTrackerVpn() {
        com.universalrp.cleansweep.vpn.CleanSweepVpnService.stop(ctx)
        mutate { it.copy(appTrackerVpnActive = false) }
    }

    fun scanAppTrackers() {
        if (_state.value.appTrackerBusy) return
        mutate {
            it.copy(
                appTrackerBusy = true,
                appTrackerVpnActive = com.universalrp.cleansweep.vpn.CleanSweepVpnService.isVpnRunning,
            )
        }
        viewModelScope.launch {
            val report = runCatching {
                com.universalrp.cleansweep.data.AppNetworkTracker.inspectConnections(ctx, _state.value.appTrackerVpnMode)
            }.getOrNull()
            mutate { it.copy(appTrackerBusy = false, appTrackerReport = report) }
        }
    }
}
