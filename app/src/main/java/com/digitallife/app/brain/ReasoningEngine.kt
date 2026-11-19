package com.digitallife.app.brain

import com.digitallife.app.core.model.MemoryItem
import com.digitallife.app.runtime.InferenceEngine

data class ReasoningResponse(
    val answer: String,
    val retrievedMemories: List<MemoryItem>,
    val usedLocalModel: Boolean
)

class ReasoningEngine(
    private val memoryEngine: MemoryEngine,
    private val inferenceEngine: InferenceEngine
) {
    suspend fun reason(
        userQuery: String,
        onToken: (String) -> Unit = {}
    ): Result<ReasoningResponse> {
        // Always retrieve relevant memories before responding
        val relevant = memoryEngine.retrieveRelevant(userQuery, limit = 5)

        if (!inferenceEngine.status.isLoaded) {
            return Result.failure(
                IllegalStateException(
                    "Local AI model is not loaded. Retrieved ${relevant.size} relevant memory item(s), " +
                        "but cannot run neural reasoning without a loaded GGUF model."
                )
            )
        }

        val memoryContext = if (relevant.isEmpty()) {
            "No prior memories found for this topic."
        } else {
            relevant.joinToString("\n") { "- [${it.type.name}] ${it.title}: ${it.content}" }
        }

        val prompt = buildString {
            appendLine("<|im_start|>system")
            appendLine("You are LIFE-01, an autonomous local AI assistant inside DIGITAL LIFE.")
            appendLine("Distinguish verified facts from hypotheses. If information is missing from memory, state uncertainty clearly.")
            appendLine("Relevant Long-Term Memory:")
            appendLine(memoryContext)
            appendLine("<|im_end|>")
            appendLine("<|im_start|>user")
            appendLine(userQuery)
            appendLine("<|im_end|>")
            appendLine("<|im_start|>assistant")
        }

        val genResult = inferenceEngine.generate(
            prompt = prompt,
            maxTokens = 512,
            temperature = 0.6f,
            onToken = onToken
        )

        return genResult.map { output ->
            ReasoningResponse(
                answer = output.trim(),
                retrievedMemories = relevant,
                usedLocalModel = true
            )
        }
    }
}
