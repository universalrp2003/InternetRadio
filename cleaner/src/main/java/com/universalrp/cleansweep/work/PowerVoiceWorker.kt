package com.universalrp.cleansweep.work

import android.content.Context
import android.os.Build
import androidx.work.*
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.notify.ChargeNotifier
import com.universalrp.cleansweep.voice.Announcer
import com.universalrp.cleansweep.voice.VoiceDeliveryPolicy
import java.util.concurrent.TimeUnit

/** Short enabled-event job, not a new persistent monitor. Keeps TTS alive after a power broadcast returns. */
class PowerVoiceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val kind = inputData.getString("kind") ?: return Result.success()
        val token = inputData.getString("connection") ?: return Result.success()
        val at = inputData.getLong("trigger_at", 0)
        val prefs = Announcer.prefs(context)
        val event = when (kind) { "plug" -> Announcer.Event.CHARGING; "unplug" -> Announcer.Event.UNPLUGGED_EARLY; "idle" -> Announcer.Event.CHARGER_IDLE; else -> return Result.success() }
        val now = System.currentTimeMillis()
        if (at <= 0 || now < at || now - at > 120_000 || prefs.getString("voice_connection_token", "") != token) {
            Announcer.noteDelivery(context, event, VoiceDeliveryPolicy.Stage.STALE_EVENT)
            return Result.success()
        }
        val battery = runCatching { BatteryReader.read(context) }.getOrNull() ?: return Result.success()
        when (kind) {
            "plug" -> if (battery.powerConnected) ChargeNotifier.announcePluggedIn(context, delayMs = 600)
            "unplug" -> {
                val hour = java.time.LocalTime.now().hour
                if (!battery.powerConnected && battery.percent in 1 until Announcer.UNPLUG_BEFORE_PERCENT && hour >= Announcer.unplugStartHour(context))
                    Announcer.speakAndWait(context, "Charger removed at ${battery.percent} percent.",
                        "சார்ஜர் அகற்றப்பட்டது: ${battery.percent} சதவீதம்.", event)
            }
            "idle" -> {
                val current = battery.currentA
                if (battery.powerConnected && !battery.charging && battery.percent in 1..99 && current != null && kotlin.math.abs(current) < 0.05f)
                    Announcer.speakAndWait(context, "The charger is connected but the battery is not charging at this sample. It may be paused; review if unexpected.",
                        "சார்ஜர் இணைந்துள்ளது. இப்போது சார்ஜ் ஆகவில்லை; இடைநிறுத்தமாக இருக்கலாம். எதிர்பாராத நிலையில் சரிபார்க்கவும்.", event)
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, kind: String, token: String) {
            if (kind !in setOf("plug", "unplug", "idle")) return
            val request = OneTimeWorkRequestBuilder<PowerVoiceWorker>()
                .setInputData(workDataOf("kind" to kind, "connection" to token, "trigger_at" to System.currentTimeMillis()))
            if (kind == "idle") request.setInitialDelay(25, TimeUnit.SECONDS)
            else if (Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            runCatching { WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("voice_power_${kind}_$token", ExistingWorkPolicy.KEEP, request.build()) }
        }
    }
}
