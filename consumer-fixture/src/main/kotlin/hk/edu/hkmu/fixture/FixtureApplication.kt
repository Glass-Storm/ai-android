package hk.edu.hkmu.fixture

import android.app.Application

/**
 * UNPUBLISHED consumer-fixture application.
 *
 * This is **not** a sample app: it is never shipped, installed, or documented as usage. Its only
 * job is to force the public API and the test-kit fakes to be resolved, compiled, and minified as a
 * real consumer of the library.
 */
class FixtureApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installed = FixtureApiGraph.wire()
    }

    companion object {
        /** The wired graph, or null before startup. Retained so R8 cannot drop the wiring. */
        @Volatile
        var installed: FixtureWiring? = null
            private set
    }
}
