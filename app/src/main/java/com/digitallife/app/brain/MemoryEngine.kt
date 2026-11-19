package com.digitallife.app.brain

import com.digitallife.app.core.model.MemoryItem
import com.digitallife.app.core.model.MemoryType
import com.digitallife.app.data.db.MemoryDao
import com.digitallife.app.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

interface MemoryStore {
    fun observeAll(): Flow<List<MemoryItem>>
    suspend fun getAll(): List<MemoryItem>
    suspend fun upsert(item: MemoryItem)
    suspend fun upsertAll(items: List<MemoryItem>)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
}

class RoomMemoryStore(private val dao: MemoryDao) : MemoryStore {
    override fun observeAll(): Flow<List<MemoryItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<MemoryItem> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(item: MemoryItem) = dao.upsert(item.toEntity())

    override suspend fun upsertAll(items: List<MemoryItem>) = dao.upsertAll(items.map { it.toEntity() })

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()

    private fun MemoryEntity.toDomain() = MemoryItem(
        id = id,
        type = MemoryType.valueOf(type),
        title = title,
        content = content,
        category = category,
        source = source,
        confidence = confidence,
        isContradiction = isContradiction,
        contradictsId = contradictsId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MemoryItem.toEntity() = MemoryEntity(
        id = id,
        type = type.name,
        title = title,
        content = content,
        category = category,
        source = source,
        confidence = confidence,
        isContradiction = isContradiction,
        contradictsId = contradictsId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

class InMemoryMemoryStore : MemoryStore {
    private val items = MutableStateFlow<List<MemoryItem>>(emptyList())

    override fun observeAll(): Flow<List<MemoryItem>> = items
    override suspend fun getAll(): List<MemoryItem> = items.value
    override suspend fun upsert(item: MemoryItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun upsertAll(newItems: List<MemoryItem>) {
        val map = items.value.associateBy { it.id }.toMutableMap()
        newItems.forEach { map[it.id] = it }
        items.value = map.values.toList()
    }
    override suspend fun deleteById(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
    override suspend fun deleteAll() {
        items.value = emptyList()
    }
}

data class SaveMemoryResult(
    val savedItem: MemoryItem,
    val isDuplicate: Boolean,
    val contradictionWith: MemoryItem? = null
)

class MemoryEngine(private val store: MemoryStore) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun observeMemories(): Flow<List<MemoryItem>> = store.observeAll()

    suspend fun getAllMemories(): List<MemoryItem> = store.getAll()

    suspend fun saveMemory(
        type: MemoryType,
        title: String,
        content: String,
        category: String = "General",
        source: String = "User"
    ): SaveMemoryResult {
        val existing = store.getAll()
        val normalizedContent = content.trim().lowercase()
        val normalizedTitle = title.trim().lowercase()

        // Duplicate detection
        val duplicate = existing.firstOrNull {
            it.content.trim().lowercase() == normalizedContent ||
                (it.title.trim().lowercase() == normalizedTitle && jaccardSimilarity(it.content, content) > 0.85f)
        }
        if (duplicate != null) {
            return SaveMemoryResult(savedItem = duplicate, isDuplicate = true)
        }

        // Contradiction detection (same title/subject, different content)
        val contradiction = existing.firstOrNull {
            it.title.trim().lowercase() == normalizedTitle && jaccardSimilarity(it.content, content) < 0.45f
        }

        val item = MemoryItem(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title.trim(),
            content = content.trim(),
            category = category.trim().ifEmpty { "General" },
            source = source,
            isContradiction = contradiction != null,
            contradictsId = contradiction?.id
        )
        store.upsert(item)
        return SaveMemoryResult(savedItem = item, isDuplicate = false, contradictionWith = contradiction)
    }

    suspend fun updateMemory(item: MemoryItem) {
        store.upsert(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: String) {
        store.deleteById(id)
    }

    suspend fun retrieveRelevant(query: String, limit: Int = 5): List<MemoryItem> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return store.getAll().take(limit)

        return store.getAll()
            .map { item ->
                val text = "${item.title} ${item.content} ${item.category}".lowercase()
                val score = tokens.count { text.contains(it) }.toFloat() / tokens.size.toFloat()
                item to score
            }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    suspend fun exportToJson(): String {
        val all = store.getAll()
        return json.encodeToString(all)
    }

    suspend fun importFromJson(rawJson: String): Result<Int> {
        return try {
            val decoded = json.decodeFromString<List<MemoryItem>>(rawJson)
            store.upsertAll(decoded)
            Result.success(decoded.size)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid memory backup JSON: ${e.message}", e))
        }
    }

    suspend fun clearAll() {
        store.deleteAll()
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
            .toSet()

    private fun jaccardSimilarity(a: String, b: String): Float {
        val setA = tokenize(a)
        val setB = tokenize(b)
        if (setA.isEmpty() && setB.isEmpty()) return 1f
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val intersection = setA.intersect(setB).size.toFloat()
        val union = setA.union(setB).size.toFloat()
        return intersection / union
    }
}
