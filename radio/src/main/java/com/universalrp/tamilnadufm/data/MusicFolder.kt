package com.universalrp.tamilnadufm.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object MusicFolder {
    suspend fun read(context: Context, tree: Uri): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to 0)
        val seen = mutableSetOf<String>()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, depth) = pending.removeFirst()
            if (!seen.add(id)) continue
            check(seen.size <= 2000 && tracks.size <= 10000 && depth <= 20) { "Folder too large; select a smaller music folder" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: error("Provider could not read folder")
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val child = it.getString(0); val name = it.getString(1).orEmpty(); val mime = it.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(child to depth + 1)
                    else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus")) {
                        tracks.add(LocalTrack(DocumentsContract.buildDocumentUriUsingTree(tree, child), name, "", 0))
                    }
                }
            }
        }
        return tracks.sortedBy { it.title.lowercase() }
    }
}
