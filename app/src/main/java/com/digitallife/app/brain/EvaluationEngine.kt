package com.digitallife.app.brain

import com.digitallife.app.core.model.TaskItem
import com.digitallife.app.core.model.TaskStatus

class EvaluationEngine {
    fun evaluate(task: TaskItem): String {
        val completed = task.steps.count { it.status == TaskStatus.COMPLETED }
        val failed = task.steps.count { it.status == TaskStatus.FAILED }
        val total = task.steps.size

        return when {
            failed == 0 && completed == total ->
                "All $total step(s) completed successfully for objective: '${task.objective}'."
            failed > 0 ->
                "Completed $completed/$total step(s), with $failed failure(s). Review failed step logs before retrying."
            else ->
                "Task ended in state ${task.status.name} ($completed/$total steps completed)."
        }
    }
}
