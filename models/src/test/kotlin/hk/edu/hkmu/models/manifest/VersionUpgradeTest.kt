package hk.edu.hkmu.models.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUpgradeTest {
    private val registrar = UserProvidedModelRegistrar()

    private fun descriptor(id: String, version: String): ModelDescriptor = registrar.register(
        UserProvidedModel(
            id,
            declaredLicense = null,
            version = version,
            sha256 = SHA,
            abi = setOf(Abi.Arm64V8a),
            sizeBytes = MODEL_SIZE,
            urls = listOf(DEFAULT_URL),
            format = ModelFormat.Onnx
        )
    )

    @Test
    fun `version comparison zero-pads and orders numerically`() {
        assertEquals(0, ModelVersion.parse("1.2").compareTo(ModelVersion.parse("1.2.0")))
        assertTrue(ModelVersion.parse("1.10") > ModelVersion.parse("1.9"))
        assertTrue(ModelVersion.parse("2.0.0") > ModelVersion.parse("1.99.99"))
    }

    @Test
    fun `invalid version is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ModelVersion.parse("v1") }
        assertThrows(IllegalArgumentException::class.java) { ModelVersion.parse("1.2.3.4.5") }
    }

    @Test
    fun `version resolution detects upgrade up-to-date and downgrade`() {
        val older = descriptor(MODEL_ID, "1.0.0")
        val newer = descriptor(MODEL_ID, "2.0.0")
        assertTrue(VersionResolution.of(older, newer) is VersionResolution.UpgradeAvailable)
        assertTrue(VersionResolution.of(newer, older) is VersionResolution.DowngradeRefused)
        assertTrue(VersionResolution.of(older, descriptor(MODEL_ID, "1.0.0")) is VersionResolution.UpToDate)
    }

    @Test
    fun `fresh install verifies the candidate or rejects it`() {
        val okStore = ModelVersionStore()
        val installed = ModelUpgradeController(okStore) { true }.installOrUpgrade(descriptor(MODEL_ID, "1.0.0"))
        assertTrue(installed is UpgradeOutcome.Installed)
        assertEquals("1.0.0", checkNotNull(okStore.active(MODEL_ID)).version.raw)

        val badStore = ModelVersionStore()
        val rejected = ModelUpgradeController(badStore) { false }.installOrUpgrade(descriptor(MODEL_ID, "1.0.0"))
        assertTrue(rejected is UpgradeOutcome.Rejected)
        assertNull(badStore.active(MODEL_ID))
    }

    @Test
    fun `unverified upgrade keeps the old version installed`() {
        val store = ModelVersionStore()
        store.install(descriptor(MODEL_ID, "1.0.0"))
        val outcome = ModelUpgradeController(store) { false }.installOrUpgrade(descriptor(MODEL_ID, "2.0.0"))
        assertTrue(outcome is UpgradeOutcome.RolledBack)
        assertEquals("1.0.0", checkNotNull(store.active(MODEL_ID)).version.raw)
    }

    @Test
    fun `verified upgrade replaces current and the previous stays rollback-able`() {
        val store = ModelVersionStore()
        store.install(descriptor(MODEL_ID, "1.0.0"))
        val outcome = ModelUpgradeController(store) { true }.installOrUpgrade(descriptor(MODEL_ID, "2.0.0"))
        assertTrue(outcome is UpgradeOutcome.Upgraded)
        assertEquals("2.0.0", checkNotNull(store.active(MODEL_ID)).version.raw)
        assertEquals("1.0.0", checkNotNull(store.rollback(MODEL_ID)).version.raw)
        assertEquals("1.0.0", checkNotNull(store.active(MODEL_ID)).version.raw)
    }

    @Test
    fun `downgrade is refused and current retained`() {
        val store = ModelVersionStore()
        store.install(descriptor(MODEL_ID, "2.0.0"))
        val outcome = ModelUpgradeController(store) { true }.installOrUpgrade(descriptor(MODEL_ID, "1.0.0"))
        assertTrue(outcome is UpgradeOutcome.Refused)
        assertEquals("2.0.0", checkNotNull(store.active(MODEL_ID)).version.raw)
    }

    private companion object {
        const val MODEL_ID = "nllb"
        const val DEFAULT_URL = "https://cdn.example.invalid/models/nllb"
        const val MODEL_SIZE = 4096L
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
