package hk.edu.hkmu.sign.gesture

import android.graphics.Bitmap

/**
 * Narrow internal seam over a native static-gesture backend.
 *
 * It emits only [GestureBackendResult] (MediaPipe-free), so the production
 * [MediaPipeGestureBackend] can be substituted by a fake in a host JVM test and the registry
 * lifecycle exercised without a native library. The production implementation is created lazily by
 * [MediaPipeGestureModelLoader].
 */
internal interface GestureBackend : AutoCloseable {

    /** Runs the static gesture recognizer on one image and returns the raw top gesture. */
    fun recognize(image: Bitmap): GestureBackendResult

    /** Releases the underlying native recognizer. Idempotent. */
    override fun close() {}
}

/**
 * Raw static-gesture output of a [GestureBackend], before projection into the public
 * [GesturePrediction].
 *
 * An empty [label] and a [handCount] of `0` mean "no gesture / no hand", which
 * [MediaPipeGestureRecognizer.recognize] maps to `null`.
 *
 * @property label Top gesture category name, or `""` when none was recognized.
 * @property score Confidence for [label], or `0f` when none was recognized.
 * @property handedness Reported hand side, or `null` when unlabeled.
 * @property handCount Number of hands for which a gesture was produced.
 */
internal class GestureBackendResult(
    val label: String,
    val score: Float,
    val handedness: String?,
    val handCount: Int
)
