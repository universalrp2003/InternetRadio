package com.universalrp.cleansweep.notify

import android.content.*
import android.net.*
import android.os.Handler
import android.os.Looper
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.voice.Announcer
import com.universalrp.cleansweep.voice.VoiceDeliveryPolicy
import com.universalrp.cleansweep.voice.VoiceObserverStatus
import kotlinx.coroutines.*

/** Immediate listeners only while the app is visible or its EXISTING charging/background service is running. */
object AutomaticAlerts {
    private val main = Handler(Looper.getMainLooper())
    private val owners = hashSetOf<String>()
    @Volatile private var registered = false
    private var context: Context? = null
    private var connectivity: ConnectivityManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryJob: Job? = null
    private val observerState = kotlinx.coroutines.flow.MutableStateFlow(VoiceObserverStatus())
    val observers: kotlinx.coroutines.flow.StateFlow<VoiceObserverStatus> = observerState
    private val powerReceiver = SystemEventWatcher()
    private val chargingReceiver = ChargingWatcher()
    private var knownTransport: String? = null
    @Volatile private var generation = 0
    private val networkRefresh = Runnable { context?.let { observeNetwork(it) } }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { changed() }
        override fun onAvailable(network: Network) { changed() }
        override fun onLost(network: Network) { changed() }
        private fun changed() { main.post { if (registered) { main.removeCallbacks(networkRefresh); main.postDelayed(networkRefresh, 900) } } }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { context?.applicationContext?.let { sampleBattery(it) } }
    }

    fun start(context: Context, owner: String) { main.post {
        owners.add(owner)
        if (registered) { observerState.value = observerState.value.copy(owners = owners.size); return@post }
        this.context = context.applicationContext; registered = true; generation++
        connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        knownTransport = transport(context).takeIf { it in setOf("wifi", "mobile") }
        knownTransport?.let { Announcer.prefs(context).edit().putString("last_transport", it).apply() }
        val network = connectivity != null && runCatching { connectivity?.registerDefaultNetworkCallback(callback) }.isSuccess
        val battery = runCatching { context.applicationContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.isSuccess
        val powerFilter = IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) }
        // Android8+ manifest implicit-power delivery can be restricted/deferred. Explicit runtime owners are required.
        val power = runCatching { context.applicationContext.registerReceiver(powerReceiver, powerFilter) }.isSuccess
        runCatching { context.applicationContext.registerReceiver(chargingReceiver, powerFilter) }
        observerState.value = VoiceObserverStatus(owners.size, battery, network, power)
    } }

    fun stop(owner: String) { main.post {
        owners.remove(owner)
        if (owners.isNotEmpty() || !registered) { observerState.value = observerState.value.copy(owners = owners.size); return@post }
        registered = false; generation++
        main.removeCallbacks(networkRefresh)
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        runCatching { context?.unregisterReceiver(batteryReceiver) }
        runCatching { context?.unregisterReceiver(powerReceiver) }
        runCatching { context?.unregisterReceiver(chargingReceiver) }
        observerState.value = VoiceObserverStatus()
        batteryJob?.cancel(); batteryJob = null; context = null; knownTransport = null
    } }

    private fun transport(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "none"
        val caps = runCatching { manager.activeNetwork?.let { manager.getNetworkCapabilities(it) } }.getOrNull() ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "mobile"
            else -> "none"
        }
    }

    private fun observeNetwork(context: Context) {
        val current = transport(context)
        val before = knownTransport
        if (current !in setOf("wifi", "mobile")) return
        knownTransport = current
        Announcer.prefs(context).edit().putString("last_transport", current).apply()
        if (!VoiceDeliveryPolicy.changedTransport(before, current)) return // Initial state/VPN/unknown is not an invented transition.
        if (current == "mobile") Announcer.speak(context, "You are now on mobile data; this uses your data plan.",
            "இப்போது மொபைல் டேட்டாவில் உள்ளீர்கள். இது உங்கள் தரவுத் திட்டத்தைப் பயன்படுத்தும்.", Announcer.Event.NETWORK_CHANGE)
        else Announcer.speak(context, "You are back on Wi-Fi.", "நீங்கள் மீண்டும் Wi-Fi-ல் உள்ளீர்கள்.", Announcer.Event.NETWORK_CHANGE)
    }

    private fun sampleBattery(context: Context) {
        if (batteryJob?.isActive == true) return
        val mine = generation
        batteryJob = scope.launch {
            val battery = runCatching { BatteryReader.read(context) }.getOrNull() ?: return@launch
            if (mine != generation || !registered || !Announcer.enabled(context)) return@launch
            val prefs = Announcer.prefs(context); val now = System.currentTimeMillis()
            fun alert(key: String, interval: Long, event: Announcer.Event, english: String, tamil: String) {
                if (VoiceDeliveryPolicy.cooldownReady(now, prefs.getLong(key, 0), interval))
                    Announcer.speak(context, english, tamil, event, onStarted = { prefs.edit().putLong(key, System.currentTimeMillis()).apply() })
            }
            if (!battery.powerConnected && battery.percent in 0 until Announcer.LOW_PERCENT)
                alert(Announcer.KEY_LAST_LOW, 6 * 60 * 60 * 1000L, Announcer.Event.BATTERY_LOW,
                    "Battery low: ${battery.percent} percent. Please recharge.", "பேட்டரி குறைவு: ${battery.percent} சதவீதம். சார்ஜ் செய்யவும்.")
            if (battery.powerConnected && battery.percent >= 100)
                alert(Announcer.KEY_LAST_FULL, 6 * 60 * 60 * 1000L, Announcer.Event.BATTERY_FULL,
                    "The battery has reached 100 percent.", "பேட்டரி முழுமை அடைந்தது. சார்ஜரை அகற்றவும்.")
            if ((battery.temperatureC ?: -100f) >= Announcer.HOT_BATTERY_C)
                alert(Announcer.KEY_LAST_HOT, 3 * 60 * 60 * 1000L, Announcer.Event.OVERHEAT,
                    "The battery is running hot. Please review charging and phone use.", "பேட்டரி அதிக வெப்பமாக உள்ளது. சார்ஜ் மற்றும் போன் பயன்பாட்டை சரிபார்க்கவும்.")
        }
    }
}
