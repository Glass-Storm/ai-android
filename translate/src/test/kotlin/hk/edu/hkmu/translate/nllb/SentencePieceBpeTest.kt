@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden lock for the pure-Kotlin SentencePiece BPE engine.
 *
 * Runs entirely on the host JVM — no native library, no ONNX runtime — and asserts that
 * [SentencePieceBpe] reproduces the reference token ids in `golden/nllb-tokenizer.tsv` byte-for-byte
 * against the committed `fixtures/spm/mini.model`. A wrong merge order, a broken `ModelProto` parse,
 * or a missed NFKC/whitespace normalisation fails here.
 */
class SentencePieceBpeTest {

    private val bpe: SentencePieceBpe = SentencePieceBpe.fromBytes(readResource(MODEL_PATH))

    @Test
    fun `encodes every golden case to the reference ids`() {
        val cases = loadGolden()
        assertTrue("the golden set must not be empty", cases.isNotEmpty())

        cases.forEach { case ->
            assertEquals("ids for '${case.text}'", case.ids, bpe.encode(case.text).toList())
        }
    }

    @Test
    fun `decodes each non-empty golden case back to its normalised text`() {
        val cases = loadGolden().filter { it.text.isNotEmpty() }
        assertTrue("the golden set must have non-empty cases", cases.isNotEmpty())

        cases.forEach { case ->
            val decoded = bpe.decode(bpe.encode(case.text))
            assertEquals("round-trip for '${case.text}'", normalized(case.text), decoded)
        }
    }

    @Test
    fun `reports a non-zero vocabulary and a real unk id`() {
        assertTrue("vocabSize must be positive", bpe.vocabSize > 0)
        assertTrue("unkId must index the vocabulary", bpe.unkId in 0 until bpe.vocabSize)
    }

    private fun loadGolden(): List<GoldenCase> = String(readResource(GOLDEN_PATH), Charsets.UTF_8)
        .lineSequence()
        .filter { it.isNotEmpty() }
        .map { line ->
            val (text, ids) = line.split('\t', limit = 2)
            GoldenCase(text, ids.split(',').filter { it.isNotEmpty() }.map { it.toInt() })
        }
        .toList()

    private fun normalized(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim()

    private fun readResource(path: String): ByteArray = javaClass.getResourceAsStream(path)?.use { it.readBytes() }
        ?: error("missing test resource $path")

    private data class GoldenCase(val text: String, val ids: List<Int>)

    private companion object {
        const val MODEL_PATH = "/fixtures/spm/mini.model"
        const val GOLDEN_PATH = "/golden/nllb-tokenizer.tsv"
    }
}
