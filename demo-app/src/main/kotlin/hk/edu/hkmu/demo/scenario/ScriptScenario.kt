package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.translate.script.ScriptConfig
import hk.edu.hkmu.translate.script.ScriptConverter

/**
 * Scenario 5: the real OpenCC script converter.
 *
 * Uses [ScriptConverter] (which wraps OpenCC) to convert a Traditional string to Simplified and back,
 * plus a Hong Kong-variant conversion. Nothing here is faked.
 */
object ScriptScenario {
    private const val TRADITIONAL = "繁體中文軟體開發"

    /** Converts the sample through the real converter and renders every result. */
    fun run(): String {
        val converter = ScriptConverter()
        val simplified = converter.convert(TRADITIONAL, ScriptConfig.T2S)
        val backToTraditional = converter.convert(simplified, ScriptConfig.S2T)
        val hongKong = converter.convert(simplified, ScriptConfig.S2HK)

        return buildString {
            appendLine("Real OpenCC converter: hk.edu.hkmu.translate.script.ScriptConverter")
            appendLine("  Traditional -> Simplified (t2s): \"$TRADITIONAL\" -> \"$simplified\"")
            appendLine("  Simplified  -> Traditional (s2t): \"$simplified\" -> \"$backToTraditional\"")
            appendLine("  Simplified  -> HK variant  (s2hk): \"$simplified\" -> \"$hongKong\"")
            append("Round-trip Traditional matches input: ${backToTraditional == TRADITIONAL}")
        }
    }
}
