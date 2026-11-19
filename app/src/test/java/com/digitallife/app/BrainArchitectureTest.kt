package com.digitallife.app

import com.digitallife.app.brain.*
import com.digitallife.app.core.model.MemoryType
import com.digitallife.app.core.model.ModelConfig
import com.digitallife.app.core.model.RuntimeStatus
import com.digitallife.app.core.model.TaskStatus
import com.digitallife.app.runtime.InferenceEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BrainArchitectureTest {

    private class FakeOfflineEngine(private val loaded: Boolean = false) : InferenceEngine {
        override val status = RuntimeStatus(isLoaded = loaded)
        override fun loadModel(modelPath: String, config: ModelConfig) = Result.success(Unit)
        override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float, onToken: (String) -> Unit) =
            Result.success("Kotlin, Android, Jetpack Compose")
        override fun cancelGeneration() {}
        override fun shutdown() {}
        override fun refreshMemoryStats() {}
    }

    @Test
    fun memoryEngine_saves_retrieves_detectsDuplicates_and_contradictions() = runTest {
        val engine = MemoryEngine(InMemoryMemoryStore())

        val r1 = engine.saveMemory(MemoryType.SEMANTIC, "Python", "Python is a high-level programming language.")
        assertFalse(r1.isDuplicate)
        assertNull(r1.contradictionWith)

        val dup = engine.saveMemory(MemoryType.SEMANTIC, "Python", "Python is a high-level programming language.")
        assertTrue(dup.isDuplicate)

        val contra = engine.saveMemory(MemoryType.SEMANTIC, "Python", "Python is a database engine for SQL.")
        assertFalse(contra.isDuplicate)
        assertNotNull(contra.contradictionWith)

        val retrieved = engine.retrieveRelevant("programming language")
        assertTrue(retrieved.any { it.title == "Python" })

        val exported = engine.exportToJson()
        engine.clearAll()
        assertEquals(0, engine.getAllMemories().size)

        val imported = engine.importFromJson(exported)
        assertTrue(imported.isSuccess)
        assertEquals(2, engine.getAllMemories().size)
    }

    @Test
    fun knowledgeGraph_connects_and_explains_relationships() = runTest {
        val kg = KnowledgeGraphEngine(InMemoryKnowledgeStore())
        kg.connectConcepts("Python", "Programming Language", "is_a")
        kg.connectConcepts("Programming Language", "Software Development", "used_in")
        kg.addOrGetConcept("IsolatedNode")

        val explanations = kg.explainConnections("Programming Language")
        assertEquals(2, explanations.size)

        val isolated = kg.findIsolatedConcepts()
        assertEquals(1, isolated.size)
        assertEquals("IsolatedNode", isolated.first().label)
    }

    @Test
    fun learningEngine_extracts_and_stores_knowledge() = runTest {
        val mem = MemoryEngine(InMemoryMemoryStore())
        val kg = KnowledgeGraphEngine(InMemoryKnowledgeStore())
        val learning = LearningEngine(mem, kg, FakeOfflineEngine(loaded = true))

        val res = learning.teach(
            title = "Android Development",
            content = "Android apps are built with Kotlin and Jetpack Compose.",
            type = MemoryType.SEMANTIC,
            category = "Mobile"
        )
        assertTrue(res.isSuccess)
        val summary = res.getOrThrow()
        assertFalse(summary.isDuplicate)
        assertTrue(summary.extractedConcepts.isNotEmpty())
        assertEquals(1, mem.getAllMemories().size)
    }

    @Test
    fun planning_and_evaluation_work_end_to_end() = runTest {
        val mem = MemoryEngine(InMemoryMemoryStore())
        val planner = PlanningEngine(mem)
        val evaluator = EvaluationEngine()

        val plan = planner.createPlan("Create an Android application architecture")
        assertEquals(3, plan.steps.size)

        val completedPlan = plan.copy(
            status = TaskStatus.COMPLETED,
            steps = plan.steps.map { it.copy(status = TaskStatus.COMPLETED) }
        )
        val eval = evaluator.evaluate(completedPlan)
        assertTrue(eval.contains("completed successfully"))
    }
}
