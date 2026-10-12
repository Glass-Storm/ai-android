@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-file lock for the deterministic OpenCC script conversion.
 *
 * OpenCC is a pure-JVM, bidirectional dictionary transform: for a fixed `(config, input)` pair the
 * output is byte-exact, so it is a valid golden target (no model, no seed, no tolerance). The expected
 * values live in `resources/golden/opencc-script.tsv`; a regression (an opencc4j bump that changes a
 * mapping) fails here rather than shipping silently.
 */
class ScriptGoldenTest {

    private val converter = ScriptConverter()

    @Test
    fun `converts every golden pair exactly`() {
        val cases = load()
        assertTrue("the golden set must not be empty", cases.isNotEmpty())

        cases.forEach { case ->
            assertEquals(
                "OpenCC ${case.config} for '${case.input}'",
                case.expected,
                converter.convert(case.input, case.config)
            )
        }
    }

    @Test
    fun `the golden set exercises every OpenCC configuration`() {
        val covered = load().map { it.config }.toSet()
        assertEquals(ScriptConfig.entries.toSet(), covered)
    }

    private fun load(): List<GoldenCase> {
        val text = javaClass.getResourceAsStream(GOLDEN)?.bufferedReader()?.use { it.readText() }
            ?: error("missing golden resource $GOLDEN")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split('|')
                GoldenCase(ScriptConfig.valueOf(columns[0]), columns[1], columns[2])
            }
            .toList()
    }

    private data class GoldenCase(val config: ScriptConfig, val input: String, val expected: String)

    private companion object {
        const val GOLDEN = "/golden/opencc-script.tsv"
    }
}
