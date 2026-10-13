package hk.edu.hkmu.demo

/**
 * One demo scenario: the [title] shown on its button plus a [body] that runs real library code and
 * renders a readable result string.
 *
 * @property title Button label.
 * @property body Suspend body executed when the button is tapped.
 */
class DemoScenario(
    val title: String,
    private val body: suspend () -> String
) {
    /** Runs the scenario and returns its rendered result. */
    suspend fun run(): String = body()
}
