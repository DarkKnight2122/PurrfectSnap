package me.eternal.purrfect.ui.manager.pages.home.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfect.LogLine
import me.eternal.purrfect.LogReader
import me.eternal.purrfect.ui.manager.pages.home.HomeLogs
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import me.eternal.purrfect.ui.manager.pages.themes.legacy.components.LegacyBackground
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import me.eternal.purrfect.ui.util.pullrefresh.PullRefreshIndicator
import me.eternal.purrfect.ui.util.pullrefresh.pullRefresh
import me.eternal.purrfect.ui.util.pullrefresh.rememberPullRefreshState

/**
 * Modularized Logs screen for the Legacy shell.
 */
@Composable
fun HomeLogs.LegacyLogsContent(nav: NavBackStackEntry) {
    val coroutineScope = rememberCoroutineScope()
    val composeContext = remember { context }
    var logReader by remember { mutableStateOf<LogReader?>(null) }
    val visibleLogs = remember { mutableStateListOf<LogLine>() }
    val mainExecutor = remember { context.androidContext.mainExecutor }
    var isRefreshing by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()
    var showDropDown by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    fun refreshLogs() {
        coroutineScope.launch {
            val readerResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.log.newReader { line ->
                        if (shouldHideLog(line)) return@newReader
                        mainExecutor.execute {
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
                val filteredLogs = withContext(Dispatchers.IO) {
                    (0 until reader.lineCount).mapNotNull { index ->
                        reader.getLogLine(index)?.takeUnless(::shouldHideLog)
                    }
                }
                visibleLogs.clear()
                visibleLogs.addAll(filteredLogs)
            }
            delay(220)
            if (visibleLogs.isNotEmpty()) {
                val targetIndex = (visibleLogs.size - 1).coerceAtLeast(0)
                logListState.scrollToItem(targetIndex)
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(externalRefreshTick.value) {
        if (externalRefreshTick.value > 0) {
            isRefreshing = true
            refreshLogs()
        }
    }

    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = {
        isRefreshing = true
        refreshLogs()
    })

    LaunchedEffect(Unit) {
        isRefreshing = true
        refreshLogs()
    }

    @Composable
    fun LogFilterDialog(onDismiss: () -> Unit) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            me.eternal.purrfect.core.ui.PurrfectOverlayTheme {
                me.eternal.purrfect.core.ui.PurrfectGlassCard(
                    title = translation["filter_logs_title"] ?: "Log Filters",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HomeLogs.LogCategory.entries.forEach { category ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                enabledCategories[category] = !(enabledCategories[category] ?: true)
                                                refreshLogs()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = enabledCategories[category] == true,
                                            onCheckedChange = { checked ->
                                                enabledCategories[category] = checked
                                                refreshLogs()
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = PurrfectPalette.glowPrimary,
                                                uncheckedColor = Color.White.copy(alpha = 0.3f),
                                                checkmarkColor = Color.White
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = translation[category.translationKey] ?: category.name,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = translation["button.positive"] ?: "Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        LogFilterDialog { showFilterDialog = false }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var controlsHeight by remember { mutableStateOf(96.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LegacyBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(statusBarHeight + controlsHeight + 8.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
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
                        modifier = Modifier.fillMaxSize(),
                        state = logListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 12.dp,
                            bottom = routes.bottomPadding + 22.dp
                        )
                    ) {
                        items(visibleLogs, key = { it.hashCode() }) { line ->
                            LogEntryCard(line = line, composeContext = context.androidContext)
                        }
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
                shape = RoundedCornerShape(28.dp),
                color = PurrfectPalette.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                        )
                    )
                )
            ) {
                Box {
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(28.dp)).background(PurrfectPalette.cardOverlay))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(onClick = { routes.navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                            }
                            Text(
                                text = translation["manager.routes.home_logs"] ?: "Logs",
                                color = PurrfectPalette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PurrfectPalette.glowSecondary
                                )
                            }
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filter Logs", tint = PurrfectPalette.glowSecondary)
                            }
                            IconButton(onClick = { isRefreshing = true; refreshLogs() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { showDropDown = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = PurrfectPalette.glowSecondary)    
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
                                        onClick = { clearLogsAndReload(); showDropDown = false },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = PurrfectPalette.glowPrimary) },
                                        text = { Text(translation["clear_logs_button"] ?: "Clear", color = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        onClick = { exportLogs(); showDropDown = false },
                                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = PurrfectPalette.glowSecondary) },
                                        text = { Text(translation["export_logs_button"] ?: "Export", color = Color.White) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
        )
    }
}
