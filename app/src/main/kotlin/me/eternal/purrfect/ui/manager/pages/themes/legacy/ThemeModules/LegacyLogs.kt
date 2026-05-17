package me.eternal.purrfect.ui.manager.pages.themes.legacy.ThemeModules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import me.eternal.purrfect.ui.util.pullrefresh.PullRefreshIndicator
import me.eternal.purrfect.ui.util.pullrefresh.pullRefresh
import me.eternal.purrfect.ui.util.pullrefresh.rememberPullRefreshState

import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import androidx.compose.ui.platform.LocalContext

/**
 * Modularized Logs screen for the Legacy shell.
 */
@Composable
fun HomeLogs.LegacyLogsScreen(nav: NavBackStackEntry) {
    val managerTheme = remember { context.config.root.global.uiSettings.managerTheme.get() }
    val skin = if (managerTheme == "APHELION") LocalPurrfectSkin.current else PurrfectPalette
    val coroutineScope = rememberCoroutineScope()
    val composeContext = remember { context }
    var logReader by remember { mutableStateOf<LogReader?>(null) }
    val visibleLogs = remember { mutableStateListOf<LogLine>() }
    val mainExecutor = remember { context.androidContext.mainExecutor }
    var isRefreshing by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()
    var showDropDown by remember { mutableStateOf(false) }

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
                context.longToast(translation["read_logs_failed_toast"])
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

    LaunchedEffect(externalRefreshTick.intValue) {
        if (externalRefreshTick.intValue > 0) {
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
                color = skin.textPrimary.copy(alpha = 0.04f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.08f))
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
                        items(visibleLogs.size, key = { visibleLogs[it].hashCode() }) { index ->
                            LogEntryCard(line = visibleLogs[index], composeContext = context.androidContext)
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
                color = skin.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            skin.glowPrimary.copy(alpha = 0.6f),
                            skin.glowSecondary.copy(alpha = 0.45f)
                        )
                    )
                )
            ) {
                Box {
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(28.dp)).background(skin.cardOverlay))
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = skin.textPrimary)
                            }
                            Text(
                                text = routeInfo.translatedKey?.value ?: translation["manager.routes.home_logs"] ?: "Logs",
                                color = skin.textPrimary,
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
                                    color = skin.glowSecondary
                                )
                            }
                            IconButton(onClick = { isRefreshing = true; refreshLogs() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = skin.textPrimary)
                            }
                            Box {
                                IconButton(onClick = { showDropDown = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = skin.glowSecondary)    
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
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = skin.glowPrimary) },
                                        text = { Text(translation["clear_logs_button"] ?: "Clear", color = skin.textPrimary) }
                                    )
                                    DropdownMenuItem(
                                        onClick = { exportLogs(); showDropDown = false },
                                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = skin.glowSecondary) },
                                        text = { Text(translation["export_logs_button"] ?: "Export", color = skin.textPrimary) }
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
