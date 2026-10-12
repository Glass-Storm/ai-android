package hk.edu.hkmu.sign.classifier

import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.sign.model.SignModelSpec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

/**
 * Production [SignClassifier] over the **LiteRT** runtime (the `litert` AAR's
 * `libtensorflowlite_jni.so`, driven through the `org.tensorflow.lite.Interpreter` API it powers).
 *
 * This is the module's single shipped classifier runtime: TGCN, SignBridge fingerspelling and any BYO
 * TFLite classifier all run here — no second model runtime is added.
 *
 * The direct [ByteBuffer]s are sized from the model's own tensors, so any `[1, ...]` float32 graph is
 * accepted without hardcoding a shape; the [SignModelSpec] input contract is enforced by the
 * window assembler before the buffer is written.
 */
internal class TfliteSignClassifier private constructor(
    private val interpreter: Interpreter,
    private val input: ByteBuffer,
    private val output: ByteBuffer,
    private val outputElements: Int
) : SignClassifier {

    override fun classify(inputVector: FloatArray): FloatArray {
        if (inputVector.size != input.capacity() / FLOAT_BYTES) {
            throw AiAndroidException.InputShapeMismatch(
                "(${input.capacity() / FLOAT_BYTES})",
                "(${inputVector.size})"
            )
        }
        input.rewind()
        input.asFloatBuffer().put(inputVector)
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val logits = FloatArray(outputElements)
        output.asFloatBuffer().get(logits)
        return logits
    }

    override fun close() {
        interpreter.close()
    }

    /**
     * Builds a [TfliteSignClassifier] by loading `modelFile` into a fresh LiteRT [Interpreter].
     *
     * The interpreter is created only here, so merely referencing the factory in host code never
     * loads the native library.
     *
     * @throws AiAndroidException.ModelLoadFailed when the native runtime refuses the graph.
     */
    internal class Factory(private val numThreads: Int = DEFAULT_NUM_THREADS) : SignClassifierFactory {
        @Suppress("TooGenericExceptionCaught")
        override fun create(spec: SignModelSpec, modelFile: File): SignClassifier = try {
            val interpreter = Interpreter(modelFile, Interpreter.Options().setNumThreads(numThreads))
            interpreter.allocateTensors()
            TfliteSignClassifier(
                interpreter = interpreter,
                input = directBuffer(interpreter.getInputTensor(0).numBytes()),
                output = directBuffer(interpreter.getOutputTensor(0).numBytes()),
                outputElements = interpreter.getOutputTensor(0).numElements()
            )
        } catch (error: Exception) {
            throw error as? AiAndroidException ?: AiAndroidException.ModelLoadFailed(spec.classifierId, error)
        }
    }

    private companion object {
        const val FLOAT_BYTES = 4
        const val DEFAULT_NUM_THREADS = 2

        fun directBuffer(bytes: Int): ByteBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    }
}
