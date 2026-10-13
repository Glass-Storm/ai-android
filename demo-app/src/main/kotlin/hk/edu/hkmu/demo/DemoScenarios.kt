package hk.edu.hkmu.demo

import hk.edu.hkmu.demo.scenario.ColdFlowScenario
import hk.edu.hkmu.demo.scenario.LicenseScenario
import hk.edu.hkmu.demo.scenario.ManifestScenario
import hk.edu.hkmu.demo.scenario.ScriptScenario
import hk.edu.hkmu.demo.scenario.SignScenario
import hk.edu.hkmu.demo.scenario.SpeechScenario

/** The full scenario list, in the order the buttons appear. */
object DemoScenarios {
    /** Every scenario the demo offers, each wired to a real library capability. */
    fun all(): List<DemoScenario> = listOf(
        DemoScenario("1. Licence gate (DefaultLicensePolicy)") { LicenseScenario.run() },
        DemoScenario("2. Signed manifest (tampered signature)") { ManifestScenario.run() },
        DemoScenario("3. Speech pipeline (ASR > recombine > translate)") { SpeechScenario.run() },
        DemoScenario("4. Sign pipeline (sign > recombine)") { SignScenario.run() },
        DemoScenario("5. Script conversion (OpenCC T2S / S2T)") { ScriptScenario.run() },
        DemoScenario("6. Cold flow (lazy until collected)") { ColdFlowScenario.run() }
    )
}
