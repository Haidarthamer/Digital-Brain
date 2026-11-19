package com.digitallife.app.runtime

import android.app.ActivityManager
import android.content.Context
import com.digitallife.app.core.model.ModelConfig
import com.digitallife.app.core.model.RuntimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LlamaRuntime(private val context: Context) : InferenceEngine {

    private var nativeHandle: Long = 0L
    private var nativeLibLoaded: Boolean = false

    override var status: RuntimeStatus = RuntimeStatus()
        private set

    init {
        try {
            System.loadLibrary("digitallife_llama")
            nativeLibLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            nativeLibLoaded = false
            status = status.copy(
                isNativeCompiled = false,
                healthStatus = "Native library not loaded: ${e.message}",
                lastError = e.message
            )
        }
    }

    fun interface TokenCallback {
        fun onToken(token: String)
    }

    private external fun nativeIsCompiledWithLlama(): Boolean
    private external fun nativeGetRuntimeVersion(): String
    private external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float, callback: TokenCallback?): String
    private external fun nativeCancelGeneration()
    private external fun nativeUnloadModel(handle: Long)

    override fun loadModel(modelPath: String, config: ModelConfig): Result<Unit> {
        if (!nativeLibLoaded) {
            return Result.failure(IllegalStateException("Native library digitallife_llama failed to load."))
        }
        val compiled = nativeIsCompiledWithLlama()
        val version = nativeGetRuntimeVersion()
        if (!compiled) {
            status = status.copy(
                isNativeCompiled = false,
                runtimeVersion = version,
                healthStatus = "llama.cpp not linked",
                lastError = "llama.cpp source was not present during native build."
            )
            return Result.failure(IllegalStateException("llama.cpp not linked in native binary."))
        }

        val file = File(modelPath)
        if (!file.exists() || file.length() == 0L) {
            val msg = "Model file missing or empty at $modelPath"
            status = status.copy(healthStatus = "Model Missing", lastError = msg)
            return Result.failure(IllegalStateException(msg))
        }

        if (nativeHandle != 0L) {
            nativeUnloadModel(nativeHandle)
            nativeHandle = 0L
        }

        val gpuLayers = if (config.useGpuAcceleration) 24 else 0
        var handle = nativeLoadModel(modelPath, config.contextSize, config.threads, gpuLayers)
        var usedGpu = config.useGpuAcceleration && handle != 0L

        // Automatic CPU fallback if GPU offload failed
        if (handle == 0L && config.useGpuAcceleration) {
            handle = nativeLoadModel(modelPath, config.contextSize, config.threads, 0)
            usedGpu = false
        }

        if (handle == 0L) {
            val msg = "llama_load_model_from_file failed for ${file.name}"
            status = status.copy(isLoaded = false, healthStatus = "Load Failed", lastError = msg)
            return Result.failure(RuntimeException(msg))
        }

        nativeHandle = handle
        status = status.copy(
            isLoaded = true,
            isNativeCompiled = true,
            runtimeVersion = version,
            backendMode = if (usedGpu) "GPU + CPU Hybrid" else "CPU (ARM64 NEON)",
            gpuSupported = usedGpu,
            modelSizeBytes = file.length(),
            healthStatus = "Healthy",
            lastError = null
        )
        refreshMemoryStats()
        return Result.success(Unit)
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onToken: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!status.isLoaded || nativeHandle == 0L) {
            return@withContext Result.failure(IllegalStateException("Local AI model is not loaded."))
        }
        val callback = TokenCallback { token -> onToken(token) }
        val result = nativeGenerate(nativeHandle, prompt, maxTokens, temperature, callback)
        if (result.startsWith("ERROR:")) {
            Result.failure(RuntimeException(result))
        } else {
            refreshMemoryStats()
            Result.success(result)
        }
    }

    override fun cancelGeneration() {
        if (nativeLibLoaded) {
            nativeCancelGeneration()
        }
    }

    override fun shutdown() {
        if (nativeLibLoaded && nativeHandle != 0L) {
            nativeUnloadModel(nativeHandle)
            nativeHandle = 0L
        }
        status = status.copy(isLoaded = false, healthStatus = "Shutdown")
    }

    override fun refreshMemoryStats() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        status = status.copy(
            totalRamMb = totalMb,
            usedRamMb = (totalMb - availMb).coerceAtLeast(0L)
        )
    }
}
