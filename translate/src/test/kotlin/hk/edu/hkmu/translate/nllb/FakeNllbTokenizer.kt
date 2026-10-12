package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.Language
import hk.edu.hkmu.core.error.AiAndroidException

/**
 * Tier A fake for the [NllbTokenizer] seam.
 *
 * Encodes each character to its code point and back, and exposes the real NLLB language ids, so the
 * engine's source layout and round-trip can be asserted without SentencePiece or a model.
 */
internal class FakeNllbTokenizer(
    private val langIds: Map<String, Int> = mapOf(
        NllbLanguageTags.ENGLISH to 256047,
        NllbLanguageTags.MANDARIN_SIMPLIFIED to 256200,
        NllbLanguageTags.MANDARIN_TRADITIONAL to 256201,
        NllbLanguageTags.CANTONESE to 256199
    )
) : NllbTokenizer {

    /** True once [close] has been called. */
    var closed: Boolean = false
        private set

    override fun languageId(floresCode: String): Int =
        langIds[floresCode] ?: throw AiAndroidException.UnsupportedLanguage(Language(floresCode))

    override fun encodeToPieceIds(text: String): IntArray = text.map { it.code }.toIntArray()

    override fun decodeFromPieceIds(pieceIds: IntArray): String =
        pieceIds.map { it.toChar() }.joinToString(separator = "")

    override fun close() {
        closed = true
    }
}
