package com.digitallife.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val content: String,
    val category: String,
    val source: String,
    val confidence: Float,
    val isContradiction: Boolean,
    val contradictsId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "knowledge_nodes")
data class KnowledgeNodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val category: String,
    val description: String,
    val createdAt: Long
)

@Entity(tableName = "knowledge_edges")
data class KnowledgeEdgeEntity(
    @PrimaryKey val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relationship: String,
    val weight: Float,
    val createdAt: Long
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val objective: String,
    val status: String,
    val stepsJson: String,
    val evaluationSummary: String,
    val createdAt: Long,
    val completedAt: Long?
)
