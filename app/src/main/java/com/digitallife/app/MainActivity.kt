package com.digitallife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitallife.app.core.model.NavTab
import com.digitallife.app.ui.DigitalLifeViewModel
import com.digitallife.app.ui.screens.*
import com.digitallife.app.ui.theme.DigitalLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalLifeTheme {
                val vm: DigitalLifeViewModel = viewModel()
                var currentTab by remember { mutableStateOf(NavTab.OVERVIEW) }

                Scaffold(
                    bottomBar = {
                        Surface(tonalElevation = 4.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                NavTab.entries.forEach { tab ->
                                    FilterChip(
                                        selected = currentTab == tab,
                                        onClick = { currentTab = tab },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (currentTab) {
                            NavTab.OVERVIEW -> OverviewScreen(vm)
                            NavTab.BRAIN -> BrainScreen(vm)
                            NavTab.TEACH -> TeachScreen(vm)
                            NavTab.MEMORY -> MemoryScreen(vm)
                            NavTab.KNOWLEDGE -> KnowledgeExplorerScreen(vm)
                            NavTab.TASKS -> TasksScreen(vm)
                            NavTab.AUTONOMOUS -> AutonomousActivityScreen(vm)
                            NavTab.TOOLS -> ToolsScreen(vm)
                            NavTab.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
