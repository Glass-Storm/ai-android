@file:Suppress("MagicNumber")

package hk.edu.hkmu.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.TtsConfig
import hk.edu.hkmu.tts.model.TtsModelCatalog
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tier B device check: synthesizes a fixed sentence for each shipped language and asserts the audio
 * is non-empty at the **model-reported** rate.
 *
 * Auto-skips when the device has no provisioned models (and is never reached at all without a
 * connected device). Provision models under `/data/local/tmp/sherpa-tts/<directoryName>/`.
 */
@RunWith(AndroidJUnit4::class)
class SherpaTtsDeviceTest {

    private val modelRoot = File("/data/local/tmp/sherpa-tts")

    private val sentences: Map<Language, String> = mapOf(
        Language.Cantonese to "你好，今日天氣好好。",
        Language.Mandarin to "你好，今天天气很好。",
        Language.English to "Hello, the weather is nice today."
    )

    @Test
    fun synthesizesFixedSentenceForEachLanguage() {
        assumeTrue("SKIPPED: no device models at $modelRoot", modelRoot.isDirectory)
        for ((language, sentence) in sentences) {
            val spec = TtsModelCatalog.Standard.requireLanguage(language)
            val voiceDir = File(modelRoot, spec.directoryName)
            assumeTrue("SKIPPED: model '${spec.id}' not provisioned", voiceDir.isDirectory)

            val engine = SherpaTtsEngine(TtsConfig(language = language), modelRoot)
            try {
                val chunks = runBlocking { engine.synthesizeInt16(sentence).toList() }
                val total = chunks.sumOf { it.samples.size }
                println("TIER_B tts lang=${language.tag} samples=$total rate=${chunks.first().sampleRateHz}")
                assertTrue("$language produced no samples", total > 0)
                assertTrue("$language reported a non-positive rate", chunks.first().sampleRateHz > 0)
            } finally {
                engine.close()
            }
        }
    }
}
