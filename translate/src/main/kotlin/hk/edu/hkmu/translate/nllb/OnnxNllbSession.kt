package hk.edu.hkmu.translate.nllb

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.native.DeviceCapabilities
import hk.edu.hkmu.core.native.adapter.BackendInitializer
import hk.edu.hkmu.core.native.adapter.OnnxExecutionProvider
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Production [NllbSession] over the two INT8 ONNX graphs (Wave-0 S1 recipe), non-cached.
 *
 * The encoder is run once per source (`last_hidden_state` of shape `[1,S,1024]`); each decoder step
 * feeds the full prefix plus the encoder hidden states and reads the `logits` row for the last
 * position. The encoder tensors are held on the [EncodedSource] and released by its `close()`.
 */
internal class OnnxNllbSession(
    private val environment: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession
) : NllbSession {

    @Suppress("TooGenericExceptionCaught") // release the native tensors, then rethrow unchanged
    override fun encode(inputIds: LongArray, attentionMask: LongArray): EncodedSource {
        val shape = longArrayOf(1L, inputIds.size.toLong())
        val ids = OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), shape)
        val mask = OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), shape)
        return try {
            EncodedSource(inputIds.size, NativeEncoderTensors(runEncoder(ids, mask), mask))
        } catch (failure: Throwable) {
            ids.close()
            mask.close()
            throw failure
        }
    }

    override fun nextToken(decoderIds: LongArray, source: EncodedSource): Int {
        val tensors = source.handle as? NativeEncoderTensors
            ?: throw AiAndroidException.InputShapeMismatch("encoder_hidden_states", "absent")
        val shape = longArrayOf(1L, decoderIds.size.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(decoderIds), shape).use { ids ->
            decoder.run(
                mapOf(
                    "input_ids" to ids,
                    "encoder_hidden_states" to tensors.hidden,
                    "encoder_attention_mask" to tensors.mask
                )
            ).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = result.get("logits").get().value as Array<Array<FloatArray>>
                return argmax(logits[0][decoderIds.size - 1])
            }
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    private fun runEncoder(ids: OnnxTensor, mask: OnnxTensor): OnnxTensor =
        encoder.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { result ->
            @Suppress("UNCHECKED_CAST")
            val hidden = result.get("last_hidden_state").get().value as Array<Array<FloatArray>>
            val sequence = hidden[0].size
            val dimension = if (sequence == 0) 0 else hidden[0][0].size
            val flat = FloatArray(sequence * dimension)
            for (index in 0 until sequence) {
                System.arraycopy(hidden[0][index], 0, flat, index * dimension, dimension)
            }
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(flat),
                longArrayOf(1L, sequence.toLong(), dimension.toLong())
            )
        }
}

/** Holds the encoder tensors reused by every decoder step; released by `EncodedSource.close()`. */
private class NativeEncoderTensors(
    val hidden: OnnxTensor,
    val mask: OnnxTensor
) : AutoCloseable {
    override fun close() {
        hidden.close()
        mask.close()
    }
}

/**
 * Builds the production [NllbSession], resolving the execution provider through the shared backend
 * adapter and validating the model assets before any ONNX session is created. The
 * [BackendInitializer] falls back from a failing accelerator to CPU (or raises a typed
 * `AcceleratorUnavailable` for an explicit `Gpu`).
 */
internal class OnnxNllbSessionFactory(
    private val modelId: String,
    private val modelDir: File,
    private val accelerator: Accelerator,
    private val capabilities: DeviceCapabilities,
    private val numThreads: Int,
    private val initializer: BackendInitializer = BackendInitializer()
) : NllbSessionFactory {

    override fun create(): NllbSession {
        val assets = NllbModelAssets.ensurePresent(modelId, modelDir)
        val plan = NllbBackend.plan(accelerator, capabilities)
        var opened: NllbSession? = null
        initializer.initialize(plan) { knob -> opened = openSession(assets, knob) }
        return opened ?: throw AiAndroidException.ModelLoadFailed(modelId)
    }

    @Suppress("TooGenericExceptionCaught") // close the already-open encoder if the decoder fails
    private fun openSession(assets: ResolvedNllbAssets, knob: OnnxExecutionProvider): NllbSession {
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(numThreads)
            if (knob == OnnxExecutionProvider.Gpu) {
                options.addNnapi()
            }
            val encoder = environment.createSession(assets.encoderFile.absolutePath, options)
            return try {
                val decoder = environment.createSession(assets.decoderFile.absolutePath, options)
                OnnxNllbSession(environment, encoder, decoder)
            } catch (failure: Exception) {
                encoder.close()
                throw failure
            }
        } finally {
            options.close()
        }
    }
}

private fun argmax(values: FloatArray): Int {
    var best = 0
    var bestValue = values[0]
    for (index in 1 until values.size) {
        if (values[index] > bestValue) {
            bestValue = values[index]
            best = index
        }
    }
    return best
}
