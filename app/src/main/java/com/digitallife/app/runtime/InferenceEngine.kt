package com.digitallife.app.runtime

import com.digitallife.app.core.model.ModelConfig
import com.digitallife.app.core.model.RuntimeStatus

interface InferenceEngine {
    val status: RuntimeStatus
    fun loadModel(modelPath: String, config: ModelConfig): Result<Unit>
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.6f,
        onToken: (String) -> Unit = {}
    ): Result<String>
    fun cancelGeneration()
    fun shutdown()
    fun refreshMemoryStats()
}
