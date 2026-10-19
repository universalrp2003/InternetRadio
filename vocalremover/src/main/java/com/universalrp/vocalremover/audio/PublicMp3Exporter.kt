package com.universalrp.vocalremover.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.universalrp.vocalremover.processing.ProcessorHost
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream

/** Encode and publish a separate MP3 in Music/Vocal Remover Studio; the source WAV is untouched. */
class PublicMp3Exporter(private val context: Context) {
    suspend fun export(
        sourceWavUri: String,
        displayName: String,
        existingPendingUri: String,
        host: ProcessorHost,
        onUriReady: (String) -> Unit,
        onProgress: (Float) -> Unit,
    ): String {
        require(sourceWavUri.isNotBlank()) { "The saved WAV location is missing" }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Vocal Remover Studio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val prior = existingPendingUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val reusableUri = prior?.takeIf { candidate ->
            runCatching { resolver.update(candidate, values, null, null) > 0 }.getOrDefault(false)
        }
        val uri = reusableUri ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android could not create the MP3 file in Music")
        var uriPersisted = false

        try {
            onUriReady(uri.toString())
            uriPersisted = true
            val input = resolver.openInputStream(Uri.parse(sourceWavUri))
                ?: throw IllegalStateException("Android could not read the saved WAV")
            input.use { source ->
                val output = resolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Android could not open the MP3 output file")
                BufferedOutputStream(output, IO_BUFFER_BYTES).use { target ->
                    PcmWavToMp3Encoder().encode(
                        source = source,
                        destination = target,
                        checkpoint = { host.checkpoint() },
                        onProgress = onProgress,
                    )
                }
            }
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) > 0) { "Android could not finish publishing the MP3" }
            onProgress(1f)
            return uri.toString()
        } catch (failure: Throwable) {
            if (!uriPersisted && reusableUri == null) runCatching { resolver.delete(uri, null, null) }
            if (failure is CancellationException) throw failure
            throw failure
        }
    }

    companion object {
        private const val IO_BUFFER_BYTES = 256 * 1024
    }
}
