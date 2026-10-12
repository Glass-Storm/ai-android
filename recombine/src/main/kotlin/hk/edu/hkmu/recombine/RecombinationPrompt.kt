package hk.edu.hkmu.recombine

import hk.edu.hkmu.core.config.Language

/**
 * Builds the Qwen2.5-Instruct ChatML prompt that turns a sequence of recognized characters or
 * sign-language glosses into one fluent sentence.
 *
 * The recombination stage receives the ASR/sign output as a compact character/gloss sequence
 * (for example `我 愛 香 港` or `I LOVE HONG KONG`) and asks the model to assemble it into a single
 * grammatical sentence. Only the sentence is requested back.
 */
internal object RecombinationPrompt {

    fun build(input: String, language: Language): String = buildString {
        append(CONTROL_TOKEN_START)
        append("\n")
        append(instruction(language))
        append("\n")
        append(CONTROL_TOKEN_END)
        append("\n")
        append(CONTROL_TOKEN_START)
        append("user\n")
        append(input)
        append("\n")
        append(CONTROL_TOKEN_END)
        append("\n")
        append(CONTROL_TOKEN_START)
        append("assistant\n")
    }

    fun instruction(language: Language): String = when (language.tag) {
        Language.English.tag -> ASSEMBLE_ENGLISH
        Language.Cantonese.tag -> ASSEMBLE_CANTONESE
        Language.Mandarin.tag -> ASSEMBLE_MANDARIN
        Language.Japanese.tag -> ASSEMBLE_JAPANESE
        Language.Korean.tag -> ASSEMBLE_KOREAN
        else -> ASSEMBLE_UNKNOWN
    }

    private const val CONTROL_TOKEN_START = "<|im_start|>"
    private const val CONTROL_TOKEN_END = "<|im_end|>"

    private const val ASSEMBLE_ENGLISH =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent English sentence. Reply with the sentence only."
    private const val ASSEMBLE_CANTONESE =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent Cantonese sentence written in Traditional Chinese. Reply with the sentence only."
    private const val ASSEMBLE_MANDARIN =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent Mandarin sentence written in Simplified Chinese. Reply with the sentence only."
    private const val ASSEMBLE_JAPANESE =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent Japanese sentence. Reply with the sentence only."
    private const val ASSEMBLE_KOREAN =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent Korean sentence. Reply with the sentence only."
    private const val ASSEMBLE_UNKNOWN =
        "You reassemble a sequence of recognized characters or sign-language glosses into one " +
            "fluent sentence in the target language. Reply with the sentence only."
}
