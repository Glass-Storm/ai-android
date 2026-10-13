@file:Suppress("MagicNumber")

package hk.edu.hkmu.diarize

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.EngineRuntimeConfig
import hk.edu.hkmu.core.config.SpeakerEmbeddingConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.Voiceprint
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance for [SherpaSpeakerEmbeddingExtractor]: a single voiceprint through the fake seam,
 * the empty-audio adversarial probe, the lazy-load lifecycle, the 16 kHz input contract, the CPU-only
 * guard and the license gate.
 */
class SherpaSpeakerEmbeddingExtractorTest {

    @Test
    fun `emits one voiceprint and completes`() = runTest {
        val backend = FakeSpeakerEmbeddingBackend()
        val factory = CountingBackendFactory(backend)

        val voiceprints = extractor(factory).extract(oneUtterance()).toList()

        assertEquals(1, voiceprints.size)
        assertArrayEquals(FakeSpeakerEmbeddingBackend.DEFAULT_EMBEDDING, voiceprints.single().embedding, 0f)
        assertEquals(SherpaSpeakerEmbeddingExtractor.MODEL_ID, voiceprints.single().modelId)
        assertEquals(1, factory.created)
    }

    @Test
    fun `empty audio emits nothing and never loads the backend`() = runTest {
        val factory = CountingBackendFactory(FakeSpeakerEmbeddingBackend())

        val voiceprints = extractor(factory).extract(emptyFlow()).toList()

        assertTrue("no voiceprint for empty audio", voiceprints.isEmpty())
        assertEquals("backend never created for empty audio", 0, factory.created)
    }

    @Test
    fun `samples from every chunk are concatenated before embedding`() = runTest {
        val backend = FakeSpeakerEmbeddingBackend()
        val chunks = SyntheticAudio.chunks(SyntheticAudio.silence(1_000), chunkSize = 1_000)

        extractor(CountingBackendFactory(backend)).extract(chunks.asFlow()).toList()

        assertEquals(listOf(16_000), backend.embedCallSampleCounts)
    }

    @Test
    fun `backend is not created until the flow is collected`() {
        val backend = FakeSpeakerEmbeddingBackend()
        val factory = CountingBackendFactory(backend)

        extractor(factory)

        assertEquals("construction must not load the backend", 0, factory.created)
        assertFalse(backend.closed)
    }

    @Test
    fun `close releases the lazily created backend`() = runTest {
        val backend = FakeSpeakerEmbeddingBackend()
        val sut = extractor(CountingBackendFactory(backend))
        sut.extract(oneUtterance()).toList()
        assertFalse("backend open while in use", backend.closed)

        sut.close()

        assertTrue("close releases the backend", backend.closed)
    }

    @Test
    fun `non 16 kHz audio is rejected with MalformedFrame`() {
        val factory = CountingBackendFactory(FakeSpeakerEmbeddingBackend())
        val sut = extractor(factory)

        val exception = assertThrows(AiAndroidException.MalformedFrame::class.java) {
            runBlocking {
                sut.extract(flowOf(AudioChunk(FloatArray(100), MISMATCHED_RATE_HZ))).toList()
            }
        }
        assertTrue(exception.message.orEmpty().contains("16000"))
        assertEquals("bad input never reaches the backend", 0, factory.created)
    }

    @Test
    fun `license gate blocks a restricted intent before loading the backend`() {
        val factory = CountingBackendFactory(FakeSpeakerEmbeddingBackend())
        val sut = SherpaSpeakerEmbeddingExtractor.forTesting(
            config = SpeakerEmbeddingConfig(),
            backendFactory = factory,
            licenseGate = DiarizationLicenseGate(
                policy = DefaultLicensePolicy(),
                intent = UsageIntent.Commercial,
                license = ModelLicense(kind = LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0")
            ),
            dispatcher = Dispatchers.Unconfined
        )

        assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            runBlocking { sut.extract(oneUtterance()).toList() }
        }
        assertEquals("restricted model never loads", 0, factory.created)
    }

    @Test
    fun `explicit GPU accelerator is refused at load`() {
        val factory = CountingBackendFactory(FakeSpeakerEmbeddingBackend())
        val sut = extractor(
            factory,
            SpeakerEmbeddingConfig(runtime = EngineRuntimeConfig(accelerator = Accelerator.Gpu))
        )

        assertThrows(AiAndroidException.AcceleratorUnavailable::class.java) {
            runBlocking { sut.extract(oneUtterance()).toList() }
        }
        assertEquals(0, factory.created)
    }

    @Test
    fun `a voiceprint maps back to a display name through an enrollment directory`() {
        val voiceprint = Voiceprint(FakeSpeakerEmbeddingBackend.DEFAULT_EMBEDDING)
        val directory = hk.edu.hkmu.core.engine.EnrollmentDirectory().enroll("Alice", voiceprint)

        assertEquals("Alice", directory.match(voiceprint)?.displayName)
    }

    private fun extractor(
        factory: SpeakerEmbeddingBackendFactory,
        config: SpeakerEmbeddingConfig = SpeakerEmbeddingConfig()
    ) = SherpaSpeakerEmbeddingExtractor.forTesting(config, factory, dispatcher = Dispatchers.Unconfined)

    private fun oneUtterance() = SyntheticAudio.chunks(SyntheticAudio.silence(1_000), chunkSize = 1_000).asFlow()

    private class CountingBackendFactory(private val backend: SpeakerEmbeddingBackend) :
        SpeakerEmbeddingBackendFactory {
        var created: Int = 0
            private set

        override fun create(): SpeakerEmbeddingBackend {
            created++
            return backend
        }
    }

    private companion object {
        const val MISMATCHED_RATE_HZ = 8_000
    }
}
