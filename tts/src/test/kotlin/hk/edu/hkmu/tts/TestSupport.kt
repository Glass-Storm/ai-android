@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.tts.asset.AssetProbe
import hk.edu.hkmu.tts.asset.ModelAssetValidator
import hk.edu.hkmu.tts.model.TtsModelCatalog
import hk.edu.hkmu.tts.synth.VitsSynthesizer
import hk.edu.hkmu.tts.synth.VitsSynthesizerFactory
import java.io.File

/** Cantonese VITS native rate used by fakes (proves the engine never hard-codes 16000). */
internal const val CANTONESE_RATE_HZ = 22_050

/** Number of synthetic samples (> 2 engine chunks) to exercise ordered chunking. */
internal const val RAMP_SAMPLE_COUNT = 9_000

/** Deterministic ramp of normalized samples spanning `[-1.0, 0.99]`. */
internal val RAMP_SAMPLES: FloatArray = FloatArray(RAMP_SAMPLE_COUNT) { ((it % 200) - 100) / 100f }

/** Every asset name any shipped voice may require, so a fake probe can satisfy validation. */
internal val ALL_ASSET_NAMES: List<String> = listOf(
    "vits-cantonese-hf-xiaomaiiwn.onnx",
    "model.onnx",
    "tokens.txt",
    "lexicon.txt",
    "rule.fst",
    "espeak-ng-data"
)

/** A fake probe reporting exactly [names] in every directory. */
internal fun probeWith(names: List<String>): AssetProbe = AssetProbe { names.map { name -> File(name) } }

/** In-memory [VitsSynthesizer] that never touches native code. */
internal class FakeVitsSynthesizer(
    override val sampleRateHz: Int = CANTONESE_RATE_HZ,
    override val numSpeakers: Int = 1,
    private val generate: (String, Int, Float) -> FloatArray = { _, _, _ -> RAMP_SAMPLES.copyOf() }
) : VitsSynthesizer {

    /** Last Float32 buffer produced, for order/equality assertions. */
    var lastSamples: FloatArray? = null

    /** Text of the last call. */
    var lastText: String? = null

    /** Whether [close] was called. */
    var closed: Boolean = false

    override fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray {
        lastText = text
        val samples = generate(text, speakerId, speed)
        lastSamples = samples
        return samples
    }

    override fun close() {
        closed = true
    }
}

/** Factory returning the same fake for every request. */
internal fun factoryReturning(synth: VitsSynthesizer): VitsSynthesizerFactory {
    return VitsSynthesizerFactory { _, _ -> synth }
}

/** Builds an engine wired to a fake seam and a permissive asset probe. */
internal fun engineFor(
    language: Language,
    factory: VitsSynthesizerFactory,
    config: TtsConfig = TtsConfig(language = language, speakerId = 3)
): SherpaTtsEngine = SherpaTtsEngine(
    config = config,
    modelRoot = File("/nonexistent-models"),
    catalog = TtsModelCatalog.Standard,
    validator = ModelAssetValidator(probeWith(ALL_ASSET_NAMES)),
    synthesizerFactory = factory
)
