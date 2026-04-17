package me.eternal.purrfectsnap.ui.manager.pages.scripting.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.util.ktx.openLink
import me.eternal.purrfectsnap.ui.manager.components.AestheticEmptyState
import me.eternal.purrfectsnap.ui.manager.pages.scripting.ScriptCatalog
import me.eternal.purrfectsnap.ui.manager.pages.scripting.ScriptingRootSection
import me.eternal.purrfectsnap.common.ui.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.util.chooseFolder
import me.eternal.purrfectsnap.ui.util.pullrefresh.PullRefreshIndicator
import me.eternal.purrfectsnap.ui.util.pullrefresh.pullRefresh
import me.eternal.purrfectsnap.ui.util.pullrefresh.rememberPullRefreshState
import me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components.LegacyBackground

import androidx.compose.ui.zIndex
import me.eternal.purrfectsnap.ui.util.headerHeightTracker

/**
 * Modularized Scripting screen for the theme isolation architecture.
 * 
 * This module implements the visual aesthetic as extension functions of the base logic class.
 * Core state and logic are hosted within [ScriptingRootSection], while these functions
 * handle the layout and component structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptingRootSection.LegacyScriptingContent(nav: NavBackStackEntry) {
    val coroutineScope = rememberCoroutineScope()
    val scriptingFolder by rememberAsyncMutableState(
        defaultValue = null,
        updateDispatcher = reloadDispatcher
    ) { context.scriptManager.getScriptsFolder() }
    val tabTitles = listOf(translation["installed_scripts_tab"], translation["catalog_tab"])
    var showImportDialog by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    var controlsHeight by remember { mutableStateOf(96.dp) }

    LaunchedEffect(scriptingFolder) {
        if (scriptingFolder == null && selectedTab != 0) {
            selectedTab = 0
        }
    }

    if (showImportDialog) {
        ImportRemoteScript { showImportDialog = false }
    }
    if (showToast) {
        LaunchedEffect(showToast) {
            context.shortToast(translation["select_scripts_folder_toast"])
            showToast = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LegacyBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + controlsHeight + 8.dp))
            
            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                when (selectedTab) {
                    0 -> InstalledTabContent(scriptingFolder = scriptingFolder)
                    1 -> CatalogTabContent(scriptingFolder = scriptingFolder)
                }
            }
        }

        // Floating Header (v1.3.9 Layout)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 0.dp)
                .zIndex(1f)
                .headerHeightTracker { height ->
                    if (height != controlsHeight) controlsHeight = height
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
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = translation["manager.routes.scripts"] ?: "Scripting",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                            Row(
                                modifier = Modifier.wrapContentWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { 
                                    context.androidContext.openLink("https://github.com/SnapEnhance/scripting-docs", context.translation["toast_open_link_failed"])
                                }) {
                                    Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = Color.White)
                                }
                                IconButton(onClick = { routes.manageScriptRepos.navigate() }) {
                                    Icon(Icons.Default.Public, contentDescription = null, tint = Color.White)
                                }
                                IconButton(onClick = { 
                                    if (scriptingFolder == null) showToast = true
                                    else scriptingFolder?.let { context.androidContext.openLink(it.uri.toString(), context.translation["toast_open_link_failed"]) }
                                }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                                }
                                IconButton(onClick = { if (scriptingFolder == null) showToast = true else showImportDialog = true }, enabled = scriptingFolder != null) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = if (scriptingFolder != null) Color.White else Color.White.copy(alpha = 0.4f))
                                }
                            }
                        }
                        
                        // Tab Switcher implementation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            tabTitles.forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f),
                                    border = if (isSelected) BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary, PurrfectPalette.glowSecondary))) else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { 
                                                if (index == 1 && scriptingFolder == null) showToast = true
                                                else selectedTab = index
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (index == 0) Icons.Default.Extension else Icons.Default.Public,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Text(
                                            text = title ?: "",
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders the content for the 'Installed' scripts tab.
 */
@Composable
fun ScriptingRootSection.InstalledTabContent(scriptingFolder: DocumentFile?) {
    val scriptModules by rememberAsyncMutableState(
        defaultValue = emptyList(),
        updateDispatcher = reloadDispatcher
    ) { context.scriptManager.sync(); context.scriptManager.getSyncedModules() }
    val coroutineScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        refreshing = true
        withContext(Dispatchers.IO) {
            reloadDispatcher.dispatch()
            refreshing = false
        }
    }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = {
        refreshing = true
        coroutineScope.launch(Dispatchers.IO) {
            reloadDispatcher.dispatch()
            refreshing = false
        }
    })

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.04f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState),
                contentPadding = PaddingValues(bottom = routes.bottomPadding + 28.dp, start = 8.dp, end = 8.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (scriptingFolder == null && !refreshing) {
                        NoFolderState(coroutineScope)
                    } else if (scriptModules.isEmpty() && !refreshing) {
                        AestheticEmptyState(
                            icon = Icons.Default.DataObject,
                            title = translation["no_scripts_found_title"],
                            subtitle = translation["use_catalog_to_add_scripts"],
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                        )
                    }
                }
                items(scriptModules.size, key = { scriptModules[it].hashCode() }) { index ->
                    ModuleItem(scriptModules[index])
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
    }
}

/**
 * State shown when no scripts folder has been selected.
 */
@Composable
private fun ScriptingRootSection.NoFolderState(coroutineScope: kotlinx.coroutines.CoroutineScope) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.6f), PurrfectPalette.glowSecondary.copy(alpha = 0.45f))))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = PurrfectPalette.glowSecondary, modifier = Modifier.size(28.dp))
            }
            Text(text = translation["no_scripts_folder_selected_title"], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Color.White)
            Text(text = translation["select_scripts_folder_toast"], style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = PurrfectPalette.textSecondary, lineHeight = 18.sp)
            SelectFolderButton(onClick = {
                activityLauncherHelper.chooseFolder {
                    context.config.root.scripting.moduleFolder.set(it)
                    context.config.writeConfig()
                    coroutineScope.launch { reloadDispatcher.dispatch() }
                }
            })
        }
    }
}

/**
 * Button for selecting the scripts directory.
 */
@Composable
private fun ScriptingRootSection.SelectFolderButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(78.dp),
        shape = CircleShape,
        color = PurrfectPalette.cardOverlayColor.copy(alpha = 0.9f),
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.7f), PurrfectPalette.glowSecondary.copy(alpha = 0.65f)))),
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(6.dp).size(66.dp).clip(CircleShape).background(Brush.radialGradient(colors = listOf(PurrfectPalette.glowPrimary.copy(alpha = 0.42f), PurrfectPalette.glowSecondary.copy(alpha = 0.34f)))), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

/**
 * Renders the content for the script catalog tab.
 */
@Composable
fun ScriptingRootSection.CatalogTabContent(scriptingFolder: DocumentFile?) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.04f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxSize()
    ) {
        if (scriptingFolder == null) {
            AestheticEmptyState(
                icon = Icons.Default.FolderOpen,
                title = translation["no_scripts_folder_selected_title"],
                subtitle = translation["select_scripts_folder_toast"],
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
            )
        } else {
            ScriptCatalog(this)
        }
    }
}

