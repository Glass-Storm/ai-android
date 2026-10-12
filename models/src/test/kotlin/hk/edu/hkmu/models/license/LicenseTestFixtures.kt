package hk.edu.hkmu.models.license

import hk.edu.hkmu.core.registry.ModelHandle
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration
import hk.edu.hkmu.models.manifest.ManifestBody
import hk.edu.hkmu.models.manifest.ManifestEntry
import hk.edu.hkmu.models.manifest.ManifestSignatureVerifier
import hk.edu.hkmu.models.manifest.SignedManifest
import hk.edu.hkmu.models.manifest.SignedManifestLoader
import hk.edu.hkmu.models.manifest.TestManifestSigner

/** Handle that records nothing but satisfies the registry's [ModelHandle] seam. */
internal class RecordingHandle : ModelHandle {
    override fun close() = Unit
}

/**
 * Loader that records which registrations were instantiated, so a blocked model can prove the
 * loader was never invoked.
 */
internal class RecordingLoader : ModelLoader<RecordingHandle> {
    val loaded: MutableList<String> = mutableListOf()

    override fun load(registration: ModelRegistration): RecordingHandle {
        loaded += registration.modelId
        return RecordingHandle()
    }
}

/**
 * Shared fixtures for the license-enforcement suite.
 *
 * Descriptors under test are always produced by [SignedManifestLoader] (a signature-verified vendor
 * manifest) or `UserProvidedModelRegistrar` (a BYO model), never hand-minted, so the license the
 * policy sees is exactly what production would receive.
 */
internal object LicenseTestFixtures {
    const val RELEASE = "2026.10.0"
    const val VERSION = "1.0.0"
    const val MODEL_SIZE = 1024L
    const val DEFAULT_URL = "https://cdn.example.invalid/models/a"
    const val PLACEHOLDER_SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    fun signer(): TestManifestSigner = TestManifestSigner()

    fun loaderFor(signer: TestManifestSigner): SignedManifestLoader =
        SignedManifestLoader(ManifestSignatureVerifier(listOf(signer.trustedKey)))

    fun entry(
        id: String,
        license: String,
        url: String = DEFAULT_URL,
        sha256: String = PLACEHOLDER_SHA,
        sizeBytes: Long = MODEL_SIZE
    ): ManifestEntry = ManifestEntry(
        id = id,
        license = license,
        version = VERSION,
        sha256 = sha256,
        abi = listOf("any"),
        sizeBytes = sizeBytes,
        urls = listOf(url),
        format = "onnx"
    )

    fun manifest(
        signer: TestManifestSigner,
        loader: SignedManifestLoader,
        vararg entries: ManifestEntry
    ): SignedManifest = loader.load(signer.json(signer.envelope(ManifestBody(RELEASE, entries.toList()))))
}
