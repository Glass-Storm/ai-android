package hk.edu.hkmu.diarize

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import hk.edu.hkmu.core.config.DiarizationConfig
import hk.edu.hkmu.core.engine.DiarizationSegment
import java.io.File

/**
 * Constructs the production [DiarizationBackend] backed by the vendored sherpa-onnx JNI.
 *
 * The directory is validated against [DiarizationModelAssets] **before** the native
 * `OfflineSpeakerDiarization` is created, because sherpa native raises no typed error for a missing
 * asset. A missing asset therefore surfaces as `AiAndroidException.ModelAssetMissing` from pure JVM
 * code.
 */
internal class SherpaDiarizationBackendFactory(
    private val spec: DiarizationModelSpec,
    private val modelDir: File,
    private val config: DiarizationConfig
) : DiarizationBackendFactory {
    override fun create(): DiarizationBackend {
        val assets = DiarizationModelAssets.ensurePresent(spec, modelDir)
        return SherpaDiarizationBackend(assets, config)
    }
}

/**
 * Thin wrapper over `com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization`.
 *
 * One diarizer instance is reused across calls; `process` is a whole-utterance call that returns
 * every speaker turn at once. This type must only be constructed on a device that has the
 * sherpa-onnx native libraries for its ABI.
 */
internal class SherpaDiarizationBackend(
    assets: ResolvedDiarizationAssets,
    config: DiarizationConfig
) : DiarizationBackend {

    private val diarization: OfflineSpeakerDiarization = buildDiarization(assets, config)

    override fun diarize(samples: FloatArray): List<DiarizationSegment> = diarization.process(samples).map { segment ->
        DiarizationSegment(
            startTimeMillis = secondsToMillis(segment.start),
            endTimeMillis = secondsToMillis(segment.end),
            speakerLabel = speakerLabel(segment.speaker),
            speakerId = speakerId(segment.speaker)
        )
    }

    override fun close() {
        diarization.release()
    }
}

private fun buildDiarization(assets: ResolvedDiarizationAssets, config: DiarizationConfig): OfflineSpeakerDiarization =
    OfflineSpeakerDiarization(
        assetManager = null,
        config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = assets.segmentationFile.absolutePath
                ),
                numThreads = config.numThreads,
                debug = false,
                provider = PROVIDER_CPU
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = assets.embeddingFile.absolutePath,
                numThreads = config.numThreads,
                debug = false,
                provider = PROVIDER_CPU
            ),
            clustering = FastClusteringConfig(
                numClusters = config.numClusters,
                threshold = config.clusteringThreshold,
                computeConfidence = false
            ),
            minDurationOn = config.minDurationOnSeconds,
            minDurationOff = config.minDurationOffSeconds
        )
    )

/** Converts a sherpa `process` boundary in seconds to whole milliseconds. */
internal fun secondsToMillis(seconds: Float): Long = (seconds * MILLIS_PER_SECOND).toLong()

/** Maps a raw, 0-based sherpa speaker index to the 1-based display label (`"Speaker N"`). */
internal fun speakerLabel(speaker: Int): String = "Speaker ${speaker + 1}"

/** Public 0-based speaker id for a raw sherpa speaker index (already 0-based; named for the offset). */
internal fun speakerId(speaker: Int): Int = speaker

private const val PROVIDER_CPU = "cpu"
private const val MILLIS_PER_SECOND = 1_000f
