package com.universalrp.cleansweep.notify

import android.content.Context
import android.content.SharedPreferences
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
import android.view.MotionEvent
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
 *  * **movable**: every phone writes different things up there (the carrier name, VoLTE,
 *    VPN, battery %), so the arrows in Phone health move the reading anywhere on screen and
 *    the position is remembered,
 *  * nothing but a number and a watt sign — no notifications, no personal data.
 *
 * Because it is an overlay, a phone can hide it in full-screen apps, and it disappears the
 * moment the user turns the switch off.
 */
object StatusPill {

    /** Position prefs, in the same plain file the charging service reads. */
    const val PREFS = "cleansweep_state"
    const val KEY_DX = "pill_dx"
    const val KEY_DY = "pill_dy"
    const val KEY_AUTO = "pill_auto"

    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    /**
     * True only while the user is placing the reading with their finger. Outside drag mode the
     * pill stays [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE], so it can never swallow a tap
     * meant for the phone's own status bar.
     */
    @Volatile
    private var draggable = false

    /** True when the "Display over other apps" permission is granted. */
    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** Where the user last moved it. 0,0 with auto = the middle of the status bar. */
    fun offsets(context: Context): Pair<Int, Int> {
        val prefs = prefs(context)
        return prefs.getInt(KEY_DX, 0) to prefs.getInt(KEY_DY, 0)
    }

    fun isAuto(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    /**
     * Moves the reading by a number of screen pixels and remembers it, so the user can push
     * it out of the way of anything their phone puts up there — or drop it below the status
     * bar entirely.
     */
    fun moveBy(context: Context, dx: Int, dy: Int) {
        val app = context.applicationContext
        nudge(app, dx, dy)
        redraw(app)
    }

    /** Adds [dx], [dy] to the remembered spot, without touching the window. */
    private fun nudge(context: Context, dx: Int, dy: Int) {
        val (x, y) = offsets(context)
        prefs(context).edit()
            .putInt(KEY_DX, x + dx)
            .putInt(KEY_DY, y + dy)
            .putBoolean(KEY_AUTO, false)
            .apply()
    }

    /**
     * Drag mode: with it on, the reading itself follows the finger, and the user can drop it
     * anywhere on the screen — including right under the status bar. Turned off, the pill goes
     * back to being touch-through.
     */
    fun setDraggable(context: Context, on: Boolean) {
        draggable = on
        val app = context.applicationContext
        main.post {
            val pill = view ?: return@post
            val layout = params ?: return@post
            val manager = pill.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            layout.flags = flags()
            try {
                manager.updateViewLayout(pill, layout)
            } catch (e: Exception) {
                // The window went away between frames.
            }
        }
    }

    private fun flags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (draggable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Back to the automatic spot (centred, beside the camera cutout). */
    fun resetPosition(context: Context) {
        val app = context.applicationContext
        prefs(app).edit()
            .putInt(KEY_DX, 0)
            .putInt(KEY_DY, 0)
            .putBoolean(KEY_AUTO, true)
            .apply()
        redraw(app)
    }

    /** Re-reads the position and moves the pill, if it is on screen right now. */
    fun redraw(context: Context) {
        val app = context.applicationContext
        main.post {
            val pill = view ?: return@post
            val manager = pill.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            position(app, manager, pill)
            pill.post { position(app, manager, pill) }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
            flags(),
            PixelFormat.TRANSLUCENT,
        )
        layout.gravity = Gravity.TOP or Gravity.START
        // Finger dragging, only while the user asked for it: each move is the same arithmetic
        // the arrows use, so the two can never disagree about where the reading is.
        var lastX = 0f
        var lastY = 0f
        pill.setOnTouchListener { v, event ->
            if (!draggable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()
                    lastX = event.rawX
                    lastY = event.rawY
                    if (dx != 0 || dy != 0) {
                        nudge(context, dx, dy)
                        position(context, manager, pill)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
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
        val screenHeight = context.resources.displayMetrics.heightPixels
        val statusHeight = statusBarHeight(context)
        val width = if (pill.width > 0) pill.width else dp(context, 56)
        val height = if (pill.height > 0) pill.height else dp(context, 16)
        val margin = dp(context, 2)

        // Default: the empty middle of the status bar, between the clock and the icons.
        var x = (screenWidth - width) / 2
        var y = (statusHeight - height) / 2

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

        // The user's own position, when they have moved it: every phone writes different
        // words up there (VoLTE, VPN, carrier name), so it has to be movable.
        if (!isAuto(context)) {
            val (dx, dy) = offsets(context)
            x += dx
            y += dy
        }

        layout.x = x.coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
        layout.y = y.coerceIn(0, (screenHeight - height - margin).coerceAtLeast(0))
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
