package com.universalrp.cleansweep.data

/** Categories of junk that the scanner can detect. */
enum class JunkKind(val label: String, val description: String, val defaultSelected: Boolean) {
    RESIDUAL(
        "Residual & temp files",
        "Leftover .tmp, .log, .bak, partial downloads and other garbage files",
        true
    ),
    THUMBNAILS(
        "Thumbnail cache",
        "Hidden .thumbnails image caches that the gallery rebuilds automatically",
        true
    ),
    DUPLICATES(
        "Duplicate files",
        "Identical files found more than once — the newest copy is always kept",
        true
    ),
    APK_FILES(
        "APK installer files",
        "Downloaded installer packages that are no longer needed",
        true
    ),
    EMPTY_FOLDERS(
        "Empty folders",
        "Folders with no content, including folders left empty after cleaning",
        true
    ),
    OLD_DOWNLOADS(
        "Old downloads",
        "Files in your Download folder older than the limit you set (off by default)",
        false
    ),
    LARGE_FILES(
        "Large files",
        "Very large files you may want to review (never deleted without your OK)",
        false
    ),
}

/** A single file or folder that can be cleaned. */
data class JunkFile(
    val path: String,
    val size: Long,
    val modified: Long,
    var selected: Boolean = true,
    val note: String? = null,
)

/** All junk found for one category. */
class CategoryResult(val kind: JunkKind) {
    val files: MutableList<JunkFile> = mutableListOf()

    val totalBytes: Long get() = files.sumOf { it.size }
    val selectedBytes: Long get() = files.filter { it.selected }.sumOf { it.size }
    val selectedCount: Int get() = files.count { it.selected }
    val allSelected: Boolean get() = files.isNotEmpty() && files.all { it.selected }

    fun setAll(selected: Boolean) {
        files.forEach { it.selected = selected }
    }
}

/** Full result of a scan. */
data class ScanReport(
    val categories: List<CategoryResult>,
    val filesScanned: Long,
    val dirsScanned: Long,
    val completedAt: Long,
) {
    val totalBytes: Long get() = categories.sumOf { it.totalBytes }
    val totalCount: Int get() = categories.sumOf { it.files.size }

    fun selectedBytes(): Long = categories.sumOf { it.selectedBytes }
    fun selectedCount(): Int = categories.sumOf { it.selectedCount }
    fun selectedPaths(): List<String> =
        categories.flatMap { c -> c.files.filter { it.selected }.map { it.path } }
}

/** Live progress while scanning. */
data class ScanProgress(
    val currentPath: String,
    val filesScanned: Long,
    val dirsScanned: Long,
    val junkCount: Int,
    val junkBytes: Long,
)

/** User-tunable scan options (persisted via DataStore). */
data class ScanSettings(
    val includeHidden: Boolean = false,
    val dupMinBytes: Long = 100L * 1024L,
    val largeThresholdBytes: Long = 200L * 1024L * 1024L,
    val oldDownloadDays: Int = 30,
    val apkOnlyInstalled: Boolean = false,
    val excludedPrefixes: Set<String> = emptySet(),
)
