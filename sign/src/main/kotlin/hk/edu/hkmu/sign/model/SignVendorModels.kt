@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.ModelLicense

/**
 * The vendor models the sign module ships (as MSL/ModelStore **declarations**, never bundled files).
 *
 * Only ASL models are shipped; CSL and any host-specific fingerspelling variant are BYO slots. See
 * [SignModelCatalog] for how they are surfaced and `.omo/evidence/task-21-*` for the availability
 * decision that selected these two.
 */

/**
 * TGCN isolated-word ASL-100 classifier (`sharonn18/tgcn-wlasl`), exported PyTorch -> ONNX -> TFLite.
 *
 * Input `[1, 100, 55]` float32 = 2 coords × 50 frames over 55 keypoints; output 100 logits. It is the
 * working shipped **ASL isolated-word** path. The repo is MIT but the weights are trained on WLASL,
 * whose C-UDA terms are non-commercial, so the model is recorded [LicenseKind.NonCommercial] and the
 * license gate refuses it under `UsageIntent.Commercial`.
 */
val TgcnWlaslIsolatedAsl: SignModelSpec = SignModelSpec(
    classifierId = "tgcn-wlasl-asl100",
    language = SignLanguages.Asl,
    license = ModelLicense(
        kind = LicenseKind.NonCommercial,
        spdxId = "C-UDA-1.0",
        notice = "TGCN trained on WLASL (C-UDA-1.0): research / non-commercial use only."
    ),
    directoryName = "tgcn-wlasl-asl100",
    modelFile = "tgcn_asl100_float32.tflite",
    windowFrames = 50,
    inputLayout = SignInputLayout.TransposedNodes(nodes = 55, dimsPerNode = 2),
    classCount = 100,
    labels = List(100) { "wlasl-$it" },
    sourceUrl = "https://github.com/sharonn18/tgcn-wlasl",
    shipped = true
)

/**
 * SignBridge ASL fingerspelling classifier (`amrouneabdelhak9/SignBridge-Studio-Models`), MIT.
 *
 * Input `[1, 30, 63]` float32 = 30 frames of 21 hand landmarks `(x, y, z)`; output 28 logits
 * (`A`..`Z`, `del`, `space`). Non-degenerate (validated on a random-input fixture: 7 distinct
 * argmax over 15 probes, non-zero per-class variance). Because the weights are MIT the model is
 * [LicenseKind.Permissive] and shippable under `UsageIntent.Commercial`.
 */
val SignBridgeFingerspellingAsl: SignModelSpec = SignModelSpec(
    classifierId = "signbridge-asl-alphabet",
    language = SignLanguages.Asl,
    license = ModelLicense(
        kind = LicenseKind.Permissive,
        spdxId = "MIT",
        notice = "SignBridge-Studio-Models (amrouneabdelhak9) is licensed MIT."
    ),
    directoryName = "signbridge-asl-alphabet",
    modelFile = "asl_alphabet_model.tflite",
    windowFrames = 30,
    inputLayout = SignInputLayout.FrameFeatures(featuresPerFrame = 63),
    classCount = 28,
    labels = ('A'..'Z').map { it.toString() } + listOf("del", "space"),
    sourceUrl = "https://huggingface.co/amrouneabdelhak9/SignBridge-Studio-Models",
    shipped = true
)
