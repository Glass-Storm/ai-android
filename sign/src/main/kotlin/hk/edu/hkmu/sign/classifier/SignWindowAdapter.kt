package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.core.engine.SignFrame
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.model.SignInputLayout
import hk.edu.hkmu.sign.model.SignModelSpec

/**
 * Assembles a window of core [SignFrame] landmarks into a classifier's flat input tensor.
 *
 * Pure Kotlin — no native, no Android type — so the whole input contract is exercisable in a host JVM
 * test. Every shape decision comes from the injected [SignModelSpec], so the same assembler serves the
 * TGCN (`[1, 100, 55]`) and SignBridge fingerspelling (`[1, 30, 63]`) graphs.
 *
 * ### Per-frame landmark ordering
 * A frame's flat `landmarks` are read as `dimsPerNode` coordinates per node, node-major
 * (`x0, y0, [z0], x1, y1, ...`) — exactly the order the `sign.landmark` extractor emits.
 *
 * @throws AiAndroidException.MalformedFrame when the window length or any frame's landmark count
 *   violates the spec.
 */
internal object SignWindowAdapter {

    /** Assembles [window] into the flattened input for [spec]. */
    fun assemble(window: List<SignFrame>, spec: SignModelSpec): FloatArray = when (val layout = spec.inputLayout) {
        is SignInputLayout.FrameFeatures -> frameFeatures(window, spec.windowFrames, layout.featuresPerFrame)
        is SignInputLayout.TransposedNodes -> transposedNodes(
            window,
            spec.windowFrames,
            layout.nodes,
            layout.dimsPerNode
        )
    }

    private fun frameFeatures(window: List<SignFrame>, frames: Int, features: Int): FloatArray {
        val out = FloatArray(frames * features)
        window.forEachIndexed { index, frame ->
            requireAtLeast(frame, index, features)
            System.arraycopy(frame.landmarks, 0, out, index * features, features)
        }
        return out
    }

    private fun transposedNodes(window: List<SignFrame>, frames: Int, nodes: Int, dims: Int): FloatArray {
        val perFrame = nodes * dims
        val out = FloatArray(dims * frames * nodes)
        for (frameIndex in 0 until frames) {
            val frame = window[frameIndex]
            requireAtLeast(frame, frameIndex, perFrame)
            val landmarks = frame.landmarks
            for (node in 0 until nodes) {
                for (dim in 0 until dims) {
                    out[(dim * frames + frameIndex) * nodes + node] = landmarks[node * dims + dim]
                }
            }
        }
        return out
    }

    private fun requireAtLeast(frame: SignFrame, index: Int, needed: Int) {
        if (frame.landmarks.size < needed) {
            throw AiAndroidException.MalformedFrame(
                "frame $index has ${frame.landmarks.size} landmark values, expected $needed"
            )
        }
    }
}
