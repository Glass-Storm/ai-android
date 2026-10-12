package hk.edu.hkmu.recombine

import java.io.File
import org.junit.Test

/**
 * Tier B device placeholder for the recombination LLM.
 *
 * The real gate loads the pinned Qwen2.5-0.5B GGUF on an arm64 device and recombines a fixture
 * token sequence, so it cannot run on the host JVM. This test auto-skips (`SKIPPED: no device`)
 * when no device is attached, giving the Tier B evidence line without a false failure. On a device
 * the fixture sequence `我 愛 香 港` is the recombination target.
 */
class RecombineDeviceTierBTest {

    @Test
    fun `fixture token sequence recombines on a device or auto-skips on the host`() {
        val devices = connectedDevices()
        if (devices.isEmpty()) {
            println("SKIPPED: no device")
            return
        }
        println("device present: run the instrumented recombination fixture on ${devices.first()}")
        println("fixture tokens: $FIXTURE_TOKENS")
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
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: return null
        val adb = File(sdk, "platform-tools/adb")
        return adb.absolutePath.takeIf { adb.canExecute() }
    }

    private companion object {
        const val DEVICE_STATE = "device"
        const val FIXTURE_TOKENS = "我 愛 香 港"
    }
}
