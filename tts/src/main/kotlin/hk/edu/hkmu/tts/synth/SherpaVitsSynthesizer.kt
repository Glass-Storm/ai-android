package hk.edu.hkmu.tts.synth

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import hk.edu.hkmu.tts.model.ResolvedTtsModel
import hk.edu.hkmu.tts.model.TtsModelSpec

/** Empty path marker accepted by sherpa-onnx for optional assets a voice does not use. */
private const val NO_PATH = ""

/** sherpa-onnx on Android only exposes the CPU execution provider for VITS. */
private const val PROVIDER_CPU = "cpu"

/**
 * Production [VitsSynthesizer]: thin adapter over `com.k2fsa.sherpa.onnx.OfflineTts`.
 *
 * The native handle is created in the constructor (file-based, no `AssetManager`), so it is only
 * ever instantiated for an already-validated [ResolvedTtsModel]. `sampleRate()`/`numSpeakers()` are
 * read from the model itself; no rate is hard-coded.
 */
internal class SherpaVitsSynthesizer(
    model: ResolvedTtsModel,
    spec: TtsModelSpec
) : VitsSynthesizer {

    private val tts: OfflineTts = OfflineTts(config = buildConfig(model, spec))

    override val sampleRateHz: Int
        get() = tts.sampleRate()

    override val numSpeakers: Int
        get() = tts.numSpeakers()

    override fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray =
        tts.generate(text, speakerId, speed).samples

    override fun close() {
        tts.release()
    }
}

/** Builds the sherpa-onnx config for a validated VITS voice. */
private fun buildConfig(model: ResolvedTtsModel, spec: TtsModelSpec): OfflineTtsConfig {
    val vits = OfflineTtsVitsModelConfig(
        model = model.modelPath.absolutePath,
        lexicon = model.lexiconPath?.absolutePath ?: NO_PATH,
        tokens = model.tokensPath.absolutePath,
        dataDir = model.dataDirPath?.absolutePath ?: NO_PATH,
        dictDir = model.dictDirPath?.absolutePath ?: NO_PATH
    )
    val modelConfig = OfflineTtsModelConfig(
        vits = vits,
        numThreads = spec.numThreads,
        provider = PROVIDER_CPU
    )
    return OfflineTtsConfig(
        model = modelConfig,
        ruleFsts = model.ruleFstPath?.absolutePath ?: NO_PATH
    )
}

/** Creates the production native synthesizer. */
internal object SherpaVitsSynthesizerFactory : VitsSynthesizerFactory {
    override fun create(model: ResolvedTtsModel, spec: TtsModelSpec): VitsSynthesizer =
        SherpaVitsSynthesizer(model, spec)
}
