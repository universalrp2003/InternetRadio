package com.universalrp.cleansweep.notify

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * The little "⚡ 3.9 W" reading that sits in the empty part of the status bar, next to the
 * clock and beside the front-camera cutout — the space the user pointed at in the
 * screenshot. Android gives third-party apps no way to add a real status-bar item, so this
 * is a tiny overlay window that CleanSweep draws there itself. It is:
 *
 *  * off by default and only drawn after the user allows "Display over other apps",
 *  * transparent to touch (it can never block a tap on the status bar),
 *  * only shown while the charger is connected,
 *  * nothing but a number and a watt sign — no notifications, no personal data.
 *
 * Because it is an overlay, a phone can hide it in full-screen apps, and it disappears the
 * moment the user turns the switch off.
 */
object StatusPill {

    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    /** True when the "Display over other apps" permission is granted. */
    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /**
     * Shows [text] (or hides the pill when it is null/blank). Safe to call from any thread
     * and from a service: the work is posted to the main looper.
     */
    fun update(context: Context, text: String?) {
        if (text.isNullOrBlank() || !canDraw(context)) {
            remove()
            return
        }
        val app = context.applicationContext
        main.post { show(app, text) }
    }

    fun remove() {
        main.post { hide() }
    }

    private fun hide() {
        val current = view ?: return
        val manager = current.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            manager?.removeViewImmediate(current)
        } catch (e: Exception) {
            // Already detached.
        }
        view = null
        params = null
    }

    private fun show(context: Context, text: String) {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val pill = view ?: createPill(context, manager) ?: return
        if (pill.text != text) pill.text = text
        position(context, manager, pill)
        // And once more after the text has been measured, so it lands in the right place.
        pill.post { position(context, manager, pill) }
    }

    /** Adds the window; returns null when the phone refuses to let us draw there. */
    private fun createPill(context: Context, manager: WindowManager): TextView? {
        val pill = TextView(context).apply {
            setTextColor(Color.parseColor("#E8FBFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(dp(context, 6), dp(context, 1), dp(context, 6), dp(context, 1))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 7).toFloat()
                setColor(Color.parseColor("#B8000000"))
                setStroke(dp(context, 1), Color.parseColor("#6638BDF8"))
            }
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        layout.gravity = Gravity.TOP or Gravity.START
        return try {
            manager.addView(pill, layout)
            view = pill
            params = layout
            pill
        } catch (e: Exception) {
            // Some phones refuse the window; then there is simply no pill.
            null
        }
    }

    private fun position(context: Context, manager: WindowManager, pill: TextView) {
        val layout = params ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val statusHeight = statusBarHeight(context)
        val width = if (pill.width > 0) pill.width else dp(context, 56)
        val height = if (pill.height > 0) pill.height else dp(context, 16)
        val margin = dp(context, 2)

        // Default: the empty middle of the status bar, between the clock and the icons.
        var x = (screenWidth - width) / 2

        // With a punch-hole camera up there, sit beside it instead of behind it.
        val cutout = topCutout(manager)
        if (cutout != null && cutout.width() > 0 && cutout.width() < screenWidth / 2) {
            val gap = dp(context, 5)
            x = if (cutout.centerX() > screenWidth / 2) {
                cutout.left - width - gap
            } else {
                cutout.right + gap
            }
            if (x < margin) x = cutout.right + gap
            if (x + width > screenWidth - margin) x = cutout.left - width - gap
        }

        layout.x = x.coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
        layout.y = ((statusHeight - height) / 2).coerceAtLeast(0)
        try {
            manager.updateViewLayout(pill, layout)
        } catch (e: Exception) {
            // The window went away between frames.
        }
    }

    /** The camera cutout at the very top of the screen, when the phone reports one. */
    private fun topCutout(manager: WindowManager): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            manager.currentWindowMetrics.windowInsets.displayCutout
                ?.boundingRects
                ?.firstOrNull { it.top <= 6 && it.height() > 0 }
        } catch (e: Exception) {
            null
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            context.resources.getDimensionPixelSize(id)
        } else {
            dp(context, 24)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
