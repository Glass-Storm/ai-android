package hk.edu.hkmu.models.manifest

/** Pure version comparison between an installed descriptor and a same-id candidate. */
sealed interface VersionResolution {
    data class UpgradeAvailable(val current: ModelDescriptor, val candidate: ModelDescriptor) : VersionResolution
    data class UpToDate(val current: ModelDescriptor) : VersionResolution
    data class DowngradeRefused(val current: ModelDescriptor, val candidate: ModelDescriptor) : VersionResolution

    companion object {
        /** Compares two descriptors of the SAME model id. */
        fun of(current: ModelDescriptor, candidate: ModelDescriptor): VersionResolution {
            require(current.id == candidate.id) { "cannot compare versions of different models" }
            return when {
                candidate.version > current.version -> UpgradeAvailable(current, candidate)
                candidate.version < current.version -> DowngradeRefused(current, candidate)
                else -> UpToDate(current)
            }
        }
    }
}

/** Result of an install/upgrade attempt. */
sealed interface UpgradeOutcome {
    data class Installed(val descriptor: ModelDescriptor) : UpgradeOutcome
    data class Upgraded(val previous: ModelDescriptor, val current: ModelDescriptor) : UpgradeOutcome
    data class UpToDate(val current: ModelDescriptor) : UpgradeOutcome
    data class Refused(val retained: ModelDescriptor, val candidate: ModelDescriptor) : UpgradeOutcome
    data class RolledBack(val retained: ModelDescriptor, val candidate: ModelDescriptor) : UpgradeOutcome
    data class Rejected(val candidate: ModelDescriptor) : UpgradeOutcome
}

/**
 * In-memory set of installed descriptors keyed by model id.
 *
 * A new install keeps the superseded descriptor in a rollback slot; [rollback] restores it. The old
 * version is therefore retained until an upgrade is committed, and can be reinstated afterwards.
 */
class ModelVersionStore {
    private val active = LinkedHashMap<String, ModelDescriptor>()
    private val superseded = HashMap<String, ModelDescriptor>()

    fun active(modelId: String): ModelDescriptor? = active[modelId]

    fun install(descriptor: ModelDescriptor): ModelDescriptor? {
        val previous = active.put(descriptor.id, descriptor)
        if (previous != null) superseded[descriptor.id] = previous
        return previous
    }

    fun rollback(modelId: String): ModelDescriptor? {
        val previous = superseded.remove(modelId) ?: return null
        val replaced = active.put(modelId, previous)
        if (replaced != null) superseded[modelId] = replaced
        return previous
    }
}

/**
 * Applies version upgrades while guaranteeing the installed version is never replaced by an
 * unverified candidate: [verifyArtifact] (the downloader's integrity check, todo 11) must pass
 * before the candidate replaces the current descriptor. On failure the previous version is retained.
 */
class ModelUpgradeController(
    private val store: ModelVersionStore,
    private val verifyArtifact: (ModelDescriptor) -> Boolean
) {
    fun installOrUpgrade(candidate: ModelDescriptor): UpgradeOutcome {
        val current = store.active(candidate.id) ?: return installFresh(candidate)
        return when (VersionResolution.of(current, candidate)) {
            is VersionResolution.UpToDate -> UpgradeOutcome.UpToDate(current)
            is VersionResolution.DowngradeRefused -> UpgradeOutcome.Refused(current, candidate)
            is VersionResolution.UpgradeAvailable -> commitOrRollback(current, candidate)
        }
    }

    private fun installFresh(candidate: ModelDescriptor): UpgradeOutcome = if (verified(candidate)) {
        store.install(candidate)
        UpgradeOutcome.Installed(candidate)
    } else {
        UpgradeOutcome.Rejected(candidate)
    }

    private fun commitOrRollback(current: ModelDescriptor, candidate: ModelDescriptor): UpgradeOutcome =
        if (verified(candidate)) {
            store.install(candidate)
            UpgradeOutcome.Upgraded(current, candidate)
        } else {
            UpgradeOutcome.RolledBack(current, candidate)
        }

    private fun verified(candidate: ModelDescriptor): Boolean =
        runCatching { verifyArtifact(candidate) }.getOrDefault(false)
}
