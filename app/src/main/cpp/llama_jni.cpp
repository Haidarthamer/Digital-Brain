#include <jni.h>
#include <string>
#include <atomic>
#include <android/log.h>

#define LOG_TAG "DigitalLifeLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if HAS_LLAMA_CPP
#include "llama.h"
#include "common.h"
#endif

static std::atomic<bool> g_cancel_requested{false};

#if HAS_LLAMA_CPP
struct LlamaContextWrapper {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
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
    return env->NewStringUTF("llama.cpp (NOT LINKED - clone llama.cpp)");
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

    llama_model* model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load GGUF model");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(nCtx);
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama_context");
        llama_free_model(model);
        return 0;
    }

    auto* wrapper = new LlamaContextWrapper();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->n_ctx = nCtx;
    wrapper->gpu_offload = (nGpuLayers > 0);
    return reinterpret_cast<jlong>(wrapper);
#else
    env->ReleaseStringUTFChars(modelPath, path);
    LOGE("Cannot load model: llama.cpp was not linked at compile time.");
    return 0;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_digitallife_app_runtime_LlamaRuntime_nativeGenerate(
        JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jint maxTokens, jfloat temperature, jobject callback) {
    g_cancel_requested.store(false);
#if HAS_LLAMA_CPP
    auto* wrapper = reinterpret_cast<LlamaContextWrapper*>(handle);
    if (!wrapper || !wrapper->ctx || !wrapper->model) {
        return env->NewStringUTF("ERROR: Model not loaded.");
    }

    const char* promptCStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptCStr);
    env->ReleaseStringUTFChars(prompt, promptCStr);

    jclass cbClass = callback ? env->GetObjectClass(callback) : nullptr;
    jmethodID onTokenMethod = cbClass ? env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V") : nullptr;

    std::vector<llama_token> tokens_list = common_tokenize(wrapper->ctx, promptStr, true, true);
    if (static_cast<int>(tokens_list.size()) >= wrapper->n_ctx) {
        return env->NewStringUTF("ERROR: Prompt exceeds context window.");
    }

    llama_kv_cache_clear(wrapper->ctx);

    llama_batch batch = llama_batch_init(wrapper->n_ctx, 0, 1);
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], static_cast<llama_pos>(i), {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(wrapper->ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("ERROR: llama_decode failed on prompt.");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    int n_cur = batch.n_tokens;
    int n_decode = 0;

    while (n_decode < maxTokens && !g_cancel_requested.load()) {
        llama_token new_token_id = llama_sampler_sample(smpl, wrapper->ctx, -1);
        if (llama_token_is_eog(wrapper->model, new_token_id)) {
            break;
        }

        std::string piece = common_token_to_piece(wrapper->ctx, new_token_id);
        output += piece;

        if (callback && onTokenMethod) {
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);
        n_cur++;
        n_decode++;

        if (llama_decode(wrapper->ctx, batch) != 0) {
            break;
        }
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
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
        if (wrapper->model) llama_free_model(wrapper->model);
        delete wrapper;
    }
    llama_backend_free();
#endif
}

} // extern "C"
