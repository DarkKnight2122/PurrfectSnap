package me.eternal.purrfect.ui.manager.pages.features

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.config.*
import me.eternal.purrfect.common.config.FeatureNotice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.eternal.purrfect.common.ui.TopBarActionButton
import me.eternal.purrfect.common.ui.rememberAsyncMutableState
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfect.core.features.impl.experiments.RandomizedDeviceProfile
import me.eternal.purrfect.core.features.impl.experiments.RandomizedDeviceProfileStore
import me.eternal.purrfect.ui.manager.components.AestheticDialog
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.ui.manager.theme.aetherGlass
import me.eternal.purrfect.common.ui.util.G2RoundedRectangle
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.ManagerTheme
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import me.eternal.purrfect.ui.util.*
import me.eternal.purrfect.ui.util.Dialog
import me.eternal.purrfect.ui.util.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class FeaturesRootSection : Routes.Route() {
    override val title: @Composable (() -> Unit)? = @Composable {
        val navBackStackEntry by routes.navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val titleText = when (currentDestination?.route) {
            FEATURE_CONTAINER_ROUTE -> {
                navBackStackEntry?.arguments?.getString("name")?.let { containerName ->
                    allContainers[containerName]?.let {
                        context.translation[it.key.propertyName()]
                    }
                } ?: routeInfo.translatedKey?.value
            }
            SEARCH_FEATURE_ROUTE -> {
                translation["search_button"] ?: "Search"
            }
            else -> {
                routeInfo.translatedKey?.value
            }
        }
        Text(titleText ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    internal val alertDialogs by lazy { AlertDialogs(context.translation) }
    internal val gson by lazy { Gson() }
    internal val listTypeToken = object : TypeToken<List<String>>() {}.type

    companion object {
        const val FEATURE_CONTAINER_ROUTE = "feature_container/{name}"
        const val SEARCH_FEATURE_ROUTE = "search_feature/{keyword}"
    }

    internal fun featureRootContainer(): ConfigContainer {
        return when {
            context.isRedditMode -> context.config.root.reddit
            else -> context.config.root
        }
    }

    internal val allContainers: Map<String, PropertyPair<*>>
        get() {
        val containers = mutableMapOf<String, PropertyPair<*>>()
        fun queryContainerRecursive(container: ConfigContainer) {
            container.properties.forEach {
                if (
                    it.key.dataType.type == DataProcessors.Type.CONTAINER &&
                    isVisibleForCurrentTarget(container, it.key)
                ) {
                    containers[it.key.name] = (it.key to it.value).toPropertyPair() as PropertyPair<Any>
                    queryContainerRecursive(it.value.get() as ConfigContainer)
                }
            }
        }
        queryContainerRecursive(featureRootContainer())
        return containers
    }

    internal val allProperties: Map<PropertyKey<*>, PropertyValue<*>>
        get() {
        val properties = mutableMapOf<PropertyKey<*>, PropertyValue<*>>()
        allContainers.values.forEach {
            val container = it.value.get() as ConfigContainer
            container.properties.forEach { property ->
                properties[property.key] = property.value
            }
        }
        return properties
    }

    internal fun isSearchVisibleProperty(propertyKey: PropertyKey<*>): Boolean {
        return !propertyKey.params.flags.contains(ConfigFlag.HIDDEN)
    }

    internal fun isVisibleForCurrentTarget(container: ConfigContainer, propertyKey: PropertyKey<*>): Boolean {
        if (propertyKey.params.flags.contains(ConfigFlag.HIDDEN)) return false
        if (!context.isLimitedTargetMode && container === context.config.root && propertyKey.name == "reddit") return false
        return true
    }

    internal fun getFolderReadablePath(context: android.content.Context, folderUri: String?): String? {
        if (folderUri == null) return null
        return try {
            val uri = android.net.Uri.parse(folderUri)
            val path = uri.path ?: return folderUri
            if (path.contains("tree/")) {
                path.substringAfter("tree/").replace("primary:", "Internal Storage/").replace(":", "/")
            } else {
                folderUri
            }
        } catch (e: Exception) {
            folderUri
        }
    }

    internal fun isRandomizedProfileEnabled(): Boolean {
        return context.config.root.experimental.spoof.randomizeDeviceProfile.globalState == true
    }

    internal fun requestFreshRandomizedProfile() {
        val randomizeConfig = context.config.root.experimental.spoof.randomizeDeviceProfile
        val newToken = UUID.randomUUID().toString()
        
        val newProfile = RandomizedDeviceProfileStore.getOrCreate(context.androidContext, context.log, newToken)
        val profileJsonString = newProfile.toJson().toString()
        
        randomizeConfig.profileGenerationToken.set(newToken)
        randomizeConfig.currentProfileSnapshot.set(newProfile.toJson().toString(2))
        randomizeConfig.profileData.set(profileJsonString)
        
        context.config.writeConfig()
        
        context.androidContext.getSharedPreferences("purrfect_spoof", 0)
            .edit()
            .putString("randomized_device_profile", profileJsonString)
            .putString("randomized_device_profile_token", newToken)
            .putString("android_id", newProfile.androidId)
            .putString("advertising_id", newProfile.advertisingId)
            .putString("bluetooth_address", newProfile.bluetoothMacAddress)
            .putString("gsf_id", newProfile.gsfId)
            .putString("random_device", newProfile.deviceInfo.model)
            .putString("device_fingerprint", newProfile.buildFingerprint)
            .apply()
    }

    internal fun getRandomizedProfileSnapshot(): String {
        val legacyPrefs = context.androidContext.getSharedPreferences("purrfect_spoof", 0)
        val hookedProfileJson = legacyPrefs.getString("randomized_device_profile", null)
        
        if (!hookedProfileJson.isNullOrBlank()) {
            return hookedProfileJson
        }

        val configProfileJson = context.config.root.experimental.spoof.randomizeDeviceProfile.profileData.getNullable()
        if (!configProfileJson.isNullOrBlank()) {
            return configProfileJson
        }

        return context.translation["manager.dialogs.randomize_device_profile.empty"]
            ?: "No generated profile is available yet. Enable the feature in Snapchat first."
    }

    internal fun isRandomizedProfileActionProperty(propertyName: String): Boolean {
        return propertyName == "generate_fresh_profile_action" ||
            propertyName == "view_current_profile_action" ||
            propertyName == "backup_profile_action" ||
            propertyName == "restore_profile_action"
    }

    internal fun backupRandomizedProfile(onConfigChanged: () -> Unit) {
        val profileSnapshot = getRandomizedProfileSnapshot()
        if (profileSnapshot.startsWith("No generated profile")) {
            context.shortToast(
                context.translation["manager.dialogs.randomize_device_profile.empty"]
                    ?: "No generated profile is available yet. Enable the feature in Snapchat first."
            )
            return
        }
        activityLauncher {
            saveFile("randomized-device-profile.json", "application/json") { uri ->
                runCatching {
                    context.androidContext.contentResolver.openOutputStream(uri.toUri())?.bufferedWriter()?.use {
                        it.write(profileSnapshot)
                    } ?: error("Failed to open backup destination")
                    onConfigChanged()
                    context.shortToast("Randomized profile backup saved")
                }.onFailure {
                    context.log.error("Failed to back up randomized profile", it)
                    context.shortToast("Failed to back up randomized profile")
                }
            }
        }
    }

    internal fun restoreRandomizedProfile(onConfigChanged: () -> Unit) {
        activityLauncher {
            openFile("application/json") { uri ->
                runCatching {
                    val importedJson = context.androidContext.contentResolver.openInputStream(uri.toUri())
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?.trim()
                        ?: error("Failed to read randomized profile backup")
                    val profile = RandomizedDeviceProfile.fromJson(importedJson)
                    val generationToken = UUID.randomUUID().toString()
                    val profileJson = profile.toJson().toString()
                    
                    context.androidContext.getSharedPreferences("purrfect_spoof", 0)
                        .edit()
                        .putString("randomized_device_profile", profileJson)
                        .putString("randomized_device_profile_token", generationToken)
                        .putString("android_id", profile.androidId)
                        .putString("advertising_id", profile.advertisingId)
                        .putString("bluetooth_address", profile.bluetoothMacAddress)
                        .putString("gsf_id", profile.gsfId)
                        .putString("random_device", profile.deviceInfo.model)
                        .putString("device_fingerprint", profile.buildFingerprint)
                        .apply()

                    val randomizeConfig = context.config.root.experimental.spoof.randomizeDeviceProfile
                    randomizeConfig.profileGenerationToken.set(generationToken)
                    randomizeConfig.currentProfileSnapshot.set(profile.toJson().toString(2))
                    randomizeConfig.profileData.set(profileJson)
                    
                    context.config.writeConfig()
                    onConfigChanged()
                    context.shortToast("Randomized profile restored. Restart Snapchat to apply it.")
                }.onFailure {
                    context.log.error("Failed to restore randomized profile", it)
                    context.shortToast("Failed to restore randomized profile")
                }
            }
        }
    }

    fun navigateToMainRoot() {
        routes.navController.navigate(routeInfo.id, NavOptions.Builder()
            .setPopUpTo(routes.navController.graph.findStartDestination().id, false)
            .setLaunchSingleTop(true)
            .build()
        )
    }

    private fun activityLauncher(block: ActivityLauncherHelper.() -> Unit) {
        routes.activityLauncher.let(block)
    }

    internal val actions: @Composable () -> List<Triple<String, ImageVector, () -> Unit>> = {
        val haptic = LocalHapticFeedback.current
        remember {
            listOf(
                Triple(translation["export_option"] ?: "Export", Icons.Filled.SaveAlt) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (context.isRedditMode) {
                        routes.configExportSummary.navigate {
                            put("exportSensitiveData", "false")
                            put("includeSavedLocations", "false")
                        }
                    } else {
                        // showExportDialog = true (Handled by caller)
                    }
                },
                Triple(translation["import_option"] ?: "Import", Icons.Filled.FileDownload) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    activityLauncher {
                        openFile("application/json") { uriString ->
                            runCatching {
                                val uri = android.net.Uri.parse(uriString)
                                context.androidContext.contentResolver.openInputStream(uri)?.use {
                                    routes.configJsonForImport = it.readBytes().toString(Charsets.UTF_8)
                                    routes.configImportConfirmation.navigate()
                                }
                            }.onFailure { err ->
                                context.log.error("Failed to read config file", err)
                                context.longToast(translation.format("config_import_failure_toast", "error" to (err.message ?: "Unknown")))
                            }
                        }
                    }
                },
                Triple(translation["reset_option"] ?: "Reset", Icons.Filled.Refresh) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // showResetConfirmationDialog = true (Handled by caller)
                }
            )
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }
        val skin = LocalPurrfectSkin.current
        key(themeId, skin.id) {
            LaunchedEffect(themeId) {
                routes.navigation?.globalScrollOffset = 0
            }
            with(ManagerTheme.fromId(themeId).theme) {
                this@FeaturesRootSection.FeaturesScreen(nav)
            }
        }
    }

    override val customComposables: NavGraphBuilder.() -> Unit = {
        routeInfo.childIds.addAll(listOf(FEATURE_CONTAINER_ROUTE, SEARCH_FEATURE_ROUTE))

        composable(FEATURE_CONTAINER_ROUTE, enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(140)
            )
        }, exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(160)
            )
        }) { backStackEntry ->
            backStackEntry.arguments?.getString("name")?.let { containerName ->
                allContainers[containerName]?.let {
                    val containerTitle = translation[it.key.propertyName()]
                    val containerSubtitle = translation[it.key.propertyDescription()]
                    Container(
                        configContainer = it.value.get() as ConfigContainer,
                        stateKey = "${routeInfo.id}:container:$containerName",
                        sectionTitle = containerTitle,
                        sectionSubtitle = containerSubtitle,
                        onBack = { routes.navController.popBackStack() }
                    )
                }
            }
        }

        composable(SEARCH_FEATURE_ROUTE) { backStackEntry ->
            backStackEntry.arguments?.getString("keyword")?.let { keyword ->
                val properties = allProperties.filter {
                    isSearchVisibleProperty(it.key) && (
                        it.key.name.contains(keyword, ignoreCase = true) ||
                            context.translation[it.key.propertyName()].contains(keyword, ignoreCase = true) ||
                            context.translation[it.key.propertyDescription()].contains(keyword, ignoreCase = true)
                    )
                }.map { (it.key to it.value).toPropertyPair() }

                PropertiesView(
                    properties = properties,
                    stateKey = "${routeInfo.id}:search:$keyword",
                    isSearchResults = true,
                    searchKeyword = keyword,
                    enableGlobalSearch = true,
                    onBack = { navigateToMainRoot() }
                )
            }
        }
    }

    @Composable
    internal fun FeatureAuroraBackdrop(modifier: Modifier = Modifier) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(skin.backgroundGradient)
        )
    }

    @Composable
    internal fun NoticeBadge(text: String, color: Color) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val bgAlpha = if (skin.isDark) 0.16f else 0.32f
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = bgAlpha),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
        ) {
            Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    @Composable
    internal fun EmptyState(isSearchResults: Boolean) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val isAether = skin.id == "AETHER"
        val shape = RoundedCornerShape(24.dp)
        val border = if (isAether) SolidColor(skin.laserBorder.copy(alpha = 0.45f)) else Brush.linearGradient(
            listOf(
                skin.glowPrimary.copy(alpha = 0.45f),
                skin.glowSecondary.copy(alpha = 0.35f)
            )
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .aetherGlass(skin, 24.dp),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 10.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, border)
        ) {
            Box(modifier = Modifier.background(skin.cardOverlayColor.copy(alpha = 0.1f), shape)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = skin.glowPrimary.copy(alpha = 0.18f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = skin.textPrimary
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isSearchResults) "No matches found" else "Nothing to show",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = skin.textPrimary
                        )
                        Text(
                            text = if (isSearchResults) "Try a different keyword or clear filters." else "Toggle visibility with the new controls above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = skin.textPrimary.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun PropertyAction(
        property: PropertyPair<*>,
        onConfigChanged: () -> Unit,
        registerClickCallback: (() -> Unit) -> (() -> Unit)
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        var showDialog by remember { mutableStateOf(false) }
        var dialogComposable by remember { mutableStateOf<@Composable () -> Unit>({}) }
        var showRandomProfileProgressDialog by remember { mutableStateOf(false) }
        var randomProfileStatus by remember { mutableStateOf("") }
        var showCurrentRandomProfileDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        fun registerDialogOnClickCallback() = registerClickCallback { showDialog = true }

        if (showDialog) {
            Dialog(
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                ),
                onDismissRequest = { showDialog = false },
            ) {
                dialogComposable()
            }
        }

        val propertyValue = property.value
        val randomProfileEnabled = isRandomizedProfileEnabled()
        val isRandomizedProfileContainer = property.name == "randomize_device_profile"
        fun persistConfig() {
            context.config.writeConfig()
            context.mirrorRedditFeaturePrefs()
            onConfigChanged()
        }

        if (showRandomProfileProgressDialog) {
            AestheticDialog(
                onDismissRequest = {},
                title = context.translation["manager.dialogs.randomize_device_profile.title"]
                    ?: "Generating random device profile",
                text = randomProfileStatus,
                icon = Icons.Filled.AutoAwesome,
                confirmButtonText = "",
                onConfirm = {},
                loading = true,
                showIcon = false,
                showCloseButton = false,
                confirmEnabled = false
            )
        }

        if (showCurrentRandomProfileDialog) {
            val profileSnapshot = getRandomizedProfileSnapshot()
            val clipboardManager = LocalClipboardManager.current
            val parsedProfile = remember(profileSnapshot) {
                runCatching { RandomizedDeviceProfile.fromJson(profileSnapshot) }.getOrNull()
            }

            AestheticDialog(
                onDismissRequest = { showCurrentRandomProfileDialog = false },
                title = context.translation["manager.dialogs.randomize_device_profile.view_title"]
                    ?: "Current randomized profile",
                text = "",
                icon = Icons.Filled.Visibility,
                dismissButtonText = context.translation["button.copy"] ?: "Copy",
                onDismiss = {
                    clipboardManager.setText(AnnotatedString(profileSnapshot))
                    context.shortToast(
                        context.translation["manager.dialogs.randomize_device_profile.copied"]
                            ?: "Randomized profile copied"
                    )
                },
                confirmButtonText = context.translation["button.positive"],
                onConfirm = { showCurrentRandomProfileDialog = false },
                showCloseButton = false,
                customContent = {
                    if (parsedProfile != null) {
                        RandomizedProfileViewer(
                            profile = parsedProfile,
                            onCopy = { textToCopy ->
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                context.shortToast("Copied to clipboard")
                            }
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = profileSnapshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                                color = skin.textSecondary,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            )
        }

        if (property.key.params.flags.contains(ConfigFlag.USER_IMPORT)) {
            registerDialogOnClickCallback()
            dialogComposable = {
                var isEmpty by remember { mutableStateOf(false) }
                val files = rememberAsyncMutableStateList(defaultValue = listOf()) {
                    context.fileHandleManager.getStoredFiles {
                        property.key.params.filenameFilter?.invoke(it.name) == true
                    }.also {
                        isEmpty = it.isEmpty()
                        if (isEmpty) {
                            propertyValue.setAny(null)
                            persistConfig()
                        }
                    }
                }
                var selectedFile by remember(files.size) { mutableStateOf(files.firstOrNull { it.name == propertyValue.getNullable() }.also {
                    if (files.isNotEmpty() && it == null) propertyValue.setAny(null)
                    if (files.isNotEmpty() && it == null) persistConfig()
                }?.name) }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                skin.glowPrimary.copy(alpha = 0.45f),
                                skin.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .background(skin.cardOverlayColor, RoundedCornerShape(24.dp))
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = context.translation["manager.dialogs.file_imports.settings_select_file_hint"] ?: "Select File",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = skin.textPrimary
                                    )
                                    if (isEmpty) {
                                        Text(
                                            text = context.translation["manager.dialogs.file_imports.no_files_settings_hint"] ?: "No files found",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = skin.textPrimary.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(top = 4.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            text = translation["manager.dialogs.file_imports.settings_select_file_subtitle"] ?: "Pick a file to import",
                                            fontSize = 13.sp,
                                            color = skin.textPrimary.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            items(files, key = { it.name }) { file ->
                                val isSelected = selectedFile == file.name
                                val fileContent = rememberAsyncMutableState(defaultValue = "") {
                                    if (isSelected) {
                                        context.fileHandleManager.readFile(file.name) ?: ""
                                    } else ""
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFile = if (isSelected) null else file.name
                                                propertyValue.setAny(selectedFile)
                                                persistConfig()
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        color = skin.textPrimary.copy(alpha = 0.05f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Brush.linearGradient(
                                                listOf(
                                                    skin.glowPrimary.copy(alpha = 0.6f),
                                                    skin.glowSecondary.copy(alpha = 0.48f)
                                                )
                                            ) else SolidColor(skin.textPrimary.copy(alpha = 0.08f))
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = skin.glowPrimary.copy(alpha = 0.16f)
                                            ) {
                                                Icon(
                                                    Icons.Filled.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(10.dp),
                                                    tint = skin.textPrimary
                                                )
                                            }
                                            Text(
                                                text = file.name,
                                                modifier = Modifier.weight(1f),
                                                fontSize = 14.sp,
                                                lineHeight = 16.sp,
                                                color = skin.textPrimary
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = skin.glowSecondary.copy(alpha = 0.18f)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.padding(8.dp),
                                                        tint = skin.glowSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isSelected && fileContent.value.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                                                .fillMaxWidth()
                                        ) {
                                            ConfigPreviewer(configJson = fileContent.value)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return
        }

        if (property.key.params.flags.contains(ConfigFlag.FOLDER)) {
            IconButton(onClick = registerClickCallback {
                routes.activityLauncher.chooseFolder { uri ->
                    propertyValue.setAny(uri)
                    persistConfig()
                }
            }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = skin.textPrimary)
            }
            return
        }

        when (val dataType = remember { property.key.dataType.type }) {
            DataProcessors.Type.BOOLEAN -> {
                var state by remember { mutableStateOf(propertyValue.get() as Boolean) }
                val hapticFeedback = LocalHapticFeedback.current
                Switch(
                    checked = state,
                    onCheckedChange = { requestedState ->
                        if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        state = requestedState
                        propertyValue.setAny(requestedState)
                        persistConfig()
                    },
                    colors = purrfectSwitchColors()
                )
            }

            DataProcessors.Type.MAP_COORDINATES -> {
                registerDialogOnClickCallback()
                dialogComposable = {
                    alertDialogs.ChooseLocationDialog(property) {
                        showDialog = false
                    }
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp).clickable { showDialog = true },
                    color = skin.textPrimary,
                    text = (propertyValue.get() as Pair<*, *>).let {
                        "${it.first.toString().toFloatOrNull() ?: 0F}, ${it.second.toString().toFloatOrNull() ?: 0F}"
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                registerDialogOnClickCallback()

                dialogComposable = {
                    alertDialogs.UniqueSelectionDialog(property)
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp).clickable { showDialog = true },
                    color = skin.textPrimary,
                    text = (propertyValue.getNullable() as? String ?: "null").let {
                        property.key.propertyOption(context.translation, it)
                    }
                )
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION, DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                if (dataType == DataProcessors.Type.STRING && isRandomizedProfileActionProperty(property.name)) {
                    val actionLabel = when (property.name) {
                        "generate_fresh_profile_action" -> context.translation[property.key.propertyName()] ?: "Generate Fresh Profile"
                        "view_current_profile_action" -> context.translation[property.key.propertyName()] ?: "View Current Profile"
                        "backup_profile_action" -> context.translation[property.key.propertyName()] ?: "Backup Profile"
                        "restore_profile_action" -> context.translation[property.key.propertyName()] ?: "Restore Profile"
                        else -> property.name
                    }
                    Button(
                        onClick = {
                            if (property.name == "generate_fresh_profile_action") {
                                showRandomProfileProgressDialog = true
                                coroutineScope.launch {
                                    randomProfileStatus = context.translation["manager.dialogs.randomize_device_profile.phase.allocating"]
                                        ?: "Allocating a randomized device fingerprint"
                                    delay(260)
                                    requestFreshRandomizedProfile()
                                    persistConfig()
                                    randomProfileStatus = context.translation["manager.dialogs.randomize_device_profile.phase.finalizing"]
                                        ?: "Finalizing the all-in-one profile and disabling manual overrides"
                                    delay(260)
                                    showRandomProfileProgressDialog = false
                                    context.shortToast(
                                        context.translation["manager.dialogs.randomize_device_profile.refresh_requested"]
                                            ?: "Fresh randomized profile requested. Restart Snapchat to apply it."
                                    )
                                }
                            } else if (property.name == "view_current_profile_action") {
                                showCurrentRandomProfileDialog = true
                            } else if (property.name == "backup_profile_action") {
                                backupRandomizedProfile(onConfigChanged)
                            } else if (property.name == "restore_profile_action") {
                                restoreRandomizedProfile(onConfigChanged)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = skin.glowPrimary.copy(alpha = 0.28f),
                            contentColor = skin.textPrimary
                        )
                    ) {
                        Text(actionLabel, maxLines = 1)
                    }
                    return
                }

                dialogComposable = {
                    when (dataType) {
                        DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                            alertDialogs.MultipleSelectionDialog(property)
                        }
                        DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                            val isMessageListProperty = property.key.name.endsWith("_messages")
                            val isSleepWindowProperty = property.key.name.contains("sleep_window")
                            val isSnapchatPlusPurchaseDateProperty = property.key.name == "snapchat_plus_purchase_date"

                            if (isMessageListProperty) {
                                alertDialogs.MessageListPropertyDialog(property) { showDialog = false }
                            } else if (isSleepWindowProperty) {
                                alertDialogs.AutoOpenScheduleDialog(property as PropertyPair<String>) { showDialog = false }
                            } else if (isSnapchatPlusPurchaseDateProperty) {
                                alertDialogs.DatePickerPropertyDialog(property) { showDialog = false }
                            } else {
                                alertDialogs.KeyboardInputDialog(property) { showDialog = false }
                            }
                        }
                        else -> {}
                    }
                }

                val click = registerDialogOnClickCallback()
                if (dataType == DataProcessors.Type.INTEGER ||
                    dataType == DataProcessors.Type.FLOAT) {
                    ValueGlowChip(
                        text = propertyValue.get().toString(),
                        onClick = click
                    )
                } else {
                    val isMessageListProperty = property.key.name.endsWith("_messages")
                    val isSnapchatPlusPurchaseDateProperty = property.key.name == "snapchat_plus_purchase_date"
                    if (isMessageListProperty) {
                        val messageCount = try {
                            val messageList: List<String> = gson.fromJson(propertyValue.get().toString(), listTypeToken) ?: emptyList()
                            messageList.size
                        } catch (e: Exception) {
                            1
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = skin.textPrimary.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.12f)),
                            modifier = Modifier.clickable { click() }
                        ) {
                            Text(
                                text = translation.format("search_results_count", "count" to messageCount.toString()),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = skin.textPrimary
                            )
                        }
                    } else if (isSnapchatPlusPurchaseDateProperty) {
                        Button(
                            onClick = click,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = skin.glowPrimary.copy(alpha = 0.28f),
                                contentColor = skin.textPrimary
                            )
                        ) {
                            Text(translation["button.set"] ?: "Set")
                        }
                    } else {
                        IconButton(onClick = click) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = skin.textPrimary)
                        }
                    }
                }
            }

            DataProcessors.Type.INT_COLOR -> {
                dialogComposable = {
                    alertDialogs.ColorPickerPropertyDialog(property) {
                        showDialog = false
                    }
                }

                val click = registerDialogOnClickCallback()
                CircularAlphaTile(selectedColor = (propertyValue.getNullable() as? Int)?.let { Color(it) })
            }

            DataProcessors.Type.CONTAINER -> {
                val container = propertyValue.get() as ConfigContainer

                registerClickCallback {
                    routes.navController.navigate(FEATURE_CONTAINER_ROUTE.replace("{name}", property.name))
                }

                if (!container.hasGlobalState) return

                var state by remember { mutableStateOf(container.globalState ?: false) }

                Box(
                    modifier = Modifier
                        .padding(end = 15.dp),
                ) {

                    Box(modifier = Modifier
                        .height(50.dp)
                        .width(1.dp)
                        .background(
                            color = skin.glowPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(5.dp)
                        ))
                }

                val hapticFeedback = LocalHapticFeedback.current
                Switch(
                    checked = state,
                    onCheckedChange = { requestedState ->
                        if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (isRandomizedProfileContainer && requestedState) {
                            showRandomProfileProgressDialog = true
                            coroutineScope.launch {
                                randomProfileStatus = context.translation["manager.dialogs.randomize_device_profile.phase.allocating"]
                                    ?: "Allocating a randomized device fingerprint"
                                delay(260)
                                randomProfileStatus = context.translation["manager.dialogs.randomize_device_profile.phase.network"]
                                    ?: "Preparing network, locale, and telephony values"
                                delay(260)
                                container.globalState = true
                                state = true
                                persistConfig()
                                randomProfileStatus = context.translation["manager.dialogs.randomize_device_profile.done"]
                                    ?: "Randomized device profile generated"
                                delay(220)
                                showRandomProfileProgressDialog = false
                                context.log.info("Enabled randomized device profile mode from manager UI")
                            }
                            return@Switch
                        }
                        state = requestedState
                        container.globalState = requestedState
                        if (!requestedState && isRandomizedProfileContainer) {
                            context.log.info("Disabled randomized device profile mode from manager UI")
                        }
                        persistConfig()
                    },
                    colors = purrfectSwitchColors()
                )
            }
        }

    }

    @Composable
    internal fun ValueGlowChip(
        text: String,
        onClick: () -> Unit
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            shape = CircleShape,
            color = skin.textPrimary.copy(alpha = 0.08f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        skin.glowPrimary.copy(alpha = 0.55f),
                        skin.glowSecondary.copy(alpha = 0.45f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                skin.glowPrimary.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = skin.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    internal fun PropertyCard(
        property: PropertyPair<*>,
        configRefreshNonce: Int,
        onConfigChanged: () -> Unit,
        onOpen: (() -> Unit)? = null
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        var clickCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
        val noticeColorMap = remember {
            mapOf(
                FeatureNotice.UNSTABLE.key to Color(0xFFFFFB87),
                FeatureNotice.BAN_RISK.key to Color(0xFFFF8585),
                FeatureNotice.INTERNAL_BEHAVIOR.key to Color(0xFFFFFB87),
            )
        }

        val versionCheck = remember { property.key.params.versionCheck }
        val versionCheckPair = remember(property) { versionCheck?.checkVersion(context.installationSummary.snapchatInfo?.versionCode ?: return@remember null)}
        val isComponentDisabled = remember { versionCheckPair != null && versionCheck?.isDisabled == true }
        val isInteractionEnabled = !isComponentDisabled

        val cardShape = RoundedCornerShape(22.dp)
        val interactionSource = remember { MutableInteractionSource() }
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    skin.glowPrimary.copy(alpha = 0.55f),
                    skin.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        val cardBackground = skin.cardOverlay

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .graphicsLayer { if (!isInteractionEnabled) alpha = 0.5f }
                .clickable(
                    enabled = isInteractionEnabled,
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    onOpen?.invoke()
                    clickCallback?.invoke()
                }
                .scaleOnPress(interactionSource),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(cardBackground, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    property.key.params.icon?.let { icon ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = skin.glowPrimary.copy(alpha = 0.16f),
                            tonalElevation = 0.dp,
                            modifier = Modifier.size(62.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                skin.glowPrimary.copy(alpha = 0.35f),
                                                skin.glowSecondary.copy(alpha = 0.28f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = skin.textPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                            Text(
                                text = context.translation[property.key.propertyName()] ?: property.key.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = skin.textPrimary,
                                lineHeight = 20.sp
                            )
                            Text(
                                text = context.translation[property.key.propertyDescription()] ?: "",
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = skin.textSecondary
                            )

                        if (property.key.params.notices.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                property.key.params.notices.forEach {
                                    NoticeBadge(
                                        text = context.translation["features.notices.${it.key}"] ?: it.key,
                                        color = noticeColorMap[it.key] ?: Color(0xFFFFFB87)
                                    )
                                }
                            }
                        }

                        if (versionCheckPair != null) {
                            NoticeBadge(
                                text = context.translation.format(
                                    "manager.sections.features.${versionCheckPair.second.key}",
                                    "version" to versionCheckPair.first.first
                                ),
                                color = Color(0xFFFF8585)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        PropertyAction(property, onConfigChanged = onConfigChanged, registerClickCallback = { callback ->
                            if (property.key.propertyTranslationPath().startsWith("rules.properties")) {
                                clickCallback = {
                                    routes.manageRuleFeature.navigate {
                                        put("rule_type", property.key.name)
                                    }
                                }
                                return@PropertyAction clickCallback!!
                            }
                            clickCallback = callback
                            callback
                        })
                    }
                }
            }
        }
    }

    override val topBarActions: @Composable (RowScope.() -> Unit) = {}

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    internal fun FloatingControls(
        isSearchResults: Boolean,
        activeSectionTitle: String? = null,
        activeSectionSubtitle: String? = null,
        searchKeyword: String? = null,
        searchHistory: SnapshotStateList<String>,
        onSearchQueryChange: (String) -> Unit,
        onBack: (() -> Unit)? = null,
        scrollOffset: Int = 0,
        modifier: Modifier = Modifier,
        onHeightMeasured: (androidx.compose.ui.unit.Dp) -> Unit = {}
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        var showSearchBar by rememberSaveable { mutableStateOf(isSearchResults) }
        val focusRequester = remember { FocusRequester() }
        val isOverlay = activeSectionTitle != null
        var searchValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = searchKeyword.orEmpty(),
                    selection = TextRange(searchKeyword?.length ?: 0)
                )
            )
        }
        val searchEntries = remember { buildSearchEntries() }
        val combinedSuggestions by remember(searchValue.text, searchHistory, searchEntries) {
            derivedStateOf {
                val query = searchValue.text
                val historyMatches = searchHistory.filter {
                    query.isBlank() || it.contains(query, ignoreCase = true)
                }
                val fuzzy = fuzzySuggest(query, searchEntries)
                (historyMatches + fuzzy).distinct().take(6)
            }
        }

        fun updateSearch(keyword: String, record: Boolean = true) {
            val term = keyword.trim()
            onSearchQueryChange(term)
            if (record && term.isNotEmpty()) upsertHistory(term, searchHistory)
        }

        var showExportDropdownMenu by remember { mutableStateOf(false) }
        var showResetConfirmationDialog by remember { mutableStateOf(false) }
        var showExportDialog by remember { mutableStateOf(false) }

        if (showResetConfirmationDialog) {
            val haptic = LocalHapticFeedback.current
            Dialog(onDismissRequest = { showResetConfirmationDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = skin.cardOverlayColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 16.dp,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    skin.glowPrimary.copy(alpha = 0.55f),
                                    skin.glowSecondary.copy(alpha = 0.45f)
                                )
                            )
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = context.translation["manager.dialogs.reset_config.title"] ?: "Reset Config",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = skin.textPrimary
                            )
                            Text(
                                text = context.translation["manager.dialogs.reset_config.content"] ?: "Reset all settings to default?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = skin.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                            ) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showResetConfirmationDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = skin.textPrimary.copy(alpha = 0.08f),
                                        contentColor = skin.textPrimary
                                    )
                                ) {
                                    Text(text = context.translation["button.negative"])
                                }
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        context.resetActiveTargetConfig()
                                        context.shortToast(context.translation["manager.dialogs.reset_config.success_toast"] ?: "Reset successful")
                                        showResetConfirmationDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = skin.glowPrimary.copy(alpha = 0.32f),
                                        contentColor = skin.textPrimary
                                    )
                                ) {
                                    Text(text = context.translation["button.positive"])
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showExportDialog) {
            SensitiveDataDialog(
                onDismiss = { showExportDialog = false },
                onConfirm = { exportSensitiveData, includeSavedLocations ->
                    showExportDialog = false
                    routes.configExportSummary.navigate {
                        put("exportSensitiveData", exportSensitiveData.toString())
                        put("includeSavedLocations", includeSavedLocations.toString())
                    }
                }
            )
        }

        val haptic = LocalHapticFeedback.current
        val actions = remember {
            listOf(
                Triple(translation["export_option"] ?: "Export", Icons.Filled.SaveAlt) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (context.isRedditMode) {
                        routes.configExportSummary.navigate {
                            put("exportSensitiveData", "false")
                            put("includeSavedLocations", "false")
                        }
                    } else {
                        showExportDialog = true
                    }
                },
                Triple(translation["import_option"] ?: "Import", Icons.Filled.FileDownload) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    activityLauncher {
                        openFile("application/json") { uriString ->
                            runCatching {
                                val uri = android.net.Uri.parse(uriString)
                                context.androidContext.contentResolver.openInputStream(uri)?.use {
                                    routes.configJsonForImport = it.readBytes().toString(Charsets.UTF_8)
                                    routes.configImportConfirmation.navigate()
                                }
                            }.onFailure { err ->
                                context.log.error("Failed to read config file", err)
                                context.longToast(translation.format("config_import_failure_toast", "error" to (err.message ?: "Unknown")))
                            }
                        }
                    }
                },
                Triple(translation["reset_option"] ?: "Reset", Icons.Filled.Refresh) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showResetConfirmationDialog = true
                }
            )
        }

        val headerTitle = activeSectionTitle ?: translation["manager.routes.features"] ?: "Features"
        val subtitleText = when {
            isSearchResults -> translation["search_button"] ?: "Search"
            !activeSectionSubtitle.isNullOrBlank() -> activeSectionSubtitle
            else -> translation["manager.sections.features.subtitle"] ?: ""
        }

        LaunchedEffect(isSearchResults, searchKeyword) {
            if (isSearchResults || !searchKeyword.isNullOrBlank()) {
                showSearchBar = true
                if (!searchKeyword.isNullOrBlank()) {
                    val text = searchKeyword
                    searchValue = TextFieldValue(
                        text = text,
                        selection = TextRange(text.length)
                    )
                }
            }
        }

        Column(modifier = modifier.headerHeightTracker { onHeightMeasured(it) }) {
            if (isAphelion) {
                me.eternal.purrfect.ui.manager.components.FloatingTopBar(
                    title = headerTitle,
                    subtitle = if (showSearchBar) null else subtitleText,
                    onBack = onBack,
                    scrollOffset = scrollOffset,
                    enableMorph = true,
                    actions = {
                        if (showSearchBar) {
                            TextField(
                                value = searchValue,
                                onValueChange = { keywordValue ->
                                    searchValue = keywordValue
                                    if (keywordValue.text.isEmpty()) {
                                        updateSearch("", record = false)
                                    } else {
                                        updateSearch(keywordValue.text, record = false)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                placeholder = { Text(text = translation["search_button"] ?: "Search", color = skin.textPrimary.copy(alpha = 0.75f)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = skin.textPrimary
                                    )
                                },
                                trailingIcon = {
                                    if (searchValue.text.isNotEmpty()) {
                                        IconButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            searchValue = TextFieldValue("", TextRange(0))
                                            updateSearch("", record = false)
                                            if (isSearchResults) {
                                                if (isOverlay) {
                                                    routes.navController.popBackStack(routeInfo.id, false)
                                                }
                                            } else {
                                                showSearchBar = false
                                            }
                                        }) {
                                            Icon(Icons.Filled.Close, contentDescription = null, tint = skin.textPrimary)
                                        }
                                    }
                                },
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        updateSearch(searchValue.text, record = true)
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = skin.textPrimary,
                                    focusedTextColor = skin.textPrimary,
                                    unfocusedTextColor = skin.textPrimary,
                                    disabledTextColor = skin.textPrimary.copy(alpha = 0.65f),
                                    focusedPlaceholderColor = skin.textPrimary.copy(alpha = 0.75f),
                                    unfocusedPlaceholderColor = skin.textPrimary.copy(alpha = 0.75f),
                                    focusedLeadingIconColor = skin.textPrimary,
                                    unfocusedLeadingIconColor = skin.textPrimary.copy(alpha = 0.9f),
                                    focusedTrailingIconColor = skin.textPrimary,
                                    unfocusedTrailingIconColor = skin.textPrimary.copy(alpha = 0.9f)
                                )
                            )
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSearchBar = true
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = skin.textPrimary
                                )
                            }
                        }

                        if (context.activity != null) {
                            Box {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showExportDropdownMenu = !showExportDropdownMenu
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        tint = skin.textPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showExportDropdownMenu,
                                    onDismissRequest = { showExportDropdownMenu = false },
                                    offset = DpOffset(0.dp, 8.dp),
                                    containerColor = skin.cardOverlayColor,
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 8.dp,
                                    shadowElevation = 12.dp
                                ) {
                                    actions.forEach { (name, icon, action) ->
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = skin.textPrimary
                                                )
                                            },
                                            text = { Text(text = name ?: "", color = skin.textPrimary) },
                                            onClick = {
                                                action()
                                                showExportDropdownMenu = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = skin.textPrimary,
                                                leadingIconColor = skin.glowPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                val topBarShape = RoundedCornerShape(26.dp)
                val topBarBackground = remember { skin.cardOverlay }
                val topBarBorder = remember {
                    Brush.linearGradient(
                        listOf(
                            skin.glowPrimary.copy(alpha = 0.55f),
                            skin.glowSecondary.copy(alpha = 0.35f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .zIndex(1f)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = topBarShape,
                        color = skin.cardOverlayColor,
                        border = BorderStroke(1.dp, topBarBorder),
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(topBarShape)
                                    .background(topBarBackground)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (onBack != null) {
                                    IconButton(onClick = onBack) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = context.translation["common.back"],
                                            tint = skin.textPrimary
                                        )
                                    }
                                }
                                
                                if (showSearchBar) {
                                    TextField(
                                        value = searchValue,
                                        onValueChange = { keywordValue ->
                                            searchValue = keywordValue
                                            updateSearch(keywordValue.text, record = false)
                                        },
                                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                        singleLine = true,
                                        placeholder = { Text(text = translation["search_button"] ?: "Search", color = skin.textPrimary.copy(alpha = 0.7f)) },
                                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = skin.textPrimary, focusedTextColor = skin.textPrimary, unfocusedTextColor = skin.textPrimary)
                                    )
                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = headerTitle,
                                            color = skin.textPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = subtitleText,
                                            color = skin.textPrimary.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showSearchBar = !showSearchBar
                                    }) {
                                        Icon(
                                            imageVector = if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = skin.textPrimary
                                        )
                                    }

                                    if (context.activity != null) {
                                        Box {
                                            IconButton(onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showExportDropdownMenu = !showExportDropdownMenu
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = null,
                                                    tint = skin.textPrimary
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showExportDropdownMenu,
                                                onDismissRequest = { showExportDropdownMenu = false },
                                                offset = DpOffset(0.dp, 8.dp),
                                                containerColor = skin.cardOverlayColor,
                                                shape = RoundedCornerShape(14.dp),
                                                tonalElevation = 8.dp,
                                                shadowElevation = 12.dp
                                            ) {
                                                actions.forEach { (name, icon, action) ->
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = null,
                                                                tint = skin.glowPrimary
                                                            )
                                                        },
                                                        text = { Text(text = name ?: "", color = skin.textPrimary) },
                                                        onClick = {
                                                            action()
                                                            showExportDropdownMenu = false
                                                        },
                                                        colors = MenuDefaults.itemColors(
                                                            textColor = skin.textPrimary,
                                                            leadingIconColor = skin.glowPrimary
                                                        )
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

            if (showSearchBar && combinedSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    color = skin.cardOverlayColor,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            combinedSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        searchValue = TextFieldValue(suggestion, TextRange(suggestion.length))
                                        updateSearch(suggestion, record = true)
                                    },
                                    label = { Text(text = suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = skin.textPrimary) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = skin.textPrimary.copy(alpha = 0.08f),
                                        labelColor = skin.textPrimary,
                                        leadingIconContentColor = skin.textPrimary
                                    )
                                )
                            }
                        }
                        if (searchHistory.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        searchHistory.clear()
                                        saveSearchHistory(emptyList())
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = skin.textPrimary.copy(alpha = 0.85f))
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = translation["clear_history"] ?: "Clear history", color = skin.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun PropertiesView(
        properties: List<PropertyPair<*>>,
        stateKey: String,
        isSearchResults: Boolean = false,
        activeSectionTitle: String? = null,
        activeSectionSubtitle: String? = null,
        searchKeyword: String? = null,
        enableGlobalSearch: Boolean = false,
        onBack: (() -> Unit)? = null
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val density = LocalDensity.current
        var controlsHeight by remember { mutableStateOf(100.dp) }
        var configRefreshNonce by rememberSaveable { mutableStateOf(0) }
        
        val listState = rememberLazyListState()

        val sharedSearchHistory = remember { mutableStateListOf<String>().apply { addAll(loadSearchHistory()) } }
        var liveSearchQuery by rememberSaveable { mutableStateOf(searchKeyword.orEmpty()) }
        val isActiveSearch = isSearchResults || liveSearchQuery.isNotBlank()
        val globalSearchProperties = remember(enableGlobalSearch) {
            if (enableGlobalSearch) {
                allProperties.filter { isSearchVisibleProperty(it.key) }.map { (it.key to it.value).toPropertyPair() } as List<PropertyPair<Any>>
            } else {
                emptyList()
            }
        }
        val displayProperties = remember(properties, globalSearchProperties, liveSearchQuery, enableGlobalSearch) {
            val query = liveSearchQuery
            if (query.isBlank()) return@remember properties
            val candidates = if (enableGlobalSearch) (properties + globalSearchProperties) else properties
            candidates.filter {
                it.key.name.contains(query, ignoreCase = true) ||
                    context.translation[it.key.propertyName()].contains(query, ignoreCase = true) ||
                    context.translation[it.key.propertyDescription()].contains(query, ignoreCase = true)
            }
        }

        LaunchedEffect(searchKeyword) {
            if (searchKeyword != null && searchKeyword != liveSearchQuery) {
                liveSearchQuery = searchKeyword
            }
        }
        var lastAppliedQuery by remember { mutableStateOf(liveSearchQuery) }
        LaunchedEffect(liveSearchQuery) {
            if (liveSearchQuery != lastAppliedQuery) {
                if (!listState.isScrollInProgress) {
                    listState.scrollToItem(0)
                }
                lastAppliedQuery = liveSearchQuery
            }
        }

        val computedScrollOffset by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt()
                else listState.firstVisibleItemScrollOffset
            }
        }

        LaunchedEffect(listState) {
            androidx.compose.runtime.snapshotFlow { computedScrollOffset }.collect {
                routes.navigation?.globalScrollOffset = it
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            FeatureAuroraBackdrop()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    end = 6.dp,
                    top = controlsHeight,
                    bottom = routes.bottomPadding
                )
            ) {
                if (displayProperties.isEmpty()) {
                    item { EmptyState(isActiveSearch) }
                } else {
                    itemsIndexed(displayProperties, key = { _, item -> item.key.propertyName() }) { _, item ->
                        val onOpen = if (isActiveSearch && liveSearchQuery.isNotBlank()) {
                            {
                                upsertHistory(liveSearchQuery, sharedSearchHistory)
                            }
                        } else null
                        PropertyCard(
                            property = item,
                            configRefreshNonce = configRefreshNonce,
                            onConfigChanged = { configRefreshNonce++ },
                            onOpen = onOpen
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            FloatingControls(
                isSearchResults = isActiveSearch,
                activeSectionTitle = activeSectionTitle,
                activeSectionSubtitle = activeSectionSubtitle,
                searchKeyword = liveSearchQuery,
                searchHistory = sharedSearchHistory,
                onSearchQueryChange = { liveSearchQuery = it },
                onBack = onBack,
                scrollOffset = computedScrollOffset,
                onHeightMeasured = { controlsHeight = it }
            )
        }
    }

    override val floatingActionButton: @Composable () -> Unit = {
        fun saveConfig() {
            context.coroutineScope.launch(Dispatchers.IO) {
                context.config.writeConfig()
                context.mirrorRedditFeaturePrefs()
                context.log.verbose("saved config!")
            }
        }

        OnLifecycleEvent { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                saveConfig()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                saveConfig()
            }
        }
    }

    @Composable
    internal fun SensitiveDataDialog(
        onDismiss: () -> Unit,
        onConfirm: (exportSensitiveData: Boolean, includeSavedLocations: Boolean) -> Unit
    ) {
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val haptic = LocalHapticFeedback.current
        Dialog(onDismissRequest = onDismiss) {
            val includeSavedLocations = remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = skin.textPrimary.copy(alpha = 0.06f),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            skin.glowPrimary.copy(alpha = 0.55f),
                            skin.glowSecondary.copy(alpha = 0.45f)
                        )
                    )
                )
            ) {
                Box(modifier = Modifier.background(skin.cardOverlay)) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = context.translation["manager.dialogs.export_config.title"] ?: "Export Config",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = skin.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = context.translation["manager.dialogs.export_config.content"] ?: "Include sensitive data?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = skin.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = context.translation["include_saved_locations"] ?: "Include Saved Locations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = skin.textPrimary
                            )
                            Switch(
                                checked = includeSavedLocations.value,
                                onCheckedChange = {
                                    if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    includeSavedLocations.value = it
                                },
                                colors = purrfectSwitchColors()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = skin.textPrimary.copy(alpha = 0.08f),
                                    contentColor = skin.textPrimary
                                )
                            ) {
                                Text(context.translation["button.negative"])
                            }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm(true, includeSavedLocations.value)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = skin.glowPrimary.copy(alpha = 0.32f),
                                    contentColor = skin.textPrimary
                                )
                            ) {
                                Text(context.translation["button.positive"])
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun Container(
        configContainer: ConfigContainer,
        stateKey: String,
        sectionTitle: String? = null,
        sectionSubtitle: String? = null,
        searchKeyword: String? = null,
        onBack: (() -> Unit)? = null,
    ) {
        PropertiesView(
            properties = remember(configContainer.globalState) {
                configContainer.properties.map { (it.key to it.value).toPropertyPair() as PropertyPair<Any> }.filter {
                    isVisibleForCurrentTarget(configContainer, it.key) &&
                        (
                            configContainer !== context.config.root.experimental.spoof.randomizeDeviceProfile ||
                                configContainer.globalState == true ||
                                !isRandomizedProfileActionProperty(it.key.name)
                        )
                }
            },
            stateKey = stateKey,
            activeSectionTitle = sectionTitle,
            activeSectionSubtitle = sectionSubtitle,
            searchKeyword = searchKeyword,
            enableGlobalSearch = configContainer == featureRootContainer(),
            showWhatsAppHiddenUiElementsManager =
                configContainer === context.config.root.whatsapp.uiElements ||
                    configContainer === context.config.root.instagram.hiddenUiElements,
            showInstagramDeveloperTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.developer,
            showInstagramAdsAndLinksTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.adsAndLinks,
            showInstagramGhostSettingsTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.privacy,
            showInstagramQuickToggleTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.privacy.quickToggle,
            showInstagramDistractionFreeTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.distractionFree,
            showInstagramMiscTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.misc,
            showInstagramDownloaderTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.downloader,
            showInstagramEmojiFontTools = context.isInstagramMode &&
                configContainer === context.config.root.instagram.misc,
            onBack = onBack
        )
    }

    // Ported Reference Fuzzy Logic
    internal data class SearchEntry(val keyword: String, val tokens: List<String>)

    internal fun buildSearchEntries(): List<SearchEntry> {
        return allProperties.keys.mapNotNull { key ->
            if (!isSearchVisibleProperty(key)) return@mapNotNull null
            val name = context.translation[key.propertyName()]
            val description = context.translation[key.propertyDescription()]
            val tokens = listOfNotNull(name, description, key.name).map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) null else SearchEntry(keyword = name ?: key.name, tokens = tokens)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + cost)
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return curr[b.length]
    }

    private fun similarityScore(query: String, target: String): Float {
        val q = query.lowercase()
        val t = target.lowercase()
        val maxLen = max(q.length, t.length)
        if (maxLen == 0) return 1f
        val dist = levenshtein(q, t)
        return 1f - (dist.toFloat() / maxLen.toFloat())
    }

    internal fun fuzzySuggest(query: String, entries: List<SearchEntry>): List<String> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return entries.map { entry ->
            val best = entry.tokens.maxOfOrNull { similarityScore(q, it) } ?: 0f
            best to entry.keyword
        }.filter { it.first >= 0.45f }
            .sortedWith(compareByDescending<Pair<Float, String>> { it.first }.thenBy { it.second.length })
            .map { it.second }
            .distinct()
            .take(6)
    }

    internal fun loadSearchHistory(): List<String> {
        return context.sharedPreferences.getString("features_search_history", "")
            ?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    internal fun saveSearchHistory(history: List<String>) {
        context.sharedPreferences.edit().putString("features_search_history", history.joinToString("|")).apply()
    }

    internal fun upsertHistory(term: String, history: SnapshotStateList<String>) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return
        val existingIndex = history.indexOfFirst { it.equals(cleaned, ignoreCase = true) }
        if (existingIndex >= 0) history.removeAt(existingIndex)
        history.add(0, cleaned)
        while (history.size > 12) history.removeLast()
        saveSearchHistory(history.toList())
    }
}
