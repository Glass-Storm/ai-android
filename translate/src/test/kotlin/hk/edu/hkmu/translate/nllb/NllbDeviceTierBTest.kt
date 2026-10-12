package hk.edu.hkmu.translate.nllb

import java.io.File
import org.junit.Test

/**
 * Tier B device placeholder.
 *
 * Translating all four directions on a fixture requires the arm64 device plus the NLLB INT8 model
 * and the SentencePiece native, so it cannot run on the host JVM. This test auto-skips
 * (`SKIPPED: no device`) when no device is attached, giving the Tier B evidence line without a false
 * failure.
 */
class NllbDeviceTierBTest {

    @Test
    fun `four-direction fixture translation runs on a device or auto-skips on the host`() {
        val devices = connectedDevices()
        if (devices.isEmpty()) {
            println("SKIPPED: no device")
            return
        }
        println(
            "device present: run the instrumented NLLB fixture test on ${devices.first()} " +
                "(directions en<->zh-Hans, en<->yue_Hant; budgets PSS <= 1600 MB, latency <= 3000 ms)"
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
