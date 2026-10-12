@file:Suppress("MagicNumber")

package hk.edu.hkmu.fixture

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.di.AiAndroidGraph
import hk.edu.hkmu.core.di.EngineSlot
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.pipeline.PipelineKind
import hk.edu.hkmu.core.pipeline.PipelineSpec
import hk.edu.hkmu.core.pipeline.PipelineStage
import hk.edu.hkmu.core.pipeline.SegmentSpan
import hk.edu.hkmu.core.pipeline.SignToTextPipeline
import hk.edu.hkmu.core.pipeline.SpeechToTextPipeline
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.core.registry.ModelRegistry
import hk.edu.hkmu.core.registry.TrimLevel
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.models.manifest.UserProvidedModelRegistrar
import hk.edu.hkmu.sign.model.SignByoContracts
import hk.edu.hkmu.sign.model.SignLanguages
import hk.edu.hkmu.sign.model.SignModelCatalog
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import hk.edu.hkmu.testkit.engine.FakeTtsEngine
import hk.edu.hkmu.testkit.license.FakeLicensePolicy
import hk.edu.hkmu.translate.script.ScriptConfig
import hk.edu.hkmu.translate.script.ScriptConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end Tier A proof that the library's **published API** is fully exercisable from the
 * unpublished consumer fixture using only the test-kit fakes.
 *
 * This is the executable form of the API-boundary check: every type referenced below is public
 * (`hk.edu.hkmu.core.*`, `hk.edu.hkmu.sign.model.*`, `hk.edu.hkmu.models.manifest.*`,
 * `hk.edu.hkmu.translate.script.*`) or part of the test kit (`hk.edu.hkmu.testkit.*`). No internal or
 * binding class is touched, and no real model is loaded — every engine is a `testkit` fake.
 *
 * The suite runs each capability (ASR, TTS, translation, sign, recombination) and their composition,
 * plus the streaming / cancellation / typed-error / memory-trim contracts and the license gate (the
 * WLASL-derived sign model, a BYO CSL model and the OpenCC script converter).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsumerFixtureEndToEndTest {

    // --- happy path: all four capabilities + recombination through the facade ---------------------

    @Test
    fun `facade end-to-end runs asr recombination translation and tts`() = runTest {
        val factory = FakeEngineFactory(
            asr = FakeAsrEngine(listOf(TranscriptionSegment("hola", 0L, 500L))),
            tts = FakeTtsEngine(),
            translation = FakeTranslationEngine(
                listOf(TranslatedText("你好", Language.English, Language.Mandarin))
            ),
            sign = FakeSignEngine(listOf(SignPrediction("HELLO", 0.9f, 0L, 900L))),
            postProcessor = FakePostProcessor(listOf("H", "i"))
        )
        val graph = AiAndroidGraph.builder()
            .factory(factory)
            .asr(AsrConfig())
            .tts(TtsConfig())
            .translation(TranslationConfig())
            .sign(SignConfig(classifierId = WLASL_CLASSIFIER_ID))
            .postProcess(PostProcessConfig())
            .build()

        val speech = SpeechToTextPipeline(
            spec = PipelineSpec.SpeechToText,
            asr = graph.asr(),
            translation = graph.translation(),
            postProcessor = graph.postProcessor()
        )
        val result = speech.transcribe(emptyFlow()).toList().single()

        assertEquals("hola", result.sourceText)
        assertEquals("Hi", result.recombinedText)
        assertEquals("你好", result.translatedText)
        assertEquals("你好", result.text)
        assertEquals(SegmentSpan(0L, 500L), result.span)

        val pcm = graph.tts().synthesize("你好").toList()
        assertTrue("tts produced audio", pcm.isNotEmpty())
        assertTrue("tts produced non-empty samples", pcm.first().samples.isNotEmpty())
        assertTrue("tts completed", (graph.tts() as FakeTtsEngine).completed)
    }

    @Test
    fun `sign pipeline recombines a prediction through the facade`() = runTest {
        val factory = FakeEngineFactory(
            sign = FakeSignEngine(listOf(SignPrediction("HELLO", 0.75f, 0L, 900L))),
            postProcessor = FakePostProcessor(listOf("H", "i"))
        )
        val graph = AiAndroidGraph.builder()
            .factory(factory)
            .sign(SignConfig(classifierId = WLASL_CLASSIFIER_ID))
            .postProcess(PostProcessConfig())
            .build()

        val pipeline = SignToTextPipeline(PipelineSpec.SignToText, graph.sign(), graph.postProcessor())
        val result = pipeline.recognize(emptyFlow()).toList().single()

        assertEquals("HELLO", result.label)
        assertEquals("Hi", result.recombinedText)
        assertEquals("Hi", result.text)
        assertEquals(0.75f, result.confidence, 0.0001f)
        assertEquals(SegmentSpan(0L, 900L), result.span)
        assertTrue(pipeline.effectiveStages.contains(PipelineStage.Recombination))
    }

    @Test
    fun `translation alone streams text through the facade`() = runTest {
        val factory = FakeEngineFactory(
            translation = FakeTranslationEngine(
                listOf(
                    TranslatedText("你好", Language.English, Language.Mandarin),
                    TranslatedText("世界", Language.English, Language.Mandarin)
                )
            )
        )
        val graph = AiAndroidGraph.builder().factory(factory).translation(TranslationConfig()).build()

        val emitted = graph.translation().translate("hello world").toList()

        assertEquals(listOf("你好", "世界"), emitted.map { it.text })
        assertEquals(Language.Mandarin, emitted.first().targetLanguage)
    }

    // --- streaming, cancellation -----------------------------------------------------------------

    @Test
    fun `streaming emissions arrive in declared order`() = runTest {
        val chunks = listOf(
            AudioChunk(floatArrayOf(0.1f)),
            AudioChunk(floatArrayOf(0.2f)),
            AudioChunk(floatArrayOf(0.3f))
        )
        val engine = FakeTtsEngine(chunks = chunks, emissionDelayMillis = 10L)

        val received = engine.synthesize("hi").toList()

        assertEquals(3, received.size)
        assertEquals(0.1f, received[0].samples[0], 0.0001f)
        assertEquals(0.2f, received[1].samples[0], 0.0001f)
        assertEquals(0.3f, received[2].samples[0], 0.0001f)
        assertTrue(engine.completed)
    }

    @Test
    fun `cancelling mid-stream stops emissions and marks the engine cancelled`() = runTest {
        val engine = FakeAsrEngine(
            segments = (0 until SEGMENT_COUNT).map { index ->
                TranscriptionSegment("s$index", index * SEGMENT_MS, (index + 1) * SEGMENT_MS)
            },
            emissionDelayMillis = EMISSION_DELAY_MS
        )
        val received = mutableListOf<TranscriptionSegment>()

        val job = launch { engine.transcribe(emptyFlow()).collect { received += it } }
        advanceTimeBy(EMISSION_DELAY_MS * SEGMENTS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one segment arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(EMISSION_DELAY_MS * SEGMENT_COUNT)
        runCurrent()

        assertEquals("no emissions after cancellation", atCancel, received.size)
        assertTrue("the engine observed the cancellation", engine.cancelled)
        assertFalse("the engine never completed", engine.completed)
    }

    // --- typed error paths -----------------------------------------------------------------------

    @Test
    fun `a failing engine surfaces its typed error through the facade`() = runTest {
        val failure = AiAndroidException.InputTooLong(actualLength = 2048, maxLength = 512)
        val factory = FakeEngineFactory(asr = FakeAsrEngine(failure = failure))
        val graph = AiAndroidGraph.builder().factory(factory).asr(AsrConfig()).build()

        val error = runCatching { graph.asr().transcribe(emptyFlow()).toList() }.exceptionOrNull()

        assertSame(failure, error)
        assertTrue((factory.asr as FakeAsrEngine).failed)
    }

    @Test
    fun `a failing post processor surfaces a typed error through the composed pipeline`() = runTest {
        val failure = AiAndroidException.InputTooLong(actualLength = 99, maxLength = 10)
        val factory = FakeEngineFactory(
            asr = FakeAsrEngine(listOf(TranscriptionSegment("x", 0L, 1L))),
            postProcessor = FakePostProcessor(failure = failure)
        )
        val graph = AiAndroidGraph.builder()
            .factory(factory)
            .asr(AsrConfig())
            .postProcess(PostProcessConfig())
            .build()
        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.of(
                PipelineKind.SpeechToText,
                listOf(PipelineStage.Asr, PipelineStage.Recombination)
            ),
            asr = graph.asr(),
            postProcessor = graph.postProcessor()
        )

        val error = runCatching { pipeline.transcribe(emptyFlow()).toList() }.exceptionOrNull()

        assertSame(failure, error)
    }

    @Test
    fun `a partial facade reports missing stages and a missing factory with typed errors`() {
        val graph = AiAndroidGraph.of(asr = FakeAsrEngine())

        val missingStage = assertThrows(AiAndroidException.MissingDependency::class.java) { graph.tts() }
        assertEquals("TtsEngine", missingStage.dependency)
        assertTrue(graph.has(EngineSlot.Asr))
        assertFalse(graph.has(EngineSlot.Tts))

        val missingFactory = assertThrows(AiAndroidException.MissingDependency::class.java) {
            AiAndroidGraph.builder().build()
        }
        assertEquals("EngineFactory", missingFactory.dependency)
    }

    // --- memory trim -----------------------------------------------------------------------------

    @Test
    fun `trim unloads idle models and spares pinned leases`() {
        val registry = ModelRegistry(recordingLoader(), FakeLicensePolicy(), UsageIntent.SelfUse)
        registry.register(ModelRegistration("a", ModelLicense(LicenseKind.Permissive)))
        registry.register(ModelRegistration("b", ModelLicense(LicenseKind.Permissive)))
        registry.load("a")
        registry.load("b")

        val lease = registry.acquire("a")

        val inUse = assertThrows(AiAndroidException.ModelInUse::class.java) { registry.unload("a") }
        assertEquals("a", inUse.modelId)

        val trimmed = registry.onTrimMemory(TrimLevel.Complete)
        assertEquals(listOf("b"), trimmed)
        assertTrue("the pinned model survives", registry.isResident("a"))
        assertFalse("the idle model was unloaded", registry.isResident("b"))

        lease.close()
        val trimmedAfterRelease = registry.onTrimMemory(TrimLevel.Complete)
        assertEquals(listOf("a"), trimmedAfterRelease)
        assertFalse(registry.isResident("a"))
    }

    // --- license gate: WLASL-derived sign model + BYO CSL model + OpenCC script converter ---------

    @Test
    fun `the shipped sign catalog records the wlasl model non commercial and fingerspelling permissive`() {
        val catalog = SignModelCatalog.Standard
        val wlasl = requireNotNull(catalog.forClassifierId(WLASL_CLASSIFIER_ID))
        val fingerspelling = requireNotNull(catalog.forClassifierId("signbridge-asl-alphabet"))

        assertEquals(LicenseKind.NonCommercial, wlasl.license.kind)
        assertEquals("C-UDA-1.0", wlasl.license.spdxId)
        assertEquals(LicenseKind.Permissive, fingerspelling.license.kind)

        val decision = DefaultLicensePolicy().evaluate(wlasl.license, UsageIntent.SelfUse)
        assertTrue("self-use allows the non-commercial model", decision is LicenseDecision.Allowed)
        val acknowledgment = (decision as LicenseDecision.Allowed).acknowledgment
        assertTrue("self-use records an NC acknowledgment", acknowledgment != null)
    }

    @Test
    fun `the wlasl derived sign model is blocked under commercial before the loader runs`() {
        val wlasl = requireNotNull(SignModelCatalog.Standard.forClassifierId(WLASL_CLASSIFIER_ID))
        val loaded = mutableListOf<String>()
        val registry = ModelRegistry(loader(loaded), DefaultLicensePolicy(), UsageIntent.Commercial)
        registry.register(ModelRegistration(wlasl.classifierId, wlasl.license))

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            registry.acquire(wlasl.classifierId)
        }

        assertEquals(LicenseKind.NonCommercial, error.licenseKind)
        assertEquals(UsageIntent.Commercial, error.intent)
        assertTrue("the loader must never run for a refused model", loaded.isEmpty())
        assertFalse(registry.isResident(wlasl.classifierId))
    }

    @Test
    fun `a self-use registry loads the non commercial wlasl model with an acknowledgment`() {
        val wlasl = requireNotNull(SignModelCatalog.Standard.forClassifierId(WLASL_CLASSIFIER_ID))
        val loaded = mutableListOf<String>()
        val registry = ModelRegistry(loader(loaded), DefaultLicensePolicy(), UsageIntent.SelfUse)
        registry.register(ModelRegistration(wlasl.classifierId, wlasl.license))

        registry.acquire(wlasl.classifierId).use { lease ->
            assertEquals(wlasl.classifierId, lease.modelId)
        }

        assertEquals(listOf(wlasl.classifierId), loaded)
        assertTrue(registry.isResident(wlasl.classifierId))
    }

    @Test
    fun `a BYO CSL model is unknown license and blocked under commercial`() {
        assertEquals(SignLanguages.Csl, SignByoContracts.Csl.language)
        assertEquals(50, SignByoContracts.Csl.windowFrames)

        val descriptor = UserProvidedModelRegistrar().register(
            UserProvidedModel(
                id = "csl-byo",
                declaredLicense = "Apache-2.0",
                version = "1.0.0",
                sha256 = BYO_SHA256,
                abi = setOf(Abi.Any),
                sizeBytes = 1024L,
                urls = listOf("https://example.invalid/csl/model.tflite"),
                format = ModelFormat.Tflite
            )
        )

        assertEquals("a BYO claim never upgrades trust", LicenseKind.UnknownLicense, descriptor.license.kind)

        val loaded = mutableListOf<String>()
        val registry = ModelRegistry(loader(loaded), DefaultLicensePolicy(), UsageIntent.Commercial)
        registry.register(ModelRegistration(descriptor.id, descriptor.license))

        val error = assertThrows(AiAndroidException.LicenseRestricted::class.java) {
            registry.acquire(descriptor.id)
        }

        assertEquals(LicenseKind.UnknownLicense, error.licenseKind)
        assertTrue("a BYO model must never reach the loader under commercial", loaded.isEmpty())
    }

    @Test
    fun `the opencc script converter converts scripts through the fixture`() {
        val converter = ScriptConverter()

        assertEquals("软体", converter.convert("軟體", ScriptConfig.T2S))
        assertEquals("軟體", converter.convert("软件", ScriptConfig.S2TW))
        assertEquals("台灣", converter.convert("台湾", ScriptConfig.S2HK))
    }

    // --- helpers ---------------------------------------------------------------------------------

    private fun recordingLoader(): ModelLoader<FixtureModelHandle> =
        ModelLoader { registration -> FixtureModelHandle(registration.modelId) }

    private fun loader(loaded: MutableList<String>): ModelLoader<FixtureModelHandle> = ModelLoader { registration ->
        loaded += registration.modelId
        FixtureModelHandle(registration.modelId)
    }

    private companion object {
        const val WLASL_CLASSIFIER_ID = "tgcn-wlasl-asl100"
        const val SEGMENT_MS = 10L
        const val EMISSION_DELAY_MS = 10L
        const val SEGMENT_COUNT = 20
        const val SEGMENTS_BEFORE_CANCEL = 3
        const val BYO_SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
