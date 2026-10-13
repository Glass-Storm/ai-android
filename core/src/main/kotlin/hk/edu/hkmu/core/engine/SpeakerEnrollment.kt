package hk.edu.hkmu.core.engine

import hk.edu.hkmu.core.error.AiAndroidException
import kotlin.math.sqrt

/** Default cosine-similarity threshold above which a voiceprint is considered a match. */
const val DEFAULT_VOICEPRINT_MATCH_THRESHOLD: Float = 0.5f

/**
 * One enrolled speaker: a display [displayName] bound to a [voiceprint].
 *
 * @property displayName Human-readable name to report when this voiceprint matches (for example
 *   `"Alice"`).
 * @property voiceprint Enrolled voiceprint. Must have been produced by the same embedding model the
 *   query voiceprint was (see [Voiceprint.modelId]).
 */
data class SpeakerEnrollment(
    val displayName: String,
    val voiceprint: Voiceprint
)

/**
 * The result of matching a query voiceprint against an [EnrollmentDirectory].
 *
 * @property displayName Display name of the matched enrollment.
 * @property similarity Cosine similarity between the query and the matched enrollment, in `[-1, 1]`.
 *   Always greater than or equal to the directory's threshold.
 */
data class SpeakerMatch(
    val displayName: String,
    val similarity: Float
)

/**
 * Pure, host-testable voiceprint comparison.
 *
 * The similarity/threshold/match decision is a plain function of the vectors, so it can be unit
 * tested without any native library, model, or device. [EnrollmentDirectory] delegates to it.
 */
object VoiceprintMatcher {

    /**
     * Cosine similarity of two equal-length embedding vectors, in `[-1, 1]`.
     *
     * The vectors do not need to be pre-normalized. A degenerate (zero-magnitude) vector has no
     * direction, so a comparison involving one returns `0f` — strictly below any positive threshold,
     * hence never a match.
     *
     * @throws AiAndroidException.InputShapeMismatch when the two vectors differ in length.
     */
    fun cosineSimilarity(query: FloatArray, candidate: FloatArray): Float {
        if (query.size != candidate.size) {
            throw AiAndroidException.InputShapeMismatch(
                expected = "voiceprint dim ${query.size}",
                actual = "voiceprint dim ${candidate.size}"
            )
        }
        var dot = 0.0
        var querySquared = 0.0
        var candidateSquared = 0.0
        for (index in query.indices) {
            dot += query[index] * candidate[index]
            querySquared += query[index] * query[index]
            candidateSquared += candidate[index] * candidate[index]
        }
        if (querySquared == 0.0 || candidateSquared == 0.0) {
            return 0f
        }
        return (dot / (sqrt(querySquared) * sqrt(candidateSquared))).toFloat()
    }

    /**
     * Best enrollment for [query] whose cosine similarity is at least [threshold], or `null`.
     *
     * Ties are broken deterministically in favour of the earliest enrollment in [enrollments].
     *
     * @throws AiAndroidException.InputShapeMismatch when [query] and an enrollment differ in length.
     */
    fun bestMatch(
        query: Voiceprint,
        enrollments: List<SpeakerEnrollment>,
        threshold: Float = DEFAULT_VOICEPRINT_MATCH_THRESHOLD
    ): SpeakerMatch? {
        var best: SpeakerMatch? = null
        enrollments.forEach { enrollment ->
            val similarity = cosineSimilarity(query.embedding, enrollment.voiceprint.embedding)
            if (similarity >= threshold && (best == null || similarity > best.similarity)) {
                best = SpeakerMatch(enrollment.displayName, similarity)
            }
        }
        return best
    }
}

/**
 * Immutable set of enrolled speakers plus the cosine-similarity [threshold] used to resolve a
 * [Voiceprint] to a display name.
 *
 * This is the enrollment directory from the local-enrollment contract: enroll each known speaker's
 * voiceprint once, then [match] an incoming voiceprint to the closest enrolled speaker. Every
 * operation is a pure function of the stored vectors — no native handle is retained — so a directory
 * can be built and matched entirely on the host JVM.
 *
 * @property enrollments Enrolled speakers, in insertion order (used as the tie-break for equal
 *   similarities).
 * @property threshold Minimum cosine similarity required for [match] to return a result; a higher
 *   value is stricter.
 */
class EnrollmentDirectory(
    val enrollments: List<SpeakerEnrollment> = emptyList(),
    val threshold: Float = DEFAULT_VOICEPRINT_MATCH_THRESHOLD
) {
    init {
        require(threshold.isFinite()) { "threshold must be finite, was $threshold" }
    }

    /** Number of enrolled speakers. */
    val size: Int get() = enrollments.size

    /** Returns the best enrolled speaker for [query], or `null` when none reaches [threshold]. */
    fun match(query: Voiceprint): SpeakerMatch? = VoiceprintMatcher.bestMatch(query, enrollments, threshold)

    /**
     * Returns a **new** directory with [displayName] bound to [voiceprint] appended; this instance is
     * left unchanged.
     */
    fun enroll(displayName: String, voiceprint: Voiceprint): EnrollmentDirectory =
        enroll(SpeakerEnrollment(displayName, voiceprint))

    /** Returns a **new** directory with [enrollment] appended; this instance is left unchanged. */
    fun enroll(enrollment: SpeakerEnrollment): EnrollmentDirectory =
        EnrollmentDirectory(enrollments + enrollment, threshold)

    /** Returns a new empty directory that keeps this directory's [threshold]. */
    fun clear(): EnrollmentDirectory = EnrollmentDirectory(emptyList(), threshold)

    companion object {
        /** An empty directory using [DEFAULT_VOICEPRINT_MATCH_THRESHOLD]. */
        val Empty: EnrollmentDirectory = EnrollmentDirectory()
    }
}
