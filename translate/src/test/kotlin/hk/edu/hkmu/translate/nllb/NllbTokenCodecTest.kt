@file:Suppress("MagicNumber")

package hk.edu.hkmu.translate.nllb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tier A acceptance for the fairseq offset and the source/decoder token layout. */
class NllbTokenCodecTest {

    @Test
    fun `toHfId applies the fairseq offset and remaps the specials`() {
        assertEquals(3, NllbTokenCodec.toHfId(0))
        assertEquals(0, NllbTokenCodec.toHfId(1))
        assertEquals(2, NllbTokenCodec.toHfId(2))
        assertEquals(5, NllbTokenCodec.toHfId(4))
        assertEquals(101, NllbTokenCodec.toHfId(100))
    }

    @Test
    fun `buildSourceInput prefixes the language id and appends eos`() {
        val input = NllbTokenCodec.buildSourceInput(256_047, intArrayOf(4, 5))

        assertEquals(listOf(256_047L, 5L, 6L, 2L), input.toList())
    }

    @Test
    fun `toPieceIds drops specials and undoes the offset`() {
        val pieces = NllbTokenCodec.toPieceIds(listOf(3, 5, 6, 2))

        assertEquals(listOf(4, 5), pieces.toList())
    }
}
