@file:Suppress("MagicNumber")

package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.config.PostProcessConfig
import hk.edu.hkmu.core.error.AiAndroidException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance for [LlamaPostProcessor] over the fake session seam: lazy load, opt-out bypass,
 * input cap, deterministic seed plumbing, unload/reload and native-failure surfacing — all without
 * a native library or GGUF model.
 */
class LlamaPostProcessorTest {

    @Test
    fun `adversarial - enabling recombination does not load the model before process`() = runTest {
        var created = 0
        val engine = engine(PostProcessConfig(recombinationEnabled = true)) {
            created++
            FakeLlamaSession()
        }

        // Constructing an ENABLED engine must not touch native memory or the GGUF.
        assertFalse("model must not be loaded at construction", engine.isLoaded)
        assertEquals("session provider must not run at construction", 0, created)
        assertFalse("native library must not be loaded at construction", NativeLlama.isLibraryLoaded)

        engine.process("我 愛 香 港").toList()

        assertTrue("model must be loaded after the first process()", engine.isLoaded)
        assertEquals(1, created)
    }

    @Test
    fun `process streams the session pieces in order`() = runTest {
        val fake = FakeLlamaSession(pieces = listOf("Hel", "lo", " wor", "ld"))
        val engine = engine { fake }

        val pieces = engine.process("hello").toList()

        assertEquals(listOf("Hel", "lo", " wor", "ld"), pieces)
        assertEquals("Hello world", pieces.joinToString(""))
    }

    @Test
    fun `opt out bypasses the model and streams input unchanged`() = runTest {
        var created = 0
        val engine = engine(PostProcessConfig(recombinationEnabled = false)) {
            created++
            FakeLlamaSession()
        }

        val pieces = engine.process("我 愛 香 港").toList()

        assertEquals(listOf("我 愛 香 港"), pieces)
        assertFalse("opt-out must never load the model", engine.isLoaded)
        assertEquals("provider must not run on opt-out", 0, created)
    }

    @Test
    fun `input longer than the cap raises InputTooLong without loading`() = runTest {
        var created = 0
        val engine = engine(PostProcessConfig(maxInputChars = 4)) {
            created++
            FakeLlamaSession()
        }

        assertThrows(AiAndroidException.InputTooLong::class.java) {
            runBlocking { engine.process("way too long").toList() }
        }
        assertFalse(engine.isLoaded)
        assertEquals(0, created)
    }

    @Test
    fun `deterministic seed and temperature are plumbed to the session`() = runTest {
        val fake = FakeLlamaSession()
        val engine = engine(seed = 7, temperature = GREEDY) { fake }

        engine.process("a b c").toList()

        val call = fake.calls.single()
        assertEquals(7, call.seed)
        assertEquals(GREEDY, call.temperature, 0.0f)
        assertTrue("ChatML prompt embeds the gloss sequence", call.prompt.contains("a b c"))
        assertTrue("ChatML prompt has an assistant turn", call.prompt.contains("<|im_start|>assistant"))
    }

    @Test
    fun `prompt selects the instruction for the configured language`() = runTest {
        val fake = FakeLlamaSession()
        val engine = engine(PostProcessConfig(language = Language.Cantonese)) { fake }

        engine.process("你好").toList()

        assertTrue(fake.calls.single().prompt.contains("Cantonese"))
    }

    @Test
    fun `unload frees the session and the next process lazily reloads`() = runTest {
        val sessions = mutableListOf<FakeLlamaSession>()
        val engine = engine { FakeLlamaSession().also { sessions += it } }

        engine.process("once").toList()
        assertTrue(engine.isLoaded)

        engine.unload()
        assertFalse("unload releases the model", engine.isLoaded)
        assertTrue("session was closed", sessions.single().closed)

        engine.process("twice").toList()
        assertTrue("next process lazily reloads", engine.isLoaded)
        assertEquals(2, sessions.size)
    }

    @Test
    fun `native error code fails the flow`() {
        val engine = engine { FakeLlamaSession(errorCode = -4) }

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { engine.process("oops").toList() }
        }

        assertTrue(error.message.orEmpty().contains("-4"))
    }

    private fun engine(
        config: PostProcessConfig = PostProcessConfig(),
        seed: Int = LlamaSamplingConfig.DEFAULT_SEED,
        temperature: Float = LlamaSamplingConfig.DEFAULT_TEMPERATURE,
        provider: () -> LlamaSession
    ) = LlamaPostProcessor.forTesting(
        config,
        provider,
        Dispatchers.Unconfined,
        LlamaSamplingConfig(seed = seed, temperature = temperature)
    )

    private companion object {
        const val GREEDY = 0.0f
    }
}
