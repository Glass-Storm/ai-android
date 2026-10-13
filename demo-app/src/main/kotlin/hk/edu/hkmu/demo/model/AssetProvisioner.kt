package hk.edu.hkmu.demo.model

import android.content.res.AssetManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PRESENT = "present"
private const val MISSING_HINT = " (the file is gitignored; stage it under src/main/assets and rebuild)"

/**
 * Copies the small artifacts shipped inside the APK into the model root, so the real engines (which
 * read ordinary files) can consume them.
 *
 * Idempotent: a file already present is left untouched. A bundled asset that is absent from the APK
 * (e.g. the demo video before it is staged) is reported rather than throwing.
 *
 * @property assets Source [AssetManager].
 * @property root Model root (`filesDir/models`).
 */
class AssetProvisioner(private val assets: AssetManager, private val root: File) {

    /** Copies every bundled row and returns a per-row transcript. */
    suspend fun provision(models: List<DemoModel>): String = withContext(Dispatchers.IO) {
        models.filter { it.isBundled }.joinToString("\n") { copyBundled(it) }
    }

    private fun copyBundled(model: DemoModel): String {
        val assetPath = requireNotNull(model.bundledAssetPath) { "bundled model ${model.label} has no asset" }
        val target = File(root, model.relativePath)
        if (target.isFile) {
            return "$assetPath -> $PRESENT"
        }
        return try {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            "$assetPath -> provisioned (${target.length()} bytes)"
        } catch (missing: IOException) {
            "$assetPath -> not in APK (${missing.message})$MISSING_HINT"
        }
    }
}
