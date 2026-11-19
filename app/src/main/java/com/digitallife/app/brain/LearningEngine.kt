package com.digitallife.app.brain

import com.digitallife.app.core.model.MemoryItem
import com.digitallife.app.core.model.MemoryType
import com.digitallife.app.runtime.InferenceEngine

data class LearningSummary(
    val title: String,
    val extractedConcepts: List<String>,
    val extractedRelationships: List<Triple<String, String, String>>,
    val savedMemory: MemoryItem,
    val isDuplicate: Boolean,
    val contradictionWith: MemoryItem?,
    val summaryText: String
)

class LearningEngine(
    private val memoryEngine: MemoryEngine,
    private val knowledgeGraph: KnowledgeGraphEngine,
    private val inferenceEngine: InferenceEngine
) {
    suspend fun teach(
        title: String,
        content: String,
        type: MemoryType = MemoryType.SEMANTIC,
        category: String = "General",
        source: String = "User Teaching"
    ): Result<LearningSummary> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot learn empty content."))
        }

        val cleanTitle = title.trim().ifEmpty {
            content.trim().lineSequence().firstOrNull()?.take(48) ?: "Untitled Knowledge"
        }

        // 1. Extract concepts & relationships (use local LLM if loaded, otherwise deterministic extraction)
        val concepts = extractConcepts(cleanTitle, content)
        val relationships = extractRelationships(cleanTitle, concepts)

        // 2. Check existing memories & save
        val saveResult = memoryEngine.saveMemory(
            type = type,
            title = cleanTitle,
            content = content,
            category = category,
            source = source
        )

        // 3. Update Knowledge Graph only if not a duplicate
        if (!saveResult.isDuplicate) {
            concepts.forEach { concept ->
                knowledgeGraph.addOrGetConcept(concept, category = category)
            }
            relationships.forEach { (from, to, rel) ->
                knowledgeGraph.connectConcepts(from, to, rel)
            }
        }

        val summaryText = buildString {
            if (saveResult.isDuplicate) {
                appendLine("Duplicate detected: identical or near-identical knowledge already exists in memory.")
            } else {
                appendLine("Stored in ${type.name} memory under '$category'.")
            }
            if (saveResult.contradictionWith != null) {
                appendLine("Contradiction flagged against existing memory: '${saveResult.contradictionWith.title}'.")
            }
            if (concepts.isNotEmpty()) {
                appendLine("Extracted concepts: ${concepts.joinToString(", ")}")
            }
            if (relationships.isNotEmpty()) {
                appendLine("Linked ${relationships.size} concept relationship(s) in Knowledge Graph.")
            }
        }.trim()

        return Result.success(
            LearningSummary(
                title = cleanTitle,
                extractedConcepts = concepts,
                extractedRelationships = relationships,
                savedMemory = saveResult.savedItem,
                isDuplicate = saveResult.isDuplicate,
                contradictionWith = saveResult.contradictionWith,
                summaryText = summaryText
            )
        )
    }

    private suspend fun extractConcepts(title: String, content: String): List<String> {
        if (inferenceEngine.status.isLoaded) {
            val prompt = "Extract up to 5 key technical or domain concepts (comma-separated, no explanation) from:\nTitle: $title\nContent: ${content.take(600)}\nConcepts:"
            val res = inferenceEngine.generate(prompt, maxTokens = 64, temperature = 0.2f)
            if (res.isSuccess) {
                val parsed = res.getOrThrow()
                    .split(",", "\n")
                    .map { it.trim().trimStart('-', '*', ' ') }
                    .filter { it.length in 2..40 }
                    .distinct()
                    .take(5)
                if (parsed.isNotEmpty()) return parsed
            }
        }

        // Deterministic fallback: capitalized phrases + title
        val words = content.split(Regex("\\s+"))
            .map { it.trim(',', '.', ':', ';', '(', ')') }
            .filter { it.length > 3 && it[0].isUpperCase() }
            .distinct()
            .take(4)
        return (listOf(title) + words).distinct().take(5)
    }

    private fun extractRelationships(title: String, concepts: List<String>): List<Triple<String, String, String>> {
        if (concepts.size < 2) return emptyList()
        return concepts.drop(1).map { concept ->
            Triple(title, concept, "relates_to")
        }
    }
}
