package me.eternal.purrfect.ui.manager.pages.features

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.common.config.*
import me.eternal.purrfect.common.config.FeatureNotice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.eternal.purrfect.common.ui.TopBarActionButton
import me.eternal.purrfect.common.ui.rememberAsyncMutableState
import me.eternal.purrfect.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfect.core.features.impl.experiments.RandomizedDeviceProfile
import me.eternal.purrfect.core.features.impl.experiments.RandomizedDeviceProfileStore
import me.eternal.purrfect.core.whatsapp.WhatsAppUiElementSelector
import me.eternal.purrfect.ui.manager.components.AestheticDialog
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.ManagerTheme
import me.eternal.purrfect.ui.manager.theme.PurrfectPalette
import me.eternal.purrfect.ui.util.*
import me.eternal.purrfect.ui.util.Dialog
import me.eternal.purrfect.ui.util.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        const val INSTAGRAM_EMOJI_FONT_URI = "content://me.eternal.purrfect.instagram.emoji/font"
    }

    internal fun featureRootContainer(): ConfigContainer {
        return when {
            context.isInstagramMode -> context.config.root.instagram
            context.isWhatsAppMode -> context.config.root.whatsapp
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
        if (
            !context.isLimitedTargetMode &&
            container === context.config.root &&
            propertyKey.name in setOf("reddit", "whatsapp", "instagram")
        ) return false
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
        
        // 1. Generate the profile immediately in the Manager app
        val newProfile = RandomizedDeviceProfileStore.getOrCreate(context.androidContext, context.log, newToken)
        val profileJsonString = newProfile.toJson().toString()
        
        // 2. Update ModConfig (Primary source)
        randomizeConfig.profileGenerationToken.set(newToken)
        randomizeConfig.currentProfileSnapshot.set(newProfile.toJson().toString(2))
        randomizeConfig.profileData.set(profileJsonString)
        
        // 3. Force immediate write to disk
        context.config.writeConfig()
        
        // 4. Sync with legacy SharedPreferences (Used by the Hook process)
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
        // Priority 1: Read from legacy SharedPreferences where the Hook process actually writes the generated profile
        val legacyPrefs = context.androidContext.getSharedPreferences("purrfect_spoof", 0)
        val hookedProfileJson = legacyPrefs.getString("randomized_device_profile", null)
        
        if (!hookedProfileJson.isNullOrBlank()) {
            return hookedProfileJson
        }

        // Priority 2: Fallback to ModConfig profileData (e.g. if a profile was manually imported but Snapchat hasn't launched yet)
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

    internal data class HiddenUiElementEntry(
        val value: String,
        val label: String,
        val isSelector: Boolean
    )

    internal data class HiddenUiIdCatalogEntry(
        val name: String,
        val hexId: String
    ) {
        val searchKey: String = "$name $hexId".lowercase()
        val displayTitle: String = if (hexId.isBlank()) name else "$name\n$hexId"
    }

    internal fun hiddenUiElementEntries(
        catalog: List<HiddenUiIdCatalogEntry> = emptyList()
    ): List<HiddenUiElementEntry> {
        val catalogByName = catalog.associateBy { it.name }
        val rawIds: String
        val rawSelectors: String
        if (context.isInstagramMode) {
            val uiElements = context.config.root.instagram.hiddenUiElements
            rawIds = uiElements.hiddenUiElementIds.getNullable().orEmpty()
            rawSelectors = uiElements.hiddenUiElementSelectors.getNullable().orEmpty()
        } else {
            val uiElements = context.config.root.whatsapp.uiElements
            rawIds = uiElements.hiddenUiElementIds.getNullable().orEmpty()
            rawSelectors = uiElements.hiddenUiElementSelectors.getNullable().orEmpty()
        }
        val ids = splitHiddenUiElementValues(rawIds)
            .map(::normalizeHiddenUiElementId)
            .filter { it.isNotEmpty() }
            .distinct()
        val selectors = splitHiddenUiElementValues(rawSelectors)
            .map(::normalizeHiddenUiElementSelector)
            .filter { it.isNotEmpty() }
            .distinct()

        return buildList {
            ids.forEach { id ->
                add(HiddenUiElementEntry(value = id, label = catalogByName[id]?.displayTitle ?: id, isSelector = false))
            }
            selectors.forEach { selector ->
                add(
                    HiddenUiElementEntry(
                        value = selector,
                        label = WhatsAppUiElementSelector.toDisplayName(selector),
                        isSelector = true
                    )
                )
            }
        }
    }

    internal fun selectedHiddenUiElementIds(): List<String> {
        val rawIds = if (context.isInstagramMode) {
            context.config.root.instagram.hiddenUiElements.hiddenUiElementIds.getNullable().orEmpty()
        } else {
            context.config.root.whatsapp.uiElements.hiddenUiElementIds.getNullable().orEmpty()
        }
        return splitHiddenUiElementValues(rawIds).map(::normalizeHiddenUiElementId).filter { it.isNotEmpty() }.distinct()
    }

    internal fun selectedHiddenUiElementSelectors(): List<String> {
        val rawSelectors = if (context.isInstagramMode) {
            context.config.root.instagram.hiddenUiElements.hiddenUiElementSelectors.getNullable().orEmpty()
        } else {
            context.config.root.whatsapp.uiElements.hiddenUiElementSelectors.getNullable().orEmpty()
        }
        return splitHiddenUiElementValues(rawSelectors).map(::normalizeHiddenUiElementSelector).filter { it.isNotEmpty() }.distinct()
    }

    internal fun loadInstagramHiddenUiIdCatalog(): List<HiddenUiIdCatalogEntry> {
        return runCatching {
            context.androidContext.assets.open("instagram_view_ids.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { rawLine ->
                    val line = rawLine.removePrefix("\uFEFF")
                    val parts = line.split('\t', limit = 2)
                    val name = parts.getOrNull(0)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    HiddenUiIdCatalogEntry(
                        name = name,
                        hexId = parts.getOrNull(1)?.trim().orEmpty()
                    )
                }.toList()
            }
        }.onFailure {
            context.log.warn("Failed to load Instagram hidden UI element catalog: ${it.message}")
        }.getOrDefault(emptyList())
    }

    internal fun splitHiddenUiElementValues(raw: String): List<String> {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    internal fun normalizeHiddenUiElementId(rawId: String): String {
        var clean = rawId.trim()
        if (clean.isEmpty()) return ""
        clean = clean.substringBefore('\t').trim()
        clean = clean.substringBefore(' ').trim()
        val slashIndex = clean.lastIndexOf('/')
        if (slashIndex >= 0 && slashIndex < clean.length - 1) clean = clean.substring(slashIndex + 1).trim()
        val dotIndex = clean.lastIndexOf(".id.")
        if (dotIndex >= 0 && dotIndex + 4 < clean.length) clean = clean.substring(dotIndex + 4).trim()
        return clean
    }

    internal fun normalizeHiddenUiElementSelector(rawSelector: String): String {
        val clean = rawSelector.trim()
        return if (clean.startsWith("selector:v1|")) clean else ""
    }

    internal fun persistHiddenUiElementValues(
        ids: List<String>,
        selectors: List<String>,
        onConfigChanged: () -> Unit
    ) {
        if (context.isInstagramMode) {
            val uiElements = context.config.root.instagram.hiddenUiElements
            uiElements.hiddenUiElementIds.set(ids.joinToString("\n"))
            uiElements.hiddenUiElementSelectors.set(selectors.joinToString("\n"))
        } else {
            val uiElements = context.config.root.whatsapp.uiElements
            uiElements.hiddenUiElementIds.set(ids.joinToString("\n"))
            uiElements.hiddenUiElementSelectors.set(selectors.joinToString("\n"))
        }
        context.config.writeConfig()
        context.mirrorWhatsAppFeaturePrefs()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()
    }

    internal fun removeHiddenUiElement(entry: HiddenUiElementEntry, onConfigChanged: () -> Unit) {
        val ids = selectedHiddenUiElementIds()
        val selectors = selectedHiddenUiElementSelectors()

        persistHiddenUiElementValues(
            ids = if (entry.isSelector) ids else ids.filterNot { it == entry.value },
            selectors = if (entry.isSelector) selectors.filterNot { it == entry.value } else selectors,
            onConfigChanged = onConfigChanged
        )
    }

    internal fun setHiddenUiElementHidden(idOrSelector: String, hidden: Boolean, onConfigChanged: () -> Unit) {
        val isSelector = WhatsAppUiElementSelector.isSelector(idOrSelector)
        val value = if (isSelector) normalizeHiddenUiElementSelector(idOrSelector) else normalizeHiddenUiElementId(idOrSelector)
        if (value.isEmpty()) return
        val ids = selectedHiddenUiElementIds().toMutableList()
        val selectors = selectedHiddenUiElementSelectors().toMutableList()
        val target = if (isSelector) selectors else ids
        if (hidden) {
            if (target.none { it == value }) target += value
        } else {
            target.removeAll { it == value }
        }
        persistHiddenUiElementValues(ids, selectors, onConfigChanged)
    }

    internal fun clearHiddenUiElements(onConfigChanged: () -> Unit) {
        persistHiddenUiElementValues(
            ids = emptyList(),
            selectors = emptyList(),
            onConfigChanged = onConfigChanged
        )
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
                    
                    // Save to local prefs for legacy compatibility
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
                    randomizeConfig.profileData.set(profileJson) // Shared storage fix
                    
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

    internal fun importInstagramEmojiFontFromManager(onConfigChanged: () -> Unit) {
        activityLauncher {
            openFile("*/*") { uriString ->
                if (uriString.isBlank()) return@openFile
                val uri = uriString.toUri()
                runCatching {
                    val appContext = context.androidContext
                    val displayName = queryDisplayName(uri).ifBlank { uri.lastPathSegment ?: "custom_emoji_font.ttf" }
                    val fontFile = File(File(appContext.filesDir, "emoji"), "custom_emoji_font.ttf")
                    fontFile.parentFile?.mkdirs()
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        fontFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to open selected font")
                    Typeface.createFromFile(fontFile)
                    makeWorldReadable(fontFile)

                    val emojiConfig = context.config.root.instagram.misc.customEmojiFont
                    context.config.root.instagram.misc.customEmojiFontEnabled.set(true)
                    emojiConfig.customEmojiFontName.set(displayName)
                    emojiConfig.customEmojiFontPath.set(fontFile.absolutePath)
                    emojiConfig.customEmojiFontUri.set(INSTAGRAM_EMOJI_FONT_URI)
                    persistInstagramConfig(onConfigChanged)
                    context.shortToast(
                        context.translation["features.properties.instagram.properties.misc.custom_emoji_font.imported_toast"]
                            ?: "Custom emoji font imported."
                    )
                }.onFailure {
                    context.log.error("Failed to import Instagram emoji font", it)
                    context.shortToast(
                        context.translation["features.properties.instagram.properties.misc.custom_emoji_font.failed_toast"]
                            ?: "Custom emoji font import failed"
                    )
                }
            }
        }
    }

    internal fun resetInstagramEmojiFont(onConfigChanged: () -> Unit) {
        runCatching {
            File(File(context.androidContext.filesDir, "emoji"), "custom_emoji_font.ttf").delete()
        }
        val emojiConfig = context.config.root.instagram.misc.customEmojiFont
        context.config.root.instagram.misc.customEmojiFontEnabled.set(false)
        emojiConfig.customEmojiFontName.set("")
        emojiConfig.customEmojiFontPath.set("")
        emojiConfig.customEmojiFontUri.set("")
        persistInstagramConfig(onConfigChanged)
        context.shortToast(
            context.translation["features.properties.instagram.properties.misc.custom_emoji_font.reset_toast"]
                ?: "Custom emoji font reset."
        )
    }

    internal fun queryDisplayName(uri: Uri): String {
        val resolver = context.androidContext.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                } else {
                    ""
                }
            }.orEmpty()
        }.getOrDefault("")
    }

    internal fun makeWorldReadable(file: File) {
        runCatching {
            file.parentFile?.setReadable(true, false)
            file.parentFile?.setExecutable(true, false)
            file.setReadable(true, false)
        }
    }

    internal fun installedInstagramPackages(): List<String> {
        val packageManager = context.androidContext.packageManager
        return Constants.INSTAGRAM_PACKAGE_NAMES.filter { packageName ->
            runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
        }
    }

    internal fun primaryInstagramPackage(): String {
        return installedInstagramPackages().firstOrNull() ?: Constants.INSTAGRAM_PACKAGE_NAME
    }

    internal val instagramDistractionKeys = listOf(
        "disableStories",
        "disableFeed",
        "disableReels",
        "disableReelsExceptDM",
        "disableExplore",
        "disableComments"
    )

    internal val instagramMiscMasterKeys = listOf(
        "disableStoryFlipping",
        "disableVideoAutoPlay",
        "feedVideosStartWithSound",
        "storiesStartWithSound",
        "showFollowerToast",
        "showFeatureToasts",
        "enableStoryMentions",
        "enableCopyComment",
        "enableCopyBio",
        "disableDoubleTapLike",
        "enableMonetTheme",
        "customEmojiFontEnabled",
        "enableShareSheetEmojiShortcuts",
        "enableActivityHistory",
        "enableNavigationTabCustomization",
        "enableConfirmRefresh",
        "enableNotesLocationSpoof",
        "enableHideChats",
        "stripShareTrackingParameters",
        "doNotSaveRecentSearches",
        "enableCustomDateFormat",
        "openLinksExternally",
        "replaceShareLinkDomain",
        "enableTeenAppIcons",
        "enableStoryTrayLongPressActions",
        "blockDmReelNotifications",
        "blockDmPostNotifications"
    )

    internal val instagramDownloaderMasterKeys = listOf(
        "enablePostDownload",
        "enableStoryDownload",
        "enableReelDownload",
        "enableProfileDownload",
        "enableReelThumbnailDownload",
        "enableStoryMarkSeenButton",
        "enableStoryRepostButton",
        "enableHighQualityStoryUpload",
        "enableGifCommentDownload",
        "enableDmAnyFileUpload"
    )

    internal val instagramSelectionStringProperties = setOf(
        "navigationTabHidden",
        "navigationTabOrder",
        "navigationDefaultTab",
        "storyRingSize",
        "notesSpoofMapLocation",
        "hiddenChatNames"
    )

    internal val instagramNavigationTabs = listOf(
        "home" to "Home",
        "search" to "Search",
        "reels" to "Reels",
        "create" to "Create",
        "direct" to "Direct",
        "shop" to "Shop",
        "profile" to "Profile"
    )

    internal fun persistInstagramConfig(onConfigChanged: () -> Unit) {
        context.config.writeConfig()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()
    }

    internal fun openInstagramDevOptionsFromManager(onConfigChanged: () -> Unit) {
        context.config.root.instagram.developer.isDevEnabled.set(true)
        context.config.writeConfig()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()

        val packages = installedInstagramPackages().ifEmpty { listOf(Constants.INSTAGRAM_PACKAGE_NAME) }
        var launched = false
        for (packageName in packages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://settings_devoptions"))
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.androidContext.startActivity(intent) }.isSuccess) {
                launched = true
                break
            }
        }
        if (!launched) {
            context.shortToast("Unable to open Instagram developer options")
        }
    }

    internal fun isInstagramAdsAndLinksCoreEnabled(): Boolean {
        val ads = context.config.root.instagram.adsAndLinks
        return ads.isAdBlockEnabled.get() && ads.isAnalyticsBlocked.get() && ads.disableTrackingLinks.get()
    }

    internal fun setInstagramAdsAndLinksCore(enabled: Boolean, onConfigChanged: () -> Unit) {
        val ads = context.config.root.instagram.adsAndLinks
        ads.isAdBlockEnabled.set(enabled)
        ads.isAnalyticsBlocked.set(enabled)
        ads.disableTrackingLinks.set(enabled)
        persistInstagramConfig(onConfigChanged)
    }

    internal fun getInstagramBooleanFeature(key: String): Boolean {
        val instagram = context.config.root.instagram
        return when (key) {
            "disableStories" -> instagram.distractionFree.disableStories.get()
            "disableFeed" -> instagram.distractionFree.disableFeed.get()
            "disableReels" -> instagram.distractionFree.disableReels.get()
            "disableReelsExceptDM" -> instagram.distractionFree.disableReelsExceptDM.get()
            "disableExplore" -> instagram.distractionFree.disableExplore.get()
            "disableComments" -> instagram.distractionFree.disableComments.get()
            "disableStoryFlipping" -> instagram.misc.disableStoryFlipping.get()
            "disableVideoAutoPlay" -> instagram.misc.disableVideoAutoPlay.get()
            "feedVideosStartWithSound" -> instagram.misc.feedVideosStartWithSound.get()
            "storiesStartWithSound" -> instagram.misc.storiesStartWithSound.get()
            "showFollowerToast" -> instagram.misc.showFollowerToast.get()
            "showFeatureToasts" -> instagram.misc.showFeatureToasts.get()
            "enableStoryMentions" -> instagram.misc.enableStoryMentions.get()
            "enableCopyComment" -> instagram.misc.enableCopyComment.get()
            "enableCopyBio" -> instagram.misc.enableCopyBio.get()
            "disableDoubleTapLike" -> instagram.misc.disableDoubleTapLike.get()
            "enableMonetTheme" -> instagram.misc.enableMonetTheme.get()
            "customEmojiFontEnabled" -> instagram.misc.customEmojiFontEnabled.get()
            "enableShareSheetEmojiShortcuts" -> instagram.misc.enableShareSheetEmojiShortcuts.get()
            "enableActivityHistory" -> instagram.misc.enableActivityHistory.get()
            "enableNavigationTabCustomization" -> instagram.misc.enableNavigationTabCustomization.get()
            "enableConfirmRefresh" -> instagram.misc.enableConfirmRefresh.get()
            "enableNotesLocationSpoof" -> instagram.misc.enableNotesLocationSpoof.get()
            "enableHideChats" -> instagram.misc.enableHideChats.get()
            "stripShareTrackingParameters" -> instagram.misc.stripShareTrackingParameters.get()
            "doNotSaveRecentSearches" -> instagram.misc.doNotSaveRecentSearches.get()
            "enableCustomDateFormat" -> instagram.misc.enableCustomDateFormat.get()
            "openLinksExternally" -> instagram.misc.openLinksExternally.get()
            "replaceShareLinkDomain" -> instagram.misc.replaceShareLinkDomain.get()
            "enableTeenAppIcons" -> instagram.misc.enableTeenAppIcons.get()
            "enableStoryTrayLongPressActions" -> instagram.misc.enableStoryTrayLongPressActions.get()
            "blockDmReelNotifications" -> instagram.misc.notificationFilters.blockDmReelNotifications.get()
            "blockDmPostNotifications" -> instagram.misc.notificationFilters.blockDmPostNotifications.get()
            "enablePostDownload" -> instagram.downloader.enablePostDownload.get()
            "enableStoryDownload" -> instagram.downloader.enableStoryDownload.get()
            "enableReelDownload" -> instagram.downloader.enableReelDownload.get()
            "enableProfileDownload" -> instagram.downloader.enableProfileDownload.get()
            "enableReelThumbnailDownload" -> instagram.downloader.enableReelThumbnailDownload.get()
            "enableStoryMarkSeenButton" -> instagram.downloader.enableStoryMarkSeenButton.get()
            "enableStoryRepostButton" -> instagram.downloader.enableStoryRepostButton.get()
            "enableHighQualityStoryUpload" -> instagram.downloader.enableHighQualityStoryUpload.get()
            "enableGifCommentDownload" -> instagram.downloader.enableGifCommentDownload.get()
            "enableDmAnyFileUpload" -> instagram.downloader.enableDmAnyFileUpload.get()
            else -> false
        }
    }

    internal fun setInstagramBooleanFeature(key: String, enabled: Boolean) {
        val instagram = context.config.root.instagram
        when (key) {
            "disableStories" -> instagram.distractionFree.disableStories.set(enabled)
            "disableFeed" -> instagram.distractionFree.disableFeed.set(enabled)
            "disableReels" -> instagram.distractionFree.disableReels.set(enabled)
            "disableReelsExceptDM" -> instagram.distractionFree.disableReelsExceptDM.set(enabled)
            "disableExplore" -> instagram.distractionFree.disableExplore.set(enabled)
            "disableComments" -> instagram.distractionFree.disableComments.set(enabled)
            "disableStoryFlipping" -> instagram.misc.disableStoryFlipping.set(enabled)
            "disableVideoAutoPlay" -> instagram.misc.disableVideoAutoPlay.set(enabled)
            "feedVideosStartWithSound" -> instagram.misc.feedVideosStartWithSound.set(enabled)
            "storiesStartWithSound" -> instagram.misc.storiesStartWithSound.set(enabled)
            "showFollowerToast" -> instagram.misc.showFollowerToast.set(enabled)
            "showFeatureToasts" -> instagram.misc.showFeatureToasts.set(enabled)
            "enableStoryMentions" -> instagram.misc.enableStoryMentions.set(enabled)
            "enableCopyComment" -> instagram.misc.enableCopyComment.set(enabled)
            "enableCopyBio" -> instagram.misc.enableCopyBio.set(enabled)
            "disableDoubleTapLike" -> instagram.misc.disableDoubleTapLike.set(enabled)
            "enableMonetTheme" -> instagram.misc.enableMonetTheme.set(enabled)
            "customEmojiFontEnabled" -> instagram.misc.customEmojiFontEnabled.set(enabled)
            "enableShareSheetEmojiShortcuts" -> instagram.misc.enableShareSheetEmojiShortcuts.set(enabled)
            "enableActivityHistory" -> instagram.misc.enableActivityHistory.set(enabled)
            "enableNavigationTabCustomization" -> instagram.misc.enableNavigationTabCustomization.set(enabled)
            "enableConfirmRefresh" -> instagram.misc.enableConfirmRefresh.set(enabled)
            "enableNotesLocationSpoof" -> instagram.misc.enableNotesLocationSpoof.set(enabled)
            "enableHideChats" -> instagram.misc.enableHideChats.set(enabled)
            "stripShareTrackingParameters" -> instagram.misc.stripShareTrackingParameters.set(enabled)
            "doNotSaveRecentSearches" -> instagram.misc.doNotSaveRecentSearches.set(enabled)
            "enableCustomDateFormat" -> instagram.misc.enableCustomDateFormat.set(enabled)
            "openLinksExternally" -> instagram.misc.openLinksExternally.set(enabled)
            "replaceShareLinkDomain" -> instagram.misc.replaceShareLinkDomain.set(enabled)
            "enableTeenAppIcons" -> instagram.misc.enableTeenAppIcons.set(enabled)
            "enableStoryTrayLongPressActions" -> instagram.misc.enableStoryTrayLongPressActions.set(enabled)
            "blockDmReelNotifications" -> instagram.misc.notificationFilters.blockDmReelNotifications.set(enabled)
            "blockDmPostNotifications" -> instagram.misc.notificationFilters.blockDmPostNotifications.set(enabled)
            "enablePostDownload" -> instagram.downloader.enablePostDownload.set(enabled)
            "enableStoryDownload" -> instagram.downloader.enableStoryDownload.set(enabled)
            "enableReelDownload" -> instagram.downloader.enableReelDownload.set(enabled)
            "enableProfileDownload" -> instagram.downloader.enableProfileDownload.set(enabled)
            "enableReelThumbnailDownload" -> instagram.downloader.enableReelThumbnailDownload.set(enabled)
            "enableStoryMarkSeenButton" -> instagram.downloader.enableStoryMarkSeenButton.set(enabled)
            "enableStoryRepostButton" -> instagram.downloader.enableStoryRepostButton.set(enabled)
            "enableHighQualityStoryUpload" -> instagram.downloader.enableHighQualityStoryUpload.set(enabled)
            "enableGifCommentDownload" -> instagram.downloader.enableGifCommentDownload.set(enabled)
            "enableDmAnyFileUpload" -> instagram.downloader.enableDmAnyFileUpload.set(enabled)
        }
    }

    internal fun isInstagramFeatureGroupEnabled(keys: List<String>): Boolean {
        return keys.all(::getInstagramBooleanFeature)
    }

    internal fun setInstagramFeatureGroup(keys: List<String>, enabled: Boolean, onConfigChanged: () -> Unit) {
        keys.forEach { setInstagramBooleanFeature(it, enabled) }
        persistInstagramConfig(onConfigChanged)
    }

    internal fun hasInstagramDistractionSelection(): Boolean {
        return instagramDistractionKeys.any(::getInstagramBooleanFeature)
    }

    internal fun isInstagramDistractionPropertyDisabled(propertyName: String): Boolean {
        val distraction = context.config.root.instagram.distractionFree
        if (propertyName == "isExtremeMode") {
            return !distraction.isExtremeMode.get() && !hasInstagramDistractionSelection()
        }
        return propertyName in instagramDistractionKeys && distraction.isExtremeMode.get()
    }

    internal fun applyInstagramBooleanSideEffects(propertyName: String, enabled: Boolean) {
        val distraction = context.config.root.instagram.distractionFree
        when (propertyName) {
            "isExtremeMode" -> {
                if (enabled) distraction.isDistractionFree.set(true)
            }
            "disableReels" -> {
                if (!enabled) distraction.disableReelsExceptDM.set(false)
            }
            "disableReelsExceptDM" -> {
                if (enabled) distraction.disableReels.set(true)
            }
        }
    }

    internal fun isInstagramGhostSettingsCoreEnabled(): Boolean {
        val ghost = context.config.root.instagram.privacy
        return ghost.isGhostSeen.get() &&
            ghost.isGhostTyping.get() &&
            ghost.isGhostStory.get() &&
            ghost.isGhostLive.get() &&
            ghost.hideVoiceMessageSeen.get() &&
            ghost.allowScreenshots.get() &&
            ghost.isGhostScreenshot.get() &&
            ghost.isGhostViewOnce.get() &&
            ghost.enableUnlimitedReplays.get() &&
            ghost.permanentViewMode.get() &&
            ghost.keepEphemeralMessages.get() &&
            ghost.keepUnsentMessages.get() &&
            ghost.markTextsSeenAfterReply.get() &&
            ghost.storyInteractionSendsSeen.get()
    }

    internal fun setInstagramGhostSettingsCore(enabled: Boolean, onConfigChanged: () -> Unit) {
        val ghost = context.config.root.instagram.privacy
        ghost.isGhostSeen.set(enabled)
        ghost.isGhostTyping.set(enabled)
        ghost.isGhostStory.set(enabled)
        ghost.isGhostLive.set(enabled)
        ghost.hideVoiceMessageSeen.set(enabled)
        ghost.allowScreenshots.set(enabled)
        ghost.isGhostScreenshot.set(enabled)
        ghost.isGhostViewOnce.set(enabled)
        ghost.enableUnlimitedReplays.set(enabled)
        ghost.permanentViewMode.set(enabled)
        ghost.keepEphemeralMessages.set(enabled)
        ghost.keepUnsentMessages.set(enabled)
        ghost.markTextsSeenAfterReply.set(enabled)
        ghost.storyInteractionSendsSeen.set(enabled)
        context.config.writeConfig()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()
    }

    internal fun isInstagramQuickToggleCoreEnabled(): Boolean {
        val quick = context.config.root.instagram.privacy.quickToggle
        return quick.quickToggleSeen.get() &&
            quick.quickToggleTyping.get() &&
            quick.quickToggleScreenshot.get() &&
            quick.quickToggleViewOnce.get() &&
            quick.quickToggleStory.get() &&
            quick.quickToggleLive.get() &&
            quick.quickToggleEphemeral.get() &&
            quick.quickToggleUnsend.get() &&
            quick.quickToggleReplays.get() &&
            quick.quickTogglePermanentView.get() &&
            quick.quickToggleAllowScreenshots.get()
    }

    internal fun setInstagramQuickToggleCore(enabled: Boolean, onConfigChanged: () -> Unit) {
        val quick = context.config.root.instagram.privacy.quickToggle
        quick.quickToggleSeen.set(enabled)
        quick.quickToggleTyping.set(enabled)
        quick.quickToggleScreenshot.set(enabled)
        quick.quickToggleViewOnce.set(enabled)
        quick.quickToggleStory.set(enabled)
        quick.quickToggleLive.set(enabled)
        quick.quickToggleEphemeral.set(enabled)
        quick.quickToggleUnsend.set(enabled)
        quick.quickToggleReplays.set(enabled)
        quick.quickTogglePermanentView.set(enabled)
        quick.quickToggleAllowScreenshots.set(enabled)
        context.config.writeConfig()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()
    }

    internal fun instagramDmMarkSeenControlMode(): String {
        return context.config.root.instagram.privacy.dmMarkSeenControlMode.get()
    }

    internal fun setInstagramDmMarkSeenControlMode(mode: String, onConfigChanged: () -> Unit) {
        val normalizedMode = if (mode == "hold_gallery") "hold_gallery" else "eye"
        context.config.root.instagram.privacy.dmMarkSeenControlMode.set(normalizedMode)
        context.config.writeConfig()
        context.mirrorInstagramFeaturePrefs()
        onConfigChanged()
    }

    internal fun importInstagramDevConfigFromManager() {
        runCatching {
            context.androidContext.startActivity(
                Intent().apply {
                    component = ComponentName(
                        context.androidContext,
                        "me.eternal.purrfect.instagram.InstagramJsonImportActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("target_package", primaryInstagramPackage())
                    putExtra("broadcast_action", Constants.INSTAGRAM_DEV_CONFIG_IMPORT_ACTION)
                }
            )
        }.onFailure {
            context.log.error("Failed to start Instagram developer config import", it)
            context.shortToast("Unable to open config importer")
        }
    }

    internal fun exportInstagramDevConfigFromManager() {
        val appContext = context.androidContext
        var handled = false
        lateinit var receiver: BroadcastReceiver
        fun unregisterReceiver() {
            runCatching { appContext.unregisterReceiver(receiver) }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (handled || intent.action != Constants.INSTAGRAM_DEV_CONFIG_SEND_ACTION) return
                handled = true
                unregisterReceiver()

                val error = intent.getStringExtra(Constants.INSTAGRAM_DEV_CONFIG_ERROR_EXTRA)
                    ?: intent.getStringExtra("error")
                if (!error.isNullOrBlank()) {
                    this@FeaturesRootSection.context.shortToast(error)
                    return
                }

                val json = intent.getStringExtra(Constants.INSTAGRAM_DEV_CONFIG_JSON_EXTRA)
                    ?: intent.getStringExtra("json_content")
                if (json.isNullOrBlank()) {
                    this@FeaturesRootSection.context.shortToast(
                        this@FeaturesRootSection.context.translation["instagram_export_no_config_data"]
                            ?: "No config data received."
                    )
                    return
                }

                runCatching {
                    appContext.startActivity(
                        Intent().apply {
                            component = ComponentName(
                                appContext,
                                "me.eternal.purrfect.instagram.InstagramJsonExportActivity"
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(Constants.INSTAGRAM_DEV_CONFIG_JSON_EXTRA, json)
                            putExtra("json_content", json)
                        }
                    )
                }.onFailure {
                    this@FeaturesRootSection.context.log.error("Failed to start Instagram developer config export", it)
                    this@FeaturesRootSection.context.shortToast("Unable to open config exporter")
                }
            }
        }

        runCatching {
            val filter = IntentFilter(Constants.INSTAGRAM_DEV_CONFIG_SEND_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }

            val packages = installedInstagramPackages().ifEmpty { listOf(Constants.INSTAGRAM_PACKAGE_NAME) }
            packages.forEach { packageName ->
                appContext.sendBroadcast(
                    Intent(Constants.INSTAGRAM_DEV_CONFIG_EXPORT_REQUEST_ACTION)
                        .setPackage(packageName)
                )
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (!handled) {
                    handled = true
                    unregisterReceiver()
                    context.shortToast("No config data received.")
                }
            }, 10_000L)
        }.onFailure {
            handled = true
            unregisterReceiver()
            context.log.error("Failed to request Instagram developer config export", it)
            context.shortToast("Unable to request config export")
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }

        key(themeId) {
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PurrfectPalette.backgroundGradient)
        )
    }

    @Composable
    internal fun NoticeBadge(text: String, color: Color) {
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.16f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
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
        val shape = RoundedCornerShape(24.dp)
        val border = Brush.linearGradient(
            listOf(
                PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
            )
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            shape = shape,
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 10.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, border)
        ) {
            Box(modifier = Modifier.background(PurrfectPalette.cardOverlay, shape)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PurrfectPalette.glowPrimary.copy(alpha = 0.18f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = Color.White
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isSearchResults) "No matches found" else "Nothing to show",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isSearchResults) "Try a different keyword or clear filters." else "Toggle visibility with the new controls above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun PropertyAction(
        property: PropertyPair<*>,
        configRefreshNonce: Int,
        onConfigChanged: () -> Unit,
        isInteractionEnabled: Boolean = true,
        registerClickCallback: (() -> Unit) -> (() -> Unit)
    ) {
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
            context.mirrorWhatsAppFeaturePrefs()
            context.mirrorInstagramFeaturePrefs()
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
            
            // Try to parse the snapshot into a structured profile object
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
                                color = PurrfectPalette.textSecondary,
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
                                PurrfectPalette.glowPrimary.copy(alpha = 0.45f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .background(PurrfectPalette.cardOverlay, RoundedCornerShape(24.dp))
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
                                        color = Color.White
                                    )
                                    if (isEmpty) {
                                        Text(
                                            text = context.translation["manager.dialogs.file_imports.no_files_settings_hint"] ?: "No files found",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PurrfectPalette.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            text = translation["manager.dialogs.file_imports.settings_select_file_subtitle"] ?: "Pick a file to import",
                                            fontSize = 13.sp,
                                            color = PurrfectPalette.textSecondary,
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
                                        color = Color.White.copy(alpha = 0.05f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Brush.linearGradient(
                                                listOf(
                                                    PurrfectPalette.glowPrimary.copy(alpha = 0.6f),
                                                    PurrfectPalette.glowSecondary.copy(alpha = 0.48f)
                                                )
                                            ) else SolidColor(Color.White.copy(alpha = 0.08f))
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = PurrfectPalette.glowPrimary.copy(alpha = 0.16f)
                                            ) {
                                                Icon(
                                                    Icons.Filled.AttachFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(10.dp),
                                                    tint = Color.White
                                                )
                                            }
                                            Text(
                                                text = file.name,
                                                modifier = Modifier.weight(1f),
                                                fontSize = 14.sp,
                                                lineHeight = 16.sp,
                                                color = Color.White
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PurrfectPalette.glowSecondary.copy(alpha = 0.18f)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.padding(8.dp),
                                                        tint = PurrfectPalette.glowSecondary
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = isInteractionEnabled,
                    onClick = registerClickCallback {
                        routes.activityLauncher.chooseFolder { uri ->
                            if (uri.isBlank()) return@chooseFolder
                            if (context.isInstagramMode && property.key.name == "downloaderCustomPath") {
                                propertyValue.setAny(getFolderReadablePath(context.androidContext, uri) ?: uri)
                                context.config.root.instagram.downloader.downloaderCustomUri.set(uri)
                            } else {
                                propertyValue.setAny(uri)
                            }
                            persistConfig()
                        }
                    }
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.White)
                }
                if (context.isInstagramMode && property.key.name == "downloaderCustomPath") {
                    IconButton(
                        enabled = isInteractionEnabled && propertyValue.getNullable()?.toString().orEmpty().isNotBlank(),
                        onClick = {
                            propertyValue.setAny("")
                            context.config.root.instagram.downloader.downloaderCustomUri.set("")
                            persistConfig()
                            context.shortToast(
                                context.translation["features.properties.instagram.properties.downloader.download_folder_reset_toast"]
                                    ?: "Download folder reset to Default"
                            )
                        }
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color(0xFFFF453A))
                    }
                }
            }
            return
        }

        when (val dataType = remember { property.key.dataType.type }) {
            DataProcessors.Type.BOOLEAN -> {
                var state by remember(configRefreshNonce) { mutableStateOf(propertyValue.get() as Boolean) }
                var showExtremeConfirm by remember { mutableStateOf(false) }
                val hapticFeedback = LocalHapticFeedback.current

                fun commitBooleanChange(requestedState: Boolean) {
                    state = requestedState
                    propertyValue.setAny(requestedState)
                    if (context.isInstagramMode) {
                        applyInstagramBooleanSideEffects(property.key.name, requestedState)
                    }
                    persistConfig()
                }

                if (showExtremeConfirm) {
                    AestheticDialog(
                        onDismissRequest = { showExtremeConfirm = false },
                        title = context.translation["features.properties.instagram.properties.distraction_free.extreme_title"]
                            ?: "Activate Extreme Mode?",
                        text = context.translation["features.properties.instagram.properties.distraction_free.extreme_message"]
                            ?: "Once activated, you cannot disable Distraction-Free Mode until you reinstall the app. Continue?",
                        icon = Icons.Filled.Warning,
                        dismissButtonText = context.translation["button.negative"] ?: "Cancel",
                        onDismiss = { showExtremeConfirm = false },
                        confirmButtonText = context.translation["button.positive"] ?: "Yes",
                        onConfirm = {
                            showExtremeConfirm = false
                            commitBooleanChange(true)
                        }
                    )
                }

                Switch(
                    checked = state,
                    onCheckedChange = { requestedState ->
                        if (!isInteractionEnabled) return@Switch
                        if (context.config.root.global.uiSettings.hapticFeedback.get()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (
                            context.isInstagramMode &&
                            property.key.name == "isExtremeMode" &&
                            requestedState &&
                            !state
                        ) {
                            showExtremeConfirm = true
                            return@Switch
                        }
                        commitBooleanChange(requestedState)
                    },
                    enabled = isInteractionEnabled,
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
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                            contentColor = Color.White
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
                            } else if (context.isInstagramMode && property.key.name in instagramSelectionStringProperties) {
                                InstagramSelectionStringDialog(
                                    property = property as PropertyPair<String>,
                                    onDismiss = { showDialog = false },
                                    onPersist = {
                                        persistConfig()
                                        showDialog = false
                                    }
                                )
                            } else {
                                alertDialogs.KeyboardInputDialog(property) { showDialog = false }
                            }
                        }
                        else -> {}
                    }
                }

                val click = registerClickCallback { showDialog = true }
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
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.clickable { click() }
                        ) {
                            Text(
                                text = translation.format("search_results_count", "count" to messageCount.toString()),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    } else if (isSnapchatPlusPurchaseDateProperty) {
                        Button(
                            onClick = click,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(translation["button.set"] ?: "Set")
                        }
                    } else {
                        IconButton(onClick = click) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
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

                val click = registerClickCallback { showDialog = true }
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
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
                                PurrfectPalette.glowPrimary.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
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
        val isAphelion = remember { context.config.root.global.uiSettings.managerTheme.get() == "APHELION" }
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
        val isInstagramConditionalDisabled = context.isInstagramMode &&
            isInstagramDistractionPropertyDisabled(property.key.name)
        val isInteractionEnabled = !isComponentDisabled && !isInstagramConditionalDisabled

        val cardShape = RoundedCornerShape(22.dp)
        val interactionSource = remember { MutableInteractionSource() }
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        val cardBackground = remember { PurrfectPalette.cardOverlay }

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
                            color = PurrfectPalette.glowPrimary.copy(alpha = 0.16f),
                            tonalElevation = 0.dp,
                            modifier = Modifier.size(62.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                PurrfectPalette.glowPrimary.copy(alpha = 0.35f),
                                                PurrfectPalette.glowSecondary.copy(alpha = 0.28f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
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
                                color = PurrfectPalette.textPrimary,
                                lineHeight = 20.sp
                            )
                            Text(
                                text = context.translation[property.key.propertyDescription()] ?: "",
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = PurrfectPalette.textSecondary
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
                        PropertyAction(property, configRefreshNonce = configRefreshNonce, onConfigChanged = onConfigChanged, registerClickCallback = { callback ->
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
                        }, isInteractionEnabled = isInteractionEnabled)
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
        val isAphelion = remember { context.config.root.global.uiSettings.managerTheme.get() == "APHELION" }
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
                    color = Color.White.copy(alpha = 0.06f),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                            )
                        )
                    )
                ) {
                    Box(modifier = Modifier.background(PurrfectPalette.cardOverlay)) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = context.translation["manager.dialogs.reset_config.title"] ?: "Reset Config",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = context.translation["manager.dialogs.reset_config.content"] ?: "Reset all settings to default?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PurrfectPalette.textSecondary
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
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = Color.White
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
                                        containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                        contentColor = Color.White
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
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (context.isRedditMode) {
                            routes.configExportSummary.navigate {
                                put("exportSensitiveData", "false")
                                put("includeSavedLocations", "false")
                            }
                        } else {
                            showExportDialog = true
                        }
                    }
                },
                Triple(translation["import_option"] ?: "Import", Icons.Filled.FileDownload) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activityLauncher {
                            openFile("application/json") { uriString ->
                                runCatching {
                                    val uri = android.net.Uri.parse(uriString)
                                    context.androidContext.contentResolver.openInputStream(uri)?.use {
                                        routes.configJsonForImport = it.readBytes().toString(Charsets.UTF_8)
                                        routes.navController.navigate(Routes.CONFIG_IMPORT_CONFIRMATION_ROUTE)
                                    }
                                }.onFailure { err ->
                                    context.log.error("Failed to read config file", err)
                                    context.longToast(translation.format("config_import_failure_toast", "error" to (err.message ?: "Unknown")))
                                }
                            }
                        }
                    }
                },
                Triple(translation["reset_option"] ?: "Reset", Icons.Filled.Refresh) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showResetConfirmationDialog = true
                    }
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
                                placeholder = { Text(text = translation["search_button"] ?: "Search", color = Color(0xFFE0DCFF)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = Color.White
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
                                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
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
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.White.copy(alpha = 0.65f),
                                    focusedPlaceholderColor = Color(0xFFE0DCFF),
                                    unfocusedPlaceholderColor = Color(0xFFE0DCFF),
                                    focusedLeadingIconColor = Color.White,
                                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.9f),
                                    focusedTrailingIconColor = Color.White,
                                    unfocusedTrailingIconColor = Color.White.copy(alpha = 0.9f)
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
                                    tint = Color.White
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
                                        tint = PurrfectPalette.glowSecondary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showExportDropdownMenu,
                                    onDismissRequest = { showExportDropdownMenu = false },
                                    offset = DpOffset(0.dp, 8.dp),
                                    containerColor = Color(0xFF161821),
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
                                                    tint = PurrfectPalette.glowPrimary
                                                )
                                            },
                                            text = { Text(text = name ?: "", color = Color.White) },
                                            onClick = {
                                                action()()
                                                showExportDropdownMenu = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = Color.White,
                                                leadingIconColor = PurrfectPalette.glowPrimary
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
                val topBarBackground = remember { PurrfectPalette.cardOverlay }
                val topBarBorder = remember {
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
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
                        color = PurrfectPalette.cardOverlayColor,
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
                                            tint = Color.White
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
                                        placeholder = { Text(text = translation["search_button"] ?: "Search", color = Color(0xFFE0DCFF)) },
                                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                    )
                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                } else {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = headerTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = subtitleText,
                                            color = Color(0xFFCEC8FF),
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
                                            tint = Color.White
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
                                                    tint = Color.White
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showExportDropdownMenu,
                                                onDismissRequest = { showExportDropdownMenu = false },
                                                offset = DpOffset(0.dp, 8.dp),
                                                containerColor = Color(0xFF161821),
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
                                                                tint = PurrfectPalette.glowPrimary
                                                            )
                                                        },
                                                        text = { Text(text = name ?: "", color = Color.White) },
                                                        onClick = {
                                                            action()()
                                                            showExportDropdownMenu = false
                                                        },
                                                        colors = MenuDefaults.itemColors(
                                                            textColor = Color.White,
                                                            leadingIconColor = PurrfectPalette.glowPrimary
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
                    color = PurrfectPalette.cardOverlayColor,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
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
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White,
                                        leadingIconContentColor = Color.White
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
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = translation["clear_history"] ?: "Clear history", color = Color.White)
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
        showWhatsAppHiddenUiElementsManager: Boolean = false,
        showInstagramDeveloperTools: Boolean = false,
        showInstagramAdsAndLinksTools: Boolean = false,
        showInstagramGhostSettingsTools: Boolean = false,
        showInstagramQuickToggleTools: Boolean = false,
        showInstagramDistractionFreeTools: Boolean = false,
        showInstagramMiscTools: Boolean = false,
        showInstagramDownloaderTools: Boolean = false,
        showInstagramEmojiFontTools: Boolean = false,
        onBack: (() -> Unit)? = null
    ) {
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
                    if (showInstagramQuickToggleTools && !isActiveSearch) {
                        item(key = "instagram_quick_toggle_master") {
                            InstagramQuickToggleMasterCard(
                                refreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ }
                            )
                        }
                    }
                    if (showInstagramMiscTools && !isActiveSearch) {
                        item(key = "instagram_misc_master") {
                            InstagramMiscMasterCard(
                                refreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ }
                            )
                        }
                    }
                    if (showInstagramDownloaderTools && !isActiveSearch) {
                        item(key = "instagram_downloader_master") {
                            InstagramDownloaderMasterCard(
                                refreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ }
                            )
                        }
                    }
                    if (showInstagramEmojiFontTools && !isActiveSearch) {
                        item(key = "instagram_emoji_font_tools") {
                            InstagramEmojiFontToolsCard(
                                refreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ }
                            )
                        }
                    }
                    displayProperties.forEach { propertyItem ->
                        item(key = propertyItem.key.propertyName()) {
                            val onOpen = if (isActiveSearch && liveSearchQuery.isNotBlank()) {
                                {
                                    upsertHistory(liveSearchQuery, sharedSearchHistory)
                                }
                            } else null
                            PropertyCard(
                                property = propertyItem,
                                configRefreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ },
                                onOpen = onOpen
                            )
                        }
                        if (showInstagramDeveloperTools && !isActiveSearch && propertyItem.key.name == "isDevEnabled") {
                            item(key = "instagram_developer_config_actions") {
                                InstagramDeveloperToolsCard(
                                    onConfigChanged = { configRefreshNonce++ }
                                )
                            }
                        }
                        if (showInstagramDistractionFreeTools && !isActiveSearch && propertyItem.key.name == "isExtremeMode") {
                            item(key = "instagram_distraction_master") {
                                InstagramDistractionFreeMasterCard(
                                    refreshNonce = configRefreshNonce,
                                    onConfigChanged = { configRefreshNonce++ }
                                )
                            }
                        }
                        if (showInstagramGhostSettingsTools && !isActiveSearch && propertyItem.key.name == "quick_toggle") {
                            item(key = "instagram_ghost_settings_master") {
                                InstagramGhostSettingsMasterCard(
                                    refreshNonce = configRefreshNonce,
                                    onConfigChanged = { configRefreshNonce++ }
                                )
                            }
                        }
                    }
                    if (showInstagramGhostSettingsTools && !isActiveSearch) {
                        item(key = "instagram_dm_mark_seen_control") {
                            InstagramDmMarkSeenControlCard(
                                refreshNonce = configRefreshNonce,
                                onConfigChanged = { configRefreshNonce++ }
                            )
                        }
                    }
                }
                if (showWhatsAppHiddenUiElementsManager && !isActiveSearch) {
                    item {
                        HiddenUiElementsManager(
                            refreshNonce = configRefreshNonce,
                            onConfigChanged = { configRefreshNonce++ }
                        )
                    }
                }
                if (showInstagramAdsAndLinksTools && !isActiveSearch) {
                    item {
                        InstagramAdsAndLinksToolsCard(
                            refreshNonce = configRefreshNonce,
                            onConfigChanged = { configRefreshNonce++ }
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
                context.mirrorWhatsAppFeaturePrefs()
                context.mirrorInstagramFeaturePrefs()
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
        val haptic = LocalHapticFeedback.current
        Dialog(onDismissRequest = onDismiss) {
            val includeSavedLocations = remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.06f),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                            PurrfectPalette.glowSecondary.copy(alpha = 0.45f)
                        )
                    )
                )
            ) {
                Box(modifier = Modifier.background(PurrfectPalette.cardOverlay)) {
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
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = context.translation["manager.dialogs.export_config.content"] ?: "Include sensitive data?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = PurrfectPalette.textSecondary,
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
                                color = Color.White
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
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
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
                                    containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.32f),
                                    contentColor = Color.White
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
    internal fun HiddenUiElementsManager(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        if (context.isInstagramMode) {
            InstagramHiddenUiElementsManager(refreshNonce, onConfigChanged)
            return
        }

        val entries = remember(refreshNonce) { hiddenUiElementEntries() }
        val translationPrefix = "features.properties.whatsapp.properties.ui_elements"
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.translation["$translationPrefix.hidden_elements_title"]
                                ?: "Hidden Elements",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                        TextButton(
                            onClick = { clearHiddenUiElements(onConfigChanged) },
                            enabled = entries.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = context.translation["$translationPrefix.unhide_all"]
                                    ?: "Unhide All"
                            )
                        }
                    }

                    if (entries.isEmpty()) {
                        Text(
                            text = context.translation["$translationPrefix.hidden_elements_empty"]
                                ?: "No hidden elements yet",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    } else {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = entry.label,
                                        fontSize = 15.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PurrfectPalette.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = context.translation[
                                            if (entry.isSelector) {
                                                "$translationPrefix.selector_label"
                                            } else {
                                                "$translationPrefix.resource_id_label"
                                            }
                                        ] ?: if (entry.isSelector) "Exact selector" else "Resource ID",
                                        fontSize = 12.sp,
                                        color = PurrfectPalette.textSecondary
                                    )
                                }
                                Switch(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        if (!checked) removeHiddenUiElement(entry, onConfigChanged)
                                    },
                                    colors = purrfectSwitchColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun InstagramHiddenUiElementsManager(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        val translationPrefix = "features.properties.instagram.properties.hidden_ui_elements"
        val catalog = remember { loadInstagramHiddenUiIdCatalog() }
        val selectedIds = remember(refreshNonce) { selectedHiddenUiElementIds().toSet() }
        val selectedSelectors = remember(refreshNonce) { selectedHiddenUiElementSelectors().toSet() }
        val selectedEntries = remember(refreshNonce, catalog) { hiddenUiElementEntries(catalog) }
        var query by rememberSaveable { mutableStateOf("") }
        val normalizedQuery = query.trim().lowercase()
        val selectedSelectorRows = remember(selectedEntries, normalizedQuery) {
            selectedEntries.filter { entry ->
                entry.isSelector && (
                    normalizedQuery.isEmpty() ||
                        entry.label.lowercase().contains(normalizedQuery) ||
                        entry.value.lowercase().contains(normalizedQuery)
                    )
            }
        }
        val catalogRows = remember(catalog, selectedIds, normalizedQuery) {
            val matches = catalog.filter { entry ->
                normalizedQuery.isEmpty() || entry.searchKey.contains(normalizedQuery)
            }
            matches.filter { it.name in selectedIds } + matches.filterNot { it.name in selectedIds }
        }
        val totalResults = selectedSelectorRows.size + catalogRows.size
        val hiddenCount = selectedIds.size + selectedSelectors.size
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = context.translation["$translationPrefix.hidden_elements_title"]
                                    ?: "Hide UI Elements",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PurrfectPalette.textPrimary
                            )
                            Text(
                                text = if (normalizedQuery.isEmpty()) {
                                    context.translation["$translationPrefix.ids_count"]
                                        ?.format(hiddenCount, catalog.size)
                                        ?: "$hiddenCount hidden / ${catalog.size} IDs"
                                } else {
                                    context.translation["$translationPrefix.ids_filtered_count"]
                                        ?.format(totalResults, catalog.size)
                                        ?: "$totalResults results / ${catalog.size} IDs"
                                },
                                fontSize = 12.sp,
                                color = PurrfectPalette.textSecondary
                            )
                        }
                        TextButton(
                            onClick = { clearHiddenUiElements(onConfigChanged) },
                            enabled = hiddenCount > 0,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = context.translation["$translationPrefix.unhide_all"]
                                    ?: "Unhide All UI Elements",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        placeholder = {
                            Text(
                                text = context.translation["$translationPrefix.search_hint"]
                                    ?: "Search Instagram resource IDs"
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.07f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = PurrfectPalette.textSecondary,
                            unfocusedPlaceholderColor = PurrfectPalette.textSecondary,
                            focusedLeadingIconColor = Color.White,
                            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (catalog.isEmpty()) {
                        Text(
                            text = context.translation["$translationPrefix.catalog_empty"]
                                ?: "Instagram resource ID catalog is unavailable.",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    } else if (totalResults == 0) {
                        Text(
                            text = context.translation["$translationPrefix.no_results"]
                                ?: "No matching IDs",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(selectedSelectorRows, key = { it.value }) { entry ->
                                InstagramHiddenUiSelectorRow(entry, onConfigChanged)
                            }
                            items(catalogRows, key = { it.name }) { entry ->
                                InstagramHiddenUiIdRow(
                                    entry = entry,
                                    checked = entry.name in selectedIds,
                                    onConfigChanged = onConfigChanged
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun InstagramHiddenUiSelectorRow(
        entry: HiddenUiElementEntry,
        onConfigChanged: () -> Unit
    ) {
        InstagramHiddenUiToggleRow(
            title = entry.label,
            label = context.translation["features.properties.instagram.properties.hidden_ui_elements.selector_label"]
                ?: "Exact selector",
            checked = true,
            onCheckedChange = { checked ->
                if (!checked) setHiddenUiElementHidden(entry.value, hidden = false, onConfigChanged = onConfigChanged)
            }
        )
    }

    @Composable
    internal fun InstagramHiddenUiIdRow(
        entry: HiddenUiIdCatalogEntry,
        checked: Boolean,
        onConfigChanged: () -> Unit
    ) {
        InstagramHiddenUiToggleRow(
            title = entry.displayTitle,
            label = context.translation["features.properties.instagram.properties.hidden_ui_elements.resource_id_label"]
                ?: "Resource ID",
            checked = checked,
            onCheckedChange = { requested ->
                setHiddenUiElementHidden(entry.name, requested, onConfigChanged)
            }
        )
    }

    @Composable
    internal fun InstagramHiddenUiToggleRow(
        title: String,
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Column {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PurrfectPalette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = PurrfectPalette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = purrfectSwitchColors()
                )
            }
        }
    }

    @Composable
    internal fun InstagramDeveloperToolsCard(
        onConfigChanged: () -> Unit
    ) {
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.developer.tools_title"
                            ] ?: "Config",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                    }

                    Button(
                        onClick = { openInstagramDevOptionsFromManager(onConfigChanged) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.30f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.developer.openInstagramDevOptions.name"
                            ] ?: "Open Instagram Dev Options",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { importInstagramDevConfigFromManager() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF30D158).copy(alpha = 0.26f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.developer.importDevConfig.name"
                            ] ?: "Import Dev Config",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { exportInstagramDevConfigFromManager() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A84FF).copy(alpha = 0.28f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.developer.exportDevConfig.name"
                            ] ?: "Export Dev Config",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun InstagramQuickToggleMasterCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        InstagramCoreMasterSwitchCard(
            refreshNonce = refreshNonce,
            title = context.translation[
                "features.properties.instagram.properties.quick_toggle.core_master_title"
            ] ?: "Enable/Disable All",
            description = context.translation[
                "features.properties.instagram.properties.quick_toggle.core_master_description"
            ] ?: "Enable or disable every Quick Toggle item together.",
            checkedProvider = { isInstagramQuickToggleCoreEnabled() },
            onToggle = { setInstagramQuickToggleCore(it, onConfigChanged) }
        )
    }

    @Composable
    internal fun InstagramGhostSettingsMasterCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        InstagramCoreMasterSwitchCard(
            refreshNonce = refreshNonce,
            title = context.translation[
                "features.properties.instagram.properties.privacy.core_master_title"
            ] ?: "Enable/Disable All",
            description = context.translation[
                "features.properties.instagram.properties.privacy.core_master_description"
            ] ?: "Enable or disable every Ghost Settings feature together.",
            checkedProvider = { isInstagramGhostSettingsCoreEnabled() },
            onToggle = { setInstagramGhostSettingsCore(it, onConfigChanged) }
        )
    }

    @Composable
    internal fun InstagramDistractionFreeMasterCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        val extremeActive = context.config.root.instagram.distractionFree.isExtremeMode.get()
        InstagramCoreMasterSwitchCard(
            refreshNonce = refreshNonce,
            title = context.translation[
                "features.properties.instagram.properties.distraction_free.core_master_title"
            ] ?: "Enable/Disable All",
            description = context.translation[
                "features.properties.instagram.properties.distraction_free.core_master_description"
            ] ?: "Enable or disable all Distraction Free features together.",
            checkedProvider = { isInstagramFeatureGroupEnabled(instagramDistractionKeys) },
            onToggle = { setInstagramFeatureGroup(instagramDistractionKeys, it, onConfigChanged) },
            enabled = !extremeActive
        )
    }

    @Composable
    internal fun InstagramMiscMasterCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        InstagramCoreMasterSwitchCard(
            refreshNonce = refreshNonce,
            title = context.translation[
                "features.properties.instagram.properties.misc.core_master_title"
            ] ?: "Enable/Disable All",
            description = context.translation[
                "features.properties.instagram.properties.misc.core_master_description"
            ] ?: "Enable or disable all Misc features together.",
            checkedProvider = { isInstagramFeatureGroupEnabled(instagramMiscMasterKeys) },
            onToggle = { setInstagramFeatureGroup(instagramMiscMasterKeys, it, onConfigChanged) }
        )
    }

    @Composable
    internal fun InstagramDownloaderMasterCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        InstagramCoreMasterSwitchCard(
            refreshNonce = refreshNonce,
            title = context.translation[
                "features.properties.instagram.properties.downloader.core_master_title"
            ] ?: "Enable/Disable All",
            description = context.translation[
                "features.properties.instagram.properties.downloader.core_master_description"
            ] ?: "Enable or disable all Downloader features together.",
            checkedProvider = { isInstagramFeatureGroupEnabled(instagramDownloaderMasterKeys) },
            onToggle = { setInstagramFeatureGroup(instagramDownloaderMasterKeys, it, onConfigChanged) }
        )
    }

    private fun parseInstagramNavigationCsv(raw: String): List<String> {
        val known = instagramNavigationTabs.map { it.first }.toSet()
        return raw.split(',')
            .map { it.trim().lowercase() }
            .filter { it in known }
            .distinct()
    }

    private fun normalizeInstagramNavigationOrder(raw: String): String {
        val ordered = parseInstagramNavigationCsv(raw).toMutableList()
        instagramNavigationTabs.forEach { (value, _) ->
            if (value !in ordered) ordered += value
        }
        return ordered.joinToString(",")
    }

    private fun firstVisibleInstagramNavigationTab(hidden: Set<String>): String {
        return instagramNavigationTabs.firstOrNull { it.first !in hidden }?.first ?: "home"
    }

    private fun instagramNavigationLabel(value: String): String {
        return instagramNavigationTabs.firstOrNull { it.first == value }?.second ?: value
    }

    @Composable
    internal fun InstagramSelectionStringDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        when (property.key.name) {
            "navigationTabHidden" -> InstagramNavigationHiddenTabsDialog(property, onDismiss, onPersist)
            "navigationTabOrder" -> InstagramNavigationOrderDialog(property, onDismiss, onPersist)
            "navigationDefaultTab" -> InstagramNavigationDefaultTabDialog(property, onDismiss, onPersist)
            "storyRingSize" -> InstagramStoryRingSizeDialog(property, onDismiss, onPersist)
            "notesSpoofMapLocation" -> InstagramNotesLocationMapDialog(onDismiss, onPersist)
            "hiddenChatNames" -> InstagramHiddenChatsDialog(property, onDismiss, onPersist)
            else -> {}
        }
    }

    private fun parseInstagramChatNames(raw: String): List<String> {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { isPlausibleInstagramChatName(it) }
            .distinctBy { it.lowercase(Locale.US) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .toList()
    }

    private fun isPlausibleInstagramChatName(value: String): Boolean {
        val clean = value.trim()
        if (clean.length !in 2..80) return false
        val lower = clean.lowercase(Locale.US).trim('\u200e', '\u200f', ' ')
        if (Regex("^\\d{4}-\\d{2}-\\d{2}(?:\\s+\\d{1,2}:\\d{2})?$").matches(lower)) return false
        if (Regex("^\\d{1,2}:\\d{2}.*").matches(lower)) return false
        if (lower.contains(" ·") || lower.contains("·")) return false
        if (lower.endsWith("...") || lower.endsWith("…")) return false
        val blocked = setOf(
            "notes", "note", "your note", "add note", "map", "play", "requests", "messages",
            "just curious", "inspo needed", "start your first note", "try sharing a song",
            "make this space yours", "ask friends anything", "your thoughts go here",
            "can't decide", "obsessed with", "ready for", "today's vibe", "unpopular opinion",
            "central park", "civic center", "rabindra sarovar"
        )
        if (lower in blocked) return false
        if (lower == "reply?" || lower == "reply") return false
        if (lower.contains("turned on disappearing messages") || lower.contains("turned off disappearing messages")) return false
        return true
    }

    @Composable
    private fun InstagramHiddenChatsDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        val known = remember {
            val fromKnown = parseInstagramChatNames(context.config.root.instagram.misc.hiddenChats.knownChatNames.getNullable()?.toString().orEmpty())
            val fromHidden = parseInstagramChatNames(property.value.getNullable()?.toString().orEmpty())
            (fromKnown + fromHidden)
                .distinctBy { it.lowercase(Locale.US) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }
        val selected = remember {
            mutableStateListOf<String>().apply { addAll(parseInstagramChatNames(property.value.getNullable()?.toString().orEmpty())) }
        }
        var query by remember { mutableStateOf("") }
        val visible = remember(query, known) {
            val needle = query.trim().lowercase(Locale.US)
            if (needle.isBlank()) known else known.filter { it.lowercase(Locale.US).contains(needle) }
        }
        AestheticDialog(
            onDismissRequest = onDismiss,
            title = "Hide conversations",
            text = "",
            icon = Icons.Filled.VisibilityOff,
            dismissButtonText = context.translation["button.negative"] ?: "Cancel",
            onDismiss = onDismiss,
            confirmButtonText = context.translation["ig_dialog_save"] ?: "Save",
            onConfirm = {
                property.value.setAny(selected.distinctBy { it.lowercase(Locale.US) }.joinToString("\n"))
                onPersist()
                context.shortToast("Hidden conversations updated")
            },
            customContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search conversations") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PurrfectPalette.glowSecondary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = PurrfectPalette.glowSecondary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            cursorColor = PurrfectPalette.glowSecondary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                visible.forEach { name -> if (selected.none { it.equals(name, ignoreCase = true) }) selected.add(name) }
                            },
                            enabled = visible.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                        ) { Text("Select visible") }
                        Button(
                            onClick = { selected.clear() },
                            enabled = selected.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                        ) { Text("Clear") }
                    }
                    if (known.isEmpty()) {
                        Text(
                            text = "Open Instagram Direct once so Purrfect can collect conversation names.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 430.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visible, key = { it.lowercase(Locale.US) }) { name ->
                                val checked = selected.any { it.equals(name, ignoreCase = true) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (checked) {
                                                selected.removeAll { it.equals(name, ignoreCase = true) }
                                            } else {
                                                selected.add(name)
                                            }
                                        }
                                        .background(Color.White.copy(alpha = if (checked) 0.12f else 0.06f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                if (selected.none { it.equals(name, ignoreCase = true) }) selected.add(name)
                                            } else {
                                                selected.removeAll { it.equals(name, ignoreCase = true) }
                                            }
                                        }
                                    )
                                    Text(
                                        name,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun InstagramNavigationHiddenTabsDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        val currentDefault = context.config.root.instagram.misc.navigationUi.navigationDefaultTab.get()
        val selected = remember {
            mutableStateListOf<String>().apply { addAll(parseInstagramNavigationCsv(property.value.getNullable()?.toString().orEmpty())) }
        }
        AestheticDialog(
            onDismissRequest = onDismiss,
            title = "Hide navigation tabs",
            text = "",
            icon = Icons.Filled.VisibilityOff,
            dismissButtonText = context.translation["button.negative"] ?: "Cancel",
            onDismiss = onDismiss,
            confirmButtonText = context.translation["ig_dialog_save"] ?: "Save",
            onConfirm = {
                val hidden = selected.toMutableList()
                if (hidden.size >= instagramNavigationTabs.size) {
                    hidden.remove(if (currentDefault in hidden) currentDefault else "home")
                    context.shortToast("At least one navigation tab must stay visible.")
                }
                val hiddenSet = hidden.toSet()
                val newDefault = if (currentDefault in hiddenSet || currentDefault !in instagramNavigationTabs.map { it.first }) {
                    firstVisibleInstagramNavigationTab(hiddenSet)
                } else {
                    currentDefault
                }
                property.value.setAny(hidden.joinToString(","))
                context.config.root.instagram.misc.navigationUi.navigationDefaultTab.set(newDefault)
                onPersist()
                context.shortToast("Navigation tabs updated")
            },
            customContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    instagramNavigationTabs.forEach { (value, label) ->
                        val checked = value in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (checked) selected.remove(value) else selected.add(value)
                                }
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (value !in selected) selected.add(value)
                                    } else {
                                        selected.remove(value)
                                    }
                                }
                            )
                            Text(label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun InstagramNavigationOrderDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        val order = remember {
            mutableStateListOf<String>().apply { addAll(parseInstagramNavigationCsv(normalizeInstagramNavigationOrder(property.value.get().toString()))) }
        }
        AestheticDialog(
            onDismissRequest = onDismiss,
            title = "Reorder navigation tabs",
            text = "Move tabs up or down to match the order you want.",
            icon = Icons.Filled.SwapVert,
            dismissButtonText = context.translation["button.negative"] ?: "Cancel",
            onDismiss = onDismiss,
            confirmButtonText = context.translation["ig_dialog_save"] ?: "Save",
            onConfirm = {
                property.value.setAny(normalizeInstagramNavigationOrder(order.joinToString(",")))
                onPersist()
                context.shortToast("Navigation tabs updated")
            },
            customContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    order.forEachIndexed { index, value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = instagramNavigationLabel(value),
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val moved = order.removeAt(index)
                                    order.add(index - 1, moved)
                                }
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White)
                            }
                            IconButton(
                                enabled = index < order.lastIndex,
                                onClick = {
                                    val moved = order.removeAt(index)
                                    order.add(index + 1, moved)
                                }
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun InstagramNavigationDefaultTabDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        val hidden = parseInstagramNavigationCsv(context.config.root.instagram.misc.navigationUi.navigationTabHidden.getNullable().orEmpty()).toSet()
        val visibleTabs = instagramNavigationTabs.filter { it.first !in hidden }.ifEmpty { listOf("home" to "Home") }
        var selected by remember { mutableStateOf(property.value.get().takeIf { tab -> visibleTabs.any { it.first == tab } } ?: visibleTabs.first().first) }
        AestheticDialog(
            onDismissRequest = onDismiss,
            title = "Default tab on open",
            text = "",
            icon = Icons.Filled.Home,
            dismissButtonText = context.translation["button.negative"] ?: "Cancel",
            onDismiss = onDismiss,
            confirmButtonText = context.translation["ig_dialog_save"] ?: "Save",
            onConfirm = {
                property.value.setAny(selected)
                onPersist()
                context.shortToast("Navigation tabs updated")
            },
            customContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleTabs.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selected = value }
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == value, onClick = { selected = value })
                            Text(label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun InstagramStoryRingSizeDialog(
        property: PropertyPair<String>,
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        fun percentFrom(value: String): Float {
            value.trim().removeSuffix("%").toFloatOrNull()?.let { return it.coerceIn(60f, 160f) }
            return when (value.lowercase(Locale.US)) {
                "small" -> 85f
                "large" -> 115f
                "huge" -> 130f
                else -> 100f
            }
        }
        fun valueFrom(percent: Float): String {
            val rounded = ((percent / 5f).roundToInt() * 5).coerceIn(60, 160)
            return if (rounded == 100) "default" else rounded.toString()
        }
        var selected by remember { mutableFloatStateOf(percentFrom(property.value.get().toString())) }
        AestheticDialog(
            onDismissRequest = onDismiss,
            title = "Home story ring size",
            text = "Adjusts only the top story tray on the home feed.",
            icon = Icons.Filled.Tune,
            dismissButtonText = context.translation["button.negative"] ?: "Cancel",
            onDismiss = onDismiss,
            confirmButtonText = context.translation["ig_dialog_save"] ?: "Save",
            onConfirm = {
                property.value.setAny(valueFrom(selected))
                onPersist()
                context.shortToast("Story ring size updated")
            },
            customContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "${selected.roundToInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        value = selected,
                        onValueChange = { selected = ((it / 5f).roundToInt() * 5).coerceIn(60, 160).toFloat() },
                        valueRange = 60f..160f,
                        steps = 19
                    )
                    Button(
                        onClick = { selected = 100f },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Reset to default")
                    }
                }
            }
        )
    }

    @Composable
    private fun InstagramNotesLocationMapDialog(
        onDismiss: () -> Unit,
        onPersist: () -> Unit
    ) {
        val notes = context.config.root.instagram.misc.notesLocation
        val initialLat = notes.notesSpoofLatitude.getNullable()?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: 40.7128
        val initialLon = notes.notesSpoofLongitude.getNullable()?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: -74.0060
        val tempValue = remember { PropertyValue(initialLat to initialLon) }
        val tempProperty = remember {
            PropertyPair(
                PropertyKey({ null }, "notesSpoofCoordinates", DataProcessors.MAP_COORDINATES),
                tempValue
            )
        }
        alertDialogs.ChooseLocationDialog(
            property = tempProperty,
            locationSearchProvider = context.config.root.global.betterLocation.locationSearchProvider.getNullable() ?: "osm",
            googleMapsApiKey = context.config.root.global.betterLocation.googleMapsApiKey.getNullable() ?: ""
        ) {
            val selected = tempValue.get()
            notes.notesSpoofLatitude.set(selected.first.toString())
            notes.notesSpoofLongitude.set(selected.second.toString())
            onPersist()
            context.shortToast("Instagram spoof location updated")
            onDismiss()
        }
    }

    @Composable
    internal fun InstagramEmojiFontToolsCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        val fontName = remember(refreshNonce) {
            context.config.root.instagram.misc.customEmojiFont.customEmojiFontName.getNullable().orEmpty()
        }
        val title = if (fontName.isBlank()) {
            context.translation["features.properties.instagram.properties.misc.custom_emoji_font.import"]
                ?: "Import Custom Emoji Font"
        } else {
            context.translation["features.properties.instagram.properties.misc.custom_emoji_font.selected"]
                ?.format(fontName)
                ?: "Selected: $fontName"
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.misc.custom_emoji_font.name"
                            ] ?: "Custom Emoji Font",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                    Button(
                        onClick = { importInstagramEmojiFontFromManager(onConfigChanged) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurrfectPalette.glowPrimary.copy(alpha = 0.30f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.translation["features.properties.instagram.properties.misc.custom_emoji_font.import"]
                                ?: "Import Custom Emoji Font",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { resetInstagramEmojiFont(onConfigChanged) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF453A).copy(alpha = 0.24f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.translation["features.properties.instagram.properties.misc.custom_emoji_font.reset"]
                                ?: "Reset Custom Emoji Font",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun InstagramCoreMasterSwitchCard(
        refreshNonce: Int,
        title: String,
        description: String,
        checkedProvider: () -> Boolean,
        onToggle: (Boolean) -> Unit,
        enabled: Boolean = true
    ) {
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        var checked by remember(refreshNonce) { mutableStateOf(checkedProvider()) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .graphicsLayer { if (!enabled) alpha = 0.5f }
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                        Text(
                            text = description,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = { requested ->
                            if (!enabled) return@Switch
                            checked = requested
                            onToggle(requested)
                        },
                        enabled = enabled,
                        colors = purrfectSwitchColors()
                    )
                }
            }
        }
    }

    @Composable
    internal fun InstagramDmMarkSeenControlCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        var selectedMode by remember(refreshNonce) { mutableStateOf(instagramDmMarkSeenControlMode()) }
        val eyeLabel = context.translation[
            "features.properties.instagram.properties.privacy.mark_seen_eye"
        ] ?: "Eye icon"
        val holdGalleryLabel = context.translation[
            "features.properties.instagram.properties.privacy.mark_seen_gallery_hold"
        ] ?: "Tap and hold gallery icon"

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.privacy.mark_seen_control_title"
                            ] ?: "DM mark-as-seen control",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.privacy.mark_seen_control_description"
                            ] ?: "Choose the manual seen control shown in Direct messages.",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("eye" to eyeLabel, "hold_gallery" to holdGalleryLabel).forEach { (mode, label) ->
                            val selected = selectedMode == mode
                            Button(
                                onClick = {
                                    selectedMode = mode
                                    setInstagramDmMarkSeenControlMode(mode, onConfigChanged)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) {
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.34f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    internal fun InstagramAdsAndLinksToolsCard(
        refreshNonce: Int,
        onConfigChanged: () -> Unit
    ) {
        val cardShape = RoundedCornerShape(22.dp)
        val cardBorder = remember {
            Brush.linearGradient(
                listOf(
                    PurrfectPalette.glowPrimary.copy(alpha = 0.55f),
                    PurrfectPalette.glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        var checked by remember(refreshNonce) { mutableStateOf(isInstagramAdsAndLinksCoreEnabled()) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .background(PurrfectPalette.cardOverlay, cardShape)
                    .border(BorderStroke(1.dp, cardBorder), cardShape)
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.ads_and_links.core_master_title"
                            ] ?: "Enable/Disable All",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PurrfectPalette.textPrimary
                        )
                        Text(
                            text = context.translation[
                                "features.properties.instagram.properties.ads_and_links.core_master_description"
                            ] ?: "Block Ads, Block Analytics, and Disable Tracking Links together.",
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = PurrfectPalette.textSecondary
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = { requested ->
                            checked = requested
                            setInstagramAdsAndLinksCore(requested, onConfigChanged)
                        },
                        colors = purrfectSwitchColors()
                    )
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
                configContainer === context.config.root.instagram.misc.customEmojiFont,
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
