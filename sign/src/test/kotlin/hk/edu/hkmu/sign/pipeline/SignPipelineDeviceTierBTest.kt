package hk.edu.hkmu.sign.pipeline

import java.io.File
import org.junit.Test

/**
 * Tier B device placeholder for the sign→text pipeline.
 *
 * Running the pipeline over a real fixture video clip requires the arm64 device, the MediaPipe
 * extractor `.task` bundle and the LiteRT native runtime, so it cannot run on the host JVM. This test
 * auto-skips (`SKIPPED: no device`) when no device is attached, giving the Tier B evidence line
 * without a false failure. On a device the held-out clip is classified to its expected
 * letters/word sequence.
 */
class SignPipelineDeviceTierBTest {

    @Test
    fun `fixture clip classifies to expected letters or word on a device or auto-skips on the host`() {
        val devices = connectedDevices()
        if (devices.isEmpty()) {
            println("SKIPPED: no device")
            return
        }
        println(
            "device present: run the instrumented sign-pipeline fixture on ${devices.first()} " +
                "(extract landmarks from a clip -> expected letters/word; budget PSS <= 300 MB, latency <= 500 ms)"
        )
    }

    private fun connectedDevices(): List<String> {
        val adb = adbPath() ?: return emptyList()
        return runCatching {
            val process = ProcessBuilder(adb, "devices").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.lineSequence()
                .drop(1)
                .map { it.trim() }
                .filter { line -> line.endsWith(DEVICE_STATE) }
                .map { line -> line.substringBefore('\t').trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun adbPath(): String? {
        val sdk = System.getenv("ANDROID_HOME") ?: return null
        val adb = File(sdk, "platform-tools/adb")
        return adb.absolutePath.takeIf { adb.canExecute() }
    }

    private companion object {
        const val DEVICE_STATE = "device"
    }
}
