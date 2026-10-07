package com.universalrp.cleansweep

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the app's last crash, on the phone, for the phone.
 *
 * A crash on a real device is the only evidence that matters, and MIUI's "keeps stopping"
 * dialog says nothing useful. This keeps the last exception — what it was, where it happened
 * and on which phone — in a small private file, and the About screen shows it so the user can
 * read it out or send it. Nothing is uploaded anywhere and the file is capped at a few
 * kilobytes; it holds no personal data, only the stack trace.
 */
object CrashLog {

    private const val FILE_NAME = "crash-last.txt"
    private const val MAX_CHARS = 6_000

    private var installed = false

    /** Hooks the default handler once per process. Safe to call from onCreate. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(app, thread, error)
            } catch (t: Throwable) {
                // Recording a crash must never cause another one.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** "CleanSweep 2.6 (build 11)" — read from the package, so no build config needed. */
    private fun versionLabel(app: Context): String = try {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        // versionCode, not longVersionCode: this file must work on Android 8 too, and a
        // crash recorder that itself crashes is worse than no recorder.
        @Suppress("DEPRECATION")
        val code = info.versionCode
        "CleanSweep ${info.versionName} (build $code)"
    } catch (e: Exception) {
        "CleanSweep"
    }

    private fun write(app: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("CleanSweep crash report")
            appendLine("When: $stamp")
            appendLine("App: " + versionLabel(app))
            appendLine(
                "Phone: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT})"
            )
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(error.javaClass.name + ": " + (error.message ?: "no message"))
            appendLine()
            error.stackTrace.take(40).forEach { appendLine("  at $it") }
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 3) {
                appendLine()
                appendLine("Caused by: ${cause.javaClass.name}: ${cause.message ?: ""}")
                cause.stackTrace.take(15).forEach { appendLine("  at $it") }
                cause = cause.cause
                depth++
            }
        }.take(MAX_CHARS)
        File(app.filesDir, FILE_NAME).writeText(text)
    }

    /** The last crash, or an empty string when nothing has crashed. */
    fun last(context: Context): String = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText().trim() else ""
    } catch (e: Exception) {
        ""
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            // Nothing to delete.
        }
    }
}
