package com.universalrp.vocalremover.processing

import java.io.File

/**
 * A source picker grant can disappear after restart. The app-private source copy is the
 * recovery authority once it has been committed; a stale saved path is only a hint.
 */
internal object PrivateSourceCopyRecovery {
    fun findExisting(savedPath: String, workDir: File): File? {
        val saved = savedPath.takeIf { it.isNotBlank() }?.let(::File)
        saved?.takeIf { it.isFile && it.length() > 0L }?.let { return it }
        return File(workDir, "source.mp3").takeIf { it.isFile && it.length() > 0L }
    }
}
