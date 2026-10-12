package com.universalrp.cleansweep.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.universalrp.cleansweep.notify.ChargeMonitorService
import com.universalrp.cleansweep.notify.ChargeNotifier
import kotlinx.coroutines.delay

/**
 * Existing user-enabled card/pill/monitor fallback. Android/OEM rules can refuse or delay
 * service starts and jobs; this is not guaranteed on every device. Voice-only power alerts
 * are independent short PowerVoiceWorker jobs, with shared connection de-duplication.
 */
class ChargerWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val persistent = ChargeMonitorService.persistentEnabled(ctx)
        val pill = ctx.getSharedPreferences(ChargeMonitorService.PILL_PREFS, Context.MODE_PRIVATE)
            .getBoolean(ChargeMonitorService.PILL_KEY, com.universalrp.cleansweep.data.MonitorDefaultsPolicy.STATUS_PILL)
        if (!ChargeMonitorService.cardEnabled(ctx) && !pill && !persistent) {
            // No visual monitoring requested. Voice-only events have their own short worker.
            ChargeNotifier.clear(ctx)
            return Result.success()
        }
        val battery = ChargeNotifier.read(ctx) ?: return Result.success()
        if (!battery.powerConnected && !persistent) {
            ChargeNotifier.clear(ctx)
            return Result.success()
        }

        // 1. The happy path: a real foreground service, started from inside a running job.
        val serviceUp = ChargeMonitorService.start(ctx)
        if (serviceUp) {
            ChargeNotifier.announcePluggedIn(ctx)
            return Result.success()
        }

        // 2. The fallback: the system will not give us a foreground service here, so the job
        //    itself keeps the same card alive. It runs in short stretches and re-schedules
        //    itself, which is allowed without any new permission.
        val owner = "charger-worker:$id"
        com.universalrp.cleansweep.notify.AutomaticAlerts.start(ctx, owner)
        try {
            ChargeNotifier.post(ctx, stillCurrent = { !isStopped })
            ChargeNotifier.announcePluggedIn(ctx)
            var ticks = 0
            while (ticks < 16 && !isStopped) {
                delay(30_000); ticks++
                val now = ChargeNotifier.read(ctx) ?: break
                if (!now.powerConnected && !ChargeMonitorService.persistentEnabled(ctx)) {
                    ChargeNotifier.clear(ctx); return Result.success()
                }
                ChargeNotifier.post(ctx, stillCurrent = { !isStopped })
                com.universalrp.cleansweep.widget.CleanSweepWidget.refresh(ctx)
            }
            val still = ChargeNotifier.read(ctx)
            if (still?.powerConnected == true || ChargeMonitorService.persistentEnabled(ctx)) schedule(ctx, continuation = true)
            return Result.success()
        } finally { com.universalrp.cleansweep.notify.AutomaticAlerts.stop(owner) }
    }

    /**
     * Expedited work is asked for first: a job the system runs immediately, which is what a
     * plug-in deserves. When the quota for that is used up, the request is simply downgraded
     * instead of being dropped.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ChargeNotifier.channel(applicationContext)
        val battery = ChargeNotifier.read(applicationContext)
        val notification = if (battery != null) {
            ChargeNotifier.build(applicationContext, battery)
        } else {
            ChargeNotifier.buildFallback(applicationContext)
        }
        return ForegroundInfo(ChargeNotifier.NOTIFICATION_ID, notification)
    }

    companion object {
        private const val UNIQUE = "cleansweep_charger_watch"

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE) }
        }

        /** Schedules the watch. Safe to call from a broadcast receiver. */
        fun schedule(context: Context, continuation: Boolean = false) {
            try {
                val request = OneTimeWorkRequestBuilder<ChargerWatchWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE,
                    if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                    request,
                )
            } catch (e: Exception) {
                // If WorkManager is unavailable, the direct service start still may work.
                ChargeMonitorService.start(context)
            }
        }
    }
}
