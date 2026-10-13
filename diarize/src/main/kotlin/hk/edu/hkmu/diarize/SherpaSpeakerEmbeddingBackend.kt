package hk.edu.hkmu.diarize

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import hk.edu.hkmu.core.config.SpeakerEmbeddingConfig
import hk.edu.hkmu.core.error.AiAndroidException
import java.io.File

/**
 * Constructs the production [SpeakerEmbeddingBackend] backed by the vendored sherpa-onnx JNI.
 *
 * The model directory is validated against [DiarizationModelAssets] **before** the native
 * `SpeakerEmbeddingExtractor` is created, because sherpa native raises no typed error for a missing
 * asset. A missing embedding graph therefore surfaces as `AiAndroidException.ModelAssetMissing` from
 * pure JVM code. Only the embedding graph is required; the segmentation graph the diarizer needs is
 * unused here.
 */
internal class SherpaSpeakerEmbeddingBackendFactory(
    private val spec: DiarizationModelSpec,
    private val modelDir: File,
    private val config: SpeakerEmbeddingConfig
) : SpeakerEmbeddingBackendFactory {
    override fun create(): SpeakerEmbeddingBackend {
        val embeddingFile = DiarizationModelAssets.ensureEmbeddingPresent(spec, modelDir)
        return SherpaSpeakerEmbeddingBackend(embeddingFile, config)
    }
}

/**
 * Thin wrapper over `com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor` — the same CAM++ embedding
 * graph the diarizer uses, driven directly to produce a standalone voiceprint.
 *
 * One extractor is reused across calls. This type must only be constructed on a device that has the
 * sherpa-onnx native libraries for its ABI.
 */
internal class SherpaSpeakerEmbeddingBackend(
    embeddingFile: File,
    private val config: SpeakerEmbeddingConfig
) : SpeakerEmbeddingBackend {

    private val extractor: SpeakerEmbeddingExtractor = buildExtractor(embeddingFile, config)

    override fun embed(samples: FloatArray): FloatArray {
        val stream = extractor.createStream()
        try {
            stream.acceptWaveform(samples, config.sampleRateHz)
            stream.inputFinished()
            if (!extractor.isReady(stream)) {
                throw AiAndroidException.MalformedFrame(
                    "speaker-embedding stream not ready (audio too short for a voiceprint)"
                )
            }
            return extractor.compute(stream)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        extractor.release()
    }
}

private fun buildExtractor(embeddingFile: File, config: SpeakerEmbeddingConfig): SpeakerEmbeddingExtractor =
    SpeakerEmbeddingExtractor(
        assetManager = null,
        config = SpeakerEmbeddingExtractorConfig(
            model = embeddingFile.absolutePath,
            numThreads = config.numThreads,
            debug = false,
            provider = PROVIDER_CPU
        )
    )

private const val PROVIDER_CPU = "cpu"
