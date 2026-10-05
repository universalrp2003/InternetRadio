package com.universalrp.cleansweep.data

import android.os.Build
import android.os.Environment
import android.os.StatFs

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (total <= 0L) 0f else (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

object StorageInfoProvider {
    fun read(): StorageInfo {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageInfo(stat.totalBytes, stat.availableBytes)
    }
}

/** Human readable byte formatting: 1.2 GB, 345.6 MB, … */
fun Long.formatBytes(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (i == 0) "$this B" else "%.1f %s".format(value, units[i])
}

/** Display path relative to internal storage, e.g. /Download/app.apk */
fun String.shortPath(): String {
    val root = Environment.getExternalStorageDirectory().absolutePath
    val stripped = removePrefix(root)
    return if (stripped.isEmpty()) "/" else stripped
}

fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
