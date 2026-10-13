package hk.edu.hkmu.demo.model

/** Hugging Face base URL for the SenseVoice ASR artifacts. */
private const val ASR_BASE =
    "https://huggingface.co/csukuangfj/" +
        "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/main"

/** Hugging Face base URL for the Cantonese VITS voice. */
private const val TTS_BASE = "https://huggingface.co/csukuangfj/vits-cantonese-hf-xiaomaiiwn/resolve/main"

/** Hugging Face base URL for the Qwen2.5-0.5B-Instruct GGUF. */
private const val LLM_BASE = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main"

/**
 * The pinned model set the demo downloads (large weights) or ships (small bundles).
 *
 * URLs and sha256 pins are the exact verified values; the file names matter because the engines
 * resolve models by glob/directory (ASR wants `model*.onnx` + `tokens.txt`; TTS wants
 * `<directoryName>/<onnxFile>`; the sign classifier wants `tgcn-wlasl-asl100/…tflite`). The bundled
 * rows are provisioned into `filesDir/models` from the APK assets on first run.
 */
object ModelCatalog {

    /** SenseVoice int8 ONNX graph — matches the ASR `model*.onnx` glob. */
    val ASR_MODEL = DemoModel(
        label = "ASR · SenseVoice model.int8.onnx",
        relativePath = "asr/model.int8.onnx",
        url = "$ASR_BASE/model.int8.onnx",
        sha256 = "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d"
    )

    /** SenseVoice token table. */
    val ASR_TOKENS = DemoModel(
        label = "ASR · tokens.txt",
        relativePath = "asr/tokens.txt",
        url = "$ASR_BASE/tokens.txt",
        sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
    )

    /** Cantonese VITS acoustic model. */
    val TTS_ONNX = DemoModel(
        label = "TTS · vits-cantonese-hf-xiaomaiiwn.onnx",
        relativePath = "tts/vits-cantonese-hf-xiaomaiiwn/vits-cantonese-hf-xiaomaiiwn.onnx",
        url = "$TTS_BASE/vits-cantonese-hf-xiaomaiiwn.onnx",
        sha256 = "7d8d4f5550b607999417a99131b034c8cc2b8dca69f9e9fdefeff6655643c139"
    )

    /** Cantonese VITS lexicon. */
    val TTS_LEXICON = DemoModel(
        label = "TTS · lexicon.txt",
        relativePath = "tts/vits-cantonese-hf-xiaomaiiwn/lexicon.txt",
        url = "$TTS_BASE/lexicon.txt",
        sha256 = "337845cd7ea6221e69684329beda84198dc8678699a4936670a08a9449c84acb"
    )

    /** Cantonese VITS text-normalization FST. */
    val TTS_RULE_FST = DemoModel(
        label = "TTS · rule.fst",
        relativePath = "tts/vits-cantonese-hf-xiaomaiiwn/rule.fst",
        url = "$TTS_BASE/rule.fst",
        sha256 = "743f402181fcfebf76cc2f0546b71fa26476e626fbe4e460fb7b4c3a7a8bd5bd"
    )

    /** Cantonese VITS token table. */
    val TTS_TOKENS = DemoModel(
        label = "TTS · tokens.txt",
        relativePath = "tts/vits-cantonese-hf-xiaomaiiwn/tokens.txt",
        url = "$TTS_BASE/tokens.txt",
        sha256 = "e6c853092480206c9611101c1eb82d930f07d79e1d399bf9ca5b6acfaf443af3"
    )

    /** Qwen2.5-0.5B-Instruct GGUF for the recombination stage (note the lowercase file name). */
    val LLM_GGUF = DemoModel(
        label = "Recombine · qwen2.5-0.5b-instruct-q4_k_m.gguf",
        relativePath = "llm/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        url = "$LLM_BASE/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    )

    /** TGCN ASL-100 sign classifier, shipped in the APK. */
    val SIGN_CLASSIFIER = DemoModel(
        label = "Sign · tgcn_asl100_float32.tflite (bundled)",
        relativePath = "sign/classifiers/tgcn-wlasl-asl100/tgcn_asl100_float32.tflite",
        bundledAssetPath = "sign/tgcn-wlasl-asl100/tgcn_asl100_float32.tflite"
    )

    /** MediaPipe holistic landmarker bundle, shipped in the APK. */
    val SIGN_LANDMARKER = DemoModel(
        label = "Sign · holistic_landmarker.task (bundled)",
        relativePath = "sign/landmarker/holistic_landmarker.task",
        bundledAssetPath = "landmarker/holistic_landmarker.task"
    )

    /** MediaPipe static-gesture recognizer bundle, shipped in the APK. */
    val SIGN_GESTURE_RECOGNIZER = DemoModel(
        label = "Sign · gesture_recognizer.task (bundled)",
        relativePath = "gesture/gesture_recognizer.task",
        bundledAssetPath = "gesture/gesture_recognizer.task"
    )

    /** Bundled Cantonese demo clip for the ASR scenario. */
    val AUDIO_DEMO = DemoModel(
        label = "ASR · demo_yue.wav (bundled)",
        relativePath = "assets/demo_yue.wav",
        bundledAssetPath = "audio/demo_yue.wav"
    )

    /** Bundled sign demo clip for the sign-video scenario. */
    val VIDEO_DEMO = DemoModel(
        label = "Sign · demo_sign.mp4 (bundled)",
        relativePath = "assets/demo_sign.mp4",
        bundledAssetPath = "video/demo_sign.mp4"
    )

    /** Every row, downloadable and bundled, in the order the buttons appear. */
    fun all(): List<DemoModel> = listOf(
        ASR_MODEL,
        ASR_TOKENS,
        TTS_ONNX,
        TTS_LEXICON,
        TTS_RULE_FST,
        TTS_TOKENS,
        LLM_GGUF,
        SIGN_CLASSIFIER,
        SIGN_LANDMARKER,
        SIGN_GESTURE_RECOGNIZER,
        AUDIO_DEMO,
        VIDEO_DEMO
    )
}
