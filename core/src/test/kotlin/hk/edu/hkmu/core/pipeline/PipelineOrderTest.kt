package hk.edu.hkmu.core.pipeline

import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A: the composition layer owns ordering — the defaults, the explicit/configurable orders, the
 * recombination opt-out, and every illegal order rejected with a typed `PipelineMisconfigured`.
 */
class PipelineOrderTest {

    @Test
    fun `default speech spec is ASR then recombination then translation`() {
        assertEquals(
            listOf(PipelineStage.Asr, PipelineStage.Recombination, PipelineStage.Translation),
            PipelineSpec.SpeechToText.stages
        )
    }

    @Test
    fun `default sign spec is SLR then recombination`() {
        assertEquals(
            listOf(PipelineStage.Sign, PipelineStage.Recombination),
            PipelineSpec.SignToText.stages
        )
    }

    @Test
    fun `a post-processor placed before ASR is misconfigured`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Recombination, PipelineStage.Asr))
        }

        assertTrue("reason names the source-first rule", failure.reason.contains("Asr must be the first"))
    }

    @Test
    fun `translation is not a legal sign stage`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            PipelineSpec.of(PipelineKind.SignToText, listOf(PipelineStage.Sign, PipelineStage.Translation))
        }

        assertTrue("reason names the illegal stage", failure.reason.contains("Translation is not valid"))
    }

    @Test
    fun `duplicate stages are misconfigured`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Asr, PipelineStage.Asr))
        }

        assertTrue("reason reports the duplicate", failure.reason.contains("duplicate"))
    }

    @Test
    fun `an empty stage list is misconfigured`() {
        val failure = assertThrows(AiAndroidException.PipelineMisconfigured::class.java) {
            PipelineSpec.of(PipelineKind.SpeechToText, emptyList())
        }

        assertTrue("reason reports the empty order", failure.reason.contains("no stages"))
    }

    @Test
    fun `a source-only speech spec is legal`() {
        val spec = PipelineSpec.of(PipelineKind.SpeechToText, listOf(PipelineStage.Asr))

        assertEquals(listOf(PipelineStage.Asr), spec.stages)
        assertFalse(spec.contains(PipelineStage.Recombination))
    }

    @Test
    fun `recombination opt-out drops the stage from the effective order but keeps it declared`() {
        val resolved = PipelineSpec.SpeechToText.resolve(recombinationEnabled = false)

        assertEquals(listOf(PipelineStage.Translation), resolved.textStages)
        assertEquals(PipelineStage.Asr, resolved.source)
        assertEquals(listOf(PipelineStage.Asr, PipelineStage.Recombination, PipelineStage.Translation), resolved.stages)
    }

    @Test
    fun `recombination default keeps the stage in the effective order`() {
        val resolved = PipelineSpec.SpeechToText.resolve()

        assertEquals(listOf(PipelineStage.Recombination, PipelineStage.Translation), resolved.textStages)
    }

    @Test
    fun `a configurable order of translation before recombination is accepted`() {
        val spec = PipelineSpec.of(
            PipelineKind.SpeechToText,
            listOf(PipelineStage.Asr, PipelineStage.Translation, PipelineStage.Recombination)
        )

        assertEquals(
            listOf(PipelineStage.Asr, PipelineStage.Translation, PipelineStage.Recombination),
            spec.stages
        )
    }
}
