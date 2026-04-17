package me.eternal.purrfectsnap.ui.manager.pages.home

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.action.EnumQuickActions
import me.eternal.purrfectsnap.common.BuildConfig
import me.eternal.purrfectsnap.common.action.EnumAction
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfectsnap.storage.getQuickTiles
import me.eternal.purrfectsnap.storage.setQuickTiles
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.ManagerTheme
import me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader
import me.eternal.purrfectsnap.ui.manager.data.Updater
import me.eternal.purrfectsnap.ui.manager.data.Updater.Channel
import me.eternal.purrfectsnap.ui.manager.components.AestheticDialog
import me.eternal.purrfectsnap.ui.util.ActivityLauncherHelper
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Encapsulates all shared state and callbacks for the Home screen.
 * This is the "Brain" passed to the "Skin" (Themes).
 */
data class HomeState(
    val selectedTiles: List<String>,
    val latestUpdate: Updater.LatestRelease?,
    val downloadState: UpdateDownloader.DownloadState,
    val downloadProgress: Float,
    val channelLabel: String,
    val isPurrAuraActive: Boolean,
    val avenirNext: FontFamily,
    val onUpdateAction: () -> Unit,
    val onShowAnnouncements: () -> Unit,
    val onShowFullChangelog: () -> Unit,
    val onShowQuickActionsMenu: () -> Unit
)

class HomeRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.home") }

    @Composable
    fun HomeScreen(nav: NavBackStackEntry) {
        val haptic = LocalHapticFeedback.current
        val prefs = remember { context.sharedPreferences }
        val allQuickTileNames = remember(cards) { cards.keys.map { it.first } }
        
        val selectedTiles = rememberAsyncMutableStateList(defaultValue = allQuickTileNames) {
            val storedTiles = context.database.getQuickTiles().filter { it.isNotBlank() }
            val hasInitializedQuickTiles = prefs.getBoolean(QUICK_TILES_INITIALIZED_PREF, false)
            when {
                storedTiles.isNotEmpty() -> {
                    if (!hasInitializedQuickTiles) prefs.edit().putBoolean(QUICK_TILES_INITIALIZED_PREF, true).apply()
                    storedTiles
                }
                hasInitializedQuickTiles -> storedTiles
                else -> {
                    context.database.setQuickTiles(allQuickTileNames)
                    prefs.edit().putBoolean(QUICK_TILES_INITIALIZED_PREF, true).apply()
                    allQuickTileNames
                }
            }
        }

        val updateChannel = context.config.root.global.updateSettings.updateChannel.getNullable() ?: "stable"
        val channelLabel = if (updateChannel == "prerelease") translation["channel_label_prerelease"] ?: "" else translation["channel_label_stable"] ?: ""
        
        val latestUpdate by rememberAsyncMutableState(defaultValue = null, keys = arrayOf(updateChannel)) {
            val channel = if (updateChannel == "prerelease") Channel.PRERELEASE else Channel.STABLE
            Updater.getLatestRelease(channel)
        }

        val changelogUrl = if (updateChannel == "prerelease") "https://raw.githubusercontent.com/particle-box/PurrfectSnap/dev/changelogs-prerelease.txt" else "https://raw.githubusercontent.com/particle-box/PurrfectSnap/dev/changelogs-stable.txt"
        val downloadState by UpdateDownloader.downloadState.collectAsState()
        val downloadProgress by UpdateDownloader.downloadProgress.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val isPurrAuraActive by rememberPreferenceBool("debug_test_mode", true)
        val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }

        var showChangelogDialog by remember { mutableStateOf(false) }
        var changelogText by remember { mutableStateOf<String?>(null) }
        var changelogLoading by remember { mutableStateOf(false) }
        var changelogError by remember { mutableStateOf<String?>(null) }

        var showAnnouncementsDialog by remember { mutableStateOf(false) }
        var announcementsText by remember { mutableStateOf<String?>(null) }
        var announcementsLoading by remember { mutableStateOf(false) }
        var announcementsError by remember { mutableStateOf<String?>(null) }

        var showFullChangelogDialog by remember { mutableStateOf(false) }
        var fullChangelogText by remember { mutableStateOf<String?>(null) }
        var fullChangelogLoading by remember { mutableStateOf(false) }
        var fullChangelogError by remember { mutableStateOf<String?>(null) }

        fun loadChangelog(version: String, url: String) {
            if (changelogText != null) return
            changelogLoading = true
            changelogError = null
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                        val body = response.body?.string() ?: throw IllegalStateException("Empty body")
                        extractChangelogForVersion(body, version)
                    }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { changelogText = text; changelogLoading = false }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) { changelogError = e.message ?: "Failed to fetch"; changelogLoading = false }
                }
            }
        }

        fun loadAnnouncements() {
            if (announcementsText != null) return
            announcementsLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    OkHttpClient().newCall(Request.Builder().url(announcementsUrl).build()).execute().use { it.body?.string() ?: "" }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { announcementsText = text; announcementsLoading = false }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) { announcementsError = e.message ?: "Failed to fetch"; announcementsLoading = false }
                }
            }
        }

        fun loadFullChangelog(url: String) {
            if (fullChangelogText != null) return
            fullChangelogLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
                }.onSuccess { text ->
                    withContext(Dispatchers.Main) { fullChangelogText = text; fullChangelogLoading = false }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) { fullChangelogError = e.message ?: "Failed to fetch"; fullChangelogLoading = false }
                }
            }
        }

        val handleUpdateAction: () -> Unit = {
            latestUpdate?.let { latest ->
                val abiName = android.os.Build.SUPPORTED_ABIS.firstNotNullOfOrNull {
                    when (it) { "arm64-v8a" -> "arm64"; "armeabi-v7a" -> "armv7"; else -> null }
                }
                if (latest.workflowId != null) {
                    if (abiName != null) {
                        val artifactName = "purrfectsnap-${if (abiName == "arm64") "armv8" else "armv7"}-debug"
                        UpdateDownloader.downloadAndInstall(context, "https://nightly.link/particle-box/PurrfectSnap/actions/runs/${latest.workflowId}/$artifactName.zip", "$artifactName.zip", coroutineScope)
                    }
                } else {
                    abiName?.let { arch -> latest.assetDownloads[arch] }?.let { url ->
                        UpdateDownloader.downloadAndInstall(context, url, url.substringAfterLast('/'), coroutineScope)
                    }
                }
            }
        }

        var showQuickActionsMenu by remember { mutableStateOf(false) }

        val homeState = HomeState(
            selectedTiles = selectedTiles,
            latestUpdate = latestUpdate,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            channelLabel = channelLabel,
            isPurrAuraActive = isPurrAuraActive,
            avenirNext = avenirNext,
            onUpdateAction = { latestUpdate?.let { showChangelogDialog = true; loadChangelog(it.versionName, changelogUrl) } },
            onShowAnnouncements = { showAnnouncementsDialog = true; loadAnnouncements() },
            onShowFullChangelog = { showFullChangelogDialog = true; loadFullChangelog(changelogUrl) },
            onShowQuickActionsMenu = { showQuickActionsMenu = true }
        )

        // Routing via Theme Contract
        val themeId = context.config.root.global.uiSettings.managerTheme.get()
        key(themeId) {
            with(ManagerTheme.fromId(themeId).theme) {
                this@HomeRootSection.HomeScreen(nav, homeState)
            }
        }

        // Shared Dialogs (Maintainer friendly)
        if (showChangelogDialog) {
            AestheticDialog(
                onDismissRequest = { showChangelogDialog = false },
                title = translation["changelog_dialog_title"] ?: "Changelog",
                text = "", icon = Icons.Filled.Info,
                confirmButtonText = translation["changelog_dialog_update_button"] ?: "Update",
                onConfirm = { showChangelogDialog = false; handleUpdateAction() },
                dismissButtonText = translation["changelog_dialog_cancel_button"] ?: "Cancel",
                onDismiss = { showChangelogDialog = false },
                showCloseButton = false,
                customContent = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (changelogLoading) CircularProgressIndicator(color = Color.White)
                        else if (changelogError != null) Text(changelogError!!, color = Color.Red, fontSize = 14.sp)
                        else Text(changelogText ?: translation["changelog_dialog_empty"] ?: "", color = Color.White, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showAnnouncementsDialog) {
            AestheticDialog(
                onDismissRequest = { showAnnouncementsDialog = false },
                title = translation["announcements_dialog_title"] ?: "Announcements",
                text = "", icon = Icons.Filled.Notifications,
                confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
                onConfirm = { showAnnouncementsDialog = false },
                showCloseButton = false,
                customContent = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (announcementsLoading) CircularProgressIndicator(color = Color.White)
                        else if (announcementsError != null) Text(announcementsError!!, color = Color.Red, fontSize = 14.sp)
                        else Text(announcementsText ?: translation["announcements_dialog_empty"] ?: "", color = Color.White, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showFullChangelogDialog) {
            AestheticDialog(
                onDismissRequest = { showFullChangelogDialog = false },
                title = translation["changelog_dialog_title"] ?: "Changelog",
                text = "", icon = Icons.Filled.Description,
                confirmButtonText = translation["announcements_dialog_close_button"] ?: "Close",
                onConfirm = { showFullChangelogDialog = false },
                showCloseButton = false,
                customContent = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (fullChangelogLoading) CircularProgressIndicator(color = Color.White)
                        else if (fullChangelogError != null) Text(fullChangelogError!!, color = Color.Red, fontSize = 14.sp)
                        else Text(fullChangelogText ?: translation["changelog_dialog_empty"] ?: "", color = Color.White, fontSize = 14.sp)
                    }
                }
            )
        }

        if (showQuickActionsMenu) {
            QuickActionsDialog(
                quickActions = cards,
                selectedQuickActions = selectedTiles,
                onDismiss = { showQuickActionsMenu = false },
                onSave = { newList ->
                    val removed = selectedTiles.filter { it !in newList }
                    removed.forEach { clearTileSpan(it); clearTileOffset(it) }
                    selectedTiles.clear(); selectedTiles.addAll(newList)
                    context.coroutineScope.launch { context.database.setQuickTiles(selectedTiles) }
                    showQuickActionsMenu = false
                },
                translation = translation
            )
        }
    }

    companion object {
        internal const val QUICK_TILES_INITIALIZED_PREF = "quick_tiles_initialized"
        val cardMargin = 10.dp
    }

    internal val announcementsUrl = "https://raw.githubusercontent.com/particle-box/PurrfectSnap/dev/announcements.txt"
    private lateinit var activityLauncherHelper: ActivityLauncherHelper
    internal val cards by lazy {
        EnumQuickActions.entries.map {
            (context.translation["actions.${it.key}.name"] to it.icon) to it.action
        }.associate { it.first to it.second }.toMutableMap().apply {
            EnumAction.entries.forEach { action ->
                this[context.translation["actions.${action.key}.name"] to action.icon] = { context.launchActionIntent(action) }
            }
        }
    }

    @Composable
    internal fun rememberPreferenceBool(key: String, default: Boolean = false): State<Boolean> {
        val prefs = remember { context.sharedPreferences }
        val state = remember { mutableStateOf(prefs.getBoolean(key, default)) }
        DisposableEffect(prefs, key) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) { state.value = prefs.getBoolean(key, default) }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }

    internal fun resolveTileKey(name: String): String {
        val entry = EnumQuickActions.entries.find { context.translation["actions.${it.key}.name"] == name }
        if (entry != null) return "quick.${entry.key}"
        val actionEntry = EnumAction.entries.find { context.translation["actions.${it.key}.name"] == name }
        return if (actionEntry != null) "action.${actionEntry.key}" else name
    }

    internal fun clearTileSpan(name: String) {
        val key = resolveTileKey(name)
        context.sharedPreferences.edit().remove("quick_tile_size_$key").apply()
    }

    internal fun clearTileOffset(name: String) {
        val key = resolveTileKey(name)
        context.sharedPreferences.edit().remove("quick_tile_offset_$key").apply()
    }

    override val title: @Composable (() -> Unit)? = {}
    override val init: () -> Unit = { activityLauncherHelper = ActivityLauncherHelper(context.activity!!) }
    override val topBarActions: @Composable (RowScope.() -> Unit) = { }
    override val content: @Composable (NavBackStackEntry) -> Unit = { nav -> HomeScreen(nav) }

    internal fun extractChangelogForVersion(raw: String, version: String): String {
        val lines = raw.lines()
        val headerRegex = Regex("^\\s*#+\\s*v?${Regex.escape(version)}\\b", RegexOption.IGNORE_CASE)
        val collected = mutableListOf<String>()
        var collecting = false
        for (line in lines) {
            if (!collecting) {
                if (headerRegex.containsMatchIn(line)) { collecting = true }
                continue
            }
            if (line.trimStart().startsWith("#")) break
            collected.add(line)
        }
        return collected.joinToString("\n").trim()
    }
}
