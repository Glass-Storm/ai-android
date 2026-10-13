package hk.edu.hkmu.demo

import android.content.Context
import java.io.File

/** Directory under `filesDir` that holds every downloaded or provisioned model. */
const val MODELS_DIR_NAME: String = "models"

/**
 * Absolute model locations shared by the engine scenarios.
 *
 * Every artifact lives under `context.filesDir/models/...`, which is exactly the layout the model
 * manager downloads into and the asset provisioner copies the bundled artifacts into, so a scenario
 * and its "Models" row always agree on the path.
 *
 * @property context Application context for the MediaPipe landmarker (the engines read real files,
 *   not assets, so no `AssetManager` is needed here).
 * @property modelsRoot Root of every model.
 */
class DemoEnvironment(context: Context) {
    val context: Context = context.applicationContext
    val modelsRoot: File = File(this.context.filesDir, MODELS_DIR_NAME)

    /** Absolute path under the model root, e.g. `dir("asr")` or `dir("sign", "landmarker")`. */
    fun dir(vararg segments: String): File = File(modelsRoot, segments.joinToString("/"))
}
