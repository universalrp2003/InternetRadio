package com.universalrp.cleansweep.notify

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import com.universalrp.cleansweep.data.Lang

/**
 * Optional, standalone overlay button for opening Android's native media-volume panel.
 * It is deliberately independent of [StatusPill]: turning one feature off does not affect
 * the other. The button never changes the volume itself.
 */
object FloatingVolumeControl {
    const val PREFS = "cleansweep_state"
    const val KEY_ENABLED = "floating_volume_button"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var view: ImageButton? = null
    @Volatile private var manager: WindowManager? = null

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
            if (view != null) return@post
            val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val button = ImageButton(app).apply {
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xEE0EA5E9.toInt())
                    setStroke(dp(app, 1), 0xFFFFFFFF.toInt())
                }
                contentDescription = Lang.forContext(app, "Open system media volume controls")
                setPadding(dp(app, 12), dp(app, 12), dp(app, 12), dp(app, 12))
                setOnClickListener {
                    try {
                        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        audio?.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_SAME,
                            AudioManager.FLAG_SHOW_UI,
                        )
                    } catch (_: Exception) {
                        // Android owns the native volume panel; OEMs may restrict its display.
                    }
                }
                elevation = dp(app, 8).toFloat()
            }
            val params = WindowManager.LayoutParams(
                dp(app, 52),
                dp(app, 52),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = dp(app, 12)
                y = 0
            }
            try {
                windowManager.addView(button, params)
                manager = windowManager
                view = button
            } catch (_: Exception) {
                // Permission can be revoked or denied between the check and addView.
                view = null
                manager = null
            }
        }
    }

    fun remove() {
        main.post {
            val current = view ?: return@post
            try {
                manager?.removeView(current)
            } catch (_: Exception) {
                // The window may already have been removed by Android.
            } finally {
                view = null
                manager = null
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
