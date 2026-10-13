@file:Suppress("MagicNumber", "TooManyFunctions")

package hk.edu.hkmu.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import hk.edu.hkmu.demo.camera.CameraGestureActivity
import hk.edu.hkmu.demo.model.AssetProvisioner
import hk.edu.hkmu.demo.model.DemoModel
import hk.edu.hkmu.demo.model.ModelCatalog
import hk.edu.hkmu.demo.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val PAGE_PADDING_DP = 16
private const val LOG_TEXT_SIZE_SP = 13f
private const val TITLE_TEXT_SIZE_SP = 15f
private const val HEADER =
    "Real engines; big models download on demand. Use a \"Models\" button to check or fetch a " +
        "weight (sha256-verified), then a \"Scenarios\" button to run the real ASR / TTS / sign / " +
        "recombine / translation engines. Target: arm64 device."

/**
 * Single-screen demo for the ai-android library.
 *
 * The "Models" section gives each pinned artifact its own check-&-download button; the "Scenarios"
 * section runs the real engines plus the real licence gate, signed manifest and OpenCC converter.
 * Every result is appended to one scrollable log. The UI is deliberately plain.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var logView: TextView
    private lateinit var environment: DemoEnvironment
    private lateinit var modelManager: ModelManager
    private lateinit var assetProvisioner: AssetProvisioner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        environment = DemoEnvironment(this)
        modelManager = ModelManager(environment.modelsRoot)
        assetProvisioner = AssetProvisioner(assets, environment.modelsRoot)
        setContentView(buildContentView())
        provisionBundles()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        logView = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = LOG_TEXT_SIZE_SP
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(PAGE_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            addView(title(HEADER))
            addView(title("Models"))
            addView(modelButtons())
            addView(title("Scenarios"))
            addView(scenarioButtons())
            addView(title("Live camera"))
            addView(cameraButton())
            addView(logView)
        }
        return ScrollView(this).apply { addView(content) }
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = TITLE_TEXT_SIZE_SP
        setPadding(0, dp(PAGE_PADDING_DP), 0, dp(PAGE_PADDING_DP / 4))
    }

    private fun modelButtons(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ModelCatalog.all().forEach { model -> column.addView(modelButton(model)) }
        return column
    }

    private fun modelButton(model: DemoModel): Button {
        val button = Button(this)
        button.text = if (model.isBundled) "Check bundled: ${model.label}" else "Check & download: ${model.label}"
        button.setOnClickListener { runAction(model.label, button) { modelManager.checkAndDownload(model) } }
        return button
    }

    private fun scenarioButtons(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        DemoScenarios.all(environment).forEach { scenario -> column.addView(scenarioButton(scenario)) }
        return column
    }

    private fun scenarioButton(scenario: DemoScenario): Button {
        val button = Button(this)
        button.text = scenario.title
        button.setOnClickListener { runAction(scenario.title, button) { scenario.run() } }
        return button
    }

    private fun cameraButton(): Button {
        val button = Button(this)
        button.text = "Live — CameraX gesture recognizer (arm64 + camera)"
        button.setOnClickListener { startActivity(Intent(this, CameraGestureActivity::class.java)) }
        return button
    }

    private fun runAction(title: String, button: Button, action: suspend () -> String) {
        button.isEnabled = false
        appendLog("\n== $title ==")
        scope.launch {
            appendLog(runCatching { action() }.getOrElse { describe(it) })
            button.isEnabled = true
        }
    }

    private fun provisionBundles() {
        scope.launch {
            appendLog("\n== Bundled assets ==")
            appendLog(runCatching { assetProvisioner.provision(ModelCatalog.all()) }.getOrElse { describe(it) })
        }
    }

    private fun describe(error: Throwable): String = "${error.javaClass.simpleName}: ${error.message}"

    private fun appendLog(line: String) {
        logView.append(line)
        logView.append("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
