package com.digitallife.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitallife.app.brain.*
import com.digitallife.app.core.model.*
import com.digitallife.app.data.db.DigitalLifeDatabase
import com.digitallife.app.runtime.LlamaRuntime
import com.digitallife.app.runtime.ModelAssetManager
import com.digitallife.app.tools.ToolRegistry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class DigitalLifeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DigitalLifeDatabase.getInstance(application)
    val runtime = LlamaRuntime(application)
    private val assetManager = ModelAssetManager(application)

    val memoryEngine = MemoryEngine(RoomMemoryStore(db.memoryDao()))
    val knowledgeGraph = KnowledgeGraphEngine(RoomKnowledgeStore(db.knowledgeDao()))
    val learningEngine = LearningEngine(memoryEngine, knowledgeGraph, runtime)
    val reasoningEngine = ReasoningEngine(memoryEngine, runtime)
    val planningEngine = PlanningEngine(memoryEngine)
    val evaluationEngine = EvaluationEngine()
    val toolRegistry = ToolRegistry(memoryEngine, File(application.filesDir, "agent_workspace"))
    val executionEngine = ExecutionEngine(toolRegistry, memoryEngine, evaluationEngine)
    val autonomousCycle = AutonomousCycleManager(memoryEngine, knowledgeGraph)

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState

    private val _initState = MutableStateFlow(InitializationState())
    val initState: StateFlow<InitializationState> = _initState

    private val _modelConfig = MutableStateFlow(ModelConfig())
    val modelConfig: StateFlow<ModelConfig> = _modelConfig

    private val _runtimeStatus = MutableStateFlow(runtime.status)
    val runtimeStatus: StateFlow<RuntimeStatus> = _runtimeStatus

    private val _activityLog = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val activityLog: StateFlow<List<ActivityEvent>> = _activityLog

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    private val _brainOutput = MutableStateFlow("")
    val brainOutput: StateFlow<String> = _brainOutput

    private val _lastLearningSummary = MutableStateFlow<LearningSummary?>(null)
    val lastLearningSummary: StateFlow<LearningSummary?> = _lastLearningSummary

    val memories: StateFlow<List<MemoryItem>> = memoryEngine.observeMemories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val knowledgeNodes: StateFlow<List<KnowledgeNode>> = knowledgeGraph.observeNodes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val knowledgeEdges: StateFlow<List<KnowledgeEdge>> = knowledgeGraph.observeEdges()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        initializeSystem()
    }

    fun initializeSystem() {
        viewModelScope.launch {
            val logs = mutableListOf<String>()
            fun step(msg: String, progress: Float) {
                logs.add(msg)
                _initState.value = _initState.value.copy(
                    currentStep = msg,
                    progress = progress,
                    logs = logs.toList()
                )
                logActivity("INIT", msg)
            }

            step("1. Initializing local AI runtime (llama.cpp)...", 0.15f)
            val modelResult = assetManager.locateOrInstallBundledModel(_modelConfig.value.fileName) { msg, p ->
                step(msg, p)
            }

            if (modelResult.isFailure) {
                val err = modelResult.exceptionOrNull()?.message ?: "Model not found"
                step("Model check failed: $err", 0.5f)
                _runtimeStatus.value = runtime.status
                _initState.value = _initState.value.copy(
                    isComplete = true,
                    hasFailed = true,
                    errorMessage = err
                )
                logActivity("ERROR", err, isError = true)
                return@launch
            }

            val modelFile = modelResult.getOrThrow()
            step("3. Verifying model integrity (SHA-256 prefix: ${assetManager.computeSha256Prefix(modelFile)})...", 0.65f)

            step("4. Loading quantized GGUF model into memory...", 0.8f)
            val loadRes = runtime.loadModel(modelFile.absolutePath, _modelConfig.value)
            _runtimeStatus.value = runtime.status

            if (loadRes.isFailure) {
                val err = loadRes.exceptionOrNull()?.message ?: "Failed to load model"
                step("Model load failed: $err", 0.85f)
                _initState.value = _initState.value.copy(
                    isComplete = true,
                    hasFailed = true,
                    errorMessage = err
                )
                logActivity("ERROR", err, isError = true)
                return@launch
            }

            step("5. Running inference health check...", 0.92f)
            val ping = runtime.generate("Say OK.", maxTokens = 8, temperature = 0.1f)
            if (ping.isFailure) {
                val err = ping.exceptionOrNull()?.message ?: "Inference health check failed"
                _initState.value = _initState.value.copy(isComplete = true, hasFailed = true, errorMessage = err)
                logActivity("ERROR", err, isError = true)
                return@launch
            }

            step("6. Brain modules & persistent SQLite memory ready.", 1.0f)
            _initState.value = _initState.value.copy(isComplete = true, hasFailed = false)
            logActivity("SYSTEM", "LIFE-01 initialized offline successfully.")
        }
    }

    fun askBrain(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _agentState.value = AgentState.THINKING
            _brainOutput.value = ""
            logActivity("BRAIN", "Retrieving memories & reasoning on: ${query.take(60)}")

            val res = reasoningEngine.reason(query) { token ->
                _brainOutput.value += token
            }

            if (res.isSuccess) {
                val response = res.getOrThrow()
                _brainOutput.value = response.answer
                _agentState.value = AgentState.COMPLETED
                logActivity("BRAIN", "Response generated using ${response.retrievedMemories.size} retrieved memory item(s).")
            } else {
                val err = res.exceptionOrNull()?.message ?: "Reasoning failed"
                _brainOutput.value = "Error: $err"
                _agentState.value = AgentState.ERROR
                logActivity("ERROR", err, isError = true)
            }
        }
    }

    fun teachAgent(title: String, content: String, type: MemoryType, category: String) {
        viewModelScope.launch {
            _agentState.value = AgentState.LEARNING
            logActivity("TEACH", "Analyzing new ${type.name} material...")
            val res = learningEngine.teach(title, content, type, category)
            if (res.isSuccess) {
                val summary = res.getOrThrow()
                _lastLearningSummary.value = summary
                _agentState.value = AgentState.COMPLETED
                logActivity("TEACH", summary.summaryText)
            } else {
                _agentState.value = AgentState.ERROR
                logActivity("ERROR", res.exceptionOrNull()?.message ?: "Teaching failed", isError = true)
            }
        }
    }

    fun submitTask(objective: String) {
        if (objective.isBlank()) return
        viewModelScope.launch {
            _agentState.value = AgentState.PLANNING
            logActivity("TASK", "Planning task: $objective")
            val plan = planningEngine.createPlan(objective)
            _tasks.value = listOf(plan) + _tasks.value

            _agentState.value = AgentState.WORKING
            val finalTask = executionEngine.executeTask(plan) { updated ->
                _tasks.value = _tasks.value.map { if (it.id == updated.id) updated else it }
            }
            _agentState.value = if (finalTask.status == TaskStatus.COMPLETED) AgentState.COMPLETED else AgentState.ERROR
            logActivity("TASK", finalTask.evaluationSummary, isError = finalTask.status == TaskStatus.FAILED)
        }
    }

    fun cancelCurrentTask() {
        executionEngine.requestCancel()
        runtime.cancelGeneration()
        _agentState.value = AgentState.IDLE
        logActivity("TASK", "Cancellation requested by user.")
    }

    fun runAutonomousCycleOnce() {
        viewModelScope.launch {
            _agentState.value = AgentState.ANALYZING
            autonomousCycle.runSingleCycle { msg ->
                logActivity("AUTONOMOUS", msg)
            }
            _agentState.value = AgentState.IDLE
        }
    }

    fun updateModelConfig(newConfig: ModelConfig) {
        _modelConfig.value = newConfig
        logActivity("SETTINGS", "Updated model config (ctx=${newConfig.contextSize}, gpu=${newConfig.useGpuAcceleration}).")
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryEngine.deleteMemory(id)
            logActivity("MEMORY", "Deleted memory $id")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            memoryEngine.clearAll()
            knowledgeGraph.clearAll()
            _tasks.value = emptyList()
            logActivity("SECURITY", "All local memories and knowledge graph data reset by user.")
        }
    }

    private fun logActivity(category: String, message: String, isError: Boolean = false) {
        val event = ActivityEvent(
            id = UUID.randomUUID().toString(),
            category = category,
            message = message,
            isError = isError
        )
        _activityLog.value = (listOf(event) + _activityLog.value).take(100)
    }

    override fun onCleared() {
        super.onCleared()
        runtime.shutdown()
    }
}
