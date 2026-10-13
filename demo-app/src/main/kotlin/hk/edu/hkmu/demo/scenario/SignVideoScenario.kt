package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.demo.media.VideoFrameSource
import hk.edu.hkmu.sign.SignClassifierRegistry
import hk.edu.hkmu.sign.landmark.MediaPipeLandmarkExtractor
import hk.edu.hkmu.sign.pipeline.SignPipeline
import hk.edu.hkmu.sign.pipeline.SignPipelineConfig
import hk.edu.hkmu.sign.pipeline.SignResult
import kotlinx.coroutines.flow.toList

/** The only classifier whose input layout the frame → text pipeline accepts for video. */
private const val CLASSIFIER_ID = "tgcn-wlasl-asl100"

/**
 * Scenario: real sign recognition over the bundled clip.
 *
 * Composes the real [MediaPipeLandmarkExtractor] (holistic landmarker), [SignClassifierRegistry]
 * (TGCN ASL-100) and [SignPipeline], feeding it frames decoded from `models/assets/demo_sign.mp4`.
 * Only [CLASSIFIER_ID] works here: the pipeline rejects the fingerspelling layout by design.
 */
object SignVideoScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val video = environment.dir("assets", "demo_sign.mp4")
        if (!video.isFile) {
            return "bundled video missing at ${video.path}; stage demo_sign.mp4 under " +
                "demo-app/src/main/assets/video/ (gitignored) and rebuild."
        }
        val extractor = MediaPipeLandmarkExtractor.create(environment.context, environment.dir("sign", "landmarker"))
        val registry = SignClassifierRegistry(environment.dir("sign", "classifiers"))
        val pipeline = SignPipeline.create(
            extractor = extractor,
            registry = registry,
            config = SignPipelineConfig(classifierId = CLASSIFIER_ID),
            postProcessor = null,
            ownsExtractor = true
        )
        return try {
            render(pipeline.recognize(VideoFrameSource.stream(video)).toList())
        } finally {
            pipeline.close()
            registry.close()
        }
    }

    private fun render(results: List<SignResult>): String = buildString {
        appendLine("Real pipeline: MediaPipeLandmarkExtractor + SignClassifierRegistry + SignPipeline")
        appendLine("Classifier: $CLASSIFIER_ID (fingerspelling layout is rejected by design)")
        if (results.isEmpty()) {
            append("No sign windows classified (video too short or motionless).")
        } else {
            results.forEach { result ->
                appendLine("  label=${result.label} confidence=${result.confidence} text=\"${result.text}\"")
            }
        }
    }
}
