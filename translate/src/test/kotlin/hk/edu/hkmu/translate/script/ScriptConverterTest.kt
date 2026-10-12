package hk.edu.hkmu.translate.script

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptConverterTest {

    private val converter = ScriptConverter()

    @Test
    fun t2s_converts_traditional_to_simplified() {
        val result = converter.convert("軟體", ScriptConfig.T2S)
        assertEquals("软体", result)
    }

    @Test
    fun s2t_converts_simplified_to_traditional() {
        val result = converter.convert("软件", ScriptConfig.S2T)
        assertEquals("軟件", result)
    }

    @Test
    fun s2hk_converts_simplified_to_hk_variant() {
        val result = converter.convert("软件", ScriptConfig.S2HK)
        assertEquals("軟件", result)
    }

    @Test
    fun s2tw_converts_simplified_to_taiwan_variant() {
        val result = converter.convert("软件", ScriptConfig.S2TW)
        assertEquals("軟體", result)
    }

    @Test
    fun round_trip_identity_preserves_hk_unchanged_text() {
        val hk = "香港"
        val simplified = converter.convert(hk, ScriptConfig.T2S)
        assertEquals("香港", simplified)
        val back = converter.convert(simplified, ScriptConfig.S2T)
        assertEquals("香港", back)
    }

    @Test
    fun s2hk_hk_variant_differs_from_s2t_for_taiwan_word() {
        val simp = "台湾"
        assertEquals("台灣", converter.convert(simp, ScriptConfig.S2HK))
        assertEquals("臺灣", converter.convert(simp, ScriptConfig.S2TW))
    }
}
