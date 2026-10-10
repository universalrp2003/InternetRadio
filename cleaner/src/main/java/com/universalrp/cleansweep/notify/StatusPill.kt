package com.universalrp.cleansweep.notify

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.universalrp.cleansweep.data.LiveNetworkQuality

/**
 * Animated dual-channel network activity LED meter:
 * - [D] Download: lights left-to-right (0 -> 1 -> 2)
 * - [U] Upload: lights right-to-left (2 -> 1 -> 0)
 * Illuminated in Gold (top quality), Emerald Green (good), Red (poor) with smooth background track.
 */
class NetworkLedMeterView(context: Context) : View(context) {
    var isDownloadActive: Boolean = true
    var isUploadActive: Boolean = true
    var qualityGrade: LiveNetworkQuality.QualityGrade? = null
    var animStep: Int = 0
    var scaleFactor: Float = 1.0f

    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val rowH = h / 2f
        textPaint.textSize = (rowH * 0.72f).coerceAtLeast(dp(6f))
        val tagW = dp(8f) * scaleFactor
        val barStartX = tagW + dp(2.5f)
        val barW = (w - barStartX).coerceAtLeast(dp(18f) * scaleFactor)
        val barH = dp(2.8f) * scaleFactor
        val numSegments = 3
        val segGap = dp(1.5f) * scaleFactor
        val totalGaps = (numSegments - 1) * segGap
        val segW = (barW - totalGaps) / numSegments

        val baseColor = when (qualityGrade) {
            LiveNetworkQuality.QualityGrade.TOP_QUALITY -> Color.parseColor("#00E676")    // Very Good = Green
            LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY -> Color.parseColor("#FFD600") // Medium = Yellow
            LiveNetworkQuality.QualityGrade.BAD_QUALITY -> Color.parseColor("#FF3B30")    // Bad = Red
            LiveNetworkQuality.QualityGrade.UNKNOWN, null -> Color.parseColor("#38BDF8")
        }

        // Row 1: Download [D] (lights left to right)
        val dCenterY = rowH * 0.5f
        textPaint.color = if (isDownloadActive) baseColor else Color.parseColor("#66FFFFFF")
        canvas.drawText("D", 0f, dCenterY + textPaint.textSize * 0.35f, textPaint)
        for (i in 0 until numSegments) {
            val left = barStartX + i * (segW + segGap)
            val top = dCenterY - barH / 2f
            val rect = RectF(left, top, left + segW, top + barH)
            val isLit = isDownloadActive && ((animStep % numSegments) >= i)
            if (isLit) {
                litPaint.color = baseColor
                litPaint.alpha = 255
            } else {
                litPaint.color = Color.parseColor("#33FFFFFF")
            }
            canvas.drawRoundRect(rect, dp(1.2f), dp(1.2f), litPaint)
        }

        // Row 2: Upload [U] (lights right to left: from mobile data count inwards)
        val uCenterY = rowH * 1.5f
        textPaint.color = if (isUploadActive) baseColor else Color.parseColor("#66FFFFFF")
        canvas.drawText("U", 0f, uCenterY + textPaint.textSize * 0.35f, textPaint)
        for (i in 0 until numSegments) {
            val left = barStartX + i * (segW + segGap)
            val top = uCenterY - barH / 2f
            val rect = RectF(left, top, left + segW, top + barH)
            val revIndex = (numSegments - 1) - i
            val isLit = isUploadActive && ((animStep % numSegments) >= revIndex)
            if (isLit) {
                litPaint.color = baseColor
                litPaint.alpha = 255
            } else {
                litPaint.color = Color.parseColor("#33FFFFFF")
            }
            canvas.drawRoundRect(rect, dp(1.2f), dp(1.2f), litPaint)
        }
    }
}

/**
 * Live Guard Status Bar Pill Overlay:
 * Clean, compact, customizable floating pill.
 * Features:
 * - Real-time mobile data (cleanly hidden if on Wi-Fi / Unlimited 5G per user choice)
 * - Watts ONLY while a charger is connected; data and D/U meter automatically return on unplug
 * - Animated D & U LED meter bars while unplugged
 * - Move, drag, and resize controls
 * - Landscape transparent touch-through behavior
 */
object StatusPill {

    const val PREFS = "cleansweep_state"
    const val KEY_DX = "pill_dx"
    const val KEY_DY = "pill_dy"
    const val KEY_AUTO = "pill_auto"
    const val KEY_SCALE = "pill_scale"
    const val KEY_HIDE_DATA_ON_WIFI = "pill_hide_data_on_wifi"

    private val main = Handler(Looper.getMainLooper())
    private var rootContainer: LinearLayout? = null
    private var dataTextView: TextView? = null
    private var meterView: NetworkLedMeterView? = null
    private var params: WindowManager.LayoutParams? = null

    private data class Reading(
        val text: String,
        val packPercent: Int?,
        val qualityGrade: LiveNetworkQuality.QualityGrade?,
        val rxSpeed: Long,
        val txSpeed: Long,
        val showNetworkMeter: Boolean,
    )
    private var lastReading: Reading? = null

    @Volatile
    private var draggable = false

    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun offsets(context: Context): Pair<Int, Int> {
        val prefs = prefs(context)
        return prefs.getInt(KEY_DX, 0) to prefs.getInt(KEY_DY, 0)
    }

    fun isAuto(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun scale(context: Context): Float = prefs(context).getFloat(KEY_SCALE, 1.0f).coerceIn(0.7f, 1.5f)

    fun setScale(context: Context, newScale: Float) {
        val app = context.applicationContext
        prefs(app).edit().putFloat(KEY_SCALE, newScale.coerceIn(0.7f, 1.5f)).apply()
        redraw(app)
    }

    fun isHideDataOnWifi(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_DATA_ON_WIFI, false)

    fun setHideDataOnWifi(context: Context, hide: Boolean) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean(KEY_HIDE_DATA_ON_WIFI, hide).apply()
        redraw(app)
    }

    fun moveBy(context: Context, dx: Int, dy: Int) {
        val app = context.applicationContext
        nudge(app, dx, dy)
        redraw(app)
    }

    private fun nudge(context: Context, dx: Int, dy: Int) {
        val (x, y) = offsets(context)
        prefs(context).edit()
            .putInt(KEY_DX, x + dx)
            .putInt(KEY_DY, y + dy)
            .putBoolean(KEY_AUTO, false)
            .apply()
    }

    fun setDraggable(context: Context, on: Boolean) {
        draggable = on
        val app = context.applicationContext
        main.post {
            val root = rootContainer ?: return@post
            val layout = params ?: return@post
            val manager = root.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            layout.flags = flags(app)
            try {
                manager.updateViewLayout(root, layout)
            } catch (e: Exception) {
                // Window went away.
            }
        }
    }

    private fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun flags(context: Context): Int {
        val landscape = isLandscape(context)
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (draggable && !landscape) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    fun resetPosition(context: Context) {
        val app = context.applicationContext
        prefs(app).edit()
            .putInt(KEY_DX, 0)
            .putInt(KEY_DY, 0)
            .putBoolean(KEY_AUTO, true)
            .apply()
        redraw(app)
    }

    fun redraw(context: Context) {
        val app = context.applicationContext
        main.post {
            val root = rootContainer ?: return@post
            val manager = root.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@post
            val tv = dataTextView ?: return@post
            val meter = meterView ?: return@post
            lastReading?.let { applyStyle(root, tv, meter, app, it) }
            position(app, manager, root)
            root.post { position(app, manager, root) }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var blinkState = false
    private var animCounter = 0

    fun remove() {
        main.post { hide() }
    }

    private fun hide() {
        val current = rootContainer ?: return
        val manager = current.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            manager?.removeViewImmediate(current)
        } catch (e: Exception) {
            // Already detached.
        }
        rootContainer = null
        dataTextView = null
        meterView = null
        lastReading = null
        params = null
    }

    private fun applyStyle(
        root: LinearLayout,
        tv: TextView,
        meter: NetworkLedMeterView,
        context: Context,
        reading: Reading,
    ) {
        val rawText = reading.text
        val packPercent = reading.packPercent.takeIf { reading.showNetworkMeter }
        val currentScale = scale(context)
        root.setPadding(
            (dp(context, 7) * currentScale).toInt(),
            (dp(context, 2) * currentScale).toInt(),
            (dp(context, 7) * currentScale).toInt(),
            (dp(context, 2) * currentScale).toInt(),
        )
        // GONE (not INVISIBLE) releases the meter's width as well as hiding its LEDs.
        meter.visibility = if (reading.showNetworkMeter) View.VISIBLE else View.GONE
        meter.layoutParams = (meter.layoutParams as LinearLayout.LayoutParams).apply {
            width = (dp(context, 34) * currentScale).toInt()
            height = (dp(context, 14) * currentScale).toInt()
            marginStart = if (rawText.isNotBlank() && reading.showNetworkMeter) {
                (dp(context, 5) * currentScale).toInt()
            } else 0
        }
        val landscape = isLandscape(context)

        // Landscape touch & opacity handling: make unobtrusively transparent in landscape mode
        val bgAlpha = if (landscape) 0x66 else 0xE6
        val strokeAlpha = if (landscape) 0x33 else 0x66

        val gd = GradientDrawable().apply {
            cornerRadius = (dp(context, 8) * currentScale).coerceAtLeast(dp(context, 4).toFloat())
            setColor((bgAlpha shl 24) or 0x000000)
        }

        // Pack alert styling
        val textColor: Int
        if (packPercent == null) {
            textColor = Color.parseColor("#E8FBFF")
            gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), (strokeAlpha shl 24) or 0x38BDF8)
        } else {
            val isFlashingStep = (packPercent >= 75)
            when {
                packPercent >= 90 -> {
                    if (isFlashingStep && blinkState) {
                        textColor = Color.parseColor("#FFFFFF")
                        gd.setColor((bgAlpha shl 24) or 0xCC0000)
                        gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), Color.parseColor("#FFFF3B30"))
                    } else {
                        textColor = Color.parseColor("#FF5252")
                        gd.setColor((bgAlpha shl 24) or 0x000000)
                        gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), Color.parseColor("#FF3B30"))
                    }
                }
                packPercent >= 75 -> {
                    if (isFlashingStep && blinkState) {
                        textColor = Color.parseColor("#FFFFFF")
                        gd.setColor((bgAlpha shl 24) or 0xB87800)
                        gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), Color.parseColor("#FFFFD700"))
                    } else {
                        textColor = Color.parseColor("#FFB74D")
                        gd.setColor((bgAlpha shl 24) or 0x000000)
                        gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), Color.parseColor("#FFA726"))
                    }
                }
                else -> {
                    textColor = Color.parseColor("#E8FBFF")
                    gd.setStroke((dp(context, 1) * currentScale).toInt().coerceAtLeast(1), (strokeAlpha shl 24) or 0x38BDF8)
                }
            }
        }

        if (rawText.isBlank()) {
            tv.visibility = View.GONE
        } else {
            tv.visibility = View.VISIBLE
            val sb = SpannableStringBuilder(rawText)
            sb.setSpan(ForegroundColorSpan(textColor), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = sb
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f * currentScale)
        }

        root.background = gd
        meter.scaleFactor = currentScale
        meter.isDownloadActive = reading.rxSpeed > 200L
        meter.isUploadActive = reading.txSpeed > 200L
        meter.qualityGrade = reading.qualityGrade
        meter.animStep = animCounter
        if (reading.showNetworkMeter) meter.invalidate()
        root.requestLayout()
    }

    fun update(
        context: Context,
        text: String?,
        packPercent: Int? = null,
        qualityGrade: LiveNetworkQuality.QualityGrade? = null,
        rxSpeed: Long = 0L,
        txSpeed: Long = 0L,
        showNetworkMeter: Boolean = true,
        stillCurrent: () -> Boolean = { true },
    ) {
        if (!canDraw(context)) {
            remove()
            return
        }
        val app = context.applicationContext
        val reading = Reading(text.orEmpty(), packPercent, qualityGrade, rxSpeed, txSpeed, showNetworkMeter)
        main.post {
            // The service can be stopped or a newer plug event can arrive BETWEEN the
            // worker's last check and this UI frame. Never resurrect a cancelled refresh.
            if (stillCurrent()) show(app, reading)
        }
    }

    private fun show(context: Context, reading: Reading) {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val root = rootContainer ?: createPill(context, manager) ?: return
        val tv = dataTextView ?: return
        val meter = meterView ?: return

        lastReading = reading
        blinkState = !blinkState
        animCounter++
        applyStyle(root, tv, meter, context, reading)
        position(context, manager, root)
        root.post { position(context, manager, root) }
    }

    private fun createPill(context: Context, manager: WindowManager): LinearLayout? {
        val currentScale = scale(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (dp(context, 7) * currentScale).toInt(),
                (dp(context, 2) * currentScale).toInt(),
                (dp(context, 7) * currentScale).toInt(),
                (dp(context, 2) * currentScale).toInt()
            )
        }

        val tv = TextView(context).apply {
            setTextColor(Color.parseColor("#E8FBFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f * currentScale)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setSingleLine(true)
        }

        val meter = NetworkLedMeterView(context).apply {
            scaleFactor = currentScale
            val lp = LinearLayout.LayoutParams(
                (dp(context, 34) * currentScale).toInt(),
                (dp(context, 14) * currentScale).toInt()
            ).apply {
                marginStart = (dp(context, 5) * currentScale).toInt()
            }
            layoutParams = lp
        }

        root.addView(tv)
        root.addView(meter)

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            flags(context),
            PixelFormat.TRANSLUCENT,
        )
        layout.gravity = Gravity.TOP or Gravity.START

        var lastX = 0f
        var lastY = 0f
        root.setOnTouchListener { v, event ->
            if (!draggable || isLandscape(context)) return@setOnTouchListener false
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
                        position(context, manager, root)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        return try {
            manager.addView(root, layout)
            rootContainer = root
            dataTextView = tv
            meterView = meter
            params = layout
            root
        } catch (e: Exception) {
            null
        }
    }

    private fun position(context: Context, manager: WindowManager, root: View) {
        val layout = params ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val statusHeight = statusBarHeight(context)
        // Re-measure AFTER a mode/size change; root.width can still be the old, wider
        // data+meter layout until the next window frame and would misplace the watts pill.
        root.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST),
        )
        val width = root.measuredWidth.coerceAtLeast(dp(context, 28))
        val height = root.measuredHeight.coerceAtLeast(dp(context, 14))
        layout.flags = flags(context)
        val margin = dp(context, 2)

        var x = (screenWidth - width) / 2
        var y = (statusHeight - height) / 2

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

        if (!isAuto(context)) {
            val (dx, dy) = offsets(context)
            x += dx
            y += dy
        }

        layout.x = x.coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
        layout.y = y.coerceIn(0, (screenHeight - height - margin).coerceAtLeast(0))
        try {
            manager.updateViewLayout(root, layout)
        } catch (e: Exception) {
            // Window went away between frames.
        }
    }

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
