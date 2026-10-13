package hk.edu.hkmu.sign.gesture

import android.graphics.Bitmap
import hk.edu.hkmu.core.registry.ModelHandle

/**
 * [ModelHandle] owning one native static-gesture recognizer.
 *
 * The shared [hk.edu.hkmu.core.registry.ModelRegistry] owns this handle for the whole residency of
 * the model and calls [close] exactly once; the registry's license gate has already run before
 * [hk.edu.hkmu.core.registry.ModelLoader.load] creates it.
 *
 * @param backend The native-backed gesture backend this handle pins.
 */
internal class GestureModelHandle(
    private val backend: GestureBackend
) : ModelHandle {

    /** Runs the static gesture recognizer on one image. */
    fun recognize(image: Bitmap): GestureBackendResult = backend.recognize(image)

    /** Frees the native recognizer. Called exactly once per residency. */
    override fun close() {
        backend.close()
    }
}
