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
import kotlinx.coroutines.isActive

/**
 * Charger plug-in, the way that works on every Android version.
 *
 * The plug-in broadcast cannot start a foreground service on Android 12 and newer, and
 * starting one anyway is how "the charger was never announced" happened: the call was
 * refused, the exception was swallowed, and the phone stayed silent. A broadcast *can*
 * schedule a job, and a running job is allowed to do the real work — so that is what happens:
 *
 *  1. the job tries the normal charging service (the live card with watts, current and
 *     temperature, refreshed every five seconds);
 *  2. if the system still refuses, the job shows the same card itself, keeps refreshing it
 *     while the charger is in, and asks to be run again when its time is up, so the reading
 *     does not disappear while the phone is still plugged in.
 *
 * The one line about the charger is spoken from [ChargeNotifier], which de-duplicates it, so
 * a plug-in is never announced twice even when both paths run.
 */
class ChargerWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ChargeMonitorService.cardEnabled(ctx)) {
            // The user switched the charging card off: nothing to show, nothing to say.
            ChargeNotifier.clear(ctx)
            return Result.success()
        }
        val battery = ChargeNotifier.read(ctx) ?: return Result.success()
        if (!battery.charging) {
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
        ChargeNotifier.post(ctx)
        ChargeNotifier.announcePluggedIn(ctx)

        var ticks = 0
        while (ticks < 16 && isActive) {
            delay(30_000)
            ticks++
            val now = ChargeNotifier.read(ctx) ?: break
            if (!now.charging) {
                ChargeNotifier.clear(ctx)
                return Result.success()
            }
            ChargeNotifier.post(ctx)
            // The piggy-backed charge of the last few percent can be watched, but there is no
            // point shouting about it — the card simply stays until the plug comes out.
            if (now.percent >= 100) break
        }

        val still = ChargeNotifier.read(ctx)
        if (still?.charging == true) schedule(ctx)

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
