@file:Suppress("MagicNumber")

package hk.edu.hkmu.fixture

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.core.registry.TrimLevel
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import hk.edu.hkmu.testkit.engine.FakeTtsEngine
import hk.edu.hkmu.testkit.license.FakeLicensePolicy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the library's public API is fully exercisable from the unpublished fixture through the
 * test-kit fakes: every engine contract, the license policy, and the model registry, with no
 * reference to any internal or binding class.
 */
class ConsumerFixtureApiTest {

    @Test
    fun `every public engine contract is injectable via fakes`() = runTest {
        val factory = FakeEngineFactory(
            asr = FakeAsrEngine(listOf(TranscriptionSegment("hi", 0, 10))),
            tts = FakeTtsEngine(),
            translation = FakeTranslationEngine(
                listOf(TranslatedText("你好", Language.English, Language.Mandarin))
            ),
            sign = FakeSignEngine(listOf(SignPrediction("HELLO", 0.9f, 0, 900))),
            postProcessor = FakePostProcessor(listOf("A", "B"))
        )

        val asrText = factory.createAsrEngine(AsrConfig()).transcribe(emptyFlow()).toList().map { it.text }
        assertEquals(listOf("hi"), asrText)

        val pcm = factory.tts.synthesize("hi").toList()
        assertFalse(pcm.isEmpty())
        assertTrue(pcm.first().samples.isNotEmpty())

        val translated = factory.translation.translate("hi").toList().map { it.text }
        assertEquals(listOf("你好"), translated)

        val signs = factory.sign.recognize(emptyFlow()).toList().map { it.label }
        assertEquals(listOf("HELLO"), signs)

        val tokens = factory.postProcessor.process("ignored").toList()
        assertEquals(listOf("A", "B"), tokens)
    }

    @Test
    fun `fixture graph resolves every public contract from fakes`() {
        val wired = FixtureApiGraph.wire()

        assertTrue(wired.factory is FakeEngineFactory)
        assertTrue(wired.licensePolicy is FakeLicensePolicy)
        assertTrue(wired.asr is FakeAsrEngine)
        assertTrue(wired.tts is FakeTtsEngine)
        assertTrue(wired.translation is FakeTranslationEngine)
        assertTrue(wired.sign is FakeSignEngine)
        assertTrue(wired.postProcessor is FakePostProcessor)
    }

    @Test
    fun `self-use registry loads a non-commercial model with an acknowledgment`() {
        val policy = FakeLicensePolicy()
        val registry = registry(policy, UsageIntent.SelfUse)
        registry.register(ModelRegistration("nc", ModelLicense(LicenseKind.NonCommercial)))

        registry.acquire("nc").use { lease -> assertEquals("nc", lease.modelId) }

        assertTrue(registry.isResident("nc"))
        assertTrue(registry.onTrimMemory(TrimLevel.Complete).contains("nc"))
    }

    @Test
    fun `commercial registry blocks a non-commercial model`() {
        val registry = registry(FakeLicensePolicy(), UsageIntent.Commercial)
        registry.register(ModelRegistration("nc", ModelLicense(LicenseKind.NonCommercial)))

        val error = runCatching { registry.acquire("nc") }.exceptionOrNull()

        assertTrue(error is AiAndroidException.LicenseRestricted)
        assertFalse(registry.isResident("nc"))
    }

    private fun registry(policy: LicensePolicy, intent: UsageIntent): ModelRegistry<FixtureModelHandle> {
        return ModelRegistry(
            loader = { registration -> FixtureModelHandle(registration.modelId) },
            licensePolicy = policy,
            intent = intent
        )
    }
}
