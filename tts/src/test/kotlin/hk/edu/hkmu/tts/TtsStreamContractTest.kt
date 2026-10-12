@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.AudioChunk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A flow-control contract for [SherpaTtsEngine].
 *
 * [SherpaTtsEngine.synthesize] bridges native synthesis with `channelFlow` + `trySendBlocking`, so a
 * slow collector must throttle chunk production rather than drop or reorder PCM. This suite locks
 * the two collection-time properties that can be observed without native code, using the fake
 * synthesizer seam and real dispatchers:
 *
 * 1. **No loss under backpressure** — a slow collector still receives every chunk, in order, with
 *    the exact sample layout (the whole utterance).
 * 2. **Cancellation + release** — no chunk is delivered after cancellation, and [SherpaTtsEngine.close]
 *    frees the (fake) native synthesizer.
 */
class TtsStreamContractTest {

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `a slow collector receives every chunk in order without drops`() = runBlocking {
        val chunks = DRAIN_CHUNK_COUNT
        val samples = FloatArray(CHUNK_SAMPLES * chunks) { it.toFloat() }
        val fake = FakeVitsSynthesizer(generate = { _, _, _ -> samples.copyOf() })
        val engine = engineFor(Language.Cantonese, factoryReturning(fake))
        val received = mutableListOf<AudioChunk>()

        engine.synthesize("你好").flowOn(DISPATCHER).collect { chunk ->
            received += chunk
            if (received.size % STALL_EVERY == 0) {
                delay(STALL_MS)
            }
        }
        engine.close()

        assertEquals("no chunk may be dropped under backpressure", chunks, received.size)
        assertEquals("the whole utterance must be delivered", samples.size, received.sumOf { it.samples.size })
        received.forEachIndexed { index, chunk ->
            assertEquals(
                "chunks must stay ordered",
                (index * CHUNK_SAMPLES).toFloat(),
                chunk.samples[0],
                0f
            )
        }
        assertTrue("the native synthesizer was released", fake.closed)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun `mid-stream cancellation stops chunk delivery and close releases the synthesizer`() = runBlocking {
        val samples = FloatArray(CHUNK_SAMPLES * CANCEL_CHUNK_COUNT) { 0f }
        val fake = FakeVitsSynthesizer(generate = { _, _, _ -> samples.copyOf() })
        val engine = engineFor(Language.Cantonese, factoryReturning(fake))
        val delivered = AtomicInteger()

        val collector = launch(DISPATCHER) {
            engine.synthesize("你好").collect {
                delivered.incrementAndGet()
                delay(STALL_MS)
            }
        }
        awaitTrue { delivered.get() > 0 }
        val atCancel = delivered.get()
        assertTrue("collection was cancelled mid-stream", atCancel < CANCEL_CHUNK_COUNT)
        collector.cancelAndJoin()
        delay(SETTLE_MS)
        val afterCancel = delivered.get()
        delay(SETTLE_MS)

        assertEquals("no chunk may be delivered after cancellation", afterCancel, delivered.get())

        engine.close()
        assertTrue("close() must free the native synthesizer after cancellation", fake.closed)
    }

    private suspend fun awaitTrue(condition: () -> Boolean) {
        repeat(POLLS) {
            if (condition()) return
            delay(POLL_INTERVAL_MS)
        }
        throw AssertionError("condition was never satisfied within ${POLLS * POLL_INTERVAL_MS} ms")
    }

    private companion object {
        // Mirrors the engine's internal PCM chunk size (a single synthesized utterance is split on it).
        const val CHUNK_SAMPLES = 4_096
        const val DRAIN_CHUNK_COUNT = 500
        const val CANCEL_CHUNK_COUNT = 20
        const val STALL_EVERY = 50
        const val STALL_MS = 1L
        const val SETTLE_MS = 50L
        const val POLL_INTERVAL_MS = 10L
        const val POLLS = 200
        const val TEST_TIMEOUT_MS = 30_000L
        val DISPATCHER = kotlinx.coroutines.Dispatchers.IO
    }
}
