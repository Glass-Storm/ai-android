package hk.edu.hkmu.sign.shuwa

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** One KNN verdict: the winning gloss, its nearest distance, and the neighbour-weighted confidence. */
data class ShuwaMatch(
    val label: String,
    val distance: Float,
    val confidence: Float
)

/**
 * Read-only KNN table of HKSL embeddings, loaded from the reference toolkit's per-gloss exports.
 *
 * Shuwa classifies by comparison, not by a softmax head: the translator produces a 336-float
 * embedding, and the label is the gloss whose stored embeddings are nearest. This index is therefore
 * the **language gate** — a table built from `Hksl_*` exports can only ever answer in HKSL.
 *
 * ### File format (`SKS1`)
 *
 * ```
 * magic       4 bytes   "SKS1"
 * labelCount  uint16
 * vectorCount uint32
 * dimensions  uint16
 * labels      labelCount x { uint16 byteLength, UTF-8 bytes }
 * labelIds    vectorCount x uint16   (index into labels)
 * vectors     vectorCount x dimensions x float16, row-major
 * ```
 *
 * float16 halves the file (about 13.6 MB for the shipped HKSL table) while keeping cosine ranking
 * intact; the reference implementation l2-normalises each embedding part, so squared Euclidean
 * ranking and cosine ranking agree.
 *
 * Instances are immutable after [load]; construction validates the header, the label ids and the
 * payload length, so a truncated or mislabelled file fails here rather than mid-recognition.
 */
class ShuwaKnnIndex private constructor(
    /** Glosses in table order; ids in the file index into this list. */
    val labels: List<String>,
    private val labelIds: IntArray,
    private val vectors: FloatArray,
    /** Embedding dimensions per row. */
    val dimensions: Int
) {

    /** Number of stored embeddings. */
    val size: Int get() = labelIds.size

    /**
     * Finds the nearest gloss for [embedding].
     *
     * Neighbours are ranked by Euclidean distance; the winner is the label with the greatest
     * neighbour weight, where each of the [k] nearest votes with `1 / (distance + epsilon)`. That is a
     * smoother form of the reference implementation's majority vote, and it yields the [ShuwaMatch.confidence].
     *
     * @throws IllegalArgumentException when [embedding] is not [dimensions] long.
     */
    fun match(embedding: FloatArray, k: Int = DEFAULT_K): ShuwaMatch {
        require(embedding.size == dimensions) {
            "embedding must have $dimensions values, was ${embedding.size}"
        }
        require(size > 0) { "the index is empty" }
        val neighbours = nearest(embedding, k.coerceIn(1, size))
        val weights = mutableMapOf<Int, Float>()
        neighbours.forEach { (id, distance) ->
            weights[id] = (weights[id] ?: 0f) + 1f / (distance + EPSILON)
        }
        val total = weights.values.sum()
        val best = weights.maxByOrNull { it.value }
        val winnerId = best?.key ?: neighbours.first().first
        val winnerWeight = best?.value ?: 0f
        return ShuwaMatch(
            label = labels[winnerId],
            distance = neighbours.filter { it.first == winnerId }.minOf { it.second },
            confidence = if (total > 0f) winnerWeight / total else 0f
        )
    }

    /** The [count] nearest `(labelId, distance)` pairs, best first. */
    private fun nearest(embedding: FloatArray, count: Int): List<Pair<Int, Float>> {
        val best = ArrayList<Pair<Int, Float>>(count)
        for (row in 0 until size) {
            val distance = distanceTo(row, embedding)
            if (best.size < count) {
                best.add(labelIds[row] to distance)
                if (best.size == count) {
                    best.sortBy { it.second }
                }
            } else if (distance < best.last().second) {
                best[count - 1] = labelIds[row] to distance
                best.sortBy { it.second }
            }
        }
        return best
    }

    /** Squared Euclidean distance, square-rooted to match the reference ranking metric. */
    private fun distanceTo(row: Int, embedding: FloatArray): Float {
        var total = 0f
        val base = row * dimensions
        for (index in 0 until dimensions) {
            val delta = vectors[base + index] - embedding[index]
            total += delta * delta
        }
        return sqrt(total)
    }

    companion object {
        private const val DEFAULT_K = 5
        private const val EPSILON = 1e-6f
        private const val MAGIC = "SKS1"
        private const val MAGIC_BYTES = 4
        private const val UNSIGNED_SHORT_MASK = 0xFFFF
        private const val HALF_BYTES = 2

        /**
         * Loads a table produced by the Shuwa exporter.
         *
         * @param file the `SKS1` binary.
         * @throws IllegalArgumentException when the header, ids or payload length do not agree.
         */
        fun load(file: File): ShuwaKnnIndex {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC_BYTES)
            buffer.get(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) { "not a Shuwa KNN table: ${file.name}" }
            val labelCount = buffer.short.toInt() and UNSIGNED_SHORT_MASK
            val vectorCount = buffer.int
            val dimensions = buffer.short.toInt() and UNSIGNED_SHORT_MASK
            val labels = List(labelCount) {
                val length = buffer.short.toInt() and UNSIGNED_SHORT_MASK
                val raw = ByteArray(length)
                buffer.get(raw)
                String(raw, Charsets.UTF_8)
            }
            val ids = IntArray(vectorCount) { buffer.short.toInt() and UNSIGNED_SHORT_MASK }
            require(ids.all { it in labels.indices }) { "label id out of range" }
            val expected = vectorCount * dimensions
            require(bytes.size - buffer.position() == expected * HALF_BYTES) {
                "payload length does not match $vectorCount x $dimensions float16 values"
            }
            val vectors = FloatArray(expected)
            for (index in 0 until expected) {
                vectors[index] = halfToFloat(buffer.short.toInt() and UNSIGNED_SHORT_MASK)
            }
            return ShuwaKnnIndex(labels = labels, labelIds = ids, vectors = vectors, dimensions = dimensions)
        }

        /** IEEE-754 binary16 to float32, the inverse of the exporter's `astype(float16)`. */
        internal fun halfToFloat(bits: Int): Float {
            val sign = (bits and SIGN_BIT) shl SIGN_SHIFT
            val exponent = (bits shr EXPONENT_SHIFT) and EXPONENT_MASK
            val mantissa = bits and MANTISSA_MASK
            val value =
                when (exponent) {
                    0 -> if (mantissa == 0) {
                        0f
                    } else {
                        Math.scalb(mantissa.toFloat(), SUBNORMAL_SCALE)
                    }
                    MAX_EXPONENT -> if (mantissa == 0) Float.POSITIVE_INFINITY else Float.NaN
                    else -> Math.scalb((mantissa + IMPLICIT_BIT).toFloat(), exponent - NORMAL_SCALE_OFFSET)
                }
            return if (sign != 0) -value else value
        }

        private const val SIGN_BIT = 0x8000
        private const val SIGN_SHIFT = 16
        private const val EXPONENT_SHIFT = 10
        private const val EXPONENT_MASK = 0x1F
        private const val MANTISSA_MASK = 0x3FF
        private const val IMPLICIT_BIT = 0x400
        private const val MAX_EXPONENT = 0x1F
        private const val SUBNORMAL_SCALE = -24
        private const val NORMAL_SCALE_OFFSET = 25
    }
}
