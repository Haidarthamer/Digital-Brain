package com.digitallife.app.brain

import com.digitallife.app.core.model.MemoryType
import com.digitallife.app.core.model.TaskItem
import com.digitallife.app.core.model.TaskStatus
import com.digitallife.app.tools.ToolRegistry

class ExecutionEngine(
    private val toolRegistry: ToolRegistry,
    private val memoryEngine: MemoryEngine,
    private val evaluationEngine: EvaluationEngine
) {
    @Volatile
    private var cancelRequested = false

    fun requestCancel() {
        cancelRequested = true
    }

    suspend fun executeTask(
        task: TaskItem,
        onStepUpdate: (TaskItem) -> Unit
    ): TaskItem {
        cancelRequested = false
        var current = task.copy(status = TaskStatus.RUNNING)
        onStepUpdate(current)

        val updatedSteps = current.steps.toMutableList()

        for (i in updatedSteps.indices) {
            if (cancelRequested) {
                current = current.copy(
                    status = TaskStatus.CANCELLED,
                    steps = updatedSteps,
                    evaluationSummary = "Task cancelled by user."
                )
                onStepUpdate(current)
                return current
            }

            val step = updatedSteps[i].copy(status = TaskStatus.RUNNING)
            updatedSteps[i] = step
            current = current.copy(steps = updatedSteps.toList())
            onStepUpdate(current)

            val toolName = step.toolName
            if (toolName == null) {
                // Final evaluation/memory step
                updatedSteps[i] = step.copy(
                    status = TaskStatus.COMPLETED,
                    output = "Prepared task outcome for persistent storage."
                )
            } else {
                val tool = toolRegistry.getTool(toolName)
                if (tool == null) {
                    updatedSteps[i] = step.copy(
                        status = TaskStatus.FAILED,
                        error = "Tool '$toolName' is not registered."
                    )
                } else if (tool.definition.requiresApproval) {
                    updatedSteps[i] = step.copy(
                        status = TaskStatus.FAILED,
                        error = "Tool '$toolName' requires explicit user approval."
                    )
                } else {
                    val res = tool.execute(task.objective)
                    updatedSteps[i] = if (res.success) {
                        step.copy(status = TaskStatus.COMPLETED, output = res.output)
                    } else {
                        step.copy(status = TaskStatus.FAILED, error = res.error ?: "Unknown tool error")
                    }
                }
            }

            current = current.copy(steps = updatedSteps.toList())
            onStepUpdate(current)
        }

        val anyFailed = updatedSteps.any { it.status == TaskStatus.FAILED }
        val finalStatus = if (anyFailed) TaskStatus.FAILED else TaskStatus.COMPLETED
        val finalized = current.copy(
            status = finalStatus,
            completedAt = System.currentTimeMillis()
        )
        val evalSummary = evaluationEngine.evaluate(finalized)
        val resultTask = finalized.copy(evaluationSummary = evalSummary)

        memoryEngine.saveMemory(
            type = MemoryType.TASK,
            title = "Task: ${task.objective.take(40)}",
            content = evalSummary,
            category = "Task History",
            source = "ExecutionEngine"
        )

        onStepUpdate(resultTask)
        return resultTask
    }
}
