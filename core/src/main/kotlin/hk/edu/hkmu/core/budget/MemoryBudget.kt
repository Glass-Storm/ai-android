package hk.edu.hkmu.core.budget

/**
 * Per-feature **peak resident PSS** budgets, copied verbatim from the plan's Budgets table
 * (`.omo/plans/ondevice-sign-asr-tts.md`, section "Budgets"). These figures are the numeric
 * acceptance targets: a spike may tighten one, never loosen it without a written note.
 *
 * [maxBytes] treats "MB" as a decimal megabyte ([MEGABYTE]); the Tier B device harness reads the
 * process PSS in KiB and compares the peak against these ceilings. This type is deliberately free of
 * Android/engine references so it can be used by the host-JVM (Tier A) instrumentation and by the
 * on-device (Tier B) harness unchanged.
 *
 * @property feature Human-readable label used in reports and evidence logs.
 * @property maxMegabytes Ceiling in decimal megabytes (the Budgets-table value).
 */
enum class MemoryBudget(val feature: String, val maxMegabytes: Long) {
    /** Peak PSS with the ASR (SenseVoice) engine resident. */
    Asr("ASR", ASR_MAX_MB),

    /** Peak PSS with the TTS (VITS) engine resident. */
    Tts("TTS", TTS_MAX_MB),

    /** Peak PSS with the NLLB translation engine resident. */
    Translation("Translation (NLLB)", TRANSLATION_MAX_MB),

    /** Peak PSS with the sign landmarker + classifier resident. */
    Sign("Sign", SIGN_MAX_MB),

    /** Peak PSS with the recombination LLM loaded. */
    Recombination("Recombination LLM", RECOMBINATION_MAX_MB),

    /** Peak PSS with translation and recombination both resident (the worst case). */
    WorstCase("worst case (translate + recombine)", WORST_CASE_MAX_MB);

    /** [maxMegabytes] expressed in bytes (decimal megabytes). */
    val maxBytes: Long get() = maxMegabytes * MEGABYTE

    companion object {
        /** Bytes in one decimal megabyte. */
        const val MEGABYTE: Long = 1_000_000L
    }
}

private const val ASR_MAX_MB = 400L
private const val TTS_MAX_MB = 250L
private const val TRANSLATION_MAX_MB = 1600L
private const val SIGN_MAX_MB = 250L
private const val RECOMBINATION_MAX_MB = 600L
private const val WORST_CASE_MAX_MB = 2200L
