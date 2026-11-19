#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>

#define LOG_TAG "DigitalLifeLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if HAS_LLAMA_CPP
#include "llama.h"
#endif

static std::atomic<bool> g_cancel_requested{false};

#if HAS_LLAMA_CPP
struct LlamaContextWrapper {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    int n_ctx = 4096;
    bool gpu_offload = false;
};
#endif

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeIsCompiledWithLlama(JNIEnv*, jobject) {
#if HAS_LLAMA_CPP
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeGetRuntimeVersion(JNIEnv* env, jobject) {
#if HAS_LLAMA_CPP
    return env->NewStringUTF("llama.cpp (ARM64 JNI)");
#else
    return env->NewStringUTF("llama.cpp (NOT LINKED)");
#endif
}

JNIEXPORT jlong JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeLoadModel(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx, jint nThreads, jint nGpuLayers) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s (ctx=%d, threads=%d, gpuLayers=%d)", path, nCtx, nThreads, nGpuLayers);

#if HAS_LLAMA_CPP
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    llama_model* model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load GGUF model");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(nCtx);
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama_context");
        llama_model_free(model);
        return 0;
    }

    auto* wrapper = new LlamaContextWrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->vocab = llama_model_get_vocab(model);
    wrapper->n_ctx = nCtx;
    wrapper->gpu_offload = (nGpuLayers > 0);
    return reinterpret_cast<jlong>(wrapper);
#else
    env->ReleaseStringUTFChars(modelPath, path);
    return 0;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring prompt, jint maxTokens, jfloat temperature, jobject callback) {
    g_cancel_requested.store(false);
#if HAS_LLAMA_CPP
    auto* wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
    if (!wrapper || !wrapper->ctx || !wrapper->model || !wrapper->vocab) {
        return env->NewStringUTF("ERROR: Model not loaded.");
    }

    const char* promptCStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptCStr);
    env->ReleaseStringUTFChars(prompt, promptCStr);

    jclass cbClass = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID onTokenMethod = cbClass ? env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V") : nullptr;

    const int n_prompt = -llama_tokenize(wrapper->vocab, promptStr.c_str(), static_cast<int32_t>(promptStr.size()), nullptr, 0, true, true);
    if (n_prompt <= 0 || n_prompt >= wrapper->n_ctx) {
        return env->NewStringUTF("ERROR: Prompt tokenization failed or exceeds context window.");
    }

    std::vector<llama_token> tokens_list(n_prompt);
    if (llama_tokenize(wrapper->vocab, promptStr.c_str(), static_cast<int32_t>(promptStr.size()), tokens_list.data(), n_prompt, true, true) < 0) {
        return env->NewStringUTF("ERROR: Failed to tokenize prompt.");
    }

    llama_memory_clear(llama_get_memory(wrapper->ctx), true);

    llama_batch batch = llama_batch_get_one(tokens_list.data(), static_cast<int32_t>(tokens_list.size()));
    if (llama_decode(wrapper->ctx, batch) != 0) {
        return env->NewStringUTF("ERROR: llama_decode failed on prompt.");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    int n_decode = 0;
    llama_token new_token_id = 0;

    while (n_decode < maxTokens && !g_cancel_requested.load()) {
        new_token_id = llama_sampler_sample(smpl, wrapper->ctx, -1);
        if (llama_vocab_is_eog(wrapper->vocab, new_token_id)) {
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(wrapper->vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            output += piece;
            if (callback && onTokenMethod) {
                jstring jPiece = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(callback, onTokenMethod, jPiece);
                env->DeleteLocalRef(jPiece);
            }
        }

        batch = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(wrapper->ctx, batch) != 0) {
            break;
        }
        n_decode++;
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(output.c_str());
#else
    return env->NewStringUTF("ERROR: llama.cpp native runtime not compiled.");
#endif
}

JNIEXPORT void JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeCancelGeneration(JNIEnv*, jobject) {
    g_cancel_requested.store(true);
}

JNIEXPORT void JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeUnloadModel(JNIEnv*, jobject, jlong handle) {
#if HAS_LLAMA_CPP
    auto* wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
    if (wrapper) {
        if (wrapper->ctx) llama_free(wrapper->ctx);
        if (wrapper->model) llama_model_free(wrapper->model);
        delete wrapper;
    }
    llama_backend_free();
#endif
}

} // extern "C"
