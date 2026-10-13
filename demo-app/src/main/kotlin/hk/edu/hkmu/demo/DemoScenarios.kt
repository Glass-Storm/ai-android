package hk.edu.hkmu.demo

import hk.edu.hkmu.demo.scenario.AsrScenario
import hk.edu.hkmu.demo.scenario.GestureScenario
import hk.edu.hkmu.demo.scenario.LicenseScenario
import hk.edu.hkmu.demo.scenario.ManifestScenario
import hk.edu.hkmu.demo.scenario.RecombineScenario
import hk.edu.hkmu.demo.scenario.ScriptScenario
import hk.edu.hkmu.demo.scenario.SignVideoScenario
import hk.edu.hkmu.demo.scenario.TranslationScenario
import hk.edu.hkmu.demo.scenario.TtsScenario

/** The full scenario list, in the order the buttons appear. */
object DemoScenarios {
    /** Every scenario, each wired to a real library engine or capability. */
    fun all(environment: DemoEnvironment): List<DemoScenario> = listOf(
        DemoScenario("1. ASR — real SenseVoice (bundled WAV)") { AsrScenario.run(environment) },
        DemoScenario("2. TTS — real sherpa-onnx VITS (Cantonese)") { TtsScenario.run(environment) },
        DemoScenario("3. Sign — real MediaPipe + TGCN (bundled video)") { SignVideoScenario.run(environment) },
        DemoScenario("4. Recombine — real llama.cpp Qwen2.5-0.5B") { RecombineScenario.run(environment) },
        DemoScenario("5. Translation — real NLLB (expected to fail)") { TranslationScenario.run(environment) },
        DemoScenario("6. Licence gate (DefaultLicensePolicy)") { LicenseScenario.run() },
        DemoScenario("7. Signed manifest (tampered signature)") { ManifestScenario.run() },
        DemoScenario("8. Script conversion (OpenCC T2S / S2T)") { ScriptScenario.run() },
        DemoScenario("9. Gesture — MediaPipe static gestures (bundled video)") { GestureScenario.run(environment) }
    )
}
