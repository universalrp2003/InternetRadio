package com.universalrp.vocalremover.processing

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** A persisted audio item; source URIs keep their document-provider read grant across restarts. */
data class AudioQueueItem(
    val id: String,
    val sourceName: String,
    val sourceUri: String,
    val profile: ProcessingProfile,
    val status: AudioQueueStatus = AudioQueueStatus.WAITING,
    val message: String = "Waiting in queue",
    val outputUri: String = "",
    val firstPassOutputUri: String = "",
    val mp3SourceUri: String = "",
    val mp3OutputUri: String = "",
    val separationPass: SeparationPass = SeparationPass.FIRST,
    val addedAtMs: Long = System.currentTimeMillis(),
)

enum class AudioQueueStatus { WAITING, PROCESSING, PAUSED, COMPLETED, FAILED, CANCELED }

data class QueueEnqueueResult(
    val added: Int,
    val duplicates: Int,
    val queueFull: Int,
)

internal data class QueueAdmissionPlan(
    val acceptedItems: List<AudioQueueItem>,
    val result: QueueEnqueueResult,
)

internal fun planQueueAdmission(
    existing: List<AudioQueueItem>,
    candidates: List<AudioQueueItem>,
    maxPendingItems: Int,
): QueueAdmissionPlan {
    val pendingStatuses = setOf(AudioQueueStatus.WAITING, AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED)
    val activeUris = existing.asSequence()
        .filter { it.status in pendingStatuses }
        .map { it.sourceUri }
        .toMutableSet()
    var duplicates = 0
    var queueFull = 0
    val accepted = mutableListOf<AudioQueueItem>()
    var remaining = (maxPendingItems - existing.count { it.status in pendingStatuses }).coerceAtLeast(0)

    candidates.forEach { candidate ->
        if (candidate.sourceUri in activeUris) {
            duplicates++
        } else if (remaining == 0) {
            queueFull++
        } else {
            accepted += candidate.copy(
                status = AudioQueueStatus.WAITING,
                message = "Waiting in queue",
                outputUri = "",
                firstPassOutputUri = "",
                mp3SourceUri = "",
                mp3OutputUri = "",
                separationPass = SeparationPass.FIRST,
            )
            activeUris += candidate.sourceUri
            remaining--
        }
    }
    return QueueAdmissionPlan(
        acceptedItems = accepted,
        result = QueueEnqueueResult(accepted.size, duplicates, queueFull),
    )
}

/** Durable, FIFO, single-worker queue for unattended multi-song processing. */
object ProcessingQueueStore {
    private const val MAX_PENDING_ITEMS = 100
    private const val MAX_FINISHED_HISTORY = 100
    private val lock = Any()
    private val mutableItems = MutableStateFlow<List<AudioQueueItem>>(emptyList())
    val items: StateFlow<List<AudioQueueItem>> = mutableItems.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) {
            mutableItems.value = read(context)
        }
    }

    fun current(context: Context): List<AudioQueueItem> = synchronized(lock) {
        mutableItems.value.ifEmpty { read(context).also { mutableItems.value = it } }
    }

    fun enqueue(context: Context, candidates: List<AudioQueueItem>): QueueEnqueueResult = synchronized(lock) {
        val existing = current(context)
        val plan = planQueueAdmission(existing, candidates, MAX_PENDING_ITEMS)
        if (plan.acceptedItems.isNotEmpty()) persist(context, trimHistory(existing + plan.acceptedItems))
        plan.result
    }

    fun update(
        context: Context,
        id: String,
        transform: (AudioQueueItem) -> AudioQueueItem,
    ) = synchronized(lock) {
        val next = current(context).map { item -> if (item.id == id) transform(item) else item }
        if (next != mutableItems.value) persist(context, next)
    }

    fun find(context: Context, id: String): AudioQueueItem? = current(context).firstOrNull { it.id == id }

    fun nextWaiting(context: Context): AudioQueueItem? = current(context).firstOrNull { it.status == AudioQueueStatus.WAITING }

    fun removeWaiting(context: Context, id: String): Boolean = synchronized(lock) {
        val existing = current(context)
        val filtered = existing.filterNot { it.id == id && it.status == AudioQueueStatus.WAITING }
        if (filtered.size == existing.size) return false
        persist(context, filtered)
        true
    }

    fun retry(context: Context, id: String): Boolean = synchronized(lock) {
        val existing = current(context)
        val failed = existing.firstOrNull { it.id == id && it.status == AudioQueueStatus.FAILED } ?: return false
        if (existing.count { it.status in PENDING_STATUSES } >= MAX_PENDING_ITEMS) return false
        val next = existing.map { item ->
            if (item.id == failed.id) item.copy(
                status = AudioQueueStatus.WAITING,
                message = "Queued to retry",
                outputUri = "",
                firstPassOutputUri = "",
                mp3SourceUri = "",
                mp3OutputUri = "",
                separationPass = SeparationPass.FIRST,
                addedAtMs = System.currentTimeMillis(),
            ) else item
        }
        persist(context, next)
        true
    }

    fun cancelWaiting(context: Context, message: String = "Removed when the queue was stopped") = synchronized(lock) {
        val next = current(context).map { item ->
            if (item.status == AudioQueueStatus.WAITING) item.copy(status = AudioQueueStatus.CANCELED, message = message)
            else item
        }
        if (next != mutableItems.value) persist(context, trimHistory(next))
    }

    fun clearFinished(context: Context): List<AudioQueueItem> = synchronized(lock) {
        val existing = current(context)
        val removed = existing.filter { it.status in FINISHED_STATUSES }
        val next = existing - removed.toSet()
        if (removed.isNotEmpty()) persist(context, next)
        removed
    }

    private fun trimHistory(items: List<AudioQueueItem>): List<AudioQueueItem> {
        val pending = items.filter { it.status in PENDING_STATUSES }
        val history = items.filter { it.status in FINISHED_STATUSES }.takeLast(MAX_FINISHED_HISTORY)
        return (pending + history).sortedBy { it.addedAtMs }
    }

    private fun persist(context: Context, items: List<AudioQueueItem>) {
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("sourceName", item.sourceName)
                    put("sourceUri", item.sourceUri)
                    put("profile", item.profile.name)
                    put("status", item.status.name)
                    put("message", item.message)
                    put("outputUri", item.outputUri)
                    put("firstPassOutputUri", item.firstPassOutputUri)
                    put("mp3SourceUri", item.mp3SourceUri)
                    put("mp3OutputUri", item.mp3OutputUri)
                    put("separationPass", item.separationPass.name)
                    put("addedAtMs", item.addedAtMs)
                })
            }
        }
        val file = AtomicFile(queueFile(context))
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            stream?.let(file::failWrite)
            throw failure
        }
        mutableItems.value = items.toList()
    }

    private fun read(context: Context): List<AudioQueueItem> = runCatching {
        val atomic = AtomicFile(queueFile(context))
        val base = atomic.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists()) return emptyList()
        val array = atomic.openRead().bufferedReader().use { JSONArray(it.readText()) }
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
                val sourceUri = json.optString("sourceUri").takeIf { it.isNotBlank() } ?: continue
                add(
                    AudioQueueItem(
                        id = id,
                        sourceName = json.optString("sourceName", "selected-song.mp3"),
                        sourceUri = sourceUri,
                        profile = runCatching { ProcessingProfile.valueOf(json.optString("profile")) }
                            .getOrDefault(ProcessingProfile.BALANCED),
                        status = runCatching { AudioQueueStatus.valueOf(json.optString("status")) }
                            .getOrDefault(AudioQueueStatus.WAITING),
                        message = json.optString("message", "Waiting in queue"),
                        outputUri = json.optString("outputUri"),
                        firstPassOutputUri = json.optString("firstPassOutputUri"),
                        mp3SourceUri = json.optString("mp3SourceUri"),
                        mp3OutputUri = json.optString("mp3OutputUri"),
                        separationPass = runCatching { SeparationPass.valueOf(json.optString("separationPass")) }
                            .getOrDefault(SeparationPass.FIRST),
                        addedAtMs = json.optLong("addedAtMs", System.currentTimeMillis()),
                    ),
                )
            }
        }.let(::trimHistory)
    }.getOrDefault(emptyList())

    private fun queueFile(context: Context): File = File(context.filesDir, "processing-queue.json")

    private val PENDING_STATUSES = setOf(AudioQueueStatus.WAITING, AudioQueueStatus.PROCESSING, AudioQueueStatus.PAUSED)
    private val FINISHED_STATUSES = setOf(AudioQueueStatus.COMPLETED, AudioQueueStatus.FAILED, AudioQueueStatus.CANCELED)
}
