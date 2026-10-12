package hk.edu.hkmu.recombine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier A acceptance (todo 29, checkbox A): the recombination LLM is **not resident unless used**,
 * observed through the shared [hk.edu.hkmu.core.registry.ModelRegistry].
 *
 * The GGUF is registered lazily; the session-provider seam acquires it from the registry only on the
 * first `process()`. So a recombine-disabled run leaves exactly one fewer resident GGUF than an
 * enabled run, and merely enabling the post-processor does not load anything until `process()` runs.
 */
class LazyRecombinationResidencyTest {

    @Test
    fun `recombine disabled leaves zero GGUF resident`() = runTest {
        val fixture = RegistryBackedRecombine(recombinationEnabled = false)

        val pieces = fixture.processor.process(GLOSS).toList()

        assertEquals(listOf(GLOSS), pieces)
        assertFalse("opt-out must not make the GGUF resident", fixture.resident())
        assertEquals(0, fixture.residentCount())
        assertEquals("provider must not run on opt-out", 0, fixture.createdSessions.size)
    }

    @Test
    fun `enabling recombination does not make the GGUF resident until process`() = runTest {
        val fixture = RegistryBackedRecombine(recombinationEnabled = true)

        assertFalse("constructing must not load the GGUF", fixture.resident())
        assertEquals(0, fixture.createdSessions.size)

        fixture.processor.process(GLOSS).toList()

        assertTrue("the first process() loads the GGUF", fixture.resident())
        assertEquals(1, fixture.residentCount())
        assertEquals(1, fixture.createdSessions.size)
    }

    @Test
    fun `recombine disabled has exactly one fewer resident GGUF than enabled`() = runTest {
        val off = RegistryBackedRecombine(recombinationEnabled = false).also { it.processor.process(GLOSS).toList() }
        val on = RegistryBackedRecombine(recombinationEnabled = true).also { it.processor.process(GLOSS).toList() }

        assertEquals(0, off.residentCount())
        assertEquals(1, on.residentCount())
        assertEquals("disabled saves exactly one resident GGUF", 1, on.residentCount() - off.residentCount())
    }

    @Test
    fun `unload frees the resident GGUF and the next process lazily reloads`() = runTest {
        val fixture = RegistryBackedRecombine(recombinationEnabled = true)

        fixture.processor.process(GLOSS).toList()
        assertTrue(fixture.resident())

        fixture.processor.unload()
        assertFalse("unload must free the GGUF", fixture.resident())
        assertTrue("the freed session was closed", fixture.createdSessions.single().closed)

        fixture.processor.process(GLOSS).toList()
        assertTrue("the next process lazily reloads", fixture.resident())
        assertEquals(2, fixture.createdSessions.size)
    }

    private companion object {
        const val GLOSS = "我 愛 香 港"
    }
}
