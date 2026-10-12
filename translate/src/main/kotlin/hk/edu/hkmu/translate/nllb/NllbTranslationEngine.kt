package hk.edu.hkmu.translate.nllb

import hk.edu.hkmu.core.config.TranslationConfig
import hk.edu.hkmu.core.engine.TranslatedText
import hk.edu.hkmu.core.engine.TranslationEngine
import hk.edu.hkmu.core.error.AiAndroidException
import hk.edu.hkmu.core.license.DefaultLicensePolicy
import hk.edu.hkmu.core.license.LicensePolicy
import hk.edu.hkmu.core.license.UsageIntent
import hk.edu.hkmu.core.native.DeviceCapabilities
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [TranslationEngine] over ONNX Runtime + NLLB-200-distilled-600M INT8 (Wave-0 S1 recipe).
 *
 * ### Encode / decode two-pass
 * Each input is encoded once (`[src_lang_id] ++ toHfId(pieces) ++ [eos]`) through the encoder graph,
 * then decoded with a non-cached greedy loop seeded `[bos, tgt_lang_id]`; the loop stops on `</s>`
 * or after [MAX_NEW_TOKENS]. Seeding with only the target language id would decode degenerately, so
 * the BOS seed is mandatory.
 *
 * ### Emission semantics
 * One [TranslatedText] is emitted per `translate` call, in order, then the flow completes. Sentence
 * segmentation is the caller's (pipeline's) responsibility — each call is one segment. Script
 * variant is selected through the target language tag (`zh-Hans`/`zh-Hant`); the 繁<->簡 OpenCC
 * conversion is a separate stage (`hk.edu.hkmu.translate.script.ScriptConverter`) and is not applied
 * here.
 *
 * ### Length limit
 * The graphs accept at most `runtime.contextWindowTokens` (512 by default) source positions. A source
 * whose id sequence (including the language token and `</s>`) exceeds it raises a typed
 * [AiAndroidException.InputTooLong] before the encoder runs.
 *
 * ### License
 * NLLB-200 is CC-BY-NC (non-commercial). The engine evaluates the shared [LicensePolicy] for the
 * host's [UsageIntent] at construction, so a `Commercial` host is refused with
 * [AiAndroidException.LicenseRestricted] instead of loading the model.
 *
 * ### Lifecycle
 * The tokenizer and the ONNX sessions are created lazily on first collection (never at construction),
 * so an engine can be built without loading ~1.1 GB of weights; [close] releases them.
 */
class NllbTranslationEngine internal constructor(
    private val config: TranslationConfig,
    private val tokenizerFactory: NllbTokenizerFactory,
    private val sessionFactory: NllbSessionFactory,
    private val licenseGate: NllbLicenseGate,
    private val dispatcher: CoroutineDispatcher
) : TranslationEngine, AutoCloseable {

    private val tokenizerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { tokenizerFactory.create() }
    private val sessionDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { sessionFactory.create() }

    private val sourceCode = NllbLanguageTags.toFloresCode(config.sourceLanguage)
    private val targetCode = NllbLanguageTags.toFloresCode(config.targetLanguage)
    private val maxInputTokens = config.runtime.contextWindowTokens

    init {
        licenseGate.ensureAllowed(MODEL_ID)
    }

    override fun translate(text: String): Flow<TranslatedText> = flow {
        val tokenizer = tokenizerDelegate.value
        val session = sessionDelegate.value
        val pieces = tokenizer.encodeToPieceIds(text)
        val sourceIds = NllbTokenCodec.buildSourceInput(tokenizer.languageId(sourceCode), pieces)
        if (sourceIds.size > maxInputTokens) {
            throw AiAndroidException.InputTooLong(sourceIds.size, maxInputTokens)
        }
        session.encode(sourceIds, LongArray(sourceIds.size) { ATTEND }).use { encoded ->
            val generated = greedyDecode(session, encoded, tokenizer.languageId(targetCode))
            val translated = tokenizer.decodeFromPieceIds(NllbTokenCodec.toPieceIds(generated))
            emit(TranslatedText(translated, config.sourceLanguage, config.targetLanguage))
        }
    }.flowOn(dispatcher)

    /** Releases the lazily created tokenizer and sessions. Idempotent. */
    override fun close() {
        if (sessionDelegate.isInitialized()) {
            sessionDelegate.value.close()
        }
        if (tokenizerDelegate.isInitialized()) {
            tokenizerDelegate.value.close()
        }
    }

    private fun greedyDecode(session: NllbSession, source: EncodedSource, targetLangId: Int): List<Int> {
        val decoderIds = ArrayList<Long>(MAX_NEW_TOKENS + PREFIX_TOKENS)
        decoderIds.add(NllbTokenCodec.BOS.toLong())
        decoderIds.add(targetLangId.toLong())
        val generated = ArrayList<Int>()
        val bound = minOf(MAX_NEW_TOKENS, maxInputTokens - decoderIds.size)
        var step = 0
        while (step < bound) {
            val next = session.nextToken(decoderIds.toLongArray(), source)
            if (next == NllbTokenCodec.EOS) {
                return generated
            }
            decoderIds.add(next.toLong())
            generated.add(next)
            step++
        }
        return generated
    }

    companion object {
        /** Stable id of the NLLB model this engine expects. */
        const val MODEL_ID: String = "venddair/nllb-200-distilled-600M-onnx"

        /** Default ONNX Runtime intra-op thread count. */
        const val DEFAULT_NUM_THREADS: Int = 1

        /** Upper bound on decoded new tokens per call. */
        const val MAX_NEW_TOKENS: Int = 256

        private const val PREFIX_TOKENS = 2
        private const val ATTEND = 1L

        /**
         * Creates an engine reading the INT8 model from [modelDir].
         *
         * The directory is validated on first use, before any native call; a missing graph or
         * tokenizer asset yields [AiAndroidException.ModelAssetMissing]. The CC-BY-NC license is
         * evaluated immediately: [intent] = [UsageIntent.Commercial] raises
         * [AiAndroidException.LicenseRestricted].
         */
        @Suppress("LongParameterList")
        fun create(
            config: TranslationConfig = TranslationConfig(),
            modelDir: File,
            licensePolicy: LicensePolicy = DefaultLicensePolicy(),
            intent: UsageIntent = UsageIntent.SelfUse,
            capabilities: DeviceCapabilities = DeviceCapabilities.CpuOnly,
            numThreads: Int = DEFAULT_NUM_THREADS
        ): NllbTranslationEngine = NllbTranslationEngine(
            config = config,
            tokenizerFactory = SentencePieceNllbTokenizerFactory(MODEL_ID, modelDir),
            sessionFactory = OnnxNllbSessionFactory(
                modelId = MODEL_ID,
                modelDir = modelDir,
                accelerator = config.runtime.accelerator,
                capabilities = capabilities,
                numThreads = numThreads
            ),
            licenseGate = NllbLicenseGate(licensePolicy, intent),
            dispatcher = Dispatchers.Default
        )

        /** Test seam: builds an engine over injected [tokenizer]/[session] seams (no native). */
        internal fun forTesting(
            config: TranslationConfig,
            tokenizer: NllbTokenizer,
            session: NllbSession,
            licenseGate: NllbLicenseGate = NllbLicenseGate(DefaultLicensePolicy(), UsageIntent.SelfUse),
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        ): NllbTranslationEngine {
            return NllbTranslationEngine(config, { tokenizer }, { session }, licenseGate, dispatcher)
        }
    }
}
