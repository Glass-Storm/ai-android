package hk.edu.hkmu.translate.script

/**
 * OpenCC configuration identifiers for traditional↔simplified Chinese script conversion.
 *
 * | Config | Description |
 * |--------|-------------|
 * | `t2s`  | Traditional Chinese → Simplified Chinese |
 * | `s2t`  | Simplified Chinese → Traditional Chinese |
 * | `s2hk` | Simplified Chinese → Hong Kong variant |
 * | `s2tw` | Simplified Chinese → Traditional Chinese (Taiwan variant) |
 */
enum class ScriptConfig(val openccConfig: String) {
    T2S("t2s"),
    S2T("s2t"),
    S2HK("s2hk"),
    S2TW("s2tw")
}
