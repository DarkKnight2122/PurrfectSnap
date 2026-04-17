package me.eternal.purrfectsnap.ui.manager.pages.tasks.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.ui.manager.pages.TasksRootSection
import me.eternal.purrfectsnap.common.ui.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components.LegacyBackground

/**
 * Modularized Tasks screen for the Legacy shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksRootSection.LegacyTasksContent(nav: NavBackStackEntry) {
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var alsoDeleteFiles by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var controlsHeight by remember { mutableStateOf(96.dp) }

    LaunchedEffect(Unit) {
        while (true) {
            fetchActiveTasks(this)
            fetchNewRecentTasks()
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LegacyBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + controlsHeight + 8.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.04f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = routes.bottomPadding + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        if (activeTasks.isEmpty() && recentTasks.isEmpty()) {
                            TasksEmptyState(text = translation["no_tasks"] ?: "No tasks")
                        }
                    }
                    items(activeTasks, key = { it.taskId }) { pendingTask ->
                        TaskCard(modifier = Modifier.fillMaxWidth(), pendingTask.task, pendingTask = pendingTask)
                    }
                    items(recentTasks, key = { it.hash }) { task ->
                        TaskCard(modifier = Modifier.fillMaxWidth(), task)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Floating Header (v1.3.9 Layout)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 0.dp)
                .zIndex(1f)
                .onGloballyPositioned { coordinates ->
                    val newHeight = with(density) { coordinates.size.height.toDp() }
                    if (newHeight != controlsHeight) controlsHeight = newHeight
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = PurrfectPalette.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.52f)
                        )
                    )
                )
            ) {
                Box {
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(PurrfectPalette.cardOverlay))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = context.translation["manager.routes.tasks"] ?: "Tasks",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (activeTasks.isNotEmpty()) {
                                    translation.format(
                                        "summary_active",
                                        "active" to activeTasks.size.toString(),
                                        "recent" to recentTasks.size.toString()
                                    )
                                } else {
                                    translation.format(
                                        "summary_idle",
                                        "recent" to recentTasks.size.toString()
                                    )
                                },
                                color = PurrfectPalette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (taskSelection.size > 1 && taskSelection.all { it.second?.type?.contains("video") == true }) {
                                Surface(
                                    onClick = {
                                        mergeSelection(
                                            taskSelection.toList().also { taskSelection.clear() }
                                                .map { it.first to it.second!! }
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        1.dp,
                                        Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Merge, contentDescription = translation["merge_button"], tint = Color.White, modifier = Modifier.size(18.dp))
                                        Text(translation["merge_button"] ?: "Merge", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.PlaylistAddCheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = translation.format("running_count", "count" to activeTasks.size.toString()),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            IconButton(onClick = { showConfirmDialog = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        val isSelection = taskSelection.isNotEmpty()
        val titleText = if (isSelection) {
            translation.format("remove_selected_tasks_confirm", "count" to taskSelection.size.toString())
        } else {
            translation["remove_all_tasks_confirm"]
        }
        val messageText = if (isSelection) translation["remove_selected_tasks_title"] else translation["remove_all_tasks_title"]

        TaskDangerDialog(
            visible = showConfirmDialog,
            title = titleText ?: "",
            message = messageText ?: "",
            showDeleteFiles = isSelection,
            deleteFilesChecked = alsoDeleteFiles,
            onToggleDeleteFiles = { alsoDeleteFiles = it },
            onConfirm = {
                showConfirmDialog = false
                clearTasks(alsoDeleteFiles, scope)
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

