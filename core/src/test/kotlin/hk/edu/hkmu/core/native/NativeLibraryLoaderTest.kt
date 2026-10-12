package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.error.AiAndroidException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the native-loader contract: lazy, thread-safe, exactly-once loading with a typed
 * [AiAndroidException.NativeLibraryMissing] when the `.so` is absent.
 */
class NativeLibraryLoaderTest {

    @Test
    fun `nothing is loaded at construction or class initialisation`() {
        val bridge = RecordingBridge()
        val loader = NativeLibraryLoader(PinnedAbiResolver(AndroidAbi.X86_64), bridge)

        assertEquals("no load before first use", 0, bridge.callCount.get())
        assertFalse(loader.isLoaded("sherpa-onnx-jni"))
        assertNull(loader.loadedOrNull("sherpa-onnx-jni"))
        assertEquals("querying state must not trigger a load", 0, bridge.callCount.get())
    }

    @Test
    fun `loads a library exactly once and caches the result`() {
        val bridge = RecordingBridge()
        val loader = NativeLibraryLoader(PinnedAbiResolver(AndroidAbi.X86_64), bridge)

        val first = loader.load("sherpa-onnx-jni")
        val second = loader.load("sherpa-onnx-jni")
        val third = loader.load("sherpa-onnx-jni")

        assertSame(first, second)
        assertSame(first, third)
        assertEquals("bridge invoked exactly once", 1, bridge.callCount.get())
        assertTrue(loader.isLoaded("sherpa-onnx-jni"))
        assertEquals("lib/x86_64/libsherpa-onnx-jni.so", first.abiPath)
        assertEquals(AndroidAbi.X86_64, first.abi)
    }

    @Test
    fun `loads distinct libraries independently`() {
        val bridge = RecordingBridge()
        val loader = NativeLibraryLoader(PinnedAbiResolver(AndroidAbi.X86_64), bridge)

        loader.load("alpha")
        loader.load("beta")

        assertEquals(2, bridge.callCount.get())
        assertEquals(setOf("alpha", "beta"), bridge.loadedNames.toSet())
    }

    @Test
    fun `concurrent callers still load exactly once`() {
        val bridge = RecordingBridge()
        val loader = NativeLibraryLoader(PinnedAbiResolver(AndroidAbi.X86_64), bridge)
        val pool = Executors.newFixedThreadPool(POOL_SIZE)

        try {
            val futures = (0 until CALLS).map { pool.submit<LoadedNativeLibrary> { loader.load("race") } }
            val results = futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertEquals("one bridge call under contention", 1, bridge.callCount.get())
            assertEquals("single logical result", 1, results.toSet().size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `missing library raises typed error naming the expected ABI path`() {
        val bridge = RecordingBridge(failing = setOf("missing"))
        val loader = NativeLibraryLoader(PinnedAbiResolver(AndroidAbi.X86_64), bridge)

        val error = assertThrows(AiAndroidException.NativeLibraryMissing::class.java) {
            loader.load("missing")
        }

        assertEquals("lib/x86_64/libmissing.so", error.expectedAbiPath)
        assertFalse(loader.isLoaded("missing"))
    }

    @Test
    fun `unsupported process ABI falls back to the production ABI path`() {
        val bridge = RecordingBridge(failing = setOf("missing"))
        val loader = NativeLibraryLoader(AbiResolver { null }, bridge)

        val error = assertThrows(AiAndroidException.NativeLibraryMissing::class.java) {
            loader.load("missing")
        }

        assertEquals("lib/arm64-v8a/libmissing.so", error.expectedAbiPath)
    }

    @Test
    fun `production and test ABIs map to their canonical names and paths`() {
        assertEquals(AndroidAbi.Arm64V8, SystemAbiResolver.mapArch("aarch64"))
        assertEquals(AndroidAbi.Arm64V8, SystemAbiResolver.mapArch("arm64"))
        assertEquals(AndroidAbi.X86_64, SystemAbiResolver.mapArch("x86_64"))
        assertEquals(AndroidAbi.X86_64, SystemAbiResolver.mapArch("amd64"))

        assertFalse("arm64-v8a is the production ABI", AndroidAbi.Arm64V8.testOnly)
        assertTrue("x86_64 is test-only", AndroidAbi.X86_64.testOnly)

        assertEquals("arm64-v8a", AndroidAbi.Arm64V8.abiName)
        assertEquals("x86_64", AndroidAbi.X86_64.abiName)
        assertEquals("lib/arm64-v8a/libfoo.so", AndroidAbi.Arm64V8.libraryPath("foo"))
        assertEquals(AndroidAbi.X86_64, AndroidAbi.fromAbiName("x86_64"))
        assertNull(AndroidAbi.fromAbiName("riscv64"))
    }

    private class PinnedAbiResolver(private val abi: AndroidAbi) : AbiResolver {
        override fun current(): AndroidAbi = abi
    }

    private class RecordingBridge(
        private val failing: Set<String> = emptySet()
    ) : NativeLibraryBridge {
        val callCount = AtomicInteger(0)
        val loadedNames: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun load(libraryName: String, abi: AndroidAbi) {
            callCount.incrementAndGet()
            loadedNames.add(libraryName)
            if (libraryName in failing) {
                throw UnsatisfiedLinkError("cannot load lib$libraryName.so for $abi")
            }
        }
    }

    private companion object {
        const val POOL_SIZE = 16
        const val CALLS = 200
        const val TIMEOUT_SECONDS = 30L
    }
}
