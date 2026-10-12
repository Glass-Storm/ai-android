package hk.edu.hkmu.sign.landmark

/**
 * Per-classifier input contracts derived from the Wave-0 S2 adapter spec (`spike/sign/ADAPTER_SPEC.md`).
 *
 * Both entries describe the **same** WLASL TGCN model, exported two ways: the ONNX/training graph
 * wants node-major `[1, 55, 100]`, while the onnx2tf-exported TFLite graph wants the transpose
 * `[1, 100, 55]`. Keeping them as data means the shipped graph's ordering is a configuration choice,
 * not a change to [LandmarkNormalizer].
 *
 * The ASL fingerspelling artifact from the spike is deliberately absent: the S2 report found it
 * degenerate (constant output) and its documented contract refuted, so it must not be wired.
 */
object SignClassifierSpecs {

    /** Frames per classification window for both shipped classifiers (S2 §1). */
    const val WINDOW_SIZE: Int = 50

    /** TGCN keypoints per frame: 13 upper-body + 21 left + 21 right (S2 §2). */
    const val TGCN_KEYPOINTS_PER_FRAME: Int = 55

    /** WLASL TGCN as exported to ONNX: `[1, 55, 100]`, node-major. */
    val TGCN_ONNX: ClassifierInputSpec = ClassifierInputSpec(
        classifierId = "tgcn-wlasl-onnx",
        windowSize = WINDOW_SIZE,
        keypointsPerFrame = TGCN_KEYPOINTS_PER_FRAME,
        layout = LandmarkTensorLayout.NodeMajor
    )

    /** WLASL TGCN as exported to TFLite (onnx2tf transpose): `[1, 100, 55]`. */
    val TGCN_TFLITE: ClassifierInputSpec = ClassifierInputSpec(
        classifierId = "tgcn-wlasl-tflite",
        windowSize = WINDOW_SIZE,
        keypointsPerFrame = TGCN_KEYPOINTS_PER_FRAME,
        layout = LandmarkTensorLayout.NodeFeatureMajor
    )
}
