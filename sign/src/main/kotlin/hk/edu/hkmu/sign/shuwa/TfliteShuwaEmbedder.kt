package hk.edu.hkmu.sign.shuwa

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import org.tensorflow.lite.Interpreter

/**
 * Runs the Shuwa translator graph over one window and returns its 336-dimension embedding.
 *
 * A `fun interface`, so a host test can substitute a lambda and exercise the KNN/facade half without a
 * native interpreter. The production implementation is [TfliteShuwaEmbedder].
 */
fun interface ShuwaEmbeddingModel {
    /**
     * Embeds one prepared window.
     *
     * @param inputs the four tensors from [ShuwaPreprocessor].
     * @return [ShuwaSpec.EMBEDDING_DIMENSIONS] floats; the reference model L2-normalises each part,
     *   so the concatenated vector has norm `2.0`.
     */
    fun embed(inputs: ShuwaInputs): FloatArray
}

/**
 * [ShuwaEmbeddingModel] over the **LiteRT** runtime already shipped by this module.
 *
 * The translator takes four float32 inputs (`pose_3d`, `face_3d`, `lh_3d`, `rh_3d`) and produces one
 * `[1, 336]` embedding. Input order is resolved from the graph's own tensor names at load time rather
 * than assumed, so a re-export with a different feed order still binds correctly; a missing expected
 * input fails with [AiAndroidException.ModelLoadFailed] instead of silently feeding the wrong tensor.
 *
 * Models are never bundled: the host provisions the `.tflite` file and passes its [ModelLicense], and
 * the license gate runs **before** the interpreter is created.
 */
class TfliteShuwaEmbedder private constructor(
    private val interpreter: Interpreter,
    private val feeds: Array<ByteBuffer>,
    private val feedIndex: IntArray,
    private val embedding: ByteBuffer,
    private val embeddingElements: Int
) : ShuwaEmbeddingModel,
    AutoCloseable {

    override fun embed(inputs: ShuwaInputs): FloatArray {
        // `feeds` is indexed by the interpreter's OWN input index, so each slot is addressed through
        // `feedIndex`; passing the array positionally would bind the wrong tensor when a graph orders
        // its inputs differently from the reference export.
        writeFeed(feeds[feedIndex[POSE_FEED]], inputs.pose)
        writeFeed(feeds[feedIndex[FACE_FEED]], inputs.face)
        writeFeed(feeds[feedIndex[LH_FEED]], inputs.leftHand)
        writeFeed(feeds[feedIndex[RH_FEED]], inputs.rightHand)
        embedding.rewind()
        // The Java API takes Object[] and Map<Integer, Object>; build widened views.
        val feedArgs = Array<Any>(feeds.size) { index -> feeds[index] }
        interpreter.runForMultipleInputsOutputs(
            feedArgs,
            mutableMapOf<Int, Any>(OUTPUT_INDEX to embedding)
        )
        embedding.rewind()
        val result = FloatArray(embeddingElements)
        embedding.asFloatBuffer().get(result)
        return result
    }

    /** Releases the native interpreter. Idempotent. */
    override fun close() {
        interpreter.close()
    }

    /** Writes one tensor into its pre-sized direct buffer. */
    private fun writeFeed(buffer: ByteBuffer, values: FloatArray) {
        if (buffer.capacity() / FLOAT_BYTES != values.size) {
            throw AiAndroidException.InputShapeMismatch(
                "(${buffer.capacity() / FLOAT_BYTES})",
                "(${values.size})"
            )
        }
        buffer.rewind()
        val floats: FloatBuffer = buffer.asFloatBuffer()
        floats.put(values)
        buffer.rewind()
    }

    /** Builds an embedder around a provisioned translator file. */
    companion object {
        /** Tensor name fragment of the pose feed. */
        const val POSE_TENSOR: String = "pose_3d"

        /** Tensor name fragment of the face feed. */
        const val FACE_TENSOR: String = "face_3d"

        /** Tensor name fragment of the left-hand feed. */
        const val LEFT_HAND_TENSOR: String = "lh_3d"

        /** Tensor name fragment of the right-hand feed. */
        const val RIGHT_HAND_TENSOR: String = "rh_3d"

        private const val POSE_FEED = 0
        private const val FACE_FEED = 1
        private const val LH_FEED = 2
        private const val RH_FEED = 3
        private const val OUTPUT_INDEX = 0
        private const val FLOAT_BYTES = 4
        private const val DEFAULT_NUM_THREADS = 2

        /**
         * Loads `modelFile` and prepares the four feeds.
         *
         * @param modelFile the provisioned `.tflite` translator.
         * @param license the model's licence, evaluated against [intent] before any native load.
         * @param policy the license gate; defaults to the library's [hk.edu.hkmu.core.license.DefaultLicensePolicy].
         * @param intent the intended use; defaults to [UsageIntent.SelfUse].
         * @param numThreads LiteRT worker threads.
         * @throws AiAndroidException.LicenseRestricted when the gate refuses the model.
         * @throws AiAndroidException.ModelLoadFailed when the graph cannot be loaded or a feed is absent.
         */
        @Suppress("TooGenericExceptionCaught", "LongParameterList")
        fun load(
            modelFile: File,
            license: ModelLicense,
            policy: LicensePolicy = hk.edu.hkmu.core.license.DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            numThreads: Int = DEFAULT_NUM_THREADS
        ): TfliteShuwaEmbedder {
            when (val decision = policy.evaluate(license, intent)) {
                is LicenseDecision.Restricted ->
                    throw AiAndroidException.LicenseRestricted(
                        modelFile.name,
                        decision.license.kind,
                        decision.intent
                    )
                is LicenseDecision.Allowed -> Unit
            }
            try {
                val interpreter = Interpreter(modelFile, Interpreter.Options().setNumThreads(numThreads))
                interpreter.allocateTensors()
                val inputs = interpreter.getInputTensor(0)
                check(inputs.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
                    "translator inputs must be float32"
                }
                val byName = buildMap {
                    for (index in 0 until interpreter.inputTensorCount) {
                        put(interpreter.getInputTensor(index).name(), index)
                    }
                }
                val order =
                    intArrayOf(
                        indexOf(byName, POSE_TENSOR, modelFile),
                        indexOf(byName, FACE_TENSOR, modelFile),
                        indexOf(byName, LEFT_HAND_TENSOR, modelFile),
                        indexOf(byName, RIGHT_HAND_TENSOR, modelFile)
                    )
                val feeds = Array(interpreter.inputTensorCount) { index ->
                    directBuffer(interpreter.getInputTensor(index).numBytes())
                }
                val output = interpreter.getOutputTensor(OUTPUT_INDEX)
                return TfliteShuwaEmbedder(
                    interpreter = interpreter,
                    feeds = feeds,
                    feedIndex = order,
                    embedding = directBuffer(output.numBytes()),
                    embeddingElements = output.numElements()
                )
            } catch (error: Exception) {
                throw error as? AiAndroidException
                    ?: AiAndroidException.ModelLoadFailed(modelFile.name, error)
            }
        }

        private fun indexOf(byName: Map<String, Int>, fragment: String, modelFile: File): Int =
            byName.entries.firstOrNull { it.key.contains(fragment) }?.value
                ?: throw AiAndroidException.ModelLoadFailed(
                    modelFile.name,
                    IllegalStateException("translator input '$fragment' is missing")
                )

        private fun directBuffer(bytes: Int): ByteBuffer =
            ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    }
}
