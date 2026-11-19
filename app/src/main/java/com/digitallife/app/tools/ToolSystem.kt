package com.digitallife.app.tools

import com.digitallife.app.brain.MemoryEngine
import com.digitallife.app.core.model.ToolDefinition
import com.digitallife.app.core.model.ToolResult
import java.io.File

interface AgentTool {
    val definition: ToolDefinition
    suspend fun execute(input: String): ToolResult
}

class ToolRegistry(
    private val memoryEngine: MemoryEngine,
    private val workspaceDir: File
) {
    private val tools = mutableMapOf<String, AgentTool>()

    init {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()

        register(KnowledgeSearchTool(memoryEngine))
        register(FileReadTool(workspaceDir))
        register(FileWriteTool(workspaceDir))
        register(DocumentAnalyzeTool())
        register(CodeGenerateTool())
        register(WebResearchTool())
    }

    fun register(tool: AgentTool) {
        tools[tool.definition.name] = tool
    }

    fun listDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }

    fun getTool(name: String): AgentTool? = tools[name]
}

class KnowledgeSearchTool(private val memoryEngine: MemoryEngine) : AgentTool {
    override val definition = ToolDefinition(
        name = "knowledge_search",
        description = "Searches persistent local long-term memory for facts, procedures, and instructions.",
        inputSchema = "{ \"query\": \"string\" }",
        requiresInternet = false,
        requiresApproval = false
    )

    override suspend fun execute(input: String): ToolResult {
        val results = memoryEngine.retrieveRelevant(input, limit = 5)
        if (results.isEmpty()) {
            return ToolResult(success = true, output = "No matching memories found for '$input'.")
        }
        val formatted = results.joinToString("\n") { "- [${it.type.name}] ${it.title}: ${it.content}" }
        return ToolResult(success = true, output = formatted)
    }
}

class FileReadTool(private val workspaceDir: File) : AgentTool {
    override val definition = ToolDefinition(
        name = "file_read",
        description = "Reads a text file from the app's sandboxed workspace directory.",
        inputSchema = "{ \"filename\": \"string\" }",
        requiresInternet = false,
        requiresApproval = false
    )

    override suspend fun execute(input: String): ToolResult {
        val safeName = File(input.trim()).name
        val target = File(workspaceDir, safeName)
        if (!target.exists()) {
            return ToolResult(success = false, output = "", error = "File '$safeName' does not exist in workspace.")
        }
        return ToolResult(success = true, output = target.readText())
    }
}

class FileWriteTool(private val workspaceDir: File) : AgentTool {
    override val definition = ToolDefinition(
        name = "file_write",
        description = "Writes text content to a file in the app's sandboxed workspace directory.",
        inputSchema = "{ \"input\": \"filename::content\" }",
        requiresInternet = false,
        requiresApproval = true
    )

    override suspend fun execute(input: String): ToolResult {
        val parts = input.split("::", limit = 2)
        if (parts.size < 2) {
            return ToolResult(success = false, output = "", error = "Expected format 'filename::content'.")
        }
        val safeName = File(parts[0].trim()).name
        val target = File(workspaceDir, safeName)
        target.writeText(parts[1])
        return ToolResult(success = true, output = "Wrote ${parts[1].length} chars to ${target.name}.")
    }
}

class DocumentAnalyzeTool : AgentTool {
    override val definition = ToolDefinition(
        name = "document_analyze",
        description = "Analyzes local text/document structure (word count, key lines, headings).",
        inputSchema = "{ \"text\": \"string\" }",
        requiresInternet = false,
        requiresApproval = false
    )

    override suspend fun execute(input: String): ToolResult {
        val lines = input.lines().filter { it.isNotBlank() }
        val words = input.split(Regex("\\s+")).filter { it.isNotBlank() }
        return ToolResult(
            success = true,
            output = "Lines: ${lines.size}, Words: ${words.size}, Preview: ${lines.take(3).joinToString(" | ")}"
        )
    }
}

class CodeGenerateTool : AgentTool {
    override val definition = ToolDefinition(
        name = "code_generate",
        description = "Prepares structured code templates locally (requires local LLM for custom synthesis).",
        inputSchema = "{ \"spec\": \"string\" }",
        requiresInternet = false,
        requiresApproval = false
    )

    override suspend fun execute(input: String): ToolResult {
        return ToolResult(
            success = true,
            output = "// Generated local template for: $input\nfun executeTask() {\n    // TODO: Implement logic\n}"
        )
    }
}

class WebResearchTool : AgentTool {
    override val definition = ToolDefinition(
        name = "web_research",
        description = "Online-only tool. Requires internet connectivity and explicit user approval.",
        inputSchema = "{ \"query\": \"string\" }",
        requiresInternet = true,
        requiresApproval = true,
        isImplemented = true
    )

    override suspend fun execute(input: String): ToolResult {
        return ToolResult(
            success = false,
            output = "",
            error = "WebResearchTool requires active internet and explicit user approval; offline mode is active by default."
        )
    }
}
