@file:Suppress("MagicNumber")

package hk.edu.hkmu.sign.gesture

import android.graphics.Bitmap
import hk.edu.hkmu.core.registry.ModelLoader
import hk.edu.hkmu.core.registry.ModelRegistration

/** A [GestureBackend] that never touches MediaPipe native. */
internal class FakeGestureBackend(
    private val result: GestureBackendResult = GestureBackendResult("", 0f, null, 0)
) : GestureBackend {

    var recognizeCount: Int = 0
        private set

    var closed: Boolean = false
        private set

    override fun recognize(image: Bitmap): GestureBackendResult {
        recognizeCount++
        return result
    }

    override fun close() {
        closed = true
    }
}

/** A [ModelLoader] recording whether it was ever invoked (used to prove the license gate). */
internal class RecordingGestureLoader(
    private val backend: GestureBackend = FakeGestureBackend()
) : ModelLoader<GestureModelHandle> {

    var loadCount: Int = 0
        private set

    override fun load(registration: ModelRegistration): GestureModelHandle {
        loadCount++
        return GestureModelHandle(backend)
    }
}
