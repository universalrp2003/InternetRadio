package com.universalrp.cleansweep.data

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Fast head/tail hashing is only a candidate filter. A duplicate decision must use the full
 * content digest; equal size plus matching edges never establishes file identity.
 */
internal object DuplicateContentHash {
    const val EDGE_BYTES: Long = 64L * 1024L
    private const val BUFFER_BYTES = 32 * 1024

    fun partialCandidateHash(file: File, expectedSize: Long): String? {
        return try {
            if (!file.isFile || expectedSize < 0L || file.length() != expectedSize) return null
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                val headSize = minOf(expectedSize, EDGE_BYTES)
                var headRead = 0L
                while (headRead < headSize) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), headSize - headRead).toInt())
                    if (count < 0) return null
                    if (count == 0) continue
                    digest.update(buffer, 0, count)
                    headRead += count
                }
                if (expectedSize > EDGE_BYTES * 2) {
                    input.channel.position(expectedSize - EDGE_BYTES)
                    var tailRead = 0L
                    while (tailRead < EDGE_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), EDGE_BYTES - tailRead).toInt())
                        if (count < 0) return null
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        tailRead += count
                    }
                }
            }
            if (file.length() != expectedSize) return null
            digest.digest().toHexString()
        } catch (_: Exception) {
            null
        }
    }

    /** Computes the full SHA-256. The callback lets a long scan remain cancellable. */
    fun fullSha256(file: File, expectedSize: Long, onChunk: () -> Unit = {}): String? {
        if (!file.isFile || expectedSize < 0L || file.length() != expectedSize) return null
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesRead = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                onChunk()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                bytesRead += count
                if (bytesRead > expectedSize) return null
                digest.update(buffer, 0, count)
            }
        }
        if (bytesRead != expectedSize || file.length() != expectedSize) return null
        return digest.digest().toHexString()
    }

    /** Byte-for-byte confirmation, used after full hashes have narrowed a candidate group. */
    fun sameContents(first: File, second: File, expectedSize: Long, onChunk: () -> Unit = {}): Boolean {
        if (expectedSize < 0L || first == second || !first.isFile || !second.isFile ||
            first.length() != expectedSize || second.length() != expectedSize
        ) return false
        val firstBuffer = ByteArray(BUFFER_BYTES)
        val secondBuffer = ByteArray(BUFFER_BYTES)
        var compared = 0L
        FileInputStream(first).use { left ->
            FileInputStream(second).use { right ->
                while (compared < expectedSize) {
                    onChunk()
                    val chunkSize = minOf(BUFFER_BYTES.toLong(), expectedSize - compared).toInt()
                    if (!readExactly(left, firstBuffer, chunkSize) || !readExactly(right, secondBuffer, chunkSize)) return false
                    for (index in 0 until chunkSize) {
                        if (firstBuffer[index] != secondBuffer[index]) return false
                    }
                    compared += chunkSize
                }
            }
        }
        return compared == expectedSize && first.length() == expectedSize && second.length() == expectedSize
    }

    private fun readExactly(input: FileInputStream, buffer: ByteArray, expectedBytes: Int): Boolean {
        var filled = 0
        while (filled < expectedBytes) {
            val count = input.read(buffer, filled, expectedBytes - filled)
            if (count < 0) return false
            if (count == 0) continue
            filled += count
        }
        return true
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
