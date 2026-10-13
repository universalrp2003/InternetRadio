package com.universalrp.cleansweep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DuplicateContentHashTest {
    @Test fun exactContentWithMatchingPartialHashIsConfirmedAsDuplicate() {
        withTempDir { dir ->
            val content = sampleContent()
            val first = File(dir, "first.bin").apply { writeBytes(content) }
            val second = File(dir, "second.bin").apply { writeBytes(content.clone()) }

            val firstPartial = DuplicateContentHash.partialCandidateHash(first, content.size.toLong())
            val secondPartial = DuplicateContentHash.partialCandidateHash(second, content.size.toLong())
            assertNotNull(firstPartial)
            assertEquals(firstPartial, secondPartial)
            assertEquals(
                DuplicateContentHash.fullSha256(first, content.size.toLong()),
                DuplicateContentHash.fullSha256(second, content.size.toLong()),
            )
            assertTrue(DuplicateContentHash.sameContents(first, second, content.size.toLong()))
        }
    }

    @Test fun sameSizeAndMatchingSampledEdgesButDifferentMiddleAreNotDuplicates() {
        withTempDir { dir ->
            val original = sampleContent()
            val changed = original.clone().apply { this[size / 2] = (this[size / 2] + 1).toByte() }
            val first = File(dir, "first.bin").apply { writeBytes(original) }
            val second = File(dir, "second.bin").apply { writeBytes(changed) }

            assertEquals(first.length(), second.length())
            assertEquals(
                DuplicateContentHash.partialCandidateHash(first, first.length()),
                DuplicateContentHash.partialCandidateHash(second, second.length()),
            )
            val firstFull = DuplicateContentHash.fullSha256(first, first.length())
            val secondFull = DuplicateContentHash.fullSha256(second, second.length())
            assertNotNull(firstFull)
            assertNotNull(secondFull)
            assertTrue("Full-file hashes must differ when the middle bytes differ", firstFull != secondFull)
            assertTrue("Byte-for-byte confirmation must reject the differing middle", !DuplicateContentHash.sameContents(first, second, first.length()))
        }
    }

    @Test fun changedOrUnreadableFileIsNotGivenAnIdentityHash() {
        withTempDir { dir ->
            val file = File(dir, "file.bin").apply { writeBytes(sampleContent()) }
            assertNull(DuplicateContentHash.partialCandidateHash(file, file.length() + 1))
            assertNull(DuplicateContentHash.fullSha256(file, file.length() + 1))
        }
    }

    private fun sampleContent(): ByteArray = ByteArray(256 * 1024) { (it * 31).toByte() }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("duplicate-hash-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
