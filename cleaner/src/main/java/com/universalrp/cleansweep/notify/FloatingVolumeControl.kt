package com.universalrp.cleansweep.notify

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import com.universalrp.cleansweep.data.Lang
import kotlin.math.abs

/**
 * Optional, standalone overlay button for opening Android's native media-volume panel.
 * It is deliberately independent of [StatusPill]: turning one feature off does not affect
 * the other. The button never changes the volume itself.
 */
object FloatingVolumeControl {
    const val PREFS = "cleansweep_state"
    const val KEY_ENABLED = "floating_volume_button"

    private const val KEY_RIGHT = "floating_volume_right"
    private const val KEY_BOTTOM = "floating_volume_bottom"
    private const val BUTTON_SIZE_DP = 42
    private const val IDLE_ALPHA = 0.24f
    private const val ACTIVE_ALPHA = 1f
    private const val IDLE_DELAY_MS = 2_800L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var view: ImageButton? = null
    @Volatile private var manager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var fadeTask: Runnable? = null

    fun canDraw(context: Context): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    } catch (_: Exception) {
        false
    }

    fun sync(context: Context, enabled: Boolean) {
        if (!enabled || !canDraw(context)) {
            remove()
            return
        }
        val app = context.applicationContext
        main.post {
            val existing = view
            val currentManager = manager
            val currentParams = layoutParams
            if (existing != null && currentManager != null && currentParams != null) {
                try {
                    placeAtSavedCorner(app, currentManager, currentParams)
                    currentManager.updateViewLayout(existing, currentParams)
                    return@post
                } catch (_: Exception) {
                    removeCurrent()
                }
            }
            attach(app)
        }
    }

    private fun attach(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val button = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0EA5E9.toInt())
                setStroke(dp(context, 1), 0xFFFFFFFF.toInt())
            }
            contentDescription = Lang.forContext(
                context,
                "Tap to open system volume controls; drag to move",
            )
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                try {
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audio?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME,
                        AudioManager.FLAG_SHOW_UI,
                    )
                } catch (_: Exception) {
                    // Android owns the native volume panel; OEMs may restrict its display.
                }
            }
            elevation = dp(context, 6).toFloat()
        }
        val params = WindowManager.LayoutParams(
            dp(context, BUTTON_SIZE_DP),
            dp(context, BUTTON_SIZE_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }

        installDragBehavior(button, context, windowManager, params)
        placeAtSavedCorner(context, windowManager, params)
        try {
            windowManager.addView(button, params)
            manager = windowManager
            layoutParams = params
            view = button
            scheduleFade(button)
        } catch (_: Exception) {
            // Permission can be revoked or denied between the check and addView.
            view = null
            manager = null
            layoutParams = null
        }
    }

    private fun installDragBehavior(
        button: ImageButton,
        context: Context,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var dragged = false

        button.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    dragged = false
                    target.isPressed = true
                    reveal(target)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragged && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragged = true
                    if (dragged) {
                        val size = displaySize(context, windowManager)
                        params.x = (originX + dx.toInt()).coerceIn(0, (size.x - params.width).coerceAtLeast(0))
                        params.y = (originY + dy.toInt()).coerceIn(0, (size.y - params.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(target, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    target.isPressed = false
                    if (dragged) {
                        snapToNearestCorner(context, windowManager, params)
                        persistCorner(context, params, windowManager)
                    } else {
                        target.performClick()
                    }
                    scheduleFade(target as ImageButton)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    target.isPressed = false
                    if (dragged) {
                        snapToNearestCorner(context, windowManager, params)
                        persistCorner(context, params, windowManager)
                    }
                    scheduleFade(target as ImageButton)
                    true
                }

                else -> true
            }
        }
    }

    private fun placeAtSavedCorner(
        context: Context,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val right = prefs.getBoolean(KEY_RIGHT, true)
        val bottom = prefs.getBoolean(KEY_BOTTOM, true)
        placeAtCorner(context, windowManager, params, right, bottom)
    }

    private fun snapToNearestCorner(
        context: Context,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val size = displaySize(context, windowManager)
        val right = params.x + params.width / 2 >= size.x / 2
        val bottom = params.y + params.height / 2 >= size.y / 2
        placeAtCorner(context, windowManager, params, right, bottom)
        view?.let { current -> runCatching { windowManager.updateViewLayout(current, params) } }
    }

    private fun placeAtCorner(
        context: Context,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
        right: Boolean,
        bottom: Boolean,
    ) {
        val size = displaySize(context, windowManager)
        val horizontalMax = (size.x - params.width).coerceAtLeast(0)
        val horizontalMargin = dp(context, 10).coerceAtMost(horizontalMax)
        val topMargin = dp(context, 58)
        val bottomMargin = dp(context, 76)
        val verticalMax = (size.y - params.height - bottomMargin).coerceAtLeast(topMargin)
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = if (right) (horizontalMax - horizontalMargin).coerceAtLeast(0) else horizontalMargin
        params.y = if (bottom) verticalMax else topMargin.coerceAtMost(verticalMax)
    }

    private fun persistCorner(
        context: Context,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
    ) {
        val size = displaySize(context, windowManager)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RIGHT, params.x + params.width / 2 >= size.x / 2)
            .putBoolean(KEY_BOTTOM, params.y + params.height / 2 >= size.y / 2)
            .apply()
    }

    private fun displaySize(context: Context, windowManager: WindowManager): Point = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also { windowManager.defaultDisplay.getRealSize(it) }
        }
    }.getOrElse {
        Point(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
    }

    private fun reveal(button: View) {
        clearFade()
        button.animate().cancel()
        button.alpha = ACTIVE_ALPHA
    }

    private fun scheduleFade(button: ImageButton) {
        clearFade()
        val task = Runnable {
            if (view === button) {
                button.animate().alpha(IDLE_ALPHA).setDuration(350L).start()
            }
        }
        fadeTask = task
        main.postDelayed(task, IDLE_DELAY_MS)
    }

    private fun clearFade() {
        fadeTask?.let { main.removeCallbacks(it) }
        fadeTask = null
    }

    fun remove() {
        main.post { removeCurrent() }
    }

    private fun removeCurrent() {
        clearFade()
        val current = view
        try {
            if (current != null) manager?.removeView(current)
        } catch (_: Exception) {
            // The window may already have been removed by Android.
        } finally {
            current?.animate()?.cancel()
            view = null
            manager = null
            layoutParams = null
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
