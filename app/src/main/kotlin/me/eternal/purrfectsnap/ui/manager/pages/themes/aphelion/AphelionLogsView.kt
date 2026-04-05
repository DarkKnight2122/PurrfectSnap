package me.eternal.purrfectsnap.ui.manager.pages.themes.aphelion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfectsnap.ui.manager.components.FloatingTopBar
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeLogs
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.util.headerHeightTracker
import me.eternal.purrfectsnap.ui.util.Motion
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeLogs.AphelionLogsScreen(nav: NavBackStackEntry) {
    val coroutineScope = rememberCoroutineScope()
    var controlsHeight by remember { mutableStateOf(100.dp) }
    val composeContext = LocalContext.current
    var logReader by remember { mutableStateOf<me.eternal.purrfectsnap.LogReader?>(null) }
    val visibleLogs = remember { mutableStateListOf<me.eternal.purrfectsnap.LogLine>() }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // FILTER STATE: Track the currently selected log category.
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    fun refreshLogs() {
        isRefreshing = true
        coroutineScope.launch(Dispatchers.IO) {
            val readerResult = runCatching {
                context.log.newReader { line ->
                    if (shouldHideLog(line)) return@newReader
                    
                    // Apply Category Filter during real-time updates
                    val passesFilter = selectedFilter?.let { filter ->
                        line.message.contains(filter, ignoreCase = true)
                    } ?: true
                    
                    if (passesFilter) {
                        coroutineScope.launch(Dispatchers.Main) {
                            visibleLogs.add(line)
                        }
                    }
                }
            }
            readerResult.onFailure {
                context.longToast(translation["read_logs_failed_toast"] ?: "Failed to read logs")
            }
            readerResult.getOrNull()?.let { reader ->
                logReader = reader
                val filteredLogs = (0 until reader.lineCount).mapNotNull { index ->
                    val line = reader.getLogLine(index) ?: return@mapNotNull null
                    if (shouldHideLog(line)) return@mapNotNull null
                    
                    // Apply Category Filter to historical logs
                    val passesFilter = selectedFilter?.let { filter ->
                        line.message.contains(filter, ignoreCase = true)
                    } ?: true
                    
                    if (passesFilter) line else null
                }
                withContext(Dispatchers.Main) {
                    visibleLogs.clear()
                    visibleLogs.addAll(filteredLogs)
                    if (visibleLogs.isNotEmpty()) {
                        logListState.scrollToItem((visibleLogs.size - 1).coerceAtLeast(0))
                    }
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(externalRefreshTick.value, selectedFilter) {
        refreshLogs()
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurrfectPalette.backgroundGradient)
    ) {
        var showDropDown by remember { mutableStateOf(false) }
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            if (visibleLogs.isEmpty() && logReader != null) {
                EmptyLogsState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = logListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = controlsHeight,
                        bottom = routes.bottomPadding + 12.dp
                    )
                ) {
                    items(visibleLogs) { line ->
                        LogEntryCard(line = line, composeContext = composeContext)
                    }
                }
            }
        }

        FloatingTopBar(
            title = context.translation["manager.routes.home_logs"] ?: "Logs",
            onBack = { routes.navController.popBackStack() },
            scrollOffset = if (logListState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt() else logListState.firstVisibleItemScrollOffset,
            enableMorph = true,
            modifier = Modifier.headerHeightTracker { controlsHeight = it },
            actions = {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
                IconButton(onClick = { refreshLogs() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showDropDown = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showDropDown,
                        onDismissRequest = { showDropDown = false },
                        offset = DpOffset(0.dp, 8.dp),
                        containerColor = Color(0xFF161821),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            onClick = {
                                clearLogsAndReload()
                                showDropDown = false
                            },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = PurrfectPalette.glowPrimary) },
                            text = { Text(translation["clear_logs_button"] ?: "Clear", color = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = PurrfectPalette.glowPrimary
                            )
                        )
                        DropdownMenuItem(
                            onClick = {
                                exportLogs()
                                showDropDown = false
                            },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = PurrfectPalette.glowSecondary) },
                            text = { Text(translation["export_logs_button"] ?: "Export", color = Color.White) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = PurrfectPalette.glowSecondary
                            )
                        )
                        
                        var showFilterSubMenu by remember { mutableStateOf(false) }
                        
                        DropdownMenuItem(
                            onClick = { showFilterSubMenu = true },
                            leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null, tint = Color.Cyan) },
                            text = { Text("Filter Logs", color = Color.White) },
                            trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White,
                                leadingIconColor = Color.Cyan
                            )
                        )

                        if (showFilterSubMenu) {
                            DropdownMenu(
                                expanded = showFilterSubMenu,
                                onDismissRequest = { 
                                    showFilterSubMenu = false
                                    showDropDown = false 
                                },
                                offset = DpOffset(180.dp, (-48).dp),
                                containerColor = Color(0xFF1A1D29),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                val filters = listOf(
                                    null to "Show All",
                                    "[SPLIT]" to "Splitting",
                                    "[AUTO-OPEN]" to "Auto-Open",
                                    "[RESOURCE]" to "Resource Aware"
                                )
                                filters.forEach { (tag, label) ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedFilter = tag
                                            showFilterSubMenu = false
                                            showDropDown = false
                                        },
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(label, color = if (selectedFilter == tag) Color.Cyan else Color.White)
                                                if (selectedFilter == tag) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
