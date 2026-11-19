package com.digitallife.app.brain

import com.digitallife.app.core.model.TaskItem
import com.digitallife.app.core.model.TaskStatus
import com.digitallife.app.core.model.TaskStep
import java.util.UUID

class PlanningEngine(private val memoryEngine: MemoryEngine) {

    suspend fun createPlan(objective: String): TaskItem {
        val relevantMemories = memoryEngine.retrieveRelevant(objective, limit = 3)
        val lower = objective.lowercase()

        val steps = mutableListOf<TaskStep>()
        steps.add(
            TaskStep(
                index = 0,
                description = "Retrieve relevant long-term memories (${relevantMemories.size} found)",
                toolName = "knowledge_search",
                status = TaskStatus.QUEUED
            )
        )

        when {
            lower.contains("analyze") || lower.contains("document") -> {
                steps.add(
                    TaskStep(
                        index = 1,
                        description = "Analyze provided material structure and key points",
                        toolName = "document_analyze",
                        status = TaskStatus.QUEUED
                    )
                )
            }
            lower.contains("code") || lower.contains("android") || lower.contains("architecture") -> {
                steps.add(
                    TaskStep(
                        index = 1,
                        description = "Generate structured architecture or code template",
                        toolName = "code_generate",
                        status = TaskStatus.QUEUED
                    )
                )
            }
            else -> {
                steps.add(
                    TaskStep(
                        index = 1,
                        description = "Synthesize knowledge and verify against stored facts",
                        toolName = "knowledge_search",
                        status = TaskStatus.QUEUED
                    )
                )
            }
        }

        steps.add(
            TaskStep(
                index = 2,
                description = "Evaluate outcome and store useful results in Task Memory",
                toolName = null,
                status = TaskStatus.QUEUED
            )
        )

        return TaskItem(
            id = UUID.randomUUID().toString(),
            objective = objective.trim(),
            status = TaskStatus.QUEUED,
            steps = steps
        )
    }
}
