package hk.edu.hkmu.core

/**
 * The shipped sign-language capability ceiling.
 *
 * This type carries the single authoritative statement of what the sign-reading pipeline can and
 * cannot do, so the limit is rendered as its own page in the generated API reference rather than
 * being left implicit in the engine contracts.
 *
 * sign reading outputs letters and isolated signs only and does not translate continuous signing sentences
 *
 * Self-use builds reach fingerspelling and isolated ASL glosses; commercial builds reach only the
 * commercially-licensed fingerspelling model. Continuous Cantonese Sign Language (CSL) is a
 * bring-your-own (BYO) model slot and no CSL model ships.
 */
object SignCapabilityCeiling
