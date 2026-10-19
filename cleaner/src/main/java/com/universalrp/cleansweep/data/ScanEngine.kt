package com.universalrp.cleansweep.data

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Walks the device storage and detects junk:
 * residual/temp files, thumbnail caches, duplicate files, APK installers,
 * empty folders (including folders that become empty once junk is removed),
 * old downloads and large files.
 */
class ScanEngine(private val context: Context) {

    companion object {
        private val JUNK_EXTENSIONS = setOf(
            "tmp", "temp", "bak", "old", "chk", "gid", "dmp", "log",
            "crdownload", "partial", "swp", "err", "stackdump"
        )
        private val JUNK_FILENAMES = setOf(
            "thumbs.db", ".ds_store", "desktop.ini", ".spotlight-v100", ".trashes"
        )
        private const val MAX_HASHED_FILES = 1500
    }

    /**
     * @param kinds when not null, only these categories are collected — that is what the
     *   "Quick cleaning actions" tiles use, so tapping "Duplicates" scans for duplicates
     *   instead of running a full sweep and showing everything.
     */
    suspend fun scan(
        root: File,
        cfg: ScanSettings,
        kinds: Set<JunkKind>? = null,
        onProgress: (ScanProgress) -> Unit,
    ): ScanReport = withContext(Dispatchers.IO) {
        val want: Set<JunkKind> = kinds ?: JunkKind.entries.toSet()
        val categories = mapOf(
            JunkKind.RESIDUAL to CategoryResult(JunkKind.RESIDUAL),
            JunkKind.THUMBNAILS to CategoryResult(JunkKind.THUMBNAILS),
            JunkKind.DUPLICATES to CategoryResult(JunkKind.DUPLICATES),
            JunkKind.APK_FILES to CategoryResult(JunkKind.APK_FILES),
            JunkKind.EMPTY_FOLDERS to CategoryResult(JunkKind.EMPTY_FOLDERS),
            JunkKind.OLD_DOWNLOADS to CategoryResult(JunkKind.OLD_DOWNLOADS),
            JunkKind.LARGE_FILES to CategoryResult(JunkKind.LARGE_FILES),
        )
        val residual = categories.getValue(JunkKind.RESIDUAL)
        val thumbs = categories.getValue(JunkKind.THUMBNAILS)
        val duplicates = categories.getValue(JunkKind.DUPLICATES)
        val apks = categories.getValue(JunkKind.APK_FILES)
        val emptyDirs = categories.getValue(JunkKind.EMPTY_FOLDERS)
        val oldDownloads = categories.getValue(JunkKind.OLD_DOWNLOADS)
        val large = categories.getValue(JunkKind.LARGE_FILES)

        val junkPaths = HashSet<String>()
        // How many children each directory has, and how many of them are junk. Keeping two
        // ints per directory instead of the name of every file on the phone is what keeps
        // this scan inside a phone's memory limit on a full 100 GB volume — the lists this
        // replaced were the difference between a finished scan and an out-of-memory crash.
        val dirChildCount = HashMap<String, Int>()
        val dirJunkCount = HashMap<String, Int>()
        val bySize = HashMap<Long, MutableList<String>>()

        fun countJunkChild(parentPath: String) {
            dirJunkCount[parentPath] = (dirJunkCount[parentPath] ?: 0) + 1
        }

        var filesScanned = 0L
        var dirsScanned = 0L
        var junkBytes = 0L
        var junkCount = 0
        var lastEmit = 0L

        fun addJunk(cat: CategoryResult, file: JunkFile) {
            cat.files.add(file)
            junkBytes += file.size
            junkCount++
        }

        val rootPath = root.absolutePath
        val downloadDir = File(root, "Download").absolutePath
        val downloadPrefix = downloadDir + File.separator
        val oldCutoff = System.currentTimeMillis() - cfg.oldDownloadDays * 24L * 3600L * 1000L

        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            dirsScanned++

            val entries = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            }
            if (entries == null) continue

            if (entries.isEmpty() && dir.absolutePath != rootPath) {
                junkPaths.add(dir.absolutePath)
                dirChildCount[dir.absolutePath] = 0
                // File.parent is the parent *path* (a String); parentFile is a File.
                dir.parent?.let { countJunkChild(it) }
                if (JunkKind.EMPTY_FOLDERS in want) {
                    addJunk(emptyDirs, JunkFile(dir.absolutePath, 0L, dir.lastModified()))
                }
                continue
            }

            val dirPath = dir.absolutePath
            dirChildCount[dirPath] = entries.size
            val isRoot = dirPath == rootPath

            for (f in entries) {
                val abs = f.absolutePath
                val name = f.name
                val lower = name.lowercase()

                if (f.isDirectory) {
                    if (isRoot && name.equals("Android", ignoreCase = true)) continue
                    if (name == "LOST.DIR") continue
                    if (isExcluded(abs, cfg.excludedPrefixes)) continue
                    if (!cfg.includeHidden && name.startsWith(".") && lower != ".thumbnails") continue
                    stack.addLast(f)
                    continue
                }

                filesScanned++
                val size = try {
                    f.length()
                } catch (e: Exception) {
                    0L
                }
                val mod = try {
                    f.lastModified()
                } catch (e: Exception) {
                    0L
                }

                val ext = lower.substringAfterLast('.', "")
                var consumed = false

                when {
                    JUNK_FILENAMES.contains(lower) || JUNK_EXTENSIONS.contains(ext) ||
                        lower.startsWith("~$") -> {
                        junkPaths.add(abs)
                        countJunkChild(dirPath)
                        if (JunkKind.RESIDUAL in want) {
                            addJunk(residual, JunkFile(abs, size, mod))
                        }
                        consumed = true
                    }
                    lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk") -> {
                        if (JunkKind.APK_FILES in want) {
                            val installed = isInstalledApk(abs)
                            if (!cfg.apkOnlyInstalled || installed) {
                                junkPaths.add(abs)
                                countJunkChild(dirPath)
                                addJunk(
                                    apks,
                                    JunkFile(
                                        abs, size, mod,
                                        note = if (installed) "App already installed"
                                        else "App not installed"
                                    )
                                )
                            }
                        }
                        consumed = true
                    }
                    abs.contains("/.thumbnails/") -> {
                        junkPaths.add(abs)
                        countJunkChild(dirPath)
                        if (JunkKind.THUMBNAILS in want) {
                            addJunk(thumbs, JunkFile(abs, size, mod))
                        }
                        consumed = true
                    }
                }

                if (!consumed) {
                    if (JunkKind.DUPLICATES in want && size >= cfg.dupMinBytes) {
                        bySize.getOrPut(size) { mutableListOf() }.add(abs)
                    }
                    if (JunkKind.LARGE_FILES in want && size >= cfg.largeThresholdBytes) {
                        addJunk(large, JunkFile(abs, size, mod))
                    }
                    if (JunkKind.OLD_DOWNLOADS in want && abs.startsWith(downloadPrefix) &&
                        mod in 1 until oldCutoff
                    ) {
                        addJunk(oldDownloads, JunkFile(abs, size, mod))
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastEmit > 120) {
                lastEmit = now
                onProgress(
                    ScanProgress(
                        currentPath = dir.absolutePath,
                        filesScanned = filesScanned,
                        dirsScanned = dirsScanned,
                        junkCount = junkCount,
                        junkBytes = junkBytes,
                    )
                )
            }
        }

        // Promote folders that will become empty once their junk children are deleted.
        val snapshot = if (JunkKind.EMPTY_FOLDERS in want) junkPaths.toList() else emptyList()
        for (p in snapshot) {
            // parentFile, not parent: Java's File.getParent() hands back a String path.
            var parent = File(p).parentFile ?: continue
            while (parent.absolutePath != rootPath && parent.absolutePath.length > rootPath.length) {
                val total = dirChildCount[parent.absolutePath] ?: break
                val junkKids = dirJunkCount[parent.absolutePath] ?: 0
                if (total == 0 || junkKids < total) break
                if (junkPaths.add(parent.absolutePath)) {
                    addJunk(emptyDirs, JunkFile(parent.absolutePath, 0L, parent.lastModified()))
                    parent.parentFile?.let { countJunkChild(it.absolutePath) }
                }
                parent = parent.parentFile ?: break
            }
        }

        // Partial hashes only narrow the candidate set. Every file proposed as a duplicate
        // is SHA-256 hashed in full before the scanner labels anything as identical.
        val scanContext = coroutineContext
        var hashed = 0
        var duplicateScanCapped = false
        val sizeGroups = if (JunkKind.DUPLICATES in want) {
            bySize.entries.filter { it.value.size > 1 }.sortedByDescending { it.key }
        } else {
            emptyList()
        }
        sizeGroupLoop@ for ((size, paths) in sizeGroups) {
            val byPartialHash = HashMap<String, MutableList<String>>()
            for (path in paths) {
                if (path in junkPaths) continue
                if (hashed >= MAX_HASHED_FILES) {
                    duplicateScanCapped = true
                    break@sizeGroupLoop
                }
                hashed++
                val partial = DuplicateContentHash.partialCandidateHash(File(path), size) ?: continue
                byPartialHash.getOrPut(partial) { mutableListOf() }.add(path)
            }
            for (candidatePaths in byPartialHash.values) {
                if (candidatePaths.size < 2) continue
                val byFullHash = HashMap<String, MutableList<File>>()
                for (path in candidatePaths) {
                    scanContext.ensureActive()
                    val file = File(path)
                    val fullHash = try {
                        DuplicateContentHash.fullSha256(file, size) { scanContext.ensureActive() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    byFullHash.getOrPut(fullHash) { mutableListOf() }.add(file)
                }
                for (hashGroup in byFullHash.values) {
                    if (hashGroup.size < 2) continue
                    val exactGroups = mutableListOf<MutableList<File>>()
                    for (candidate in hashGroup) {
                        scanContext.ensureActive()
                        val exact = exactGroups.firstOrNull { files ->
                            val representative = files.first()
                            try {
                                DuplicateContentHash.sameContents(representative, candidate, size) {
                                    scanContext.ensureActive()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (exact == null) exactGroups.add(mutableListOf(candidate)) else exact.add(candidate)
                    }
                    for (exactGroup in exactGroups) {
                        if (exactGroup.size < 2) continue
                        val sorted = exactGroup.sortedByDescending { it.lastModified() }
                        for (file in sorted.drop(1)) {
                            if (junkPaths.add(file.absolutePath)) {
                                addJunk(
                                    duplicates,
                                    JunkFile(
                                        file.absolutePath, file.length(), file.lastModified(),
                                        note = "Newest identical copy is kept"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        onProgress(
            ScanProgress(
                currentPath = rootPath,
                filesScanned = filesScanned,
                dirsScanned = dirsScanned,
                junkCount = junkCount,
                junkBytes = junkBytes,
            )
        )

        val report = ScanReport(
            categories = listOf(residual, thumbs, duplicates, apks, emptyDirs, oldDownloads, large)
                .filter { it.files.isNotEmpty() },
            filesScanned = filesScanned,
            dirsScanned = dirsScanned,
            completedAt = System.currentTimeMillis(),
            duplicateScanCapped = duplicateScanCapped,
        )
        // CleanSweep ticks nothing on its own: a fresh scan is a list for you to choose from.
        // Settings → "Preselect after a scan" is the opt-in shortcut, per category.
        report.categories.forEach { category ->
            val preset = category.kind in cfg.defaultSelected
            category.files.forEach { file -> file.selected = preset }
        }
        report
    }

    private fun isExcluded(path: String, prefixes: Set<String>): Boolean =
        prefixes.any { path == it || path.startsWith(it + File.separator) }

    private fun isInstalledApk(path: String): Boolean = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(path, 0)
        val pkg = info?.packageName ?: return false
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    } catch (e: Exception) {
        false
    }

}
