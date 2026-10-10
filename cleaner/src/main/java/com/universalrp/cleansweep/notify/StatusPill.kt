package com.universalrp.cleansweep.notify

import android.content.Context
import android.content.SharedPreferences
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
import android.view.ViewGroup
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
        val tagW = dp(8f)
        val barStartX = tagW + dp(2.5f)
        val barW = (w - barStartX).coerceAtLeast(dp(18f))
        val barH = dp(2.8f)
        val numSegments = 3
        val segGap = dp(1.5f)
        val totalGaps = (numSegments - 1) * segGap
        val segW = (barW - totalGaps) / numSegments

        val baseColor = when (qualityGrade) {
            LiveNetworkQuality.QualityGrade.TOP_QUALITY -> Color.parseColor("#FFD700")
            LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY -> Color.parseColor("#00E676")
            LiveNetworkQuality.QualityGrade.BAD_QUALITY -> Color.parseColor("#FF3B30")
            null -> Color.parseColor("#38BDF8")
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
 * Expanded status bar pill overlay:
 * Houses the colored `↑↓` indicator, the real-time mobile data count (or charging watts),
 * and the animated dual-track LED meter with [D] and [U] glowing indicator bars.
 */
object StatusPill {

    /** Position prefs, in the same plain file the charging service reads. */
    const val PREFS = "cleansweep_state"
    const val KEY_DX = "pill_dx"
    const val KEY_DY = "pill_dy"
    const val KEY_AUTO = "pill_auto"

    private val main = Handler(Looper.getMainLooper())
    private var rootContainer: LinearLayout? = null
    private var dataTextView: TextView? = null
    private var meterView: NetworkLedMeterView? = null
    private var params: WindowManager.LayoutParams? = null

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

    /** Moves the reading by a number of screen pixels and remembers it. */
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
            layout.flags = flags()
            try {
                manager.updateViewLayout(root, layout)
            } catch (e: Exception) {
                // Window went away.
            }
        }
    }

    private fun flags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (draggable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
        params = null
    }

    private fun applyStyle(
        root: LinearLayout,
        tv: TextView,
        meter: NetworkLedMeterView,
        context: Context,
        rawText: String,
        packPercent: Int?,
        qualityGrade: LiveNetworkQuality.QualityGrade?,
        rxSpeed: Long,
        txSpeed: Long
    ) {
        val gd = GradientDrawable().apply {
            cornerRadius = dp(context, 8).toFloat()
            setColor(Color.parseColor("#E6000000"))
        }

        // Arrow indicator color based on network quality
        val arrowStr = "↑↓ "
        val arrowColor = when (qualityGrade) {
            LiveNetworkQuality.QualityGrade.TOP_QUALITY -> {
                if (blinkState) Color.parseColor("#FFD700") else Color.parseColor("#FFA500")
            }
            LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY -> {
                Color.parseColor("#00E676")
            }
            LiveNetworkQuality.QualityGrade.BAD_QUALITY -> {
                if (blinkState) Color.parseColor("#FF3B30") else Color.parseColor("#88FF3B30")
            }
            null -> Color.parseColor("#38BDF8")
        }

        // Pack alert styling
        val textColor: Int
        if (packPercent == null) {
            textColor = Color.parseColor("#E8FBFF")
            gd.setStroke(dp(context, 1), Color.parseColor("#6638BDF8"))
        } else {
            val isFlashingStep = (packPercent >= 75)
            when {
                packPercent >= 90 -> {
                    if (isFlashingStep && blinkState) {
                        textColor = Color.parseColor("#FFFFFF")
                        gd.setColor(Color.parseColor("#E6CC0000"))
                        gd.setStroke(dp(context, 1), Color.parseColor("#FFFF3B30"))
                    } else {
                        textColor = Color.parseColor("#FF5252")
                        gd.setColor(Color.parseColor("#E0000000"))
                        gd.setStroke(dp(context, 1), Color.parseColor("#FF3B30"))
                    }
                }
                packPercent >= 75 -> {
                    if (isFlashingStep && blinkState) {
                        textColor = Color.parseColor("#FFFFFF")
                        gd.setColor(Color.parseColor("#E6B87800"))
                        gd.setStroke(dp(context, 1), Color.parseColor("#FFFFD700"))
                    } else {
                        textColor = Color.parseColor("#FFB74D")
                        gd.setColor(Color.parseColor("#E0000000"))
                        gd.setStroke(dp(context, 1), Color.parseColor("#FFA726"))
                    }
                }
                else -> {
                    textColor = Color.parseColor("#E8FBFF")
                    gd.setStroke(dp(context, 1), Color.parseColor("#4438BDF8"))
                }
            }
        }

        val sb = SpannableStringBuilder()
        sb.append(arrowStr)
        sb.setSpan(
            ForegroundColorSpan(arrowColor),
            0,
            arrowStr.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val textStart = sb.length
        sb.append(rawText)
        sb.setSpan(
            ForegroundColorSpan(textColor),
            textStart,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tv.text = sb
        root.background = gd

        // Update animated LED meter
        // If speed > 100 bytes/sec, consider active transfer; default active if network is connected
        meter.isDownloadActive = rxSpeed > 200L || (qualityGrade != null && rxSpeed >= 0)
        meter.isUploadActive = txSpeed > 200L || (qualityGrade != null && txSpeed >= 0)
        meter.qualityGrade = qualityGrade
        meter.animStep = animCounter
        meter.invalidate()
    }

    fun update(
        context: Context,
        text: String?,
        packPercent: Int? = null,
        qualityGrade: LiveNetworkQuality.QualityGrade? = null,
        rxSpeed: Long = 0L,
        txSpeed: Long = 0L
    ) {
        if (text.isNullOrBlank() || !canDraw(context)) {
            remove()
            return
        }
        val app = context.applicationContext
        main.post { show(app, text, packPercent, qualityGrade, rxSpeed, txSpeed) }
    }

    private fun show(
        context: Context,
        text: String,
        packPercent: Int? = null,
        qualityGrade: LiveNetworkQuality.QualityGrade? = null,
        rxSpeed: Long = 0L,
        txSpeed: Long = 0L
    ) {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val root = rootContainer ?: createPill(context, manager) ?: return
        val tv = dataTextView ?: return
        val meter = meterView ?: return

        blinkState = !blinkState
        animCounter++

        applyStyle(root, tv, meter, context, text, packPercent, qualityGrade, rxSpeed, txSpeed)
        position(context, manager, root)
        root.post { position(context, manager, root) }
    }

    private fun createPill(context: Context, manager: WindowManager): LinearLayout? {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 7), dp(context, 2), dp(context, 7), dp(context, 2))
        }

        val tv = TextView(context).apply {
            setTextColor(Color.parseColor("#E8FBFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.8f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

        val meter = NetworkLedMeterView(context).apply {
            val lp = LinearLayout.LayoutParams(dp(context, 34), dp(context, 14)).apply {
                marginStart = dp(context, 6)
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
            flags(),
            PixelFormat.TRANSLUCENT,
        )
        layout.gravity = Gravity.TOP or Gravity.START

        var lastX = 0f
        var lastY = 0f
        root.setOnTouchListener { v, event ->
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
        val width = if (root.width > 0) root.width else dp(context, 100)
        val height = if (root.height > 0) root.height else dp(context, 18)
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
