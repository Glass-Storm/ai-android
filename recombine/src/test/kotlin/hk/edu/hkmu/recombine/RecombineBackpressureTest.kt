@file:Suppress("MagicNumber")

package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.config.PostProcessConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A flow-control contract for the recombination [hk.edu.hkmu.core.engine.PostProcessor].
 *
 * [LlamaPostProcessor] bridges the native token callback with `callbackFlow` + `trySendBlocking`.
 * This suite proves the two consequences of that bridge, using a counting [LlamaSession] fake and a
 * deliberately slow collector on real dispatchers (so a genuinely blocking producer, not just a
 * suspended coroutine, is exercised):
 *
 * 1. **Bounded buffering / backpressure** — a stalled collector throttles the producer to the
 *    channel's bounded capacity instead of letting it race to completion (unbounded buffering).
 * 2. **No element loss** — a slow-but-draining collector still receives every token, in order.
 * 3. **Cancellation + release** — no token is delivered after cancellation, and
 *    [LlamaPostProcessor.close] releases the native session (the fake session observes `close`).
 */
class RecombineBackpressureTest {

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `a stalled collector backpressures the producer and bounds buffering`() = runBlocking {
        val session = CountingSession(TOTAL_TOKENS)
        val engine = recombineEngine(session)
        val firstConsumed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val collector = launch(Dispatchers.IO) {
            engine.process(GLOSS).collect {
                firstConsumed.complete(Unit)
                release.await()
            }
        }

        firstConsumed.await()
        val producedWhileStalled = awaitStableCount(session.produced)
        println("OBSERVABLE backpressure: produced=$producedWhileStalled of $TOTAL_TOKENS while collector stalled")

        assertTrue("the producer must have started", producedWhileStalled > 0)
        assertTrue(
            "a stalled collector must bound the buffer to the channel capacity, was $producedWhileStalled",
            producedWhileStalled <= MAX_BUFFERED_TOKENS
        )
        assertTrue(
            "the producer must not run to completion while the collector is stalled",
            producedWhileStalled < TOTAL_TOKENS
        )

        release.complete(Unit)
        collector.join()
        engine.close()
        assertTrue("close() must release the native session", session.closed)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `a slow but draining collector receives every token in order without drops`() = runBlocking {
        val session = CountingSession(SMALL_TOTAL_TOKENS)
        val engine = recombineEngine(session)
        val received = mutableListOf<String>()

        engine.process(GLOSS).collect { piece ->
            received += piece
            if (received.size % CONSUMER_STALL_EVERY == 0) {
                delay(CONSUMER_STALL_MS)
            }
        }
        engine.close()

        assertEquals("no token may be dropped under backpressure", SMALL_TOTAL_TOKENS, received.size)
        assertEquals(
            "the producer must have produced exactly what was consumed",
            SMALL_TOTAL_TOKENS,
            session.produced.get()
        )
        assertTrue("tokens must be delivered in order", received.all { it == TOKEN })
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `mid-stream cancellation stops delivery and close releases the session`() = runBlocking {
        val session = CountingSession(CANCELLABLE_TOKENS)
        val engine = recombineEngine(session)
        val delivered = mutableListOf<String>()

        val collector = launch(Dispatchers.IO) {
            engine.process(GLOSS).collect {
                delivered += it
                delay(CONSUMER_STALL_MS)
            }
        }
        awaitTrue { delivered.isNotEmpty() }
        val atCancel = delivered.size
        collector.cancelAndJoin()
        delay(SETTLE_MS)
        val afterCancel = delivered.size
        delay(SETTLE_MS)

        assertTrue("collection was cancelled mid-stream", atCancel < CANCELLABLE_TOKENS)
        assertEquals("no token may be delivered after cancellation", afterCancel, delivered.size)

        engine.close()
        assertTrue("close() must release the native session after cancellation", session.closed)
    }

    private fun recombineEngine(session: LlamaSession): LlamaPostProcessor =
        LlamaPostProcessor.forTesting(PostProcessConfig(), { session }, Dispatchers.IO, LlamaSamplingConfig())

    /** Polls [produced] until it has not changed for [STABLE_POLLS] consecutive samples. */
    private suspend fun awaitStableCount(produced: AtomicInteger): Int {
        var last = produced.get()
        var stable = 0
        repeat(POLLS) {
            delay(POLL_INTERVAL_MS)
            val now = produced.get()
            stable = if (now == last) stable + 1 else 0
            last = now
            if (stable >= STABLE_POLLS) return last
        }
        return last
    }

    private suspend fun awaitTrue(condition: () -> Boolean) {
        repeat(POLLS) {
            if (condition()) return
            delay(POLL_INTERVAL_MS)
        }
        throw AssertionError("condition was never satisfied within ${POLLS * POLL_INTERVAL_MS} ms")
    }

    /**
     * Deterministic [LlamaSession] that emits [total] identical [TOKEN] pieces and records how many
     * it produced, so a test can observe a producer blocked by `trySendBlocking`.
     */
    private class CountingSession(private val total: Int) : LlamaSession {
        /** Pieces produced so far (incremented just before each `onToken`). */
        val produced = AtomicInteger()

        /** Set by [close]; models native handle release. */
        var closed: Boolean = false
            private set

        override fun generate(
            prompt: String,
            maxTokens: Int,
            seed: Int,
            temperature: Float,
            onToken: (String) -> Unit
        ): Int {
            repeat(total) {
                produced.incrementAndGet()
                onToken(TOKEN)
            }
            return total
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val GLOSS = "h e l l o"
        const val TOKEN = "t"
        const val TOTAL_TOKENS = 100_000
        const val SMALL_TOTAL_TOKENS = 5_000
        const val CANCELLABLE_TOKENS = 50
        const val MAX_BUFFERED_TOKENS = 512
        const val CONSUMER_STALL_EVERY = 500
        const val CONSUMER_STALL_MS = 1L
        const val SETTLE_MS = 50L
        const val POLL_INTERVAL_MS = 5L
        const val POLLS = 40
        const val STABLE_POLLS = 4
        const val TEST_TIMEOUT_MS = 30_000L
    }
}
