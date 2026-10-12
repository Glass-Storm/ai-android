package hk.edu.hkmu.core.native

import hk.edu.hkmu.core.error.AiAndroidException
import java.util.concurrent.ConcurrentHashMap

/** A native library that has been loaded for a specific [AndroidAbi]. */
data class LoadedNativeLibrary(
    val libraryName: String,
    val abi: AndroidAbi,
    val abiPath: String
)

/**
 * Performs the actual native load for a logical `libraryName` on the given `abi`.
 *
 * A missing library is expected to fail with a [LinkageError] (typically
 * [UnsatisfiedLinkError]). Injectable so tests can observe and fault-inject loads without a
 * real `.so`.
 */
fun interface NativeLibraryBridge {
    fun load(libraryName: String, abi: AndroidAbi)
}

/**
 * [NativeLibraryBridge] backed by `System.loadLibrary`, which resolves the ABI from the installed
 * APK's `jniLibs`. The `abi` argument is carried for diagnostics only.
 */
class SystemNativeLibraryBridge : NativeLibraryBridge {
    override fun load(libraryName: String, abi: AndroidAbi) {
        System.loadLibrary(libraryName)
    }
}

/**
 * Lazily and thread-safely loads each native library **exactly once**.
 *
 * Nothing is loaded at construction (or class-initialisation) time: the first [load] call for a
 * logical name triggers the native load, and every concurrent or subsequent caller receives the
 * same [LoadedNativeLibrary] without triggering a re-load. A missing library surfaces as a typed
 * [AiAndroidException.NativeLibraryMissing] naming the expected APK-style ABI path.
 *
 * @param abiResolver resolves the running process ABI (injectable for tests).
 * @param bridge performs the native load (injectable for tests).
 * @param fallbackAbi the ABI assumed when [abiResolver] cannot resolve one; production is arm64.
 */
class NativeLibraryLoader(
    private val abiResolver: AbiResolver = SystemAbiResolver(),
    private val bridge: NativeLibraryBridge = SystemNativeLibraryBridge(),
    private val fallbackAbi: AndroidAbi = AndroidAbi.Arm64V8
) {
    private val loaded = ConcurrentHashMap<String, LoadedNativeLibrary>()

    /** @return true when `libraryName` has already been loaded successfully. */
    fun isLoaded(libraryName: String): Boolean = loaded.containsKey(libraryName)

    /** @return the already-loaded library for `libraryName`, or `null` when not yet loaded. */
    fun loadedOrNull(libraryName: String): LoadedNativeLibrary? = loaded[libraryName]

    /**
     * Loads `libraryName` for the resolved ABI, invoking the native bridge at most once.
     *
     * @throws AiAndroidException.NativeLibraryMissing when the library cannot be loaded for the
     *   resolved ABI; the exception's `expectedAbiPath` names the missing file.
     */
    fun load(libraryName: String): LoadedNativeLibrary {
        return loaded.computeIfAbsent(libraryName) { name -> loadOnce(name) }
    }

    private fun loadOnce(libraryName: String): LoadedNativeLibrary {
        val abi = abiResolver.current() ?: fallbackAbi
        val abiPath = abi.libraryPath(libraryName)
        try {
            bridge.load(libraryName, abi)
        } catch (error: LinkageError) {
            throw AiAndroidException.NativeLibraryMissing(abiPath).also { it.addSuppressed(error) }
        }
        return LoadedNativeLibrary(libraryName, abi, abiPath)
    }
}
