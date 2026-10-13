@file:Suppress("MagicNumber")

package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.DiarizationConfig
import hk.edu.hkmu.core.config.EngineRuntimeConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.DiarizationSegment
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance for [SherpaDiarizationEngine]: ordered turns through the fake seam, the
 * empty-audio adversarial probe, the lazy-load lifecycle, the 16 kHz input contract and the license
 * gate.
 */
class SherpaDiarizationEngineTest {

    @Test
    fun `emits ordered turns and completes`() = runTest {
        val backend = FakeDiarizationBackend(
            listOf(turn(0, 1_000, "Speaker 1"), turn(1_000, 2_500, "Speaker 2"), turn(2_500, 4_000, "Speaker 1"))
        )
        val factory = CountingBackendFactory(backend)

        val turns = engine(factory).diarize(oneUtterance()).toList()

        assertEquals(listOf("Speaker 1", "Speaker 2", "Speaker 1"), turns.map { it.speakerLabel })
        assertEquals(listOf(0L, 1_000L, 2_500L), turns.map { it.startTimeMillis })
        assertEquals(1, factory.created)
    }

    @Test
    fun `empty audio emits nothing and never loads the backend`() = runTest {
        val factory = CountingBackendFactory(FakeDiarizationBackend(listOf(turn(0, 1_000, "Speaker 1"))))

        val turns = engine(factory).diarize(emptyFlow()).toList()

        assertTrue("no turns for empty audio", turns.isEmpty())
        assertEquals("backend never created for empty audio", 0, factory.created)
    }

    @Test
    fun `samples from every chunk are concatenated before diarizing`() = runTest {
        val backend = FakeDiarizationBackend()
        val chunks = SyntheticAudio.chunks(SyntheticAudio.silence(1_000), chunkSize = 1_000)

        engine(CountingBackendFactory(backend)).diarize(chunks.asFlow()).toList()

        assertEquals(listOf(16_000), backend.diarizeCallSampleCounts)
    }

    @Test
    fun `backend is not created until the flow is collected`() {
        val backend = FakeDiarizationBackend()
        val factory = CountingBackendFactory(backend)

        engine(factory)

        assertEquals("construction must not load the backend", 0, factory.created)
        assertFalse(backend.closed)
    }

    @Test
    fun `close releases the lazily created backend`() = runTest {
        val backend = FakeDiarizationBackend()
        val sut = engine(CountingBackendFactory(backend))
        sut.diarize(oneUtterance()).toList()
        assertFalse("backend open while in use", backend.closed)

        sut.close()

        assertTrue("close releases the backend", backend.closed)
    }

    @Test
    fun `non 16 kHz audio is rejected with MalformedFrame`() {
        val factory = CountingBackendFactory(FakeDiarizationBackend())
        val sut = engine(factory)

        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            runBlocking {
                sut.diarize(flowOf(AudioChunk(FloatArray(100), MISMATCHED_RATE_HZ))).toList()
            }
        }
        assertTrue(exception.message.orEmpty().contains("16000"))
        assertEquals("bad input never reaches the backend", 0, factory.created)
    }

    @Test
    fun `license gate blocks a restricted intent before loading the backend`() {
        val factory = CountingBackendFactory(FakeDiarizationBackend())
        val sut = SherpaDiarizationEngine.forTesting(
            config = DiarizationConfig(),
            backendFactory = factory,
            licenseGate = DiarizationLicenseGate(
                policy = DefaultLicensePolicy(),
                intent = UsageIntent.Commercial,
                license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
            ),
            dispatcher = Dispatchers.Unconfined
        )

        assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            runBlocking { sut.diarize(oneUtterance()).toList() }
        }
        assertEquals("restricted model never loads", 0, factory.created)
    }

    @Test
    fun `explicit GPU accelerator is refused at load`() {
        val factory = CountingBackendFactory(FakeDiarizationBackend())
        val sut = engine(factory, DiarizationConfig(runtime = EngineRuntimeConfig(accelerator = Accelerator.Gpu)))

        assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            runBlocking { sut.diarize(oneUtterance()).toList() }
        }
        assertEquals(0, factory.created)
    }

    private fun engine(factory: DiarizationBackendFactory, config: DiarizationConfig = DiarizationConfig()) =
        SherpaDiarizationEngine.forTesting(config, factory, dispatcher = Dispatchers.Unconfined)

    private fun oneUtterance() = SyntheticAudio.chunks(SyntheticAudio.silence(1_000), chunkSize = 1_000).asFlow()

    private fun turn(startMillis: Long, endMillis: Long, label: String) =
        DiarizationSegment(startMillis, endMillis, label)

    private class CountingBackendFactory(private val backend: DiarizationBackend) : DiarizationBackendFactory {
        var created: Int = 0
            private set

        override fun create(): DiarizationBackend {
            created++
            return backend
        }
    }

    private companion object {
        const val MISMATCHED_RATE_HZ = 8_000
    }
}
