package com.universalrp.pulseeq

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalrp.pulseeq.audio.Bands
import com.universalrp.pulseeq.audio.DeviceEq
import com.universalrp.pulseeq.audio.EqCore
import com.universalrp.pulseeq.audio.EqSettings
import com.universalrp.pulseeq.audio.EqStats
import com.universalrp.pulseeq.audio.PlayerEngine
import com.universalrp.pulseeq.audio.Presets
import com.universalrp.pulseeq.service.EqService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EqTab { EQUALIZER, PRESETS, PLAYER, SETTINGS, ABOUT }

data class EqUiState(
    val tab: EqTab = EqTab.EQUALIZER,
    val gains: List<Float> = List(Bands.COUNT) { 0f },
    val preampDb: Float = 0f,
    val presetName: String = Presets.FLAT.name,
    val bypass: Boolean = false,
    // service / reach
    val serviceRunning: Boolean = false,
    val systemEqWanted: Boolean = false,
    val systemEqActive: Boolean = false,
    val deviceBandInfo: String = "Unknown",
    val systemStatus: String = "",
    val cooperating: Boolean = true,
    val cooperatingSessions: Int = 0,
    val batteryOptimized: Boolean = true,
    // leds
    val ledAnimation: Boolean = true,
    val ledBrightness: Float = 1f,
    // player
    val playerState: PlayerEngine.State = PlayerEngine.State.IDLE,
    val playerTitle: String = "",
    val playerError: String? = null,
    val playerPositionMs: Long = 0L,
    val playerDurationMs: Long = 0L,
    val lastUri: String? = null,
    val lastTitle: String? = null,
    // diagnostics
    val engineInfo: String = "",
    val message: String? = null,
)

class EqViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val settings: EqSettings = EqCore.settings(ctx)
    private val player: PlayerEngine = EqCore.player(ctx)

    private val _state = MutableStateFlow(
        EqUiState(
            gains = settings.gains,
            preampDb = settings.preampDb,
            presetName = settings.presetName,
            bypass = settings.bypass,
            systemEqWanted = settings.systemEqWanted,
            cooperating = settings.cooperating,
            ledAnimation = settings.ledAnimation,
            ledBrightness = settings.ledBrightness,
            lastUri = settings.lastUri,
            lastTitle = settings.lastTitle,
        )
    )
    val state: StateFlow<EqUiState> = _state

    init {
        EqCore.refreshFromSettings(ctx)
        refresh()
        player.onStateChanged = { st -> mutate { it.copy(playerState = st) } }
        // A light ticker so the transport bar and status stay truthful.
        viewModelScope.launch {
            while (true) {
                delay(700)
                mutate {
                    it.copy(
                        playerState = player.state,
                        playerTitle = player.title,
                        playerError = player.errorMessage,
                        playerPositionMs = player.positionMs,
                        playerDurationMs = player.durationMs,
                        systemEqActive = DeviceEq.globalActive,
                        systemStatus = DeviceEq.status,
                        cooperatingSessions = DeviceEq.cooperatingSessions,
                        serviceRunning = settings.serviceWanted,
                        batteryOptimized = isBatteryOptimized(),
                    )
                }
            }
        }
    }

    private fun mutate(block: (EqUiState) -> EqUiState) = _state.update(block)

    fun refresh() {
        mutate {
            it.copy(
                gains = settings.gains,
                preampDb = settings.preampDb,
                presetName = settings.presetName,
                bypass = settings.bypass,
                serviceRunning = settings.serviceWanted,
                systemEqWanted = settings.systemEqWanted,
                systemEqActive = DeviceEq.globalActive,
                systemStatus = DeviceEq.status,
                deviceBandInfo = DeviceEq.deviceBandInfo,
                cooperatingSessions = DeviceEq.cooperatingSessions,
                batteryOptimized = isBatteryOptimized(),
                playerState = player.state,
                playerTitle = player.title,
                playerError = player.errorMessage,
                playerPositionMs = player.positionMs,
                playerDurationMs = player.durationMs,
                lastUri = settings.lastUri,
                lastTitle = settings.lastTitle,
                engineInfo = engineInfo(),
            )
        }
    }

    fun navigate(tab: EqTab) = mutate { it.copy(tab = tab) }
    fun dismissMessage() = mutate { it.copy(message = null) }

    // ------------------------------------------------------------------ curve

    fun setBand(index: Int, gainDb: Float) {
        val next = _state.value.gains.toMutableList()
        if (index !in next.indices) return
        next[index] = gainDb.coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        val preamp = autoPreamp(next)
        EqCore.applyCurve(ctx, next, preamp)
        mutate {
            it.copy(
                gains = next,
                preampDb = preamp,
                presetName = Presets.matchName(next),
            )
        }
    }

    fun resetFlat() {
        EqCore.applyCurve(ctx, List(Bands.COUNT) { 0f }, 0f)
        refresh()
    }

    fun applyPreset(preset: com.universalrp.pulseeq.audio.EqPreset) {
        EqCore.applyPreset(ctx, preset)
        mutate {
            it.copy(
                gains = preset.gains.toList(),
                preampDb = preset.preampDb,
                presetName = preset.name,
            )
        }
        com.universalrp.pulseeq.audio.Sfx.tick(ctx)
    }

    fun setPreamp(db: Float) {
        EqCore.applyCurve(ctx, _state.value.gains, db)
        mutate { it.copy(preampDb = db) }
    }

    fun setBypass(on: Boolean) {
        EqCore.setBypass(ctx, on)
        mutate { it.copy(bypass = on) }
    }

    fun nudgeAll(delta: Float) {
        val next = _state.value.gains.map {
            (it + delta).coerceIn(Bands.MIN_DB, Bands.MAX_DB)
        }
        EqCore.applyCurve(ctx, next, autoPreamp(next))
        mutate { it.copy(gains = next, preampDb = autoPreamp(next), presetName = Presets.matchName(next)) }
    }

    /** Keeps the loudest boost at 0 dB, the way a sane EQ should. */
    private fun autoPreamp(gains: List<Float>): Float {
        val maxGain = gains.maxOrNull() ?: 0f
        return (-maxGain).coerceIn(-12f, 0f)
    }

    // ---------------------------------------------------------------- service

    fun setServiceWanted(on: Boolean) {
        settings.serviceWanted = on
        settings.systemEqWanted = on
        if (on) {
            EqService.start(ctx)
            mutate { it.copy(message = "PulseEQ will keep running in the background — you can close the app.") }
        } else {
            EqService.stop(ctx)
            DeviceEq.releaseEverything()
            mutate { it.copy(message = "Background equalizer stopped.") }
        }
        refresh()
    }

    fun setSystemEqWanted(on: Boolean) {
        settings.systemEqWanted = on
        if (on) {
            if (!settings.serviceWanted) setServiceWanted(true)
            val ok = EqCore.startGlobal()
            mutate {
                it.copy(
                    systemEqActive = ok,
                    message = if (ok)
                        "System-wide effect attached."
                    else
                        "Your ROM blocked system-wide effects. The PulseEQ player and cooperating apps still work — see Settings for details.",
                )
            }
        } else {
            EqCore.stopGlobal()
        }
        refresh()
    }

    fun setCooperating(on: Boolean) {
        settings.cooperating = on
        if (!on) DeviceEq.releaseAllSessions()
        refresh()
    }

    fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (t: Throwable) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t2: Throwable) {
                mutate { it.copy(message = "Open Settings → Apps → PulseEQ → Battery and choose “Unrestricted”.") }
            }
        }
    }

    fun isBatteryOptimized(): Boolean = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (t: Throwable) {
        true
    }

    // ------------------------------------------------------------------- LEDs

    fun setLedAnimation(on: Boolean) {
        settings.ledAnimation = on
        mutate { it.copy(ledAnimation = on) }
    }

    fun setLedBrightness(value: Float) {
        settings.ledBrightness = value
        mutate { it.copy(ledBrightness = value) }
    }

    // ----------------------------------------------------------------- player

    fun playFile(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (t: Throwable) {
            // Not every picker grants a persistable permission; the immediate read still works.
        }
        settings.lastUri = uri.toString()
        val title = player.title.ifBlank { uri.lastPathSegment ?: "Audio file" }
        settings.lastTitle = title
        EqService.start(ctx)
        player.play(uri)
        mutate { it.copy(playerState = player.state, playerTitle = player.title, lastUri = uri.toString()) }
    }

    fun resumeLast() {
        val uri = _state.value.lastUri?.let { Uri.parse(it) } ?: return
        EqService.start(ctx)
        player.play(uri)
    }

    fun togglePlayPause() {
        if (player.state == PlayerEngine.State.PLAYING || player.state == PlayerEngine.State.PAUSED) {
            player.toggle()
        } else {
            resumeLast()
        }
        mutate { it.copy(playerState = player.state, playerTitle = player.title) }
    }

    fun stopPlayer() {
        player.stop()
        mutate { it.copy(playerState = player.state) }
    }

    // ------------------------------------------------------------ diagnostics

    private fun engineInfo(): String {
        val t = EqStats.currentSampleRate
        return buildString {
            append("20-band peaking EQ (own DSP)\n")
            append("Sample rate: ${if (t > 0) "$t Hz" else "reported by the player when it starts"}\n")
            append("Preamp: %.1f dB • Soft limiter: on\n".format(_state.value.preampDb))
            append("Device effects: ${DeviceEq.deviceBandInfo}\n")
            append("Frames processed: ${EqStats.framesProcessed}\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
    }

    fun copyDiagnostics() {
        val text = _state.value.engineInfo + "\n" + _state.value.systemStatus
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PulseEQ info", text))
        mutate { it.copy(message = "Copied.") }
    }

    override fun onCleared() {
        player.onStateChanged = null
        super.onCleared()
    }
}
