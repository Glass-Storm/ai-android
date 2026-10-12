@file:Suppress("MagicNumber")

package hk.edu.hkmu.fixture

import hk.edu.hkmu.core.config.AsrConfig
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.SignConfig
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.di.AiAndroidGraph
import hk.edu.hkmu.core.engine.SignPrediction
import hk.edu.hkmu.core.engine.TranscriptionSegment
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.pipeline.PipelineKind
import hk.edu.hkmu.core.pipeline.PipelineSpec
import hk.edu.hkmu.core.pipeline.PipelineStage
import hk.edu.hkmu.core.pipeline.SignToTextPipeline
import hk.edu.hkmu.core.pipeline.SpeechToTextPipeline
import hk.edu.hkmu.testkit.engine.FakeAsrEngine
import hk.edu.hkmu.testkit.engine.FakeEngineFactory
import hk.edu.hkmu.testkit.engine.FakePostProcessor
import hk.edu.hkmu.testkit.engine.FakeSignEngine
import hk.edu.hkmu.testkit.engine.FakeTranslationEngine
import hk.edu.hkmu.testkit.engine.FakeTtsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A cancellation contract: **every** pipeline stage must stop producing and observe the
 * cancellation when a collector cancels mid-stream.
 *
 * Each stage is driven through the public facade or a composed pipeline with a `testkit` fake that
 * records its terminal state (`cancelled` / `completed`). The assertion in every case is the same
 * pairing guaranteed by the streaming contract: no emission after `cancel`, the fake observed the
 * cancellation, and it never completed. Native-handle release on cancellation is asserted for the
 * handle-owning engines in their own modules (`RecombineBackpressureTest`, `TtsStreamContractTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellationContractTest {

    @Test
    fun `cancelling asr mid-stream stops segments and marks the engine cancelled`() = runTest {
        val engine = FakeAsrEngine(
            segments = (0 until ITEMS).map { TranscriptionSegment("s$it", it * ITEM_MS, (it + 1) * ITEM_MS) },
            emissionDelayMillis = DELAY_MS
        )
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(asr = engine)).asr(AsrConfig()).build()
        val received = mutableListOf<TranscriptionSegment>()

        val job = launch { graph.asr().transcribe(emptyFlow()).collect { received += it } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one segment arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no segments after cancellation", atCancel, received.size)
        assertTrue("the ASR fake observed the cancellation", engine.cancelled)
        assertFalse("the ASR fake never completed", engine.completed)
    }

    @Test
    fun `cancelling tts mid-stream stops chunks and marks the engine cancelled`() = runTest {
        val chunks = (0 until ITEMS).map { hk.edu.hkmu.core.engine.AudioChunk(floatArrayOf(it.toFloat())) }
        val engine = FakeTtsEngine(chunks = chunks, emissionDelayMillis = DELAY_MS)
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(tts = engine)).tts(TtsConfig()).build()
        val received = mutableListOf<Float>()

        val job = launch { graph.tts().synthesize("hi").collect { received += it.samples[0] } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one chunk arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no chunks after cancellation", atCancel, received.size)
        assertTrue("the TTS fake observed the cancellation", engine.cancelled)
        assertFalse("the TTS fake never completed", engine.completed)
    }

    @Test
    fun `cancelling translation mid-stream stops emissions and marks the engine cancelled`() = runTest {
        val engine = FakeTranslationEngine(
            translations = (0 until ITEMS).map { TranslatedText("t$it", Language.English, Language.Mandarin) },
            emissionDelayMillis = DELAY_MS
        )
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(translation = engine))
            .translation(TranslationConfig()).build()
        val received = mutableListOf<String>()

        val job = launch { graph.translation().translate("hello").collect { received += it.text } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one translation arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no translations after cancellation", atCancel, received.size)
        assertTrue("the translation fake observed the cancellation", engine.cancelled)
        assertFalse("the translation fake never completed", engine.completed)
    }

    @Test
    fun `cancelling sign recognition mid-stream stops predictions and marks the engine cancelled`() = runTest {
        val engine = FakeSignEngine(
            predictions = (0 until ITEMS).map { SignPrediction("S$it", 0.9f, it * ITEM_MS, (it + 1) * ITEM_MS) },
            emissionDelayMillis = DELAY_MS
        )
        val graph = AiAndroidGraph.builder().factory(FakeEngineFactory(sign = engine))
            .sign(SignConfig(classifierId = "cancel-probe")).build()
        val received = mutableListOf<String>()

        val job = launch { graph.sign().recognize(emptyFlow()).collect { received += it.label } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one prediction arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no predictions after cancellation", atCancel, received.size)
        assertTrue("the sign fake observed the cancellation", engine.cancelled)
        assertFalse("the sign fake never completed", engine.completed)
    }

    @Test
    fun `cancelling recombination mid-stream stops tokens and marks the post processor cancelled`() = runTest {
        val postProcessor = FakePostProcessor(
            tokens = (0 until ITEMS).map { "t$it" },
            emissionDelayMillis = DELAY_MS
        )
        val asr = FakeAsrEngine(listOf(TranscriptionSegment("hello", 0L, 10L)))
        val pipeline = SpeechToTextPipeline(
            spec = PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Asr, PipelineStage.Recombination)),
            asr = asr,
            postProcessor = postProcessor
        )
        val received = mutableListOf<String>()

        val job = launch { pipeline.transcribe(emptyFlow()).collect { received += it.recombinedText.orEmpty() } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        assertTrue("a pipeline result is emitted only after every token is collected", received.isEmpty())

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no result after cancellation", 0, received.size)
        assertTrue("the post-processor fake observed the cancellation", postProcessor.cancelled)
        assertFalse("the post-processor fake never completed", postProcessor.completed)
    }

    @Test
    fun `cancelling the sign pipeline cancels the upstream sign stage`() = runTest {
        val sign = FakeSignEngine(
            predictions = (0 until ITEMS).map { SignPrediction("S$it", 0.9f, it * ITEM_MS, (it + 1) * ITEM_MS) },
            emissionDelayMillis = DELAY_MS
        )
        val pipeline = SignToTextPipeline(
            spec = PipelineSpec.SignToText,
            sign = sign,
            postProcessor = null,
            recombinationEnabled = false
        )
        val received = mutableListOf<String>()

        val job = launch { pipeline.recognize(emptyFlow()).collect { received += it.text } }
        advanceTimeBy(DELAY_MS * ITEMS_BEFORE_CANCEL)
        runCurrent()
        val atCancel = received.size
        assertTrue("at least one result arrived before cancel", atCancel >= 1)

        job.cancel()
        job.join()
        advanceTimeBy(DELAY_MS * ITEMS)
        runCurrent()

        assertEquals("no results after cancellation", atCancel, received.size)
        assertTrue("the composed pipeline cancelled its sign stage", sign.cancelled)
        assertFalse("the sign stage never completed", sign.completed)
    }

    private companion object {
        const val ITEM_MS = 10L
        const val DELAY_MS = 10L
        const val ITEMS = 20
        const val ITEMS_BEFORE_CANCEL = 3
    }
}
