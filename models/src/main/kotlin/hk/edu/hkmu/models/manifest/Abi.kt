package hk.edu.hkmu.models.manifest

/**
 * Android ABI a native model artifact is pinned for.
 *
 * [Any] marks an artifact that is not tied to a native ABI (e.g. a `.task` MediaPipe bundle or a
 * pure-JVM asset); it must appear alone and never alongside a concrete ABI.
 */
enum class Abi(val dir: String) {
    ArmeabiV7a("armeabi-v7a"),
    Arm64V8a("arm64-v8a"),
    X86("x86"),
    X86_64("x86_64"),
    Any("any");

    companion object {
        /** Resolves a manifest `abi` token, or `null` when it is not a known ABI. */
        fun fromDir(dir: String): Abi? = entries.firstOrNull { it.dir.equals(dir.trim(), ignoreCase = true) }
    }
}
