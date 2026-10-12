package hk.edu.hkmu.tts.synth

import hk.edu.hkmu.tts.model.ResolvedTtsModel
import hk.edu.hkmu.tts.model.TtsModelSpec

/**
 * Result of a single native synthesis pass.
 *
 * @property samples Normalized Float32 PCM in `[-1.0, 1.0]`.
 * @property sampleRateHz Sample rate actually reported by the loaded model (never assumed).
 * @property numSpeakers Speaker count reported by the loaded model.
 */
internal class SynthesizedAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val numSpeakers: Int
)

/**
 * **Narrow internal seam** over the native VITS runtime.
 *
 * This is the only surface the engine's orchestration (language mapping, asset validation,
 * Float32→Int16 conversion, chunking, backpressure) couples to. Unit tests substitute a fake
 * implementation, so the entire engine logic is exercised **without loading the native
 * `sherpa-onnx-jni` library** — which matters because `com.k2fsa.sherpa.onnx.OfflineTts`
 * calls `System.loadLibrary("sherpa-onnx-jni")` in its static initializer.
 */
internal interface VitsSynthesizer : AutoCloseable {
    /** Sample rate reported by the loaded model, in Hz. */
    val sampleRateHz: Int

    /** Speaker count reported by the loaded model. */
    val numSpeakers: Int

    /**
     * Synthesizes [text].
     *
     * @param speakerId Model speaker id (`0` for single-speaker voices).
     * @param speed Duration scale (1.0 = normal).
     * @return Normalized Float32 PCM.
     */
    fun synthesize(text: String, speakerId: Int, speed: Float): FloatArray
}

/** Creates a [VitsSynthesizer] for an already-validated model. The only native-touching seam. */
internal fun interface VitsSynthesizerFactory {
    fun create(model: ResolvedTtsModel, spec: TtsModelSpec): VitsSynthesizer
}
