package me.eternal.purrfect.ui.manager.pages.themes.legacy

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.saveable.rememberSaveable
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfect.R
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.ui.rememberAsyncMutableState
import me.eternal.purrfect.common.util.ktx.openLink
import me.eternal.purrfect.ui.manager.data.UpdateDownloader
import me.eternal.purrfect.ui.manager.data.Updater
import me.eternal.purrfect.ui.manager.data.Updater.Channel
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import me.eternal.purrfect.ui.util.scaleOnPress
import me.eternal.purrfect.storage.getQuickTiles
import me.eternal.purrfect.storage.setQuickTiles
import me.eternal.purrfect.ui.manager.pages.home.QuickActionsDialog
import kotlinx.coroutines.launch

/**
 * Sacred Legacy Home View - Bit-perfect restoration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeRootSection.LegacyHomeView(
    nav: NavBackStackEntry,
    routes: me.eternal.purrfect.ui.manager.Routes
) {
    val skin = PurrfectPalette // Fixed classic palette
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }

    // State logic restoration
    val latestUpdate by rememberAsyncMutableState<me.eternal.purrfect.ui.manager.data.Updater.LatestRelease?>(defaultValue = null) {
        Updater.getLatestRelease(Channel.STABLE)
    }
    val downloadState by UpdateDownloader.downloadState.collectAsState()
    val downloadProgress by UpdateDownloader.downloadProgress.collectAsState()
    val isPurrAuraActive by rememberPreferenceBool("debug_test_mode", true)
    
    // Quick Actions Logic
    val isRedditMode = remember { context.activeTargetApp == me.eternal.purrfect.common.TargetApp.REDDIT }
    val activeCards = if (isRedditMode) redditCards else cards
    val allQuickTileNames = remember(activeCards) { activeCards.keys.map { it.first } }

    val selectedTiles = rememberAsyncMutableStateList<String>(defaultValue = if (isRedditMode) allQuickTileNames else emptyList()) {
        if (isRedditMode) return@rememberAsyncMutableStateList allQuickTileNames
        context.database.getQuickTiles()
    }

    var showQuickActionsMenu by rememberSaveable { mutableStateOf(false) }
    var showAnnouncementsDialog by rememberSaveable { mutableStateOf(false) }
    var announcementsText by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullChangelogDialog by rememberSaveable { mutableStateOf(false) }
    var fullChangelogText by rememberSaveable { mutableStateOf<String?>(null) }
    
    val channelLabel = "STABLE"
    val onShowQuickActionsMenu = { if (!isRedditMode) showQuickActionsMenu = true }

    fun onUpdateAction() {
        if (downloadState == UpdateDownloader.DownloadState.IDLE || downloadState == UpdateDownloader.DownloadState.FAILED) {
            latestUpdate?.let { update ->
                val deviceAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"
                val downloadUrl = if (deviceAbi.contains("64")) {
                    update.assetDownloads["arm64"] ?: update.releaseUrl
                } else {
                    update.assetDownloads["armv7"] ?: update.assetDownloads["arm64"] ?: update.releaseUrl
                }
                
                UpdateDownloader.downloadAndInstall(
                    remoteContext = context,
                    downloadUrl = downloadUrl,
                    fileName = "Purrfect${if (context.activeTargetApp == me.eternal.purrfect.common.TargetApp.REDDIT) "Reddit" else "Snap"}-${update.versionName}.apk",
                    scope = coroutineScope
                )
            }
        }
    }

    val onShowAnnouncements = {
        coroutineScope.launch {
            announcementsText = fetchTextWithFallback(announcementsUrls)
            showAnnouncementsDialog = true
        }
    }
    
    val onShowFullChangelog = {
        coroutineScope.launch {
            fullChangelogText = fetchTextWithFallback(changelogStableUrls)
            showFullChangelogDialog = true
        }
    }

    if (showAnnouncementsDialog) {
        me.eternal.purrfect.ui.manager.components.AestheticDialog(
            onDismissRequest = { showAnnouncementsDialog = false },
            title = translation["announcements_dialog_title"] ?: "Announcements",
            text = announcementsText ?: translation["announcements_dialog_loading"] ?: "Loading...",
            icon = Icons.Default.Campaign,
            confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
            onConfirm = { showAnnouncementsDialog = false },
            showCloseButton = false
        )
    }

    if (showFullChangelogDialog) {
        me.eternal.purrfect.ui.manager.components.AestheticDialog(
            onDismissRequest = { showFullChangelogDialog = false },
            title = translation["changelog_dialog_title"] ?: "Changelog",
            text = fullChangelogText ?: translation["changelog_dialog_loading"] ?: "Loading...",
            icon = Icons.Default.History,
            confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
            onConfirm = { showFullChangelogDialog = false },
            showCloseButton = false
        )
    }

    if (showQuickActionsMenu) {
        QuickActionsDialog(
            quickActions = activeCards,
            selectedQuickActions = selectedTiles,
            onDismiss = { showQuickActionsMenu = false },
            onSave = { updatedTiles ->
                selectedTiles.clear()
                selectedTiles.addAll(updatedTiles)
                context.database.setQuickTiles(updatedTiles)
                showQuickActionsMenu = false
            },
            translation = translation
        )
    }

    // Original Page Background from dev
    val pageBackgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF261F58),
            Color(0xFF302A6D),
            Color(0xFF241F52)
        )
    )

    // Original Quick Actions Gradient from dev
    val quickActionsGradientColors = listOf(
        Color(0xFF241C3E),
        Color(0xFF151127)
    )

    @Composable
    fun TopBarActionChip(
        icon: ImageVector,
        label: String? = null,
        contentDescription: String? = label,
        onClick: () -> Unit,
    ) {
        Surface(
            shape = RoundedCornerShape(40),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(40))
                    .clickable { onClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
                label?.let {
                    Text(text = it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    fun HeroBadge(text: String, onClick: () -> Unit = {}) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onClick() }
                .background(Color.White.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }

    @Composable
    fun ExternalLinkIcon(
        imageVector: ImageVector,
        onClick: () -> Unit,
        tint: Color,
        containerColor: Color
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .scaleOnPress(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
    }

    val scrollState = rememberScrollState()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = routes.bottomPadding + navigationBarPadding + 96.dp

    Box(modifier = Modifier.fillMaxSize().background(pageBackgroundGradient)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = contentBottomPadding)) {
            // Header Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = HomeRootSection.cardMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopBarActionChip(icon = Icons.Filled.Notifications) { onShowAnnouncements() }
                    TopBarActionChip(icon = Icons.Filled.Description) { onShowFullChangelog() }
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    me.eternal.purrfect.ui.manager.ManagerAssistantEntry(
                        context = context,
                        routes = routes,
                        style = me.eternal.purrfect.ui.manager.ManagerAssistantTriggerStyle.DEFAULT,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isRedditMode) {
                        TopBarActionChip(icon = Icons.Filled.BugReport, label = context.translation["manager.routes.home_logs"]) { routes.homeLogs.navigate() }
                    }
                    TopBarActionChip(icon = Icons.Filled.Info, label = translation["manager.routes.home_about"]) { routes.about.navigate() }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hero Section (Restored Bit-Perfect Radii & Gradients)
            val heroShape = RoundedCornerShape(36.dp)
            val gitHashShort = remember { (context.installationSummary.modInfo?.gitHash ?: BuildConfig.GIT_HASH).take(7) }
            val heroGradientColors = listOf(Color(0xFF5C4B99), Color(0xFF322B5E), Color(0xFF1B1836))

            Box(
                modifier = Modifier
                    .padding(horizontal = HomeRootSection.cardMargin, vertical = 6.dp)
                    .clip(heroShape)
                    .background(Brush.linearGradient(heroGradientColors))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), heroShape)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = buildAnnotatedString {
                                append("Purrfect")
                                withStyle(SpanStyle(color = if (isRedditMode) Color(0xFFFF4500) else Color(0xFFFFE100))) {
                                    append(if (isRedditMode) "Reddit" else "Snap")
                                }
                            },
                            color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = avenirNext
                        )
                        Text(text = "By ΞTΞRNAL", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontFamily = avenirNext)
                        Text(
                            text = if (isRedditMode) (translation["hero_tagline"] ?: "").replace("Snapchat", "Reddit") else translation["hero_tagline"] ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switch to ${if (isRedditMode) "Snapchat" else "Reddit"} in settings",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroBadge(translation.format("hero_version_label", "version" to BuildConfig.VERSION_NAME, "channel" to channelLabel))
                        gitHashShort.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }?.let {
                            HeroBadge(translation.format("hero_build_label", "build" to it))
                        }
                    }

                    if (latestUpdate != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(translation["update_title"] ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(translation.format("update_content", "version" to latestUpdate!!.versionName), color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                                }
                                AnimatedContent(targetState = downloadState, label = "UpdateDownload") { currentDownloadState ->
                                    when (currentDownloadState) {
                                        UpdateDownloader.DownloadState.IDLE,
                                        UpdateDownloader.DownloadState.FAILED -> {
                                            Button(onClick = { onUpdateAction() }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        UpdateDownloader.DownloadState.DOWNLOADING -> {
                                            CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(28.dp), color = Color.White)
                                        }
                                        UpdateDownloader.DownloadState.COMPLETED -> {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA3F0C2))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // System States
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                    ) {
                        val unifiedButtonWidth = 180.dp
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),        
                                modifier = Modifier.width(unifiedButtonWidth).height(46.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (isPurrAuraActive) skin.glowPrimary else Color(0xFF8C8CA3)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (isPurrAuraActive) translation["purr_aura_active_label"] ?: "" else translation["purr_aura_inactive_label"] ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { routes.settings.navigate() },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(translation.getOrNull("manager.routes.home_settings") ?: "Settings")
                            }
                        }
                    }

                    // Links
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://purrfectsnap.me", context.translation["toast_open_link_failed"]) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Site", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://github.com/particle-box/Purrfect", context.translation["toast_open_link_failed"]) }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = translation["github_button"] ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            ExternalLinkIcon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram), onClick = { context.androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"]) }, tint = Color.White, containerColor = Color.White.copy(alpha = 0.14f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions (Restored Original Background and Padding)
            AnimatedContent(targetState = selectedTiles.isNotEmpty(), label = "QuickActions") { hasQuickActions ->
                val quickCardShape = RoundedCornerShape(34.dp)
                Surface(
                    modifier = Modifier.padding(horizontal = HomeRootSection.cardMargin, vertical = 10.dp),
                    shape = quickCardShape, color = Color.Transparent, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(quickActionsGradientColors)).padding(horizontal = 24.dp, vertical = 28.dp).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!hasQuickActions) {
                            Text(translation["quick_actions_title"] ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))       
                            Spacer(modifier = Modifier.height(24.dp))
                            Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.White)
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { onShowQuickActionsMenu() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(translation["quick_actions_add_tile_button"] ?: "")
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(translation["quick_actions_title"] ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(translation.format("quick_actions_count_label", "count" to selectedTiles.size.toString()), fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { onShowQuickActionsMenu() }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White) ) {
                                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_manage), contentDescription = null, modifier = Modifier.size(18.dp))     
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(translation["quick_actions_manage_button"] ?: "")
                                }
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val spacing = 12.dp
                                val gridPadding = 8.dp
                                val preferredTileWidth = 100.dp
                                val columns = ((maxWidth + spacing) / (preferredTileWidth + spacing)).toInt().coerceAtLeast(2).coerceAtMost(4)
                                val computedWidth = (maxWidth - gridPadding * 2 - spacing * (columns - 1)) / columns
                                val tileWidth = if (computedWidth < preferredTileWidth) computedWidth else preferredTileWidth
                                
                                FlowRow(modifier = Modifier.fillMaxWidth().padding(gridPadding), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(spacing), maxItemsInEachRow = columns) {
                                    for (tileName in selectedTiles) {
                                        val cardEntry = activeCards.entries.find { it.key.first == tileName }
                                        if (cardEntry != null) {
                                            val interactionSource = remember { MutableInteractionSource() }
                                            Surface(
                                                modifier = Modifier.width(tileWidth).aspectRatio(1.05f).scaleOnPress(interactionSource).clickable { cardEntry.value(routes) },
                                                shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                                            ) {
                                                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(skin.glowPrimary.copy(alpha = 0.3f), skin.glowSecondary.copy(alpha = 0.22f)))).clipToBounds()) {
                                                    Column(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                        Icon(cardEntry.key.second, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(text = cardEntry.key.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            }
        }

        // Footer (Fixed position at the very bottom)
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = navigationBarPadding + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Purrfect v${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall)
        }
    }
}
