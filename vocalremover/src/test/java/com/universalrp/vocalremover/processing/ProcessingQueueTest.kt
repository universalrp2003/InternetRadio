package com.universalrp.vocalremover.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingQueueTest {
    @Test
    fun skipsPendingDuplicatesButAllowsRequeueAfterCompletion() {
        val completed = item("done", "content://songs/old", AudioQueueStatus.COMPLETED)
        val waiting = item("waiting", "content://songs/next", AudioQueueStatus.WAITING)
        val plan = planQueueAdmission(
            existing = listOf(completed, waiting),
            candidates = listOf(
                item("dup", "content://songs/next"),
                item("repeat", "content://songs/old"),
                item("new", "content://songs/new"),
            ),
            maxPendingItems = 10,
        )

        assertEquals(2, plan.result.added)
        assertEquals(1, plan.result.duplicates)
        assertEquals(0, plan.result.queueFull)
        assertEquals(listOf("content://songs/old", "content://songs/new"), plan.acceptedItems.map { it.sourceUri })
        assertTrue(plan.acceptedItems.all { it.status == AudioQueueStatus.WAITING })
    }

    @Test
    fun enforcesPendingLimitWithoutDroppingEarlierEntries() {
        val existing = listOf(
            item("one", "content://songs/one", AudioQueueStatus.WAITING),
            item("two", "content://songs/two", AudioQueueStatus.PAUSED),
        )
        val plan = planQueueAdmission(
            existing = existing,
            candidates = listOf(
                item("dup", "content://songs/one"),
                item("three", "content://songs/three"),
                item("four", "content://songs/four"),
            ),
            maxPendingItems = 3,
        )

        assertEquals(1, plan.result.added)
        assertEquals(1, plan.result.duplicates)
        assertEquals(1, plan.result.queueFull)
        assertEquals("content://songs/three", plan.acceptedItems.single().sourceUri)
    }

    private fun item(
        id: String,
        uri: String,
        status: AudioQueueStatus = AudioQueueStatus.WAITING,
    ) = AudioQueueItem(
        id = id,
        sourceName = "$id.mp3",
        sourceUri = uri,
        profile = ProcessingProfile.BALANCED,
        status = status,
    )
}
