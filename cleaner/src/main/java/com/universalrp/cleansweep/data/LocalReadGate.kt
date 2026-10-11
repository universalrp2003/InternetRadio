package com.universalrp.cleansweep.data

import kotlinx.coroutines.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android/Binder reads may not interrupt instantly. A cancelled UI request does not wait for them;
 * at most four detached owners remain, and their late results are dropped. No unbounded work queue.
 * Side-effecting helpers must check coroutine cancellation before saving a completed observation.
 */
object LocalReadGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger()
    suspend fun <T> read(timeoutMs: Long = 8_000, block: suspend () -> T): T? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<T> { continuation ->
            if (active.incrementAndGet() > 4) {
                active.decrementAndGet(); continuation.resumeWithException(RejectedExecutionException("Local reader slots busy"))
                return@suspendCancellableCoroutine
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try { val result = block(); ensureActive(); if (continuation.isActive) continuation.resume(result) }
                catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
            job.invokeOnCompletion { active.decrementAndGet() }
            continuation.invokeOnCancellation { job.cancel() }
            job.start()
        }
    }
}
