package me.eternal.purrfectsnap.ui.manager.pages.themes.aphelion

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.common.ui.TopBarActionButton
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.task.*
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.pages.TasksRootSection
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.util.OnLifecycleEvent
import me.eternal.purrfectsnap.ui.util.coil.cacheKey
import me.eternal.purrfectsnap.ui.util.scaleOnPress
import me.eternal.purrfectsnap.ui.util.headerHeightTracker
import me.eternal.purrfectsnap.ui.util.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksRootSection.AphelionTasksScreen(nav: NavBackStackEntry) {
    val scrollState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var controlsHeight by remember { mutableStateOf(100.dp) }

    LaunchedEffect(scrollState.firstVisibleItemScrollOffset, scrollState.firstVisibleItemIndex) {
        val offset = if (scrollState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt() else scrollState.firstVisibleItemScrollOffset
        routes.navigation?.globalScrollOffset = offset
    }

    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var alsoDeleteFiles by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fetchActiveTasks(this)
    }

    DisposableEffect(Unit) {
        onDispose {
            taskSelection.clear()
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            fetchActiveTasks(scope)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurrfectPalette.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val subtitle = if (activeTasks.isNotEmpty()) {
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
            }

            me.eternal.purrfectsnap.ui.manager.components.FloatingTopBar(
                title = context.translation["manager.routes.tasks"] ?: "Tasks",
                subtitle = subtitle,
                scrollOffset = routes.navigation?.globalScrollOffset ?: 0,
                enableMorph = true,
                modifier = Modifier.headerHeightTracker { controlsHeight = it },
                actions = {
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
                            Icon(Icons.Filled.PlaylistAddCheckCircle, contentDescription = null, tint = Color.White)
                            Text(
                                text = translation.format("running_count", "count" to activeTasks.size.toString()),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (taskSelection.size > 1 && taskSelection.all { it.second?.type?.contains("video") == true }) {     
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mergeSelection(
                                    taskSelection.toList().also { taskSelection.clear() }
                                        .map { it.first to it.second!! }
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
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
                                Icon(Icons.Filled.Merge, contentDescription = translation["merge_button"], tint = Color.White)
                                Text(translation["merge_button"], color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)     
                        showConfirmDialog = true
                    }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = translation["clear_button_description"], tint = Color.White)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = controlsHeight,
                    bottom = routes.bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (activeTasks.isEmpty() && recentTasks.isEmpty()) {
                        AphelionTasksEmptyState(text = translation["no_tasks"] ?: "No tasks")
                    }
                }
                items(activeTasks, key = { it.taskId }) { pendingTask ->
                    TaskCard(modifier = Modifier.fillMaxWidth(), pendingTask.task, pendingTask = pendingTask)
                }
                items(recentTasks, key = { it.hash }) { task ->
                    TaskCard(modifier = Modifier.fillMaxWidth(), task)
                }
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    LaunchedEffect(remember { derivedStateOf { scrollState.firstVisibleItemIndex } }) {
                        fetchNewRecentTasks()
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
