package hk.edu.hkmu.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import hk.edu.hkmu.core.config.Language
import java.io.File

/**
 * Constructs the production [SenseVoiceRecognizer] backed by the vendored sherpa-onnx JNI.
 *
 * The directory is validated against [AsrModelAssets] **before** the native `OfflineRecognizer` is
 * created, because sherpa native raises no typed error for a missing asset (S4). A missing asset
 * therefore surfaces as `AiAndroidException.ModelAssetMissing` from pure JVM code.
 */
internal class NativeSenseVoiceRecognizerFactory(
    private val modelId: String,
    private val modelDir: File,
    private val language: Language,
    private val numThreads: Int
) {
    /** Validates assets and constructs the native recognizer. */
    fun create(): SenseVoiceRecognizer {
        val assets = AsrModelAssets.ensurePresent(modelId, modelDir)
        return NativeSenseVoiceRecognizer(assets, SenseVoiceLanguages.toModelCode(language), numThreads)
    }
}

/**
 * Thin wrapper over `com.k2fsa.sherpa.onnx.OfflineRecognizer`.
 *
 * One recognizer instance is reused across utterances; an `OfflineStream` is created per utterance
 * and released in a `finally`. This type must only be constructed on a device that has the
 * sherpa-onnx native libraries for its ABI.
 */
internal class NativeSenseVoiceRecognizer(
    assets: ResolvedAsrAssets,
    languageCode: String,
    numThreads: Int
) : SenseVoiceRecognizer {

    private val recognizer: OfflineRecognizer = buildRecognizer(assets, languageCode, numThreads)

    override fun recognize(samples: FloatArray, sampleRateHz: Int): SenseVoiceResult {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRateHz)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            return SenseVoiceResult(result.text, result.lang)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }
}

private fun buildRecognizer(assets: ResolvedAsrAssets, languageCode: String, numThreads: Int): OfflineRecognizer =
    OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = FEATURE_SAMPLE_RATE_HZ,
                featureDim = FEATURE_DIM,
                dither = DITHER_DISABLED
            ),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = assets.modelFile.absolutePath,
                    language = languageCode,
                    useInverseTextNormalization = true
                ),
                tokens = assets.tokensFile.absolutePath,
                numThreads = numThreads,
                provider = PROVIDER_CPU,
                debug = false
            )
        )
    )

private const val FEATURE_SAMPLE_RATE_HZ = 16_000
private const val FEATURE_DIM = 80
private const val DITHER_DISABLED = 0.0f
private const val PROVIDER_CPU = "cpu"
