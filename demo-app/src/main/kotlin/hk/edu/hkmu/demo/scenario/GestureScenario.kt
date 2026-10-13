@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.demo.media.VideoFrameSource
import hk.edu.hkmu.demo.model.AssetProvisioner
import hk.edu.hkmu.demo.model.ModelCatalog
import hk.edu.hkmu.sign.gesture.MediaPipeGestureRecognizer
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectIndexed

private const val GESTURE_DIR = "gesture"
private const val PERCENT = 100

/**
 * Scenario: per-image static hand-gesture recognition over the bundled clip.
 *
 * Provisions the `gesture/gesture_recognizer.task` bundle into the model root, builds the real
 * [MediaPipeGestureRecognizer], and runs it **per decoded frame** of `models/assets/demo_sign.mp4`
 * (via [VideoFrameSource]), printing each frame that yields a gesture plus a summary of the
 * distinct labels. MediaPipe Tasks Vision ships arm64-v8a only, so on an x86_64 emulator the native
 * load fails; that case is caught and reported rather than crashing.
 */
object GestureScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val provisioned = AssetProvisioner(environment.context.assets, environment.modelsRoot)
            .provision(listOf(ModelCatalog.SIGN_GESTURE_RECOGNIZER, ModelCatalog.VIDEO_DEMO))
        val video = environment.dir("assets", "demo_sign.mp4")
        if (!video.isFile) {
            return "bundled video missing at ${video.path}; stage video/demo_sign.mp4 under " +
                "demo-app/src/main/assets/video/ (gitignored) and rebuild.\n$provisioned"
        }
        val modelDir = File(environment.modelsRoot, GESTURE_DIR)
        val recognizer = MediaPipeGestureRecognizer.create(environment.context, modelDir)
        return try {
            render(video, recognizer)
        } catch (error: AiAndroidException) {
            "Gesture recognizer unavailable: ${error.javaClass.simpleName}: ${error.message}"
        } catch (error: UnsatisfiedLinkError) {
            nativeUnavailable(error)
        } catch (error: LinkageError) {
            nativeUnavailable(error)
        } finally {
            recognizer.close()
        }
    }

    private fun nativeUnavailable(error: LinkageError): String =
        "Gesture recognizer native unavailable: ${error.message}\n" +
            "MediaPipe Tasks Vision ships arm64-v8a only; run this on an arm64 phone."

    private suspend fun render(video: File, recognizer: MediaPipeGestureRecognizer): String {
        val frameLines = StringBuilder()
        val counts = linkedMapOf<String, Int>()
        var gestureFrames = 0
        var frames = 0
        VideoFrameSource.stream(video).collectIndexed { index, frame ->
            recognizer.recognize(frame.image)?.let { prediction ->
                gestureFrames++
                counts[prediction.label] = (counts[prediction.label] ?: 0) + 1
                val percent = (prediction.score * PERCENT).roundToInt()
                frameLines.appendLine("frame $index: ${prediction.label} ($percent%)")
            }
            frames = index + 1
        }
        return buildString {
            appendLine("Static gesture recognizer over $frames decoded frame(s) of ${video.name}")
            append(frameLines)
            append("gesture frames: $gestureFrames; distinct labels: ${counts.size}")
            if (counts.isNotEmpty()) {
                appendLine()
                counts.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                    appendLine("  $label: $count")
                }
            }
        }
    }
}
