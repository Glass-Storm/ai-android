package hk.edu.hkmu.sign.landmark

import java.io.File
import org.junit.Test

/**
 * Tier B device placeholder.
 *
 * The real extraction requires the arm64 device, the MediaPipe Tasks Vision native library, and a
 * provisioned `.task` bundle, so it cannot run on the host JVM. This test auto-skips
 * (`SKIPPED: no device`) when no device is attached, giving the Tier B evidence line without a false
 * failure. On a device the instrumented run must supply a fixture frame sequence and assert the
 * landmarker returns non-empty landmarks with `num_hands = 2`.
 */
class SignLandmarkDeviceTierBTest {

    @Test
    fun `landmarker produces non-empty landmarks on a device or auto-skips on the host`() {
        val devices = connectedDevices()
        if (devices.isEmpty()) {
            println("SKIPPED: no device")
            return
        }
        println("device present: run the instrumented landmarker fixture test on ${devices.first()}")
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
