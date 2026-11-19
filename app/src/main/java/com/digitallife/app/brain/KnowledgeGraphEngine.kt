package com.digitallife.app.brain

import com.digitallife.app.core.model.KnowledgeEdge
import com.digitallife.app.core.model.KnowledgeNode
import com.digitallife.app.data.db.KnowledgeDao
import com.digitallife.app.data.db.KnowledgeEdgeEntity
import com.digitallife.app.data.db.KnowledgeNodeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface KnowledgeStore {
    fun observeNodes(): Flow<List<KnowledgeNode>>
    fun observeEdges(): Flow<List<KnowledgeEdge>>
    suspend fun getAllNodes(): List<KnowledgeNode>
    suspend fun getAllEdges(): List<KnowledgeEdge>
    suspend fun upsertNode(node: KnowledgeNode)
    suspend fun upsertEdge(edge: KnowledgeEdge)
    suspend fun clearAll()
}

class RoomKnowledgeStore(private val dao: KnowledgeDao) : KnowledgeStore {
    override fun observeNodes(): Flow<List<KnowledgeNode>> =
        dao.observeNodes().map { list -> list.map { KnowledgeNode(it.id, it.label, it.category, it.description, it.createdAt) } }

    override fun observeEdges(): Flow<List<KnowledgeEdge>> =
        dao.observeEdges().map { list -> list.map { KnowledgeEdge(it.id, it.fromNodeId, it.toNodeId, it.relationship, it.weight, it.createdAt) } }

    override suspend fun getAllNodes(): List<KnowledgeNode> =
        dao.getAllNodes().map { KnowledgeNode(it.id, it.label, it.category, it.description, it.createdAt) }

    override suspend fun getAllEdges(): List<KnowledgeEdge> =
        dao.getAllEdges().map { KnowledgeEdge(it.id, it.fromNodeId, it.toNodeId, it.relationship, it.weight, it.createdAt) }

    override suspend fun upsertNode(node: KnowledgeNode) =
        dao.upsertNode(KnowledgeNodeEntity(node.id, node.label, node.category, node.description, node.createdAt))

    override suspend fun upsertEdge(edge: KnowledgeEdge) =
        dao.upsertEdge(KnowledgeEdgeEntity(edge.id, edge.fromNodeId, edge.toNodeId, edge.relationship, edge.weight, edge.createdAt))

    override suspend fun clearAll() {
        dao.deleteAllEdges()
        dao.deleteAllNodes()
    }
}

class InMemoryKnowledgeStore : KnowledgeStore {
    private val nodes = MutableStateFlow<List<KnowledgeNode>>(emptyList())
    private val edges = MutableStateFlow<List<KnowledgeEdge>>(emptyList())

    override fun observeNodes(): Flow<List<KnowledgeNode>> = nodes
    override fun observeEdges(): Flow<List<KnowledgeEdge>> = edges
    override suspend fun getAllNodes(): List<KnowledgeNode> = nodes.value
    override suspend fun getAllEdges(): List<KnowledgeEdge> = edges.value
    override suspend fun upsertNode(node: KnowledgeNode) {
        nodes.value = nodes.value.filterNot { it.id == node.id } + node
    }
    override suspend fun upsertEdge(edge: KnowledgeEdge) {
        edges.value = edges.value.filterNot { it.id == edge.id } + edge
    }
    override suspend fun clearAll() {
        nodes.value = emptyList()
        edges.value = emptyList()
    }
}

class KnowledgeGraphEngine(private val store: KnowledgeStore) {

    fun observeNodes(): Flow<List<KnowledgeNode>> = store.observeNodes()
    fun observeEdges(): Flow<List<KnowledgeEdge>> = store.observeEdges()

    suspend fun addOrGetConcept(label: String, category: String = "Concept", description: String = ""): KnowledgeNode {
        val existing = store.getAllNodes().firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
        if (existing != null) return existing
        val node = KnowledgeNode(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            category = category,
            description = description
        )
        store.upsertNode(node)
        return node
    }

    suspend fun connectConcepts(fromLabel: String, toLabel: String, relationship: String): KnowledgeEdge {
        val fromNode = addOrGetConcept(fromLabel)
        val toNode = addOrGetConcept(toLabel)
        val existing = store.getAllEdges().firstOrNull {
            it.fromNodeId == fromNode.id && it.toNodeId == toNode.id && it.relationship.equals(relationship, ignoreCase = true)
        }
        if (existing != null) return existing

        val edge = KnowledgeEdge(
            id = UUID.randomUUID().toString(),
            fromNodeId = fromNode.id,
            toNodeId = toNode.id,
            relationship = relationship.trim()
        )
        store.upsertEdge(edge)
        return edge
    }

    suspend fun findIsolatedConcepts(): List<KnowledgeNode> {
        val nodes = store.getAllNodes()
        val edges = store.getAllEdges()
        val connectedIds = edges.flatMap { listOf(it.fromNodeId, it.toNodeId) }.toSet()
        return nodes.filterNot { it.id in connectedIds }
    }

    suspend fun explainConnections(conceptLabel: String): List<String> {
        val nodes = store.getAllNodes()
        val edges = store.getAllEdges()
        val target = nodes.firstOrNull { it.label.equals(conceptLabel.trim(), ignoreCase = true) } ?: return emptyList()
        val nodeMap = nodes.associateBy { it.id }

        return edges.filter { it.fromNodeId == target.id || it.toNodeId == target.id }.map { edge ->
            val from = nodeMap[edge.fromNodeId]?.label ?: "?"
            val to = nodeMap[edge.toNodeId]?.label ?: "?"
            "$from —[${edge.relationship}]→ $to"
        }
    }

    suspend fun clearAll() = store.clearAll()
}
