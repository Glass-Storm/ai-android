@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * Pure-Kotlin SentencePiece BPE engine for the NLLB `sentencepiece.bpe.model`.
 *
 * It parses the SentencePiece `ModelProto` itself and runs the greedy BPE merge, so no native library
 * and no DJL/JNI is involved — it runs unchanged on the host JVM and on Android. The algorithm mirrors
 * the reference SentencePiece BPE (NFKC normalise, collapse whitespace, `\u2581` word prefix, then
 * repeatedly merge the highest-scoring adjacent pair), which reproduces reference ids byte-for-byte;
 * the host test `SentencePieceBpeTest` locks that against `golden/nllb-tokenizer.tsv`.
 *
 * Ids are raw SentencePiece ids (0-based, `<unk>` = 0). The fairseq offset and special-token remapping
 * live in [NllbTokenCodec], never here.
 */
internal class SentencePieceBpe private constructor(
    private val pieces: List<String>,
    private val scores: FloatArray
) {
    private val pieceToId: Map<String, Int> = HashMap<String, Int>(pieces.size * 2).apply {
        pieces.forEachIndexed { index, piece -> put(piece, index) }
    }

    /** Number of pieces in the vocabulary. */
    val vocabSize: Int = pieces.size

    /** Raw SentencePiece id of the unknown piece. */
    val unkId: Int = pieceToId[UNK] ?: 0

    /** Encodes [text] to raw SentencePiece ids. */
    fun encode(text: String): IntArray {
        val normalized = normalize(text)
        if (normalized.isEmpty()) {
            return IntArray(0)
        }
        val symbols = splitToSymbols(normalized)
        merge(symbols)
        return IntArray(symbols.size) { pieceToId[symbols[it]] ?: unkId }
    }

    /** Decodes raw SentencePiece ids back to text. */
    fun decode(ids: IntArray): String {
        val raw = buildString {
            ids.forEach { id -> if (id in pieces.indices) append(pieces[id]) }
        }
        return raw.replace(WORD_START, " ").trimStart()
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(WHITESPACE, " ")
        .trim()

    private fun splitToSymbols(text: String): MutableList<String> {
        val symbols = ArrayList<String>(text.length + 1)
        symbols.add(WORD_START)
        text.forEach { char -> symbols.add(if (char == ' ') WORD_START else char.toString()) }
        return symbols
    }

    private fun merge(symbols: MutableList<String>) {
        while (true) {
            val index = bestMergeIndex(symbols)
            if (index < 0) {
                return
            }
            symbols[index] = symbols[index] + symbols[index + 1]
            symbols.removeAt(index + 1)
        }
    }

    private fun bestMergeIndex(symbols: List<String>): Int {
        var bestIndex = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until symbols.size - 1) {
            val mergedId = pieceToId[symbols[i] + symbols[i + 1]] ?: continue
            val score = scores[mergedId]
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex
    }

    companion object {
        /** Loads and parses the SentencePiece `ModelProto` at [file]. */
        fun loadModel(file: File): SentencePieceBpe = fromBytes(file.readBytes())

        /** Parses the SentencePiece `ModelProto` encoded in [bytes]. */
        fun fromBytes(bytes: ByteArray): SentencePieceBpe {
            val parsed = parseModel(bytes)
            return SentencePieceBpe(parsed.pieces, parsed.scores)
        }

        private const val UNK = "<unk>"
        private const val WORD_START = "\u2581"
    }
}

private class ParsedModel(val pieces: List<String>, val scores: FloatArray)

private class RawPiece(val name: String, val score: Float)

private fun parseModel(bytes: ByteArray): ParsedModel {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val pieces = ArrayList<String>()
    val scores = ArrayList<Float>()
    while (buffer.hasRemaining()) {
        val tag = readVarint(buffer)
        val field = (tag ushr 3).toInt()
        val wire = (tag and WIRE_MASK.toLong()).toInt()
        if (field == PIECE_FIELD && wire == WIRE_LENGTH_DELIMITED) {
            val raw = readLengthDelimited(buffer)
            val piece = parsePiece(raw)
            pieces.add(piece.name)
            scores.add(piece.score)
        } else {
            skipField(buffer, wire)
        }
    }
    return ParsedModel(pieces, scores.toFloatArray())
}

private fun parsePiece(bytes: ByteArray): RawPiece {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var name = ""
    var score = 0f
    while (buffer.hasRemaining()) {
        val tag = readVarint(buffer)
        val field = (tag ushr 3).toInt()
        val wire = (tag and WIRE_MASK.toLong()).toInt()
        when {
            field == PIECE_NAME_FIELD && wire == WIRE_LENGTH_DELIMITED ->
                name = String(readLengthDelimited(buffer), Charsets.UTF_8)
            field == PIECE_SCORE_FIELD && wire == WIRE_FIXED32 -> score = buffer.float
            else -> skipField(buffer, wire)
        }
    }
    return RawPiece(name, score)
}

private fun readLengthDelimited(buffer: ByteBuffer): ByteArray {
    val length = readVarint(buffer).toInt()
    val slice = ByteArray(length)
    buffer.get(slice)
    return slice
}

private fun readVarint(buffer: ByteBuffer): Long {
    var result = 0L
    var shift = 0
    while (true) {
        val byte = buffer.get().toInt() and 0xFF
        result = result or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) {
            return result
        }
        shift += 7
    }
}

private fun skipField(buffer: ByteBuffer, wire: Int) {
    when (wire) {
        WIRE_VARINT -> readVarint(buffer)
        WIRE_FIXED64 -> buffer.position(buffer.position() + FIXED64_BYTES)
        WIRE_LENGTH_DELIMITED -> {
            val length = readVarint(buffer).toInt()
            buffer.position(buffer.position() + length)
        }
        WIRE_FIXED32 -> buffer.position(buffer.position() + FIXED32_BYTES)
        else -> error("unsupported protobuf wire type $wire")
    }
}

private val WHITESPACE = Regex("\\s+")

private const val WIRE_MASK = 0x7
private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5
private const val FIXED64_BYTES = 8
private const val FIXED32_BYTES = 4
private const val PIECE_FIELD = 1
private const val PIECE_NAME_FIELD = 1
private const val PIECE_SCORE_FIELD = 2
