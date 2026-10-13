@file:Suppress("TooGenericExceptionCaught")

package hk.edu.hkmu.demo.scenario

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.demo.DemoEnvironment
import hk.edu.hkmu.translate.nllb.NllbTranslationEngine
import kotlinx.coroutines.flow.toList

/** Text to translate for the demo. */
private const val DEMO_TEXT = "你好世界"

/**
 * Scenario: real NLLB translation, best effort.
 *
 * Constructs the real [NllbTranslationEngine] and translates [DEMO_TEXT] from Cantonese to English.
 * This is expected to fail on Android — the NLLB SentencePiece tokenizer has no Android native — so
 * the failure is surfaced clearly rather than hidden. The engine is built lazily and still proves
 * the public constructor/`translate` wiring.
 */
object TranslationScenario {

    suspend fun run(environment: DemoEnvironment): String {
        val engine = try {
            NllbTranslationEngine.create(
                config = TranslationConfig(
                    sourceLanguage = Language.Cantonese,
                    targetLanguage = Language.English
                ),
                modelDir = environment.dir("nllb")
            )
        } catch (error: Throwable) {
            return "constructing NllbTranslationEngine failed: ${describe(error)}"
        }
        return try {
            render(engine.translate(DEMO_TEXT).toList())
        } catch (error: Throwable) {
            "translation failed (EXPECTED on Android: the NLLB SentencePiece tokenizer has no Android " +
                "native): ${describe(error)}"
        } finally {
            engine.close()
        }
    }

    private fun render(results: List<TranslatedText>): String = buildString {
        appendLine("Real engine: hk.edu.hkmu.translate.nllb.NllbTranslationEngine (model dir models/nllb)")
        appendLine("Input (${Language.Cantonese.tag}): \"$DEMO_TEXT\"")
        results.forEach { result ->
            appendLine("  output (${result.targetLanguage.tag}): \"${result.text}\"")
        }
    }

    private fun describe(error: Throwable): String = "${error.javaClass.simpleName}: ${error.message}"
}
