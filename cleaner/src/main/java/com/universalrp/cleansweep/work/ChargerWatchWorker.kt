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
            .getBoolean(ChargeMonitorService.PILL_KEY, false)
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
        ChargeNotifier.post(ctx, stillCurrent = { !isStopped })
        ChargeNotifier.announcePluggedIn(ctx)

        var ticks = 0
        // isStopped is a member of every worker; a worker that has been cancelled or has
        // run out of time comes back here as stopped, and then this loop ends cleanly.
        while (ticks < 16 && !isStopped) {
            delay(30_000)
            ticks++
            val now = ChargeNotifier.read(ctx) ?: break
            if (!now.powerConnected && !ChargeMonitorService.persistentEnabled(ctx)) {
                ChargeNotifier.clear(ctx)
                return Result.success()
            }
            ChargeNotifier.post(ctx, stillCurrent = { !isStopped })
            com.universalrp.cleansweep.widget.CleanSweepWidget.refresh(ctx)
            // The piggy-backed charge of the last few percent can be watched, but there is no
            // point shouting about it — the card simply stays until the plug comes out.
            if (now.percent >= 100) break
        }

        val still = ChargeNotifier.read(ctx)
        if (still?.powerConnected == true || ChargeMonitorService.persistentEnabled(ctx)) schedule(ctx)

        return Result.success()
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
        fun schedule(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<ChargerWatchWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            } catch (e: Exception) {
                // If WorkManager is unavailable, the direct service start still may work.
                ChargeMonitorService.start(context)
            }
        }
    }
}
