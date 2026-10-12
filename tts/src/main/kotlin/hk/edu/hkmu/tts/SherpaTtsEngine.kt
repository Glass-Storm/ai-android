package hk.edu.hkmu.tts

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.core.engine.AudioChunk
import hk.edu.hkmu.core.engine.TtsEngine
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.tts.asset.FileSystemAssetProbe
import hk.edu.hkmu.tts.asset.ModelAssetValidator
import hk.edu.hkmu.tts.model.ResolvedTtsModel
import hk.edu.hkmu.tts.model.TtsModelCatalog
import hk.edu.hkmu.tts.model.TtsModelSpec
import hk.edu.hkmu.tts.pcm.Int16Chunk
import hk.edu.hkmu.tts.pcm.toInt16
import hk.edu.hkmu.tts.synth.SherpaVitsSynthesizerFactory
import hk.edu.hkmu.tts.synth.SynthesizedAudio
import hk.edu.hkmu.tts.synth.VitsSynthesizer
import hk.edu.hkmu.tts.synth.VitsSynthesizerFactory
import java.io.File
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** PCM frame size used to split a synthesized utterance into ordered chunks. */
private const val PCM_CHUNK_SAMPLES = 4_096

/** Default synthesis speed (duration scale); 1.0 = normal. */
private const val DEFAULT_SPEED = 1.0f

/**
 * Voice metadata reported by a loaded model.
 *
 * @property language Voice language.
 * @property sampleRateHz **Model-reported** sample rate (Hz); not a library constant.
 * @property speakerId Speaker id used for synthesis.
 * @property numSpeakers Speaker count reported by the model.
 */
data class TtsVoiceInfo(
    val language: Language,
    val sampleRateHz: Int,
    val speakerId: Int,
    val numSpeakers: Int
)

/**
 * [TtsEngine] over sherpa-onnx VITS, serving Cantonese / Mandarin / English voices.
 *
 * ### Pipeline
 * `Language` → [TtsModelCatalog] spec → [ModelAssetValidator] (typed `ModelAssetMissing` before any
 * native call) → native synthesis (Float32 PCM) → ordered chunks. [synthesize] emits the core
 * Float32 [AudioChunk]s; [synthesizeInt16] emits the same audio converted to 16-bit PCM. Every
 * chunk carries the **model-reported** sample rate; no rate is ever assumed.
 *
 * The native adapter is behind the narrow [VitsSynthesizerFactory] seam, so unit tests exercise the
 * whole engine with a fake and never load `sherpa-onnx-jni`.
 *
 * @param config Engine configuration (language selects the voice; speaker id selects the speaker).
 * @param modelRoot Directory under which each voice's `directoryName` lives.
 * @param catalog Language → voice mapping.
 * @param validator Asset pre-validator.
 * @param synthesizerFactory Native seam.
 */
class SherpaTtsEngine internal constructor(
    private val config: TtsConfig,
    private val modelRoot: File,
    private val catalog: TtsModelCatalog,
    private val validator: ModelAssetValidator,
    private val synthesizerFactory: VitsSynthesizerFactory
) : TtsEngine, AutoCloseable {

    private val loaded: MutableMap<Language, VitsSynthesizer> = HashMap()
    private val lock = Mutex()

    /** Production constructor: real file-system validator and the native sherpa-onnx synthesizer. */
    constructor(
        config: TtsConfig,
        modelRoot: File,
        catalog: TtsModelCatalog = TtsModelCatalog.Standard
    ) : this(config, modelRoot, catalog, ModelAssetValidator(FileSystemAssetProbe), SherpaVitsSynthesizerFactory)

    /** Emits the synthesized utterance as ordered normalized Float32 [AudioChunk]s. */
    override fun synthesize(text: String): Flow<AudioChunk> = channelFlow {
        val audio = synthesizeInternal(text)
        for (chunk in audio.samples.asFloatChunks()) {
            trySendBlocking(AudioChunk(chunk, audio.sampleRateHz))
        }
    }

    /**
     * Emits the synthesized utterance converted to 16-bit PCM, in order.
     *
     * This is the `Float32`→`Int16` boundary; see `hk.edu.hkmu.tts.pcm.toInt16`.
     */
    fun synthesizeInt16(text: String): Flow<Int16Chunk> = channelFlow {
        val audio = synthesizeInternal(text)
        for (chunk in audio.samples.asFloatChunks()) {
            trySendBlocking(Int16Chunk(toInt16(chunk), audio.sampleRateHz, config.speakerId))
        }
    }

    /** Loads (if needed) the configured voice and returns its model-reported metadata. */
    suspend fun voiceInfo(): TtsVoiceInfo = lock.withLock {
        val language = config.language
        val synth = loadedOrLoad(language)
        TtsVoiceInfo(language, synth.sampleRateHz, config.speakerId, synth.numSpeakers)
    }

    /** Frees every loaded native session. */
    override fun close() {
        loaded.values.forEach { runCatching { it.close() } }
        loaded.clear()
    }

    private suspend fun synthesizeInternal(text: String): SynthesizedAudio = lock.withLock {
        val spec = catalog.requireLanguage(config.language)
        val synth = loadedOrLoad(config.language)
        val samples = synth.synthesize(text, config.speakerId, DEFAULT_SPEED)
        if (text.isNotBlank() && samples.isEmpty()) {
            throw AiAndroidException.ModelLoadFailed(spec.id, EmptySynthesisException())
        }
        SynthesizedAudio(samples, synth.sampleRateHz, synth.numSpeakers)
    }

    private fun loadedOrLoad(language: Language): VitsSynthesizer {
        loaded[language]?.let { return it }
        val synth = load(catalog.requireLanguage(language))
        loaded[language] = synth
        return synth
    }

    private fun load(spec: TtsModelSpec): VitsSynthesizer {
        requireCpuOnly()
        val resolved = validator.validate(spec, modelRoot)
        return createSynthesizer(resolved, spec)
    }

    private fun requireCpuOnly() {
        if (config.runtime.accelerator == Accelerator.Gpu) {
            throw AiAndroidException.AcceleratorUnavailable(Accelerator.Gpu, listOf(Accelerator.Cpu))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createSynthesizer(resolved: ResolvedTtsModel, spec: TtsModelSpec): VitsSynthesizer {
        return try {
            synthesizerFactory.create(resolved, spec)
        } catch (e: AiAndroidException) {
            throw e
        } catch (e: Exception) {
            throw AiAndroidException.ModelLoadFailed(spec.id, e)
        }
    }
}

/** Signals that the native runtime accepted the model but produced zero samples. */
private class EmptySynthesisException : IllegalStateException("native synthesis produced no samples")

/** Splits a sample buffer into fixed-size ordered chunks (the last one may be shorter). */
private fun FloatArray.asFloatChunks(chunkSize: Int = PCM_CHUNK_SAMPLES): Sequence<FloatArray> {
    val source = this
    return sequence {
        var offset = 0
        while (offset < source.size) {
            val end = minOf(offset + chunkSize, source.size)
            yield(source.copyOfRange(offset, end))
            offset = end
        }
    }
}
