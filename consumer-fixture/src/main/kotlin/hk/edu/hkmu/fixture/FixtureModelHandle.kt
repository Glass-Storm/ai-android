package hk.edu.hkmu.fixture

import hk.edu.hkmu.core.registry.ModelHandle

/**
 * Minimal [ModelHandle] used by the fixture to exercise [hk.edu.hkmu.core.registry.ModelRegistry]
 * through its public API.
 *
 * @property modelId Identifier of the model this handle owns.
 */
class FixtureModelHandle(val modelId: String) : ModelHandle {

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
    }
}
