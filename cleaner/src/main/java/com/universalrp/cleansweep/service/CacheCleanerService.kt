package com.universalrp.cleansweep.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional accessibility service that automates clearing other apps' caches.
 *
 * Android does not allow one app to clear another app's cache directly (root
 * would be needed). So, when the user taps "Auto clean", this service walks a
 * queue of apps: for each one it opens the app-info page, taps "Storage" (or
 * "Clear data" on MIUI/HyperOS), taps "Clear cache", then goes back and moves
 * to the next app. Text matching covers English and Chinese UI strings.
 */
class CacheCleanerService : AccessibilityService() {

    private enum class Step { IDLE, WAIT_APP_INFO, FIND_ENTRY, FIND_CLEAR_CACHE, FIND_CONFIRM, BETWEEN }

    private var step = Step.IDLE
    private var stepAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (step == Step.IDLE || step == Step.BETWEEN) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        handle()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance == this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handle() {
        val now = System.currentTimeMillis()
        if (now - stepAt < 400) return // Let the new screen settle.
        if (now - stepAt > 8_000) {
            skipCurrent()
            return
        }
        val root = rootInActiveWindow ?: return
        try {
            when (step) {
                Step.WAIT_APP_INFO -> {
                    step = Step.FIND_ENTRY
                    stepAt = now
                }
                Step.FIND_ENTRY -> {
                    // 1) Some ROMs show "Clear cache" directly on this page.
                    if (clickByText(root, CLEAR_CACHE_TEXTS)) {
                        finishCurrent()
                        return
                    }
                    // 2) MIUI / HyperOS (com.miui.securitycenter) app-info page has a
                    //    "Clear data" button at the bottom whose bottom sheet contains
                    //    "Clear cache". This MUST be tried before "Storage": on HyperOS
                    //    the Storage page has no clear buttons and would dead-end.
                    if (clickByText(root, CLEAR_DATA_TEXTS)) {
                        step = Step.FIND_CLEAR_CACHE
                        stepAt = now
                        return
                    }
                    // 3) Stock Android / One UI: open the "Storage (& cache)" page,
                    //    which contains the "Clear cache" button.
                    if (clickByText(root, STORAGE_TEXTS)) {
                        step = Step.FIND_CLEAR_CACHE
                        stepAt = now
                    }
                }
                Step.FIND_CLEAR_CACHE -> {
                    val node = findText(root, CLEAR_CACHE_TEXTS)
                    if (node != null) {
                        if (node.isEnabled && performClick(node)) {
                            // HyperOS/MIUI pops a "Clear cache?" confirmation dialog;
                            // other ROMs clear straight away. FIND_CONFIRM handles both.
                            step = Step.FIND_CONFIRM
                            stepAt = now
                        } else {
                            skipCurrent() // Cache already empty or blocked.
                        }
                    }
                }
                Step.FIND_CONFIRM -> {
                    // Strict match only: never click a substring like "Bookmarks".
                    val ok = findText(root, CONFIRM_TEXTS, exact = true)
                    if (ok != null && ok.isEnabled && performClick(ok)) {
                        finishCurrent()
                    } else if (now - stepAt > 4_000) {
                        // No confirmation dialog on this ROM — the clear already ran.
                        finishCurrent()
                    }
                }
                else -> Unit
            }
        } catch (e: Exception) {
            skipCurrent()
        }
    }

    private fun finishCurrent() {
        step = Step.BETWEEN
        stepAt = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ nextApp() }, 500)
        }, 700)
    }

    private fun skipCurrent() {
        step = Step.BETWEEN
        stepAt = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ nextApp() }, 600)
    }

    private fun nextApp() {
        val pkg = queue.removeFirstOrNull()
        if (pkg == null) {
            step = Step.IDLE
            android.widget.Toast.makeText(
                this, "CleanSweep: auto clean finished", android.widget.Toast.LENGTH_SHORT
            ).show()
            com.universalrp.cleansweep.data.SoundFx.play(this, com.universalrp.cleansweep.R.raw.sound_success)
            returnToApp()
            return
        }
        step = Step.WAIT_APP_INFO
        stepAt = System.currentTimeMillis()
        android.widget.Toast.makeText(
            this,
            "CleanSweep: clearing cache (${queue.size + 1} left)…",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", pkg, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            nextApp()
        }
    }

    private fun returnToApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Ignored.
        }
    }

    private fun findText(
        root: AccessibilityNodeInfo,
        texts: List<String>,
        exact: Boolean = false,
    ): AccessibilityNodeInfo? {
        for (t in texts) {
            val found = try {
                root.findAccessibilityNodeInfosByText(t)
            } catch (e: Exception) {
                null
            } ?: continue
            var fallback: AccessibilityNodeInfo? = null
            for (n in found) {
                if (!n.isVisibleToUser) continue
                val txt = n.text?.toString() ?: n.contentDescription?.toString() ?: continue
                if (txt.equals(t, ignoreCase = true)) return clickableTarget(n)
                if (!exact && fallback == null) fallback = clickableTarget(n)
            }
            if (fallback != null) return fallback
        }
        return null
    }

    private fun clickByText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        val n = findText(root, texts) ?: return false
        return performClick(n)
    }

    private fun clickableTarget(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return n
    }

    private fun performClick(n: AccessibilityNodeInfo): Boolean = try {
        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    } catch (e: Exception) {
        false
    }

    companion object {
        @Volatile
        private var instance: CacheCleanerService? = null

        private val queue = ArrayDeque<String>()

        private val STORAGE_TEXTS = listOf("storage & cache", "storage usage", "storage", "存储", "儲存空間")
        private val CLEAR_CACHE_TEXTS = listOf("clear cache", "清除缓存", "清除快取")
        private val CLEAR_DATA_TEXTS = listOf("clear data", "清除数据", "清除資料")
        private val CONFIRM_TEXTS = listOf("ok", "okay", "confirm", "allow", "确定", "確認")

        val isRunning: Boolean get() = instance != null

        /** Starts the automated cleaning queue. Does nothing if the service is off. */
        fun startCleaning(context: Context, packages: List<String>) {
            val svc = instance ?: return
            queue.clear()
            queue.addAll(packages)
            svc.nextApp()
        }
    }
}
