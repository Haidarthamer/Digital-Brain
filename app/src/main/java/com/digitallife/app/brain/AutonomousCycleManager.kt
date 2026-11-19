package com.digitallife.app.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AutonomousReport(
    val cycleCount: Int = 0,
    val lastPhase: String = "IDLE",
    val isolatedConceptsFound: Int = 0,
    val totalMemoriesReviewed: Int = 0,
    val suggestedObjectives: List<String> = emptyList()
)

class AutonomousCycleManager(
    private val memoryEngine: MemoryEngine,
    private val knowledgeGraph: KnowledgeGraphEngine
) {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    private val _report = MutableStateFlow(AutonomousReport())
    val report: StateFlow<AutonomousReport> = _report

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            _report.value = _report.value.copy(lastPhase = "PAUSED")
        }
    }

    suspend fun runSingleCycle(onLog: (String) -> Unit): AutonomousReport {
        onLog("OBSERVE: Reviewing stored memories and knowledge graph...")
        val memories = memoryEngine.getAllMemories()
        val isolated = knowledgeGraph.findIsolatedConcepts()

        onLog("UNDERSTAND & RETRIEVE: Found ${memories.size} memories and ${isolated.size} isolated concept(s).")
        val suggestions = mutableListOf<String>()
        if (isolated.isNotEmpty()) {
            suggestions.add("Connect isolated concept '${isolated.first().label}' to related domain knowledge.")
        }
        if (memories.isEmpty()) {
            suggestions.add("Teach LIFE-01 foundational knowledge in the Teaching Center.")
        }

        val updated = AutonomousReport(
            cycleCount = _report.value.cycleCount + 1,
            lastPhase = "REMEMBER",
            isolatedConceptsFound = isolated.size,
            totalMemoriesReviewed = memories.size,
            suggestedObjectives = suggestions
        )
        _report.value = updated
        onLog("REMEMBER: Autonomous cycle #${updated.cycleCount} completed.")
        return updated
    }
}
