// Minimalny most JNI do llama.cpp: wczytanie modelu GGUF i generowanie odpowiedzi
// na jedną wiadomość (z opcjonalną instrukcją systemową). Całość działa lokalnie.

#include <jni.h>

#include <algorithm>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "gwarek-llm", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "gwarek-llm", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::string tmpl;
    int n_ctx = 0;
};

std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    // GetStringUTFChars zwraca zmodyfikowane UTF-8, które dla znaków BMP (w tym polskich) jest zgodne z UTF-8.
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::string apply_template(const Engine &e, const std::string &system, const std::string &user) {
    std::vector<llama_chat_message> msgs;
    if (!system.empty()) msgs.push_back({"system", system.c_str()});
    msgs.push_back({"user", user.c_str()});
    std::vector<char> buf((system.size() + user.size()) * 2 + 512);
    int32_t n = llama_chat_apply_template(e.tmpl.c_str(), msgs.data(), msgs.size(), true,
                                          buf.data(), (int32_t) buf.size());
    if (n > (int32_t) buf.size()) {
        buf.resize(n + 1);
        n = llama_chat_apply_template(e.tmpl.c_str(), msgs.data(), msgs.size(), true,
                                      buf.data(), (int32_t) buf.size());
    }
    if (n < 0) {
        // Szablon nieobsługiwany – prosty format ChatML jako awaryjny.
        std::string p;
        if (!system.empty()) p += "<|im_start|>system\n" + system + "<|im_end|>\n";
        p += "<|im_start|>user\n" + user + "<|im_end|>\n<|im_start|>assistant\n";
        return p;
    }
    return std::string(buf.data(), n);
}

std::string generate(Engine &e, const std::string &prompt, int max_tokens, float temperature) {
    llama_memory_clear(llama_get_memory(e.ctx), true);

    int32_t n_prompt = -llama_tokenize(e.vocab, prompt.c_str(), (int32_t) prompt.size(),
                                       nullptr, 0, true, true);
    if (n_prompt <= 0) return {};
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(e.vocab, prompt.c_str(), (int32_t) prompt.size(),
                       tokens.data(), n_prompt, true, true) < 0) {
        return {};
    }
    if (n_prompt + max_tokens > e.n_ctx) {
        LOGE("prompt za długi: %d tokenów", n_prompt);
        return {};
    }

    if (llama_decode(e.ctx, llama_batch_get_one(tokens.data(), n_prompt)) != 0) {
        LOGE("llama_decode(prompt) nie powiodło się");
        return {};
    }

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string out;
    char piece[256];
    for (int i = 0; i < max_tokens; ++i) {
        llama_token tok = llama_sampler_sample(smpl, e.ctx, -1);
        if (llama_vocab_is_eog(e.vocab, tok)) break;
        int32_t n = llama_token_to_piece(e.vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) out.append(piece, n);
        if (out.find("<|im_end|>") != std::string::npos) {
            out.erase(out.find("<|im_end|>"));
            break;
        }
        if (llama_decode(e.ctx, llama_batch_get_one(&tok, 1)) != 0) break;
    }
    llama_sampler_free(smpl);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_gwarek_aac_ai_LlamaBridge_nativeLoad(JNIEnv *env, jobject, jstring path, jint n_ctx, jint n_threads) {
    static bool backend_ready = false;
    if (!backend_ready) {
        llama_backend_init();
        backend_ready = true;
    }
    std::string p = jstring_to_std(env, path);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(p.c_str(), mp);
    if (model == nullptr) {
        LOGE("nie udało się wczytać modelu: %s", p.c_str());
        return 0;
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) n_ctx;
    cp.n_batch = (uint32_t) n_ctx;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }
    auto *e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    const char *t = llama_model_chat_template(model, nullptr);
    e->tmpl = t != nullptr ? t : "chatml";
    e->n_ctx = n_ctx;
    LOGI("model wczytany, kontekst %d, wątki %d", n_ctx, n_threads);
    return reinterpret_cast<jlong>(e);
}

/** Zwraca bajty UTF-8 (nie String), bo JNI NewStringUTF nie obsługuje znaków spoza BMP. */
JNIEXPORT jbyteArray JNICALL
Java_org_gwarek_aac_ai_LlamaBridge_nativeGenerate(JNIEnv *env, jobject, jlong handle,
                                                  jstring system, jstring user,
                                                  jint max_tokens, jfloat temperature) {
    auto *e = reinterpret_cast<Engine *>(handle);
    if (e == nullptr) return nullptr;
    std::string prompt = apply_template(*e, jstring_to_std(env, system), jstring_to_std(env, user));
    std::string out = generate(*e, prompt, max_tokens, temperature);
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return arr;
}

JNIEXPORT void JNICALL
Java_org_gwarek_aac_ai_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *e = reinterpret_cast<Engine *>(handle);
    if (e == nullptr) return;
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

}  // extern "C"
