package hk.edu.hkmu.sign.model

import hk.edu.hkmu.core.config.Language

/**
 * Sign-language identifiers used by the sign module.
 *
 * These are [Language] tags, not spoken-language tags: they name the *sign* language a classifier
 * serves so a host can ask for "ASL" or "CSL" without the spoken-language vocabulary of the ASR/TTS
 * engines. The set is open-ended — a host may serve any other sign language by registering a BYO
 * model under its own tag.
 */
object SignLanguages {
    /** American Sign Language. Served by the shipped TGCN (isolated signs) and SignBridge (fingerspelling). */
    val Asl: Language = Language("asl")

    /** Chinese Sign Language. No model is shipped; only the BYO slot is exposed. */
    val Csl: Language = Language("csl")
}
