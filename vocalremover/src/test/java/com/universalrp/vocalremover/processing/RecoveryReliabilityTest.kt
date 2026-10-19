package com.universalrp.vocalremover.processing

import com.universalrp.vocalremover.audio.DecodedPcmInfo
import com.universalrp.vocalremover.health.DeviceSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File

class RecoveryReliabilityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun restartStitchesCommittedChunkWithoutOriginalSourceAccessOrReloadingModel() = runBlocking {
        val workDir = File(temp.root, "job").apply { mkdirs() }
        val chunkDir = File(workDir, "chunks-restart-checkpoint").apply { mkdirs() }
        val committedChunk = File(chunkDir, "chunk_00000.f32")
        DataOutputStream(committedChunk.outputStream()).use { output ->
            repeat(100 * 2) { output.writeFloat(0.25f) }
        }
        val decodedPcm = File(workDir, "decoded-float.pcm").apply { createNewFile() }
        val placeholderModel = File(temp.root, "model.onnx").apply { createNewFile() }
        // Deliberately no source.mp3: its original content:// grant has been revoked.
        val revokedUri = "content://provider/song-with-revoked-access"
        val updates = mutableListOf<ProcessorUpdate>()
        val host = RecordingHost(updates)
        val processor = ChunkedSeparationProcessor { safeDeviceSnapshot() }
        val job = SeparationJob(
            id = "restart-job",
            sourceName = "song.mp3",
            sourceUri = revokedUri,
            outputSampleRate = 8_000,
            status = JobStatus.PAUSED,
            completedChunks = 1,
            totalChunks = 1,
        )

        val output = processor.process(
            job = job,
            workDir = workDir,
            inputPcmFile = decodedPcm,
            decoded = DecodedPcmInfo(sampleRate = 8_000, channels = 1, frames = 100, durationUs = 12_500),
            modelFile = placeholderModel,
            host = host,
            modelCacheKey = "restart-checkpoint",
        )

        assertTrue("Recovered chunk should produce a valid WAV", WavStitcher.isValid(output, 8_000, 100))
        assertTrue(updates.any { it.message == "Recovered saved chunk 1 of 1" })
        assertFalse("A complete checkpoint must not reload the native model", updates.any {
            it.phase == "Loading separation model"
        })
        assertFalse("The revoked source was not reopened or copied", File(workDir, "source.mp3").exists())
        assertFalse(File(workDir, "instrumental-restart-checkpoint.wav.part").exists())
    }

    @Test fun revokedPickerGrantFallsBackToTheDurablePrivateSourceCopy() {
        val workDir = File(temp.root, "source-recovery").apply { mkdirs() }
        val cachedCopy = File(workDir, "source.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val staleSavedPath = File(temp.root, "deleted-original-copy.mp3").absolutePath

        val recovered = PrivateSourceCopyRecovery.findExisting(staleSavedPath, workDir)

        assertNotNull(recovered)
        assertEquals(cachedCopy.canonicalFile, recovered!!.canonicalFile)
    }

    @Test fun lostSourceGrantWithoutAnyPrivateCopyRequiresReopeningTheSource() {
        val workDir = File(temp.root, "empty-source-recovery").apply { mkdirs() }

        assertNull(PrivateSourceCopyRecovery.findExisting("", workDir))
    }

    private fun safeDeviceSnapshot() = DeviceSnapshot(
        thermal = ThermalLevel.NORMAL,
        thermalLabel = "Normal",
        batteryTemperatureC = null,
        cpuTemperatureC = null,
        cpuSensorLabel = null,
        availableRamBytes = 2L * 1024 * 1024 * 1024,
        totalRamBytes = 4L * 1024 * 1024 * 1024,
        appPssBytes = null,
        appNativeHeapBytes = 0L,
        appCpuPercent = null,
        availableStorageBytes = 2L * 1024 * 1024 * 1024,
    )

    private data class ProcessorUpdate(
        val phase: String,
        val message: String,
    )

    private class RecordingHost(private val updates: MutableList<ProcessorUpdate>) : ProcessorHost {
        override suspend fun checkpoint(
            reason: PauseReason?,
            message: String,
            beforePause: () -> Unit,
        ) = true

        override fun isCanceled() = false

        override fun update(
            phase: String,
            progress: Float,
            message: String,
            completedChunks: Int?,
            totalChunks: Int?,
            outputSampleRate: Int?,
            privateOutputPath: String?,
        ) {
            updates += ProcessorUpdate(phase, message)
        }
    }
}
