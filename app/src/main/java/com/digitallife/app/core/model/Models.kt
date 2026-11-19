package com.digitallife.app.core.model

import kotlinx.serialization.Serializable

enum class AgentState {
    IDLE,
    THINKING,
    LEARNING,
    ANALYZING,
    PLANNING,
    WORKING,
    COMPLETED,
    ERROR
}

enum class MemoryType {
    SEMANTIC,      // Facts and general knowledge
    EPISODIC,      // Previous experiences and interactions
    PROCEDURAL,    // Procedures and workflows
    INSTRUCTION,   // User-provided instructions
    TASK           // Previous tasks and outcomes
}

@Serializable
data class MemoryItem(
    val id: String,
    val type: MemoryType,
    val title: String,
    val content: String,
    val category: String = "General",
    val source: String = "User",
    val confidence: Float = 1.0f,
    val isContradiction: Boolean = false,
    val contradictsId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class KnowledgeNode(
    val id: String,
    val label: String,
    val category: String = "Concept",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class KnowledgeEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relationship: String,
    val weight: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskStatus {
    QUEUED,
    PLANNING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class TaskStep(
    val index: Int,
    val description: String,
    val toolName: String? = null,
    val status: TaskStatus = TaskStatus.QUEUED,
    val output: String = "",
    val error: String? = null
)

@Serializable
data class TaskItem(
    val id: String,
    val objective: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val steps: List<TaskStep> = emptyList(),
    val evaluationSummary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class ActivityEvent(
    val id: String,
    val category: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String,
    val requiresInternet: Boolean,
    val requiresApproval: Boolean,
    val isImplemented: Boolean = true
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

data class ModelConfig(
    val modelName: String = "Qwen2.5-1.5B-Instruct-Q4_K_M",
    val fileName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    val quantization: String = "Q4_K_M (4-bit)",
    val contextSize: Int = 4096,
    val maxTokens: Int = 512,
    val temperature: Float = 0.6f,
    val threads: Int = 6,
    val useGpuAcceleration: Boolean = false
)

data class RuntimeStatus(
    val isLoaded: Boolean = false,
    val isNativeCompiled: Boolean = false,
    val runtimeVersion: String = "llama.cpp",
    val backendMode: String = "CPU (ARM64 NEON)",
    val gpuSupported: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val healthStatus: String = "Not Initialized",
    val lastError: String? = null
)

data class InitializationState(
    val isComplete: Boolean = false,
    val hasFailed: Boolean = false,
    val currentStep: String = "Preparing runtime...",
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)

enum class NavTab(val label: String) {
    OVERVIEW("Overview"),
    BRAIN("Brain"),
    TEACH("Teach"),
    MEMORY("Memory"),
    KNOWLEDGE("Knowledge"),
    TASKS("Tasks"),
    AUTONOMOUS("Autonomous"),
    TOOLS("Tools"),
    SETTINGS("Settings")
}
