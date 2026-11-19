package com.digitallife.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitallife.app.core.model.*
import com.digitallife.app.ui.DigitalLifeViewModel
import com.digitallife.app.ui.components.AvatarView
import com.digitallife.app.ui.theme.CyanAccent
import com.digitallife.app.ui.theme.ErrorRed
import com.digitallife.app.ui.theme.SuccessGreen

@Composable
fun OverviewScreen(vm: DigitalLifeViewModel) {
    val agentState by vm.agentState.collectAsState()
    val runtimeStatus by vm.runtimeStatus.collectAsState()
    val modelConfig by vm.modelConfig.collectAsState()
    val memories by vm.memories.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val activity by vm.activityLog.collectAsState()
    val initState by vm.initState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(state = agentState)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("DIGITAL LIFE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CyanAccent)
                        Text("Agent: LIFE-01 (Local-First)", style = MaterialTheme.typography.bodyMedium)
                        Text("Model: ${modelConfig.modelName}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Runtime: ${runtimeStatus.healthStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (runtimeStatus.isLoaded) SuccessGreen else ErrorRed
                        )
                    }
                }
            }
        }

        if (initState.hasFailed && initState.errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Local Model Initialization Notice", fontWeight = FontWeight.Bold, color = ErrorRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(initState.errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Memories", "${memories.size}", Modifier.weight(1f))
                StatCard("Tasks", "${tasks.size}", Modifier.weight(1f))
                StatCard("RAM", "${runtimeStatus.usedRamMb} MB", Modifier.weight(1f))
            }
        }

        item {
            Text("Recent System Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        items(activity.take(8)) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "[${event.category}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.isError) ErrorRed else CyanAccent
                    )
                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CyanAccent)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun BrainScreen(vm: DigitalLifeViewModel) {
    var query by remember { mutableStateOf("") }
    val output by vm.brainOutput.collectAsState()
    val agentState by vm.agentState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reasoning Engine (LIFE-01)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ask or reason with LIFE-01") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.askBrain(query) }, enabled = query.isNotBlank()) {
                Text("Reason")
            }
            if (agentState == AgentState.THINKING) {
                OutlinedButton(onClick = { vm.cancelCurrentTask() }) {
                    Text("Cancel")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                item {
                    Text(
                        output.ifBlank { "Awaiting query. Memory retrieval runs automatically before inference." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TeachScreen(vm: DigitalLifeViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var selectedType by remember { mutableStateOf(MemoryType.SEMANTIC) }
    val summary by vm.lastLearningSummary.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Teaching Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Concept / Title") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Knowledge, Instruction, or Example") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryType.entries.take(3).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name) }
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    vm.teachAgent(title, content, selectedType, category)
                    title = ""
                    content = ""
                },
                enabled = content.isNotBlank()
            ) {
                Text("Teach LIFE-01")
            }
        }
        summary?.let { s ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Learning Summary: ${s.title}", fontWeight = FontWeight.Bold, color = CyanAccent)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(s.summaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryScreen(vm: DigitalLifeViewModel) {
    val memories by vm.memories.collectAsState()
    var search by remember { mutableStateOf("") }
    val filtered = memories.filter {
        search.isBlank() || it.title.contains(search, true) || it.content.contains(search, true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Long-Term Memory (${memories.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search memories") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.title} [${item.type.name}]", fontWeight = FontWeight.Bold, color = CyanAccent)
                            TextButton(onClick = { vm.deleteMemory(item.id) }) { Text("Delete", color = ErrorRed) }
                        }
                        Text(item.content, style = MaterialTheme.typography.bodySmall)
                        if (item.isContradiction) {
                            Text("⚠ Contradiction flagged", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KnowledgeExplorerScreen(vm: DigitalLifeViewModel) {
    val nodes by vm.knowledgeNodes.collectAsState()
    val edges by vm.knowledgeEdges.collectAsState()
    val nodeMap = nodes.associateBy { it.id }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Knowledge Explorer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${nodes.size} Concepts • ${edges.size} Relationships", style = MaterialTheme.typography.bodySmall)
        }
        items(edges) { edge ->
            val from = nodeMap[edge.fromNodeId]?.label ?: "?"
            val to = nodeMap[edge.toNodeId]?.label ?: "?"
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("$from —[${edge.relationship}]→ $to", modifier = Modifier.padding(12.dp), color = CyanAccent)
            }
        }
        if (edges.isEmpty() && nodes.isNotEmpty()) {
            items(nodes) { node ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("${node.label} (${node.category})", modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
fun TasksScreen(vm: DigitalLifeViewModel) {
    var objective by remember { mutableStateOf("") }
    val tasks by vm.tasks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Autonomous Task Execution", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = objective, onValueChange = { objective = it }, label = { Text("Enter task objective") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.submitTask(objective); objective = "" }, enabled = objective.isNotBlank()) {
                Text("Plan & Execute")
            }
            OutlinedButton(onClick = { vm.cancelCurrentTask() }) {
                Text("Cancel")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${task.objective} [${task.status.name}]", fontWeight = FontWeight.Bold, color = CyanAccent)
                        task.steps.forEach { step ->
                            Text("• Step ${step.index + 1}: ${step.description} (${step.status.name})", style = MaterialTheme.typography.bodySmall)
                        }
                        if (task.evaluationSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(task.evaluationSummary, style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutonomousActivityScreen(vm: DigitalLifeViewModel) {
    val isEnabled by vm.autonomousCycle.isEnabled.collectAsState()
    val report by vm.autonomousCycle.report.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Autonomous Learning Cycle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("OBSERVE → UNDERSTAND → RETRIEVE → PLAN → EXECUTE → EVALUATE → REMEMBER", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = isEnabled, onCheckedChange = { vm.autonomousCycle.setEnabled(it) })
            Text(if (isEnabled) "Autonomous Mode Enabled" else "Autonomous Mode Paused")
        }
        Button(onClick = { vm.runAutonomousCycleOnce() }) {
            Text("Run Single Autonomous Cycle")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Cycle #${report.cycleCount} • Last Phase: ${report.lastPhase}", fontWeight = FontWeight.Bold)
                Text("Memories Reviewed: ${report.totalMemoriesReviewed}", style = MaterialTheme.typography.bodySmall)
                Text("Isolated Concepts: ${report.isolatedConceptsFound}", style = MaterialTheme.typography.bodySmall)
                report.suggestedObjectives.forEach { s ->
                    Text("• Suggestion: $s", style = MaterialTheme.typography.bodySmall, color = CyanAccent)
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(vm: DigitalLifeViewModel) {
    val tools = remember { vm.toolRegistry.listDefinitions() }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Modular Tool System", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(tools) { tool ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(tool.name, fontWeight = FontWeight.Bold, color = CyanAccent)
                    Text(tool.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Internet: ${if (tool.requiresInternet) "Required" else "Offline"} • Approval: ${if (tool.requiresApproval) "Required" else "Auto"}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: DigitalLifeViewModel) {
    val config by vm.modelConfig.collectAsState()
    val status by vm.runtimeStatus.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Local AI Control Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bundled Model: ${config.modelName}", fontWeight = FontWeight.Bold, color = CyanAccent)
                    Text("Quantization: ${config.quantization}", style = MaterialTheme.typography.bodySmall)
                    Text("Runtime: ${status.runtimeVersion}", style = MaterialTheme.typography.bodySmall)
                    Text("Backend: ${status.backendMode}", style = MaterialTheme.typography.bodySmall)
                    Text("Status: ${status.healthStatus}", style = MaterialTheme.typography.bodySmall)
                    Text("RAM Used: ${status.usedRamMb} / ${status.totalRamMb} MB", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("GPU Acceleration (Adreno / OpenCL)")
                Switch(
                    checked = config.useGpuAcceleration,
                    onCheckedChange = { vm.updateModelConfig(config.copy(useGpuAcceleration = it)) }
                )
            }
        }
        item {
            Button(onClick = { vm.initializeSystem() }) {
                Text("Re-initialize Local Runtime")
            }
        }
        item {
            OutlinedButton(onClick = { vm.resetAllData() }) {
                Text("Reset All Local Knowledge & Memory", color = ErrorRed)
            }
        }
    }
}
