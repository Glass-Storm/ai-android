package hk.edu.hkmu.sign.landmark

/** Element type of a normalized classifier input tensor. Only float32 is currently produced. */
enum class TensorDtype {
    /** 32-bit IEEE-754 float. */
    Float32
}

/**
 * Memory layout of a normalized landmark tensor.
 *
 * Both layouts hold the same `keypointsPerFrame * 2 * windowSize` values but disagree on the nesting
 * order, which is why the S2 spec publishes two shapes for the same classifier (ONNX vs the
 * onnx2tf-transposed TFLite export). Keeping both here removes the "which ordering does the shipped
 * graph want" footgun.
 */
enum class LandmarkTensorLayout {
    /**
     * `[1, keypointsPerFrame, 2*windowSize]` — node-major. For each keypoint the first `windowSize`
     * entries are its `x` over the window, followed by its `y`. The ONNX/training contract
     * (`[1, 55, 100]` for TGCN).
     */
    NodeMajor,

    /**
     * `[1, 2*windowSize, keypointsPerFrame]` — the onnx2tf transpose of [NodeMajor]. The shipped
     * TFLite contract (`[1, 100, 55]` for TGCN).
     */
    NodeFeatureMajor
}

/**
 * Declares exactly what one classifier expects from a landmark window.
 *
 * This is the seam that keeps normalization from being hardcoded to a single classifier: the shape,
 * element type, value range and frame window are all data. Pass a different [ClassifierInputSpec] per
 * classifier (see [LandmarkerModelCatalog]) and the same [LandmarkNormalizer] serves them.
 *
 * @property classifierId Stable id of the classifier this spec targets.
 * @property windowSize Number of consecutive frames in one classifier window (e.g. 50).
 * @property keypointsPerFrame Number of 2D keypoints per frame (e.g. 55).
 * @property layout Memory layout / nesting order of the produced tensor.
 * @property range Inclusive value range each coordinate is clamped into (e.g. `0f..1f`).
 */
data class ClassifierInputSpec(
    val classifierId: String,
    val windowSize: Int,
    val keypointsPerFrame: Int,
    val layout: LandmarkTensorLayout,
    val range: ClosedFloatingPointRange<Float> = DEFAULT_RANGE
) {
    /** Values contributed by one keypoint: `windowSize` x's followed by `windowSize` y's. */
    val nodeFeatureLength: Int = windowSize * COORDS_PER_KEYPOINT

    /** Total flattened element count (`keypointsPerFrame * nodeFeatureLength`). */
    val elementCount: Int = keypointsPerFrame * nodeFeatureLength

    /** Tensor shape including the leading batch dimension of 1. */
    val shape: List<Int> = when (layout) {
        LandmarkTensorLayout.NodeMajor -> listOf(BATCH, keypointsPerFrame, nodeFeatureLength)
        LandmarkTensorLayout.NodeFeatureMajor -> listOf(BATCH, nodeFeatureLength, keypointsPerFrame)
    }

    init {
        require(windowSize > 0) { "windowSize must be positive" }
        require(keypointsPerFrame > 0) { "keypointsPerFrame must be positive" }
        require(range.start < range.endInclusive) { "range must be increasing" }
    }

    companion object {
        /** `x` and `y` per keypoint. */
        const val COORDS_PER_KEYPOINT: Int = 2

        /** Single-frame batch. */
        const val BATCH: Int = 1

        /** Default value range: image-normalized coordinates. */
        val DEFAULT_RANGE: ClosedFloatingPointRange<Float> = 0f..1f
    }
}
