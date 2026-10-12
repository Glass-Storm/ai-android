package hk.edu.hkmu.core.native

/**
 * Resolves the ABI of the currently running process.
 *
 * Injectable so tests can pin a deterministic ABI; the production implementation reads the
 * JVM/Android `os.arch` system property.
 */
fun interface AbiResolver {
    /** @return the current [AndroidAbi], or `null` when the process ABI is unknown/unsupported. */
    fun current(): AndroidAbi?
}

/**
 * [AbiResolver] backed by the `os.arch` system property.
 *
 * @param arch the architecture token to resolve; defaults to the running JVM's `os.arch`.
 */
class SystemAbiResolver(
    private val arch: String = System.getProperty("os.arch").orEmpty().lowercase()
) : AbiResolver {
    override fun current(): AndroidAbi? = mapArch(arch)

    companion object {
        private val ARM64 = setOf("aarch64", "arm64")
        private val X86_64 = setOf("x86_64", "amd64")
        private val ARM32 = setOf("arm", "armv7l", "armv7")
        private val X86_32 = setOf("x86", "i386", "i486", "i586", "i686")

        /** Maps a `os.arch` token to an [AndroidAbi], or `null` when unrecognised. */
        fun mapArch(arch: String): AndroidAbi? = when (arch.lowercase()) {
            in ARM64 -> AndroidAbi.Arm64V8
            in X86_64 -> AndroidAbi.X86_64
            in ARM32 -> AndroidAbi.ArmeabiV7a
            in X86_32 -> AndroidAbi.X86
            else -> null
        }
    }
}
