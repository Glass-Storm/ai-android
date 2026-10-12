package hk.edu.hkmu.recombine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Manual-QA (A) probe: prints the lazy-residency observable for the evidence log.
 *
 * Run with `-i` so Gradle surfaces `STANDARD_OUT`:
 * `./gradlew :recombine:testDebugUnitTest --tests '*LazyResidencyManualQaProbeTest' -i --rerun-tasks`.
 */
class LazyResidencyManualQaProbeTest {

    @Test
    fun `manual QA - recombine off loads no extra GGUF and on loads only at process`() = runTest {
        val off = RegistryBackedRecombine(recombinationEnabled = false)
        val on = RegistryBackedRecombine(recombinationEnabled = true)

        println(
            "RECOMBINE-MANUAL-QA before process: off.resident=${off.residentCount()} " +
                "on.resident=${on.residentCount()} (enabling alone loads nothing)"
        )

        val offPieces = off.processor.process(GLOSS).toList()
        val onResidentBeforeProcess = on.residentCount()
        val onPieces = on.processor.process(GLOSS).toList()

        val offLine = "off pieces=$offPieces resident=${off.residentCount()} sessions=${off.createdSessions.size}"
        val onLine = "on pieces=$onPieces resident=${on.residentCount()} sessions=${on.createdSessions.size}"

        println("RECOMBINE-MANUAL-QA enabled before first process(): on.resident=$onResidentBeforeProcess")
        println("RECOMBINE-MANUAL-QA $offLine")
        println("RECOMBINE-MANUAL-QA $onLine")
        println("RECOMBINE-MANUAL-QA delta resident GGUF (on - off) = ${on.residentCount() - off.residentCount()}")

        assertEquals(listOf(GLOSS), offPieces)
        assertEquals(0, off.residentCount())
        assertEquals(0, onResidentBeforeProcess)
        assertEquals(1, on.residentCount())
    }

    private companion object {
        const val GLOSS = "我 愛 香 港"
    }
}
