package hk.edu.hkmu.sign.landmark

import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Assembles a [RawLandmarkFrame]'s per-part landmarks into the classifier's flat per-frame keypoint
 * order defined by a [LandmarkTopology].
 *
 * Pure Kotlin and MediaPipe-free, so it is fully unit-testable on the host. It is the single place
 * that turns "a hand/pose was absent in this frame" into a typed
 * [AiAndroidException.MalformedFrame] instead of silently emitting a short frame that the classifier
 * would mis-consume.
 *
 * @param topology The keypoint order this assembler produces.
 */
internal class LandmarkResultAssembler(private val topology: LandmarkTopology) {

    /**
     * Flattens [raw] into `upperBody (selected) + left hand + right hand`.
     *
     * @throws AiAndroidException.MalformedFrame when a required pose landmark or hand is absent.
     */
    fun assemble(raw: RawLandmarkFrame): LandmarkFrame {
        val keypoints = ArrayList<Keypoint2D>(topology.keypointsPerFrame)
        topology.upperBodyPoseIndices.forEach { poseIndex ->
            keypoints += requirePoint(raw.pose, poseIndex, "pose[$poseIndex]")
        }
        appendHand(raw.leftHand, LEFT_HAND, keypoints)
        appendHand(raw.rightHand, RIGHT_HAND, keypoints)
        return LandmarkFrame(keypoints, raw.timestampMillis)
    }

    private fun appendHand(hand: List<Keypoint3D>, side: String, out: MutableList<Keypoint2D>) {
        if (hand.size != topology.handKeypointsPerSide) {
            throw AiAndroidException.MalformedFrame(
                "missing $side hand landmarks: ${hand.size}/${topology.handKeypointsPerSide}"
            )
        }
        hand.forEach { out += it.to2D() }
    }

    private fun requirePoint(points: List<Keypoint3D>, index: Int, name: String): Keypoint2D {
        val point = points.getOrNull(index)
            ?: throw AiAndroidException.MalformedFrame("missing landmark '$name'")
        return point.to2D()
    }

    private companion object {
        const val LEFT_HAND = "left"
        const val RIGHT_HAND = "right"
    }
}
