package hk.edu.hkmu.translate.script

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import hk.edu.hkmu.core.error.AiAndroidException

class ScriptConverter {

    @Suppress("TooGenericExceptionCaught")
    fun convert(text: String, config: ScriptConfig): String {
        return try {
            when (config) {
                ScriptConfig.T2S -> ZhConverterUtil.toSimple(text)
                ScriptConfig.S2T -> ZhConverterUtil.toTraditional(text)
                ScriptConfig.S2HK -> ZhHkConverterUtil.toTraditional(text)
                ScriptConfig.S2TW -> ZhTwConverterUtil.toTraditional(text)
            }
        } catch (e: RuntimeException) {
            throw AiAndroidException.ScriptConversionError(
                configId = config.openccConfig,
                cause = e
            )
        }
    }
}
