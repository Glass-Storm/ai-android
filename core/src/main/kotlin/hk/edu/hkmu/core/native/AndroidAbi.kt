package hk.edu.hkmu.core.native

/**
 * An Android Application Binary Interface (ABI) understood by the native loader.
 *
 * Production ships [Arm64V8] only; every [testOnly] entry exists solely for host/emulator unit
 * tests and must never be packaged into a release AAR (enforced by the consuming module's
 * `abiFilters`, see todo 30).
 *
 * @property abiName the canonical Android ABI directory name (e.g. `arm64-v8a`).
 * @property testOnly true when the ABI is test-only and excluded from production packaging.
 */
enum class AndroidAbi(
    val abiName: String,
    val testOnly: Boolean
) {
    /** 64-bit ARM — the single production target. */
    Arm64V8("arm64-v8a", testOnly = false),

    /** 32-bit ARM — not shipped; retained for graceful diagnostics. */
    ArmeabiV7a("armeabi-v7a", testOnly = true),

    /** 64-bit x86 — emulator/host test target. */
    X86_64("x86_64", testOnly = true),

    /** 32-bit x86 — not shipped. */
    X86("x86", testOnly = true);

    /**
     * APK-style relative path for [libraryName] on this ABI, for example
     * `lib/arm64-v8a/libsherpa-onnx-jni.so`.
     */
    fun libraryPath(libraryName: String): String = "lib/$abiName/lib$libraryName.so"

    companion object {
        /** Returns the [AndroidAbi] whose [abiName] equals [abiName], or `null` when unknown. */
        fun fromAbiName(abiName: String): AndroidAbi? = entries.firstOrNull { it.abiName == abiName }
    }
}
