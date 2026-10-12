@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-file lock for the tag -> FLORES-200 language mapping.
 *
 * [NllbLanguageTags] is a deterministic, pure mapping (no model, no native), so its output is a valid
 * golden target. The expected table lives in `resources/golden/nllb-language-codes.tsv`; a dropped or
 * re-pointed alias (e.g. `zh-Hant` silently becoming Simplified) fails here.
 */
class NllbLanguageGoldenTest {

    @Test
    fun `maps every golden tag to its flores code`() {
        val cases = load()
        assertTrue("the golden set must not be empty", cases.isNotEmpty())

        cases.forEach { case ->
            assertEquals(
                "FLORES code for '${case.tag}'",
                case.expected,
                NllbLanguageTags.toFloresCode(Language(case.tag))
            )
        }
    }

    @Test
    fun `the golden set covers the three shipped target languages`() {
        val codes = load().map { it.expected }.toSet()
        assertTrue(codes.contains(NllbLanguageTags.ENGLISH))
        assertTrue(codes.contains(NllbLanguageTags.MANDARIN_SIMPLIFIED))
        assertTrue(codes.contains(NllbLanguageTags.MANDARIN_TRADITIONAL))
        assertTrue(codes.contains(NllbLanguageTags.CANTONESE))
    }

    @Test
    fun `an unmapped tag fails fast with a typed UnsupportedLanguage`() {
        assertThrows(AiAndroidException.UnsupportedLanguage::class.java) {
            NllbLanguageTags.toFloresCode(Language("fr"))
        }
    }

    private fun load(): List<GoldenCase> {
        val text = javaClass.getResourceAsStream(GOLDEN)?.bufferedReader()?.use { it.readText() }
            ?: error("missing golden resource $GOLDEN")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split('|')
                GoldenCase(columns[0], columns[1])
            }
            .toList()
    }

    private data class GoldenCase(val tag: String, val expected: String)

    private companion object {
        const val GOLDEN = "/golden/nllb-language-codes.tsv"
    }
}
