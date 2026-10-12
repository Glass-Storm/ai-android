package hk.edu.hkmu.models.store

import hk.edu.hkmu.core.license.LicenseDecision
import hk.edu.hkmu.core.license.LicenseKind
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.ModelLicense
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.models.manifest.Abi
import hk.edu.hkmu.models.manifest.ModelDescriptor
import hk.edu.hkmu.models.manifest.ModelFormat
import hk.edu.hkmu.models.manifest.ModelSource
import hk.edu.hkmu.models.manifest.ModelVersion
import hk.edu.hkmu.models.manifest.UserProvidedModel
import hk.edu.hkmu.models.manifest.UserProvidedModelRegistrar
import java.security.MessageDigest

/** Shared descriptors, byte fixtures, and policies for the downloader suite. */
internal object TestModelFixtures {
    const val MODEL_ID = "test-model"
    const val VERSION = "1.0.0"
    const val SIZE = 64 * 1024
    private const val CHUNK_SEED_STRIDE = 7
    private const val BYTE_MASK = 0xff

    /** Deterministic [SIZE]-byte artifact; [seed] yields independent payloads. */
    fun body(seed: Int = 0): ByteArray = ByteArray(SIZE) { index ->
        ((index + seed * CHUNK_SEED_STRIDE) and BYTE_MASK).toByte()
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun vendorDescriptor(
        id: String = MODEL_ID,
        urls: List<String>,
        sha256: String,
        sizeBytes: Long = SIZE.toLong(),
        license: ModelLicense = ModelLicense(LicenseKind.Permissive, "Apache-2.0")
    ): ModelDescriptor = ModelDescriptor(
        id = id,
        license = license,
        version = ModelVersion.parse(VERSION),
        sha256 = sha256,
        abi = setOf(Abi.Any),
        sizeBytes = sizeBytes,
        urls = urls,
        format = ModelFormat.Onnx,
        source = ModelSource.Vendor("test-manifest", "2026.10.0")
    )

    fun userProvidedDescriptor(
        id: String,
        urls: List<String>,
        sha256: String,
        sizeBytes: Long = SIZE.toLong()
    ): ModelDescriptor = UserProvidedModelRegistrar().register(
        UserProvidedModel(
            id = id,
            declaredLicense = null,
            version = VERSION,
            sha256 = sha256,
            abi = setOf(Abi.Any),
            sizeBytes = sizeBytes,
            urls = urls,
            format = ModelFormat.Tflite
        )
    )

    val nonCommercialLicense: ModelLicense =
        ModelLicense(LicenseKind.NonCommercial, spdxId = "CC-BY-NC-4.0", notice = "NC only")
}

/** License policy mirroring the documented core semantics (Commercial allows only permissive). */
internal class TestLicensePolicy : LicensePolicy {
    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision = when (intent) {
        UsageIntent.SelfUse -> LicenseDecision.Allowed(license.notice)
        UsageIntent.Commercial -> if (license.kind == LicenseKind.Permissive) {
            LicenseDecision.Allowed()
        } else {
            LicenseDecision.Restricted("commercial use blocked for ${license.kind}", license, intent)
        }
    }
}

/** A policy that permits everything; used to isolate non-license behaviour. */
internal val AllowAllLicensePolicy: LicensePolicy = object : LicensePolicy {
    override fun evaluate(license: ModelLicense, intent: UsageIntent): LicenseDecision =
        LicenseDecision.Allowed(license.notice)
}
