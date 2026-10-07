package com.universalrp.cleansweep.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Deletes the selected junk and reports how much space was freed. */
class JunkDeleter(private val context: Context) {

    data class Result(
        val freedBytes: Long,
        val deleted: Int,
        val failed: List<String>,
        /** Why the first delete failed — usually a missing storage permission. */
        val firstFailure: String? = null,
    )

    suspend fun delete(
        paths: List<String>,
        onProgress: (done: Int, total: Int, path: String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val selectedSet = paths.toHashSet()
        var freed = 0L
        var deleted = 0
        val failed = mutableListOf<String>()
        var firstFailure: String? = null
        var done = 0
        val total = paths.size

        val dirs = mutableListOf<File>()
        val files = mutableListOf<File>()
        for (p in paths) {
            val f = File(p)
            if (f.isDirectory) dirs.add(f) else files.add(f)
        }

        // Files first.
        for (f in files) {
            coroutineContext.ensureActive()
            val size = try {
                f.length()
            } catch (e: Exception) {
                0L
            }
            var failureReason: String? = null
            val ok = try {
                f.delete()
            } catch (e: Exception) {
                // RecoverableSecurityException / EACCES land here on Android 8-9 when the
                // app has read but not write access to shared storage.
                failureReason = "${e.javaClass.simpleName}: ${e.message ?: "delete refused"}"
                false
            }
            if (ok) {
                freed += size
                deleted++
                removeFromMediaStore(f.absolutePath)
            } else if (f.exists()) {
                failed.add(f.absolutePath)
                if (firstFailure == null) {
                    firstFailure = failureReason ?: "the system refused to delete this file"
                }
            } else {
                deleted++
            }
            done++
            onProgress(done, total, f.absolutePath)
        }

        // Then folders, deepest first. Only delete a folder when all of its
        // children were selected too (so we never remove something the user kept).
        for (d in dirs.sortedByDescending { it.absolutePath.length }) {
            coroutineContext.ensureActive()
            val children = try {
                d.listFiles()
            } catch (e: Exception) {
                null
            }
            val safeToDelete = children != null && children.all { it.absolutePath in selectedSet }
            if (!safeToDelete) {
                if (d.exists()) failed.add(d.absolutePath)
            } else {
                val ok = try {
                    d.delete()
                } catch (e: Exception) {
                    false
                }
                if (ok || !d.exists()) {
                    deleted++
                    removeFromMediaStore(d.absolutePath)
                } else {
                    failed.add(d.absolutePath)
                }
            }
            done++
            onProgress(done, total, d.absolutePath)
        }

        Result(freed, deleted, failed, firstFailure)
    }

    /** Best-effort cleanup so gallery/file managers stop showing deleted media. */
    private fun removeFromMediaStore(path: String) {
        try {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                MediaStore.MediaColumns.DATA + "=?",
                arrayOf(path)
            )
        } catch (e: Exception) {
            // Ignored: the file itself is already gone.
        }
    }
}
