package com.universalrp.cleansweep.notify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import java.util.Locale

/**
 * Creates dynamic status bar notification icons showing real-time mobile data count in GB or MB.
 *
 * For instance:
 *  - 0 MB -> "0" / "MB"
 *  - 106 MB -> "106" / "M"
 *  - 1.45 GB -> "1.5" / "GB"
 *  - 2.8 GB -> "2.8" / "GB"
 */
object DataIconFactory {

    fun createIcon(bytes: Long): IconCompat {
        val (numStr, unitStr) = formatShort(bytes)
        return generateBitmapIcon(numStr, unitStr)
    }

    fun formatShort(bytes: Long): Pair<String, String> {
        if (bytes <= 0L) return "0" to "MB"
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0
        return if (gb >= 1.0) {
            val gbStr = if (gb >= 10.0) {
                String.format(Locale.US, "%.0f", gb)
            } else {
                String.format(Locale.US, "%.1f", gb)
            }
            gbStr to "GB"
        } else if (mb >= 100.0) {
            String.format(Locale.US, "%.0f", mb) to "M"
        } else if (mb >= 1.0) {
            String.format(Locale.US, "%.0f", mb) to "MB"
        } else {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.0f", kb) to "K"
        }
    }

    private fun generateBitmapIcon(numText: String, unitText: String): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintNumber = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when {
                numText.length <= 2 -> 48f
                numText.length == 3 -> 40f
                numText.length == 4 -> 32f
                else -> 26f
            }
        }

        val paintUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (unitText.length > 2) 22f else 26f
        }

        // Draw top number and bottom unit inside 96x96 bounds
        canvas.drawText(numText, size / 2f, 46f, paintNumber)
        canvas.drawText(unitText, size / 2f, 82f, paintUnit)

        return IconCompat.createWithBitmap(bitmap)
    }
}
