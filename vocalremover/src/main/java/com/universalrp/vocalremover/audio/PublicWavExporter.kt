package com.universalrp.vocalremover.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.universalrp.vocalremover.processing.ProcessorHost
import kotlinx.coroutines.CancellationException
import java.io.File

/** Save a completed WAV under Music/Vocal Remover Studio using Android's scoped MediaStore API. */
class PublicWavExporter(private val context: Context) {
    suspend fun export(
        source: File,
        displayName: String,
        existingUri: String,
        host: ProcessorHost,
        onUriReady: (String) -> Unit,
        onProgress: (Float) -> Unit,
    ): String {
        check(source.isFile && source.length() >= 44L) { "The WAV file is missing or incomplete" }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Vocal Remover Studio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val prior = existingUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val reusableUri = prior?.takeIf { candidate ->
            runCatching { resolver.update(candidate, values, null, null) > 0 }.getOrDefault(false)
        }
        val uri = reusableUri ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android could not create the WAV file in Music")
        var uriPersisted = false

        try {
            onUriReady(uri.toString())
            uriPersisted = true
            val output = resolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Android could not open the WAV output file")
            source.inputStream().buffered().use { input ->
                output.buffered().use { target ->
                    val buffer = ByteArray(512 * 1024)
                    var copied = 0L
                    while (true) {
                        if (!host.checkpoint()) throw CancellationException("Canceled while saving WAV")
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        copied += count
                        onProgress((copied.toFloat() / source.length()).coerceIn(0f, 1f))
                    }
                    target.flush()
                }
            }
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) > 0) { "Android could not finish publishing the WAV" }
            onProgress(1f)
            return uri.toString()
        } catch (t: Throwable) {
            // Keep a known row so restart/retry can rewrite it from the private WAV. If the
            // first durable URI callback failed, discard the newly-created hidden row.
            if (!uriPersisted && reusableUri == null) runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }
}
