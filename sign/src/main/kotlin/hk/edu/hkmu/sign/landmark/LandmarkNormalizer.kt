package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Turns a window of [LandmarkFrame]s into the [ClassifierInputSpec]'s tensor.
 *
 * Pure Kotlin, no native and no Android type, so the entire classification input contract is
 * exercisable in a host JVM test without the MediaPipe native library. It is deliberately
 * classifier-agnostic: every shape/range/layout decision comes from the injected [spec].
 *
 * ### Transform (per the S2 adapter spec §3b)
 * For keypoint `k` and frame `t`, with `F = 2 * windowSize`:
 * - `nodeMajor[k*F + t]     = clamp(x_k at frame t)`
 * - `nodeMajor[k*F + W + t] = clamp(y_k at frame t)`
 *
 * [LandmarkTensorLayout.NodeMajor] keeps that buffer; [LandmarkTensorLayout.NodeFeatureMajor]
 * transposes it to `[1, F, keypoints]` for the onnx2tf-exported graph.
 *
 * ### Validation
 * A window whose frame count, per-frame keypoint count, or coordinate finiteness violates the
 * contract throws a typed [AiAndroidException.MalformedFrame] rather than producing a silently wrong
 * tensor. A "missing landmarks" frame (fewer keypoints than the classifier needs) is the canonical
 * trigger.
 *
 * @param spec The classifier input contract to satisfy.
 */
class LandmarkNormalizer(private val spec: ClassifierInputSpec) {

    /**
     * Normalizes one full window into the classifier tensor.
     *
     * @throws AiAndroidException.MalformedFrame when the window or any frame violates the contract.
     */
    fun normalize(window: List<LandmarkFrame>): NormalizedLandmarkTensor {
        validate(window)
        val nodeMajor = FloatArray(spec.elementCount)
        window.forEachIndexed { frameIndex, frame -> accumulate(frame, frameIndex, nodeMajor) }
        val data = when (spec.layout) {
            LandmarkTensorLayout.NodeMajor -> nodeMajor
            LandmarkTensorLayout.NodeFeatureMajor -> transpose(nodeMajor)
        }
        return NormalizedLandmarkTensor(spec, spec.shape, TensorDtype.Float32, data)
    }

    private fun validate(window: List<LandmarkFrame>) {
        if (window.size != spec.windowSize) {
            throw AiAndroidException.MalformedFrame(
                "expected a ${spec.windowSize}-frame window, got ${window.size}"
            )
        }
        window.forEachIndexed { index, frame ->
            if (frame.keypoints.size != spec.keypointsPerFrame) {
                throw AiAndroidException.MalformedFrame(
                    "frame $index has ${frame.keypoints.size} landmarks, expected ${spec.keypointsPerFrame}"
                )
            }
        }
    }

    private fun accumulate(frame: LandmarkFrame, frameIndex: Int, out: FloatArray) {
        frame.keypoints.forEachIndexed { keypointIndex, keypoint ->
            val base = keypointIndex * spec.nodeFeatureLength
            out[base + frameIndex] = clamp(keypoint.x)
            out[base + spec.windowSize + frameIndex] = clamp(keypoint.y)
        }
    }

    private fun clamp(value: Float): Float {
        if (!value.isFinite()) {
            throw AiAndroidException.MalformedFrame("non-finite landmark coordinate: $value")
        }
        return value.coerceIn(spec.range.start, spec.range.endInclusive)
    }

    private fun transpose(nodeMajor: FloatArray): FloatArray {
        val keypoints = spec.keypointsPerFrame
        val features = spec.nodeFeatureLength
        val transposed = FloatArray(nodeMajor.size)
        for (feature in 0 until features) {
            for (keypoint in 0 until keypoints) {
                transposed[feature * keypoints + keypoint] = nodeMajor[keypoint * features + feature]
            }
        }
        return transposed
    }
}
