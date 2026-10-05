package com.universalrp.tamilnadufm.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.universalrp.tamilnadufm.data.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    color: Color = SurfaceC,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = color) {
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) Color(0xFF2A1200) else TextPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Saffron else SurfaceHigh)
            .border(1.dp, if (selected) Saffron else OutlineC, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun IconChip(
    icon: ImageVector,
    label: String,
    tint: Color = Saffron,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHigh)
            .border(1.dp, OutlineC, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small station artwork: the real logo when we can fetch it, a monogram otherwise. */
private object LogoCache {
    private val cache = object : LruCache<String, Bitmap>(120) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

private val MONOGRAM_COLORS = listOf(
    Color(0xFFFF8A3D), Color(0xFFFFD166), Color(0xFF2FD8C4),
    Color(0xFF8B5CF6), Color(0xFFF472B6), Color(0xFF60A5FA),
)

@Composable
fun StationLogo(station: RadioStation, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, station.favicon) {
        val url = station.favicon
        if (!url.startsWith("http")) return@produceState
        val cached = LogoCache.get(url)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val downloaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "TamilnaduFMRadio/1.0")
                }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.also { bitmap ->
                        LogoCache.put(url, bitmap)
                    }
                }
            }.getOrNull()
        }
        value = downloaded
    }

    val shape = RoundedCornerShape(size / 4)
    val picture = bitmap
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        if (picture != null) {
            Image(
                bitmap = picture.asImageBitmap(),
                contentDescription = station.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
        } else {
            val color = MONOGRAM_COLORS[
                kotlin.math.abs(station.name.hashCode()) % MONOGRAM_COLORS.size
            ]
            Box(
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    station.monogram,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
