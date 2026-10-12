package hk.edu.hkmu.sign.pipeline

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.landmark.ClassifierInputSpec
import hk.edu.hkmu.sign.landmark.LandmarkTensorLayout
import hk.edu.hkmu.sign.model.SignInputLayout
import hk.edu.hkmu.sign.model.SignModelSpec

/**
 * Projects a classifier's [SignModelSpec] onto the landmark module's [ClassifierInputSpec], so the
 * todo-19 [hk.edu.hkmu.sign.landmark.LandmarkNormalizer] can serve every classifier the pipeline
 * drives (todo 21 contract) without per-model normalization code.
 *
 * Only 2D node-major models are expressible: the extractor emits `(x, y)` per keypoint, which
 * matches [SignInputLayout.TransposedNodes] with [SignInputLayout.TransposedNodes.dimsPerNode] `== 2`
 * (the shipped TGCN `[1, 100, 55]` contract). The produced layout is
 * [LandmarkTensorLayout.NodeFeatureMajor], the onnx2tf/TFLite transpose the shipped graph expects;
 * it is byte-identical to `SignWindowAdapter`'s `TransposedNodes` assembly.
 *
 * A model whose contract the 2D landmark extractor cannot satisfy (e.g. the SignBridge
 * fingerspelling `[1, 30, 63]` z-including `FrameFeatures` layout) fails fast with
 * [AiAndroidException.PipelineMisconfigured] rather than silently feeding the wrong tensor.
 *
 * @throws AiAndroidException.PipelineMisconfigured when the layout is not 2D node-major.
 */
internal fun SignModelSpec.toClassifierInputSpec(): ClassifierInputSpec {
    val layout = inputLayout
    if (layout !is SignInputLayout.TransposedNodes) {
        throw AiAndroidException.PipelineMisconfigured(
            "classifier '$classifierId' uses a ${layout::class.simpleName} layout; the landmark " +
                "pipeline only serves 2D node-major (TransposedNodes) models"
        )
    }
    if (layout.dimsPerNode != ClassifierInputSpec.COORDS_PER_KEYPOINT) {
        throw AiAndroidException.PipelineMisconfigured(
            "classifier '$classifierId' expects ${layout.dimsPerNode} dims per node but the " +
                "landmark extractor provides ${ClassifierInputSpec.COORDS_PER_KEYPOINT} (x, y)"
        )
    }
    return ClassifierInputSpec(
        classifierId = classifierId,
        windowSize = windowFrames,
        keypointsPerFrame = layout.nodes,
        layout = LandmarkTensorLayout.NodeFeatureMajor
    )
}
