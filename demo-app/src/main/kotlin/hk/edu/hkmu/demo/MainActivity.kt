@file:Suppress("MagicNumber")

package hk.edu.hkmu.demo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val PAGE_PADDING_DP = 16
private const val LOG_TEXT_SIZE_SP = 13f
private const val HEADER =
    "Engines: test-kit fakes (no model bundled). " +
        "Licence gate, signed manifest, pipelines and OpenCC are the real library code."

/**
 * Single-screen demo for the ai-android library.
 *
 * A header explains what is real, then one button per scenario appends a readable result to a
 * scrollable log. Every scenario runs the library's real code; only the engines are test-kit fakes,
 * so the app needs no model, no native runtime and no network. The UI is deliberately plain.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
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

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        DemoScenarios.all().forEach { scenario -> buttons.addView(scenarioButton(scenario)) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(PAGE_PADDING_DP)
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@MainActivity).apply { text = HEADER })
            addView(buttons)
            addView(logView)
        }
        return ScrollView(this).apply { addView(content) }
    }

    private fun scenarioButton(scenario: DemoScenario): Button = Button(this).apply {
        text = scenario.title
        setOnClickListener { runScenario(scenario, this) }
    }

    private fun runScenario(scenario: DemoScenario, button: Button) {
        button.isEnabled = false
        appendLog("\n== ${scenario.title} ==")
        scope.launch {
            val outcome = runCatching { scenario.run() }
                .getOrElse { error -> "ERROR ${error.javaClass.simpleName}: ${error.message}" }
            appendLog(outcome)
            button.isEnabled = true
        }
    }

    private fun appendLog(line: String) {
        logView.append(line)
        logView.append("\n")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
