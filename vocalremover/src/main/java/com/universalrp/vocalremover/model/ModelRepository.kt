package com.universalrp.vocalremover.model

import android.content.Context
import android.os.StatFs
import com.universalrp.vocalremover.health.DeviceMonitor
import com.universalrp.vocalremover.processing.ProcessingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fixed, checksum-pinned model download. This class deliberately has no API that accepts an
 * audio URI or file, so song data cannot be accidentally attached to a network request.
 */
class ModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)

    fun installedFile(): File? {
        val installed = modelFile.takeIf { it.isFile && it.length() == MODEL_BYTES && sha256(it) == MODEL_SHA256 }
        if (installed != null) deleteRetiredModels()
        return installed
    }

    suspend fun download(
        checkpoint: suspend () -> Boolean,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        installedFile()?.let { return@withContext it }
        val requiredBytes = MODEL_BYTES + ProcessingPolicy.STORAGE_SAFETY_RESERVE_BYTES
        val availableBytes = StatFs(modelDir.absolutePath).availableBytes
        check(availableBytes >= requiredBytes) {
            "The model needs about ${DeviceMonitor.formatBytes(requiredBytes)} free including a 96 MiB reserve; " +
                "only ${DeviceMonitor.formatBytes(availableBytes)} is available."
        }
        if (modelFile.exists()) modelFile.delete()
        modelDir.mkdirs()
        val part = File(modelDir, "$MODEL_FILENAME.part")
        part.delete()
        var lastError: Throwable? = null

        repeat(3) { attempt ->
            try {
                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("User-Agent", "VocalRemoverStudio/0.1")
                connection.setRequestProperty("Accept", "application/octet-stream")
                try {
                    connection.connect()
                    val finalUrl = connection.url
                    require(finalUrl.protocol == "https" && finalUrl.host in ALLOWED_MODEL_HOSTS) {
                        "The model download redirected to an untrusted host"
                    }
                    check(connection.responseCode in 200..299) {
                        "Model download failed (HTTP ${connection.responseCode})"
                    }
                    val announcedLength = connection.contentLengthLong
                    check(announcedLength < 0L || announcedLength == MODEL_BYTES) {
                        "Unexpected model download size"
                    }
                    connection.inputStream.buffered().use { input ->
                        part.outputStream().buffered().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            var total = 0L
                            while (true) {
                                if (!checkpoint()) throw CancellationException("Canceled while downloading the model")
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= MODEL_BYTES) { "Model download exceeded the pinned size" }
                                output.write(buffer, 0, count)
                                onProgress(total, MODEL_BYTES)
                            }
                            output.flush()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                check(part.length() == MODEL_BYTES) { "Model file is incomplete" }
                check(sha256(part) == MODEL_SHA256) { "Model checksum did not match; download removed" }
                FileOutputStreamSync.sync(part)
                modelFile.delete()
                check(part.renameTo(modelFile)) { "Could not install the downloaded model" }
                deleteRetiredModels()
                return@withContext modelFile
            } catch (t: Throwable) {
                part.delete()
                if (t is CancellationException) throw t
                lastError = t
                if (attempt < 2) kotlinx.coroutines.delay(750L * (attempt + 1))
            }
        }
        throw IllegalStateException(lastError?.message ?: "Model download failed", lastError)
    }

    private fun deleteRetiredModels() {
        listOf(LEGACY_MODEL_FILENAME, "$LEGACY_MODEL_FILENAME.part").forEach { name ->
            runCatching { File(modelDir, name).delete() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private object FileOutputStreamSync {
        fun sync(file: File) {
            java.io.FileOutputStream(file, true).use { it.fd.sync() }
        }
    }

    companion object {
        const val MODEL_FILENAME = "uvr_mdxnet_voc_ft.onnx"
        const val MODEL_BYTES = 66_762_795L
        const val MODEL_SHA256 = "e411182ce2c53541fefdcc99a8f46f2fe03978eb22038b9497c1d9d95a00fad4"
        const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/UVR-MDX-NET-Voc_FT.onnx"
        private const val LEGACY_MODEL_FILENAME = "uvr_mdxnet_9482.onnx"
        private val ALLOWED_MODEL_HOSTS = setOf("github.com", "release-assets.githubusercontent.com")
    }
}
