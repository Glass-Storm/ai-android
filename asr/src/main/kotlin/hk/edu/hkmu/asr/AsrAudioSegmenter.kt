package hk.edu.hkmu.asr

import kotlin.math.sqrt

/** One detected utterance: a slice of speech samples plus its position in the input stream. */
internal class SpeechUtterance(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val samples: FloatArray
)

/** Tunables for [AsrAudioSegmenter]; defaults target 16 kHz mono speech. */
internal data class AsrSegmenterConfig(
    val frameLengthSamples: Int = DEFAULT_FRAME_LENGTH_SAMPLES,
    val rmsThreshold: Float = DEFAULT_RMS_THRESHOLD,
    val minSpeechFrames: Int = DEFAULT_MIN_SPEECH_FRAMES,
    val silenceFramesToClose: Int = DEFAULT_SILENCE_FRAMES_TO_CLOSE,
    val maxUtteranceFrames: Int = DEFAULT_MAX_UTTERANCE_FRAMES
)

private const val DEFAULT_FRAME_LENGTH_SAMPLES = 480
private const val DEFAULT_RMS_THRESHOLD = 0.01f
private const val DEFAULT_MIN_SPEECH_FRAMES = 3
private const val DEFAULT_SILENCE_FRAMES_TO_CLOSE = 10
private const val DEFAULT_MAX_UTTERANCE_FRAMES = 500
private const val INITIAL_UTTERANCE_CAPACITY = 16_000
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Energy-based voice-activity segmenter.
 *
 * SenseVoice is an offline, whole-segment recognizer, so a caller streaming continuous PCM must be
 * split into utterances first. This segmenter is intentionally pure Kotlin (no native, no Android)
 * so its boundary logic is fully exercised by Tier A tests.
 *
 * Audio is consumed frame-by-frame (default 30 ms). A run of frames above [AsrSegmenterConfig.rmsThreshold]
 * starts an utterance; a run of at least [AsrSegmenterConfig.silenceFramesToClose] silent frames ends
 * it (trailing silence is trimmed). Utterances shorter than [AsrSegmenterConfig.minSpeechFrames] are
 * dropped as noise. Reaching [AsrSegmenterConfig.maxUtteranceFrames] force-closes the utterance.
 *
 * This class is **not** thread-safe; one instance is created per ASR flow collection.
 */
internal class AsrAudioSegmenter(
    private val sampleRateHz: Int,
    private val config: AsrSegmenterConfig = AsrSegmenterConfig()
) {
    private var frameBuffer = FloatArray(config.frameLengthSamples)
    private var frameBufferSize = 0
    private var utteranceSamples = FloatArray(INITIAL_UTTERANCE_CAPACITY)
    private var utteranceLength = 0
    private var inSpeech = false
    private var speechFrames = 0
    private var silenceFrames = 0
    private var framesInUtterance = 0
    private var totalSamplesSeen = 0L
    private var utteranceStartSample = 0L

    /** Feeds more PCM samples; returns any utterances completed by this input. */
    fun accept(samples: FloatArray): List<SpeechUtterance> {
        val completed = mutableListOf<SpeechUtterance>()
        for (sample in samples) {
            frameBuffer[frameBufferSize++] = sample
            if (frameBufferSize == frameBuffer.size) {
                val frame = frameBuffer
                frameBuffer = FloatArray(config.frameLengthSamples)
                frameBufferSize = 0
                processFrame(frame, completed)
            }
        }
        return completed
    }

    /** Closes any in-progress utterance (used at end of stream) and returns it. */
    fun flush(): List<SpeechUtterance> {
        val completed = mutableListOf<SpeechUtterance>()
        if (inSpeech) {
            completeUtterance(completed)
        }
        return completed
    }

    private fun processFrame(frame: FloatArray, completed: MutableList<SpeechUtterance>) {
        val frameStartSample = totalSamplesSeen
        totalSamplesSeen += frame.size
        val isSpeech = rms(frame) > config.rmsThreshold
        if (!inSpeech) {
            if (isSpeech) {
                beginUtterance(frameStartSample, frame)
            }
            return
        }
        appendFrame(frame)
        framesInUtterance++
        if (isSpeech) {
            speechFrames++
            silenceFrames = 0
        } else {
            silenceFrames++
        }
        if (silenceFrames >= config.silenceFramesToClose || framesInUtterance >= config.maxUtteranceFrames) {
            completeUtterance(completed)
        }
    }

    private fun beginUtterance(frameStartSample: Long, frame: FloatArray) {
        inSpeech = true
        utteranceStartSample = frameStartSample
        utteranceLength = 0
        speechFrames = 1
        silenceFrames = 0
        framesInUtterance = 1
        appendFrame(frame)
    }

    private fun appendFrame(frame: FloatArray) {
        val required = utteranceLength + frame.size
        if (required > utteranceSamples.size) {
            utteranceSamples = utteranceSamples.copyOf(maxOf(utteranceSamples.size * 2, required))
        }
        frame.copyInto(utteranceSamples, utteranceLength)
        utteranceLength = required
    }

    private fun completeUtterance(completed: MutableList<SpeechUtterance>) {
        val trailingSilence = silenceFrames * config.frameLengthSamples
        val sampleCount = (utteranceLength - trailingSilence).coerceAtLeast(0)
        if (speechFrames >= config.minSpeechFrames && sampleCount > 0) {
            completed += SpeechUtterance(
                startTimeMillis = toMillis(utteranceStartSample),
                endTimeMillis = toMillis(utteranceStartSample + sampleCount),
                samples = utteranceSamples.copyOf(sampleCount)
            )
        }
        inSpeech = false
        utteranceLength = 0
        speechFrames = 0
        silenceFrames = 0
        framesInUtterance = 0
    }

    private fun toMillis(sampleIndex: Long): Long = sampleIndex * MILLIS_PER_SECOND / sampleRateHz

    private fun rms(frame: FloatArray): Float {
        val meanSquare = frame.sumOf { (it * it).toDouble() } / frame.size
        return sqrt(meanSquare).toFloat()
    }
}
