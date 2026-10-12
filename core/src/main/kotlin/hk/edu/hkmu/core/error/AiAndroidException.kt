package hk.edu.hkmu.core.error

import hk.edu.hkmu.core.config.Accelerator
import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.UsageIntent

/**
 * Root of the typed error hierarchy for the on-device AI pipeline.
 *
 * Every failure raised by the library is one of the nested subclasses below so callers can catch a
 * precise type instead of inspecting message strings. Subclasses carry structured fields (model id,
 * language, shape, ...) so hosts can react programmatically.
 */
sealed class AiAndroidException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** A model or capability is blocked by the active license policy. */
    class LicenseRestricted(
        val modelId: String,
        val licenseKind: LicenseKind,
        val intent: UsageIntent,
        detail: String = "license ${licenseKind.name} forbids ${intent.name} use"
    ) : AiAndroidException("License restricted for model '$modelId': $detail")

    /** The signed manifest failed signature verification. */
    class ManifestSignatureInvalid(
        val manifestId: String,
        cause: Throwable? = null
    ) : AiAndroidException("Manifest '$manifestId' signature is invalid", cause)

    /** The manifest is structurally valid-signed but malformed. */
    class ManifestInvalid(
        val manifestId: String,
        val reason: String
    ) : AiAndroidException("Manifest '$manifestId' is invalid: $reason")

    /** A required model asset file is missing before native load. */
    class ModelAssetMissing(
        val modelId: String,
        val missingAsset: String
    ) : AiAndroidException("Model '$modelId' is missing required asset '$missingAsset'")

    /** The engine failed to load a model at the native runtime boundary. */
    class ModelLoadFailed(
        val modelId: String,
        cause: Throwable? = null
    ) : AiAndroidException("Failed to load model '$modelId'", cause)

    /** The model is currently in use (refcount > 0) and cannot be evicted or unloaded. */
    class ModelInUse(
        val modelId: String
    ) : AiAndroidException("Model '$modelId' is in use")

    /** The requested model is not present in the registry or store. */
    class ModelNotAvailable(
        val modelId: String
    ) : AiAndroidException("Model '$modelId' is not available")

    /** A downloaded or installed artifact did not match its pinned checksum. */
    class ChecksumMismatch(
        val modelId: String,
        val expectedSha256: String,
        val actualSha256: String
    ) : AiAndroidException("Checksum mismatch for '$modelId': expected $expectedSha256, got $actualSha256")

    /** The native library for the running ABI is absent. */
    class NativeLibraryMissing(
        val expectedAbiPath: String
    ) : AiAndroidException("Native library missing; expected ABI path '$expectedAbiPath'")

    /** The requested language is not supported by the configured engine/model. */
    class UnsupportedLanguage(
        val language: Language
    ) : AiAndroidException("Language '${language.tag}' is not supported")

    /** Text or audio input exceeds the configured maximum length. */
    class InputTooLong(
        val actualLength: Int,
        val maxLength: Int
    ) : AiAndroidException("Input length $actualLength exceeds maximum $maxLength")

    /** Tensor or frame input shape does not match the model contract. */
    class InputShapeMismatch(
        val expected: String,
        val actual: String
    ) : AiAndroidException("Input shape mismatch: expected $expected, got $actual")

    /** A frame (audio/video) could not be decoded. */
    class MalformedFrame(
        val reason: String
    ) : AiAndroidException("Malformed frame: $reason")

    /** The requested accelerator is not available on this device. */
    class AcceleratorUnavailable(
        val requested: Accelerator,
        val available: List<Accelerator>
    ) : AiAndroidException("Accelerator ${requested.name} unavailable; available=${available.joinToString()}")

    /** The caller composed an invalid pipeline (e.g. a missing engine for a stage). */
    class PipelineMisconfigured(
        val reason: String
    ) : AiAndroidException("Pipeline misconfigured: $reason")

    /** A required runtime dependency/library is not on the classpath. */
    class MissingDependency(
        val dependency: String,
        cause: Throwable? = null
    ) : AiAndroidException("Missing dependency '$dependency'", cause)

    /** Script conversion (e.g. zh-Hant↔zh-Hans) failed for an unknown configuration id. */
    class ScriptConversionError(
        val configId: String,
        cause: Throwable? = null
    ) : AiAndroidException("Script conversion error for config '$configId'", cause)
}
