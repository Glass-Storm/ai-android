// Production JNI bridge: streams llama.cpp tokens to Kotlin for the recombination
// post-processor (todo 17).
//
// Bound class: hk.edu.hkmu.recombine.NativeLlama (Kotlin object, @JvmStatic external funs)
// Callback:    hk.edu.hkmu.recombine.TokenCallback.onToken(Ljava/lang/String;)V
//
// Lifecycle is owned by the Kotlin layer: [LlamaPostProcessor] keeps a `0` handle until the
// first `process()` call and frees it via `free()` on `unload()`. Nothing here is eager.

#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define RECOMBINE_LOG(...) __android_log_print(ANDROID_LOG_INFO, "recombine", __VA_ARGS__)
#else
#define RECOMBINE_LOG(...)                        \
    do {                                          \
        fprintf(stderr, __VA_ARGS__);             \
        fprintf(stderr, "\n");                    \
        fflush(stderr);                           \
    } while (0)
#endif

namespace {

struct LlamaHandle {
    llama_model *   model = nullptr;
    llama_context * ctx   = nullptr;
};

// Length of the longest valid UTF-8 prefix of `s` (whole string if fully valid).
// A multi-byte codepoint can be split across decode steps; only whole codepoints are
// forwarded so the Java String never carries a broken UTF-8 sequence.
size_t utf8_valid_prefix(const std::string & s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = 0;
        if (c < 0x80) {
            len = 1;
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;
        } else {
            return i; // invalid lead byte
        }
        if (i + len > n) {
            return i; // incomplete trailing codepoint
        }
        for (size_t k = 1; k < len; ++k) {
            if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) {
                return i;
            }
        }
        i += len;
    }
    return n;
}

// Forwards one whole-UTF-8 piece to the Kotlin callback. Returns false when the
// callback threw (e.g. the collector cancelled), so the caller can abort promptly.
bool emit(JNIEnv * env, jobject cb, jmethodID mid, const std::string & chunk) {
    if (cb == nullptr || mid == nullptr || chunk.empty()) {
        return true;
    }
    jstring js = env->NewStringUTF(chunk.c_str());
    if (js == nullptr) {
        env->ExceptionClear();
        return true;
    }
    env->CallVoidMethod(cb, mid, js);
    env->DeleteLocalRef(js);
    return !env->ExceptionCheck();
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_hk_edu_hkmu_recombine_NativeLlama_backendInit(JNIEnv *, jclass) {
    llama_backend_init();
}

extern "C" JNIEXPORT void JNICALL
Java_hk_edu_hkmu_recombine_NativeLlama_backendFree(JNIEnv *, jclass) {
    llama_backend_free();
}

// Returns an opaque handle (>0) on success, 0 on failure (Kotlin -> ModelLoadFailed).
extern "C" JNIEXPORT jlong JNICALL
Java_hk_edu_hkmu_recombine_NativeLlama_load(JNIEnv * env, jclass, jstring jpath, jint nCtx) {
    if (jpath == nullptr) {
        return 0;
    }
    const char * cpath = env->GetStringUTFChars(jpath, nullptr);
    if (cpath == nullptr) {
        return 0;
    }

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU-only; the Vulkan tier is not compiled into this build.

    llama_model * model = llama_model_load_from_file(cpath, mp);
    env->ReleaseStringUTFChars(jpath, cpath);

    if (model == nullptr) {
        RECOMBINE_LOG("[recombine] model load FAILED (not a valid GGUF?)");
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx    = static_cast<uint32_t>(nCtx > 0 ? nCtx : 512);
    cp.n_batch  = 512;
    cp.n_ubatch = 512;
    cp.no_perf  = true;

    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        RECOMBINE_LOG("[recombine] context init FAILED");
        llama_model_free(model);
        return 0;
    }

    LlamaHandle * h = new LlamaHandle();
    h->model = model;
    h->ctx   = ctx;
    RECOMBINE_LOG("[recombine] model loaded, handle=%p", static_cast<void *>(h));
    return static_cast<jlong>(reinterpret_cast<intptr_t>(h));
}

// Streams up to maxTokens pieces through callback.onToken(). Returns the number of
// tokens produced, or a negative error code. `temperature <= 0` selects deterministic
// greedy decoding; otherwise a seeded temperature+dist sampler chain (reproducible).
extern "C" JNIEXPORT jint JNICALL
Java_hk_edu_hkmu_recombine_NativeLlama_generate(JNIEnv * env, jclass, jlong handle,
                                                jstring jprompt, jint maxTokens,
                                                jint seed, jfloat temperature,
                                                jobject callback) {
    if (handle == 0) {
        return -1;
    }
    LlamaHandle * h = reinterpret_cast<LlamaHandle *>(static_cast<intptr_t>(handle));

    const char * cprompt = env->GetStringUTFChars(jprompt, nullptr);
    if (cprompt == nullptr) {
        return -2;
    }
    const std::string prompt(cprompt);
    env->ReleaseStringUTFChars(jprompt, cprompt);

    const llama_vocab * vocab = llama_model_get_vocab(h->model);

    // Tokenize (add_special = true, parse_special = true so the Qwen <|im_start|> ChatML
    // template tokens resolve to their vocab ids).
    const int n_prompt = -llama_tokenize(vocab, prompt.c_str(),
                                         static_cast<int32_t>(prompt.size()),
                                         nullptr, 0, true, true);
    if (n_prompt <= 0) {
        return -3;
    }
    std::vector<llama_token> toks(static_cast<size_t>(n_prompt));
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                       toks.data(), n_prompt, true, true) < 0) {
        return -3;
    }

    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    sp.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    if (temperature <= 0.0f) {
        // Deterministic: greedy is seed-independent and byte-reproducible.
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }

    jclass cbCls = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbCls, "onToken", "(Ljava/lang/String;)V");
    if (onToken == nullptr) {
        llama_sampler_free(smpl);
        return -5;
    }

    llama_memory_clear(llama_get_memory(h->ctx), true);

    llama_batch batch = llama_batch_get_one(toks.data(), n_prompt);
    if (llama_decode(h->ctx, batch) != 0) {
        llama_sampler_free(smpl);
        return -4;
    }

    std::string pending;
    int produced = 0;
    for (int i = 0; i < maxTokens; ++i) {
        const llama_token id = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }
        char buf[256];
        const int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n <= 0) {
            break;
        }
        pending.append(buf, static_cast<size_t>(n));

        const size_t valid = utf8_valid_prefix(pending);
        if (valid > 0) {
            if (!emit(env, callback, onToken, pending.substr(0, valid))) {
                break; // collector cancelled; stop generating
            }
            pending.erase(0, valid);
        }
        ++produced;

        llama_token next = id;
        llama_batch nb = llama_batch_get_one(&next, 1);
        if (llama_decode(h->ctx, nb) != 0) {
            break;
        }
    }
    if (!pending.empty()) {
        emit(env, callback, onToken, pending);
    }

    llama_sampler_free(smpl);
    return produced;
}

extern "C" JNIEXPORT void JNICALL
Java_hk_edu_hkmu_recombine_NativeLlama_free(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) {
        return;
    }
    LlamaHandle * h = reinterpret_cast<LlamaHandle *>(static_cast<intptr_t>(handle));
    if (h->ctx != nullptr) {
        llama_free(h->ctx);
    }
    if (h->model != nullptr) {
        llama_model_free(h->model);
    }
    delete h;
    RECOMBINE_LOG("[recombine] handle freed");
}
