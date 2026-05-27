package me.eternal.purrfect.core.instagram

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.bridge.BridgeInterface
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.core.logger.CoreLogger
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class InstagramFeatureState(
    val isDevEnabled: Boolean = false,
    val removeBuildExpiredPopup: Boolean = false,
    val isGhostModeEnabled: Boolean = false,
    val isGhostSeen: Boolean = false,
    val markTextsSeenAfterReply: Boolean = false,
    val isGhostTyping: Boolean = false,
    val isGhostStory: Boolean = false,
    val storyInteractionSendsSeen: Boolean = false,
    val isGhostLive: Boolean = false,
    val hideVoiceMessageSeen: Boolean = false,
    val allowScreenshots: Boolean = false,
    val isGhostScreenshot: Boolean = false,
    val isGhostViewOnce: Boolean = false,
    val enableUnlimitedReplays: Boolean = false,
    val permanentViewMode: Boolean = false,
    val keepEphemeralMessages: Boolean = false,
    val keepUnsentMessages: Boolean = true,
    val quickToggleSeen: Boolean = false,
    val quickToggleTyping: Boolean = false,
    val quickToggleScreenshot: Boolean = false,
    val quickToggleViewOnce: Boolean = false,
    val quickToggleStory: Boolean = false,
    val quickToggleLive: Boolean = false,
    val quickToggleEphemeral: Boolean = false,
    val quickToggleUnsend: Boolean = true,
    val quickToggleReplays: Boolean = false,
    val quickTogglePermanentView: Boolean = false,
    val quickToggleAllowScreenshots: Boolean = false,
    val isExtremeMode: Boolean = false,
    val isDistractionFree: Boolean = false,
    val disableStories: Boolean = false,
    val disableFeed: Boolean = false,
    val disableReels: Boolean = false,
    val disableReelsExceptDM: Boolean = false,
    val disableExplore: Boolean = false,
    val disableComments: Boolean = false,
    val disableRepost: Boolean = false,
    val isAdBlockEnabled: Boolean = false,
    val isAnalyticsBlocked: Boolean = false,
    val disableTrackingLinks: Boolean = false,
    val stripShareTrackingParameters: Boolean = false,
    val openLinksExternally: Boolean = false,
    val replaceShareLinkDomain: Boolean = false,
    val hideSuggestionsInFeed: Boolean = false,
    val doNotSaveRecentSearches: Boolean = false,
    val blockDmReelNotifications: Boolean = false,
    val blockDmPostNotifications: Boolean = false,
    val enablePostDownload: Boolean = false,
    val enableStoryDownload: Boolean = false,
    val enableReelDownload: Boolean = false,
    val enableProfileDownload: Boolean = false,
    val enableDmContextMenuOptions: Boolean = false,
    val enableReelThumbnailDownload: Boolean = false,
    val enableStoryMarkSeenButton: Boolean = false,
    val enableStoryRepostButton: Boolean = false,
    val enableCopyBio: Boolean = false,
    val enableHighQualityStoryUpload: Boolean = false,
    val enableDmAnyFileUpload: Boolean = false,
    val enableGifCommentDownload: Boolean = false,
    val downloaderUsernameFolder: Boolean = false,
    val downloaderAddTimestamp: Boolean = false,
    val isMiscEnabled: Boolean = false,
    val disableStoryFlipping: Boolean = false,
    val disableVideoAutoPlay: Boolean = false,
    val feedVideosStartWithSound: Boolean = false,
    val storiesStartWithSound: Boolean = false,
    val disableDoubleTapLike: Boolean = false,
    val enableConfirmRefresh: Boolean = false,
    val enableMonetTheme: Boolean = false,
    val customEmojiFontEnabled: Boolean = false,
    val enableShareSheetEmojiShortcuts: Boolean = false,
    val enableNavigationTabCustomization: Boolean = false,
    val enableTeenAppIcons: Boolean = false,
    val enableStoryTrayLongPressActions: Boolean = false,
    val captureUiElementIdsEnabled: Boolean = false,
    val showFollowerToast: Boolean = false,
    val showFeatureToasts: Boolean = false,
    val enableStoryMentions: Boolean = false,
    val disableDiscoverPeople: Boolean = false,
    val enableHideChats: Boolean = false,
    val enableActivityHistory: Boolean = false,
    val enableCopyComment: Boolean = false,
    val enableCustomDateFormat: Boolean = false,
    val customDateFormatFeed: Boolean = true,
    val customDateFormatComments: Boolean = true,
    val customDateFormatReels: Boolean = true,
    val customDateFormatStories: Boolean = true,
    val customDateFormatDirect: Boolean = true,
    val enableNotesLocationSpoof: Boolean = false,
    val dmMarkSeenControlMode: String = "eye",
    val shareLinkReplacementDomain: String = "kkinstagram.com",
    val downloaderCustomPath: String = "",
    val downloaderCustomUri: String = "",
    val customEmojiFontPath: String = "",
    val customEmojiFontName: String = "",
    val customEmojiFontUri: String = "",
    val navigationTabOrder: String = "home,search,reels,create,direct,shop,profile",
    val navigationTabHidden: String = "",
    val navigationDefaultTab: String = "home",
    val storyRingSize: String = "default",
    val hiddenUiElementIds: String = "",
    val hiddenUiElementSelectors: String = "",
    val hiddenChatNames: String = "",
    val knownChatNames: String = "",
    val customDateFormat: String = "yyyy-MM-dd HH:mm",
    val notesSpoofLatitude: String = "",
    val notesSpoofLongitude: String = "",
    val source: String = "unavailable"
) {
    val hiddenUiElementIdSet: Set<String> =
        hiddenUiElementIds.lineSequence().map { normalizeUiElementId(it) }.filter { it.isNotEmpty() }.toSet()

    val hiddenUiElementSelectorSet: Set<String> =
        hiddenUiElementSelectors.lineSequence().map { it.trim() }.filter { it.startsWith("selector:v1|") }.toSet()

    val hiddenChatNameSet: Set<String> =
        hiddenChatNames.lineSequence().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun hasAnyFeedSuppression(): Boolean {
        return isExtremeMode || isDistractionFree || disableStories || disableFeed ||
            disableReels || disableExplore || disableComments || hideSuggestionsInFeed ||
            hiddenUiElementIdSet.isNotEmpty() || hiddenUiElementSelectorSet.isNotEmpty()
    }

    fun shouldRewriteShareDomain(): Boolean {
        return replaceShareLinkDomain && shareLinkReplacementDomain.isNotBlank()
    }

    companion object {
        const val TAG = "PurrfectInsta"
        private const val PREFS_NAME = "instagram_features"
        private const val PROVIDER_AUTHORITY = "me.eternal.purrfect.instagram.config"
        private const val PROVIDER_METHOD_GET_FEATURES = "getInstagramFeatures"
        private const val INSTAGRAM_FEATURES_FILE = "files/instagram_features.json"
        private const val INSTAGRAM_EXTERNAL_FEATURES_FILE = "instagram_features.json"
        private const val INSTAGRAM_PROCESS_CACHE_FILE = "purrfect_instagram_features_cache.json"
        private val unavailableLogged = AtomicBoolean(false)

        val booleanFeatureKeys = listOf(
            "isDevEnabled", "removeBuildExpiredPopup", "isGhostModeEnabled", "isGhostSeen", "markTextsSeenAfterReply",
            "isGhostTyping", "isGhostStory", "storyInteractionSendsSeen", "isGhostLive",
            "hideVoiceMessageSeen", "allowScreenshots", "isGhostScreenshot", "isGhostViewOnce",
            "enableUnlimitedReplays", "permanentViewMode", "keepEphemeralMessages",
            "keepUnsentMessages", "quickToggleSeen", "quickToggleTyping", "quickToggleScreenshot",
            "quickToggleViewOnce", "quickToggleStory", "quickToggleLive", "quickToggleEphemeral",
            "quickToggleUnsend", "quickToggleReplays", "quickTogglePermanentView",
            "quickToggleAllowScreenshots", "isExtremeMode", "isDistractionFree", "disableStories",
            "disableFeed", "disableReels", "disableReelsExceptDM", "disableExplore",
            "disableComments", "isAdBlockEnabled", "isAnalyticsBlocked", "disableTrackingLinks",
            "stripShareTrackingParameters", "openLinksExternally", "replaceShareLinkDomain",
            "hideSuggestionsInFeed", "doNotSaveRecentSearches", "blockDmReelNotifications",
            "blockDmPostNotifications", "enablePostDownload", "enableStoryDownload",
            "enableReelDownload", "enableProfileDownload",
            "enableReelThumbnailDownload", "enableStoryMarkSeenButton", "enableStoryRepostButton",
            "enableCopyBio", "enableHighQualityStoryUpload",
            "enableDmAnyFileUpload", "enableGifCommentDownload", "downloaderUsernameFolder",
            "downloaderAddTimestamp", "isMiscEnabled", "disableStoryFlipping", "disableVideoAutoPlay",
            "feedVideosStartWithSound", "storiesStartWithSound", "disableDoubleTapLike",
            "enableConfirmRefresh", "enableMonetTheme", "customEmojiFontEnabled",
            "enableShareSheetEmojiShortcuts", "enableNavigationTabCustomization",
            "enableTeenAppIcons", "enableStoryTrayLongPressActions", "captureUiElementIdsEnabled",
            "showFollowerToast", "showFeatureToasts", "enableStoryMentions",
            "enableHideChats", "enableActivityHistory", "enableCopyComment", "enableCustomDateFormat",
            "customDateFormatFeed", "customDateFormatComments", "customDateFormatReels",
            "customDateFormatStories", "customDateFormatDirect", "enableNotesLocationSpoof"
        )

        val stringFeatureKeys = listOf(
            "dmMarkSeenControlMode", "shareLinkReplacementDomain", "downloaderCustomPath",
            "downloaderCustomUri", "customEmojiFontPath", "customEmojiFontName", "customEmojiFontUri",
            "navigationTabOrder", "navigationTabHidden", "navigationDefaultTab", "storyRingSize",
            "hiddenUiElementIds", "hiddenUiElementSelectors", "hiddenChatNames", "knownChatNames",
            "customDateFormat", "notesSpoofLatitude", "notesSpoofLongitude", "notesSpoofMapLocation"
        )

        fun load(androidContext: Context): InstagramFeatureState {
            InstagramFeatureStateStore.current.takeIf { it.source != "unavailable" }?.let { return it }
            return loadFresh(androidContext)
                ?: loadFromInstagramProcessCache(androidContext)
                ?: loadFromXposedPrefs()
                ?: loadFromWorldReadablePrefs()
                ?: InstagramFeatureState().also {
                    if (unavailableLogged.compareAndSet(false, true)) {
                        log("Instagram feature state unavailable; waiting for broadcast config")
                    }
                }
        }

        fun loadFresh(androidContext: Context): InstagramFeatureState? {
            return loadFromProvider(androidContext)
                ?: loadFromBridge(androidContext)
                ?: loadFromModuleJson(androidContext)
                ?: loadFromExternalJson()
                ?: loadFromRootJson()
        }

        fun cacheInInstagramProcess(androidContext: Context, json: String) {
            runCatching {
                File(androidContext.filesDir, INSTAGRAM_PROCESS_CACHE_FILE).apply {
                    parentFile?.mkdirs()
                    writeText(json, Charsets.UTF_8)
                }
            }
        }

        fun loadAsync(androidContext: Context) {
            Thread {
                repeat(8) { attempt ->
                    val state = loadFresh(androidContext) ?: if (InstagramFeatureStateStore.current.source == "unavailable") {
                        load(androidContext)
                    } else {
                        null
                    }
                    if (state != null && state.source != "unavailable") {
                        val previous = InstagramFeatureStateStore.current
                        if (state.copy(source = previous.source) == previous) return@Thread
                        InstagramFeatureStateStore.update(state)
                        InstagramCustomEmojiFontHooks.syncAfterStateUpdate(androidContext, previous, state)
                        InstagramHooks.refreshActiveHooks("async-load")
                        return@Thread
                    }
                    if (InstagramFeatureStateStore.current.source == "unavailable") {
                        InstagramFeatureStateStore.update(state ?: InstagramFeatureState())
                    }
                    SystemClock.sleep(1_500L + attempt * 500L)
                }
            }.apply {
                name = "PurrfectInstagramConfig"
                isDaemon = true
                start()
            }
        }

        private fun loadFromProvider(androidContext: Context): InstagramFeatureState? {
            return runCatching {
                val resolverContext = moduleContext(androidContext) ?: androidContext
                val result = resolverContext.contentResolver.call(
                    Uri.parse("content://$PROVIDER_AUTHORITY"),
                    PROVIDER_METHOD_GET_FEATURES,
                    null,
                    null
                ) ?: return null
                val source = "provider:${result.getString("source", "unknown")}"
                val json = result.getString("json")
                if (!json.isNullOrBlank()) {
                    cacheInInstagramProcess(androidContext, json)
                    fromJson(json, source).also { logLoadedState(it) }
                } else {
                    fromLookups(
                        source,
                        getBoolean = { key, defaultValue -> result.getBoolean(key, defaultValue) },
                        getString = { key, defaultValue -> result.getString(key) ?: defaultValue }
                    ).also { logLoadedState(it) }
                }
            }.onFailure { throwable ->
                if (!throwable.message.orEmpty().contains("Unknown authority")) {
                    log("Instagram config provider unavailable: ${throwable.javaClass.simpleName}: ${throwable.message}")
                }
            }.getOrNull()
        }

        private fun loadFromBridge(androidContext: Context): InstagramFeatureState? {
            val bridgeContext = moduleContext(androidContext) ?: androidContext
            var service: BridgeInterface? = null
            val latch = CountDownLatch(1)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    service = BridgeInterface.Stub.asInterface(binder)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                }
            }

            return runCatching {
                runCatching {
                    bridgeContext.startActivity(
                        Intent()
                            .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfect.bridge.ForceStartActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    )
                }

                val intent = Intent()
                    .setClassName(Constants.MODULE_PACKAGE_NAME, "me.eternal.purrfect.bridge.BridgeService")
                    .setPackage(Constants.MODULE_PACKAGE_NAME)
                if (!bridgeContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return null

                try {
                    if (!latch.await(900L, TimeUnit.MILLISECONDS)) return null
                    val json = service?.instagramFeaturesJson ?: return null
                    fromJson(json, "bridge").also { logLoadedState(it) }
                } finally {
                    runCatching { bridgeContext.unbindService(connection) }
                }
            }.onFailure { throwable ->
                log("Failed to load Instagram feature state from bridge: ${throwable.message}")
                runCatching { bridgeContext.unbindService(connection) }
            }.getOrNull()
        }

        private fun loadFromModuleJson(androidContext: Context): InstagramFeatureState? {
            val moduleContext = moduleContext(androidContext) ?: return null
            readFeatureJsonFile(File(moduleContext.filesDir, INSTAGRAM_EXTERNAL_FEATURES_FILE), "module-json")?.let { return it }
            return readMainConfigJsonFile(File(moduleContext.filesDir, "config.json"), "module-config")
        }

        private fun loadFromInstagramProcessCache(androidContext: Context): InstagramFeatureState? {
            return readFeatureJsonFile(File(androidContext.filesDir, INSTAGRAM_PROCESS_CACHE_FILE), "instagram-process-cache")
        }

        private fun loadFromExternalJson(): InstagramFeatureState? {
            val candidates = listOf(
                File("/storage/emulated/0/Android/media/${BuildConfig.APPLICATION_ID}/$INSTAGRAM_EXTERNAL_FEATURES_FILE"),
                File("/sdcard/Android/media/${BuildConfig.APPLICATION_ID}/$INSTAGRAM_EXTERNAL_FEATURES_FILE")
            ).distinctBy { it.absolutePath }
            candidates.forEach { file ->
                readFeatureJsonFile(file, "external-json:${file.absolutePath}")?.let { return it }
            }
            return null
        }

        private fun loadFromRootJson(): InstagramFeatureState? {
            listOf(
                "/data/user/0/${BuildConfig.APPLICATION_ID}/$INSTAGRAM_FEATURES_FILE",
                "/data/data/${BuildConfig.APPLICATION_ID}/$INSTAGRAM_FEATURES_FILE"
            ).distinct().forEach { path ->
                readRootFile(path)?.let { json -> fromJson(json, "root-json:$path").also { logLoadedState(it) } }?.let { return it }
            }
            listOf(
                "/data/user/0/${BuildConfig.APPLICATION_ID}/files/config.json",
                "/data/data/${BuildConfig.APPLICATION_ID}/files/config.json"
            ).distinct().forEach { path ->
                readRootFile(path)?.let { json -> fromMainConfigJson(json, "root-config:$path").also { logLoadedState(it) } }?.let { return it }
            }
            return null
        }

        private fun readRootFile(path: String): String? {
            return runCatching {
                val process = ProcessBuilder("su", "-c", "cat '$path'")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(700L, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                if (process.exitValue() != 0 || output.isBlank() || !output.startsWith("{")) return@runCatching null
                output
            }.getOrNull()
        }

        private fun readFeatureJsonFile(file: File, source: String): InstagramFeatureState? {
            return runCatching {
                if (!file.exists()) return@runCatching null
                val json = file.readText(Charsets.UTF_8).trim()
                if (!json.startsWith("{")) return@runCatching null
                fromJson(json, source).also { logLoadedState(it) }
            }.getOrNull()
        }

        private fun readMainConfigJsonFile(file: File, source: String): InstagramFeatureState? {
            return runCatching {
                if (!file.exists()) return@runCatching null
                val json = file.readText(Charsets.UTF_8).trim()
                if (!json.startsWith("{")) return@runCatching null
                fromMainConfigJson(json, source).also { logLoadedState(it) }
            }.getOrNull()
        }

        private fun loadFromXposedPrefs(): InstagramFeatureState? {
            return runCatching {
                val prefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences")
                val prefs = prefsClass
                    .getConstructor(String::class.java, String::class.java)
                    .newInstance(BuildConfig.APPLICATION_ID, PREFS_NAME)
                runCatching { prefsClass.getMethod("makeWorldReadable").invoke(prefs) }
                runCatching { prefsClass.getMethod("reload").invoke(prefs) }
                val getBoolean = prefsClass.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                val getString = prefsClass.getMethod("getString", String::class.java, String::class.java)
                fromLookups(
                    "XSharedPreferences",
                    getBoolean = { key, defaultValue -> getBoolean.invoke(prefs, key, defaultValue) as Boolean },
                    getString = { key, defaultValue -> getString.invoke(prefs, key, defaultValue) as? String ?: defaultValue }
                ).also { logLoadedState(it) }
            }.getOrNull()
        }

        private fun loadFromWorldReadablePrefs(): InstagramFeatureState? {
            listOf(
                File("/data/user/0/${BuildConfig.APPLICATION_ID}/shared_prefs/$PREFS_NAME.xml"),
                File("/data/data/${BuildConfig.APPLICATION_ID}/shared_prefs/$PREFS_NAME.xml")
            ).distinctBy { it.absolutePath }.forEach { file ->
                val state = runCatching {
                    if (!file.exists()) return@runCatching null
                    val prefs = parsePrefs(file)
                    fromLookups(
                        "world-readable:${file.absolutePath}",
                        getBoolean = { key, defaultValue -> prefs.booleans[key] ?: defaultValue },
                        getString = { key, defaultValue -> prefs.strings[key] ?: defaultValue }
                    ).also { logLoadedState(it) }
                }.getOrNull()
                if (state != null) return state
            }
            return null
        }

        private fun moduleContext(androidContext: Context): Context? {
            return runCatching {
                androidContext.createPackageContext(Constants.MODULE_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull()
        }

        private data class ParsedPrefs(
            val booleans: Map<String, Boolean>,
            val strings: Map<String, String>
        )

        private fun parsePrefs(file: File): ParsedPrefs {
            val booleans = mutableMapOf<String, Boolean>()
            val strings = mutableMapOf<String, String>()
            file.inputStream().use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "utf-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "boolean" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")
                                if (name != null && value != null) booleans[name] = value.toBooleanStrictOrNull() ?: false
                            }
                            "string" -> {
                                val name = parser.getAttributeValue(null, "name")
                                if (name != null) strings[name] = parser.nextText().orEmpty()
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            return ParsedPrefs(booleans, strings)
        }

        fun fromJson(json: String, source: String): InstagramFeatureState {
            val obj = JSONObject(json)
            return fromLookups(
                source,
                getBoolean = { key, defaultValue -> obj.optBoolean(key, defaultValue) },
                getString = { key, defaultValue -> obj.optString(key, defaultValue) }
            )
        }

        private fun fromMainConfigJson(json: String, source: String): InstagramFeatureState {
            val instagram = JSONObject(json).optJSONObject("instagram") ?: JSONObject()
            val properties = instagram.optJSONObject("properties") ?: instagram
            return fromLookups(
                source,
                getBoolean = { key, defaultValue -> readBooleanProperty(properties, key, defaultValue) },
                getString = { key, defaultValue -> readStringProperty(properties, key, defaultValue) }
            )
        }

        private fun readBooleanProperty(properties: JSONObject, key: String, defaultValue: Boolean): Boolean {
            return when (val value = findPropertyValue(properties, key) ?: return defaultValue) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.toBooleanStrictOrNull() ?: defaultValue
                else -> defaultValue
            }
        }

        private fun readStringProperty(properties: JSONObject, key: String, defaultValue: String): String {
            return when (val value = findPropertyValue(properties, key) ?: return defaultValue) {
                is String -> value
                else -> value.toString()
            }
        }

        private fun findPropertyValue(root: JSONObject, key: String): Any? {
            if (root.has(key)) return root.opt(key).takeUnless { it == JSONObject.NULL }

            root.optJSONObject("properties")?.let { nestedProperties ->
                findPropertyValue(nestedProperties, key)?.let { return it }
            }

            val keys = root.keys()
            while (keys.hasNext()) {
                val childKey = keys.next()
                if (childKey == key || childKey == "properties") continue
                val child = root.optJSONObject(childKey) ?: continue
                findPropertyValue(child, key)?.let { return it }
            }
            return null
        }

        private fun fromLookups(
            source: String,
            getBoolean: (key: String, defaultValue: Boolean) -> Boolean,
            getString: (key: String, defaultValue: String) -> String
        ): InstagramFeatureState {
            return InstagramFeatureState(
                isDevEnabled = getBoolean("isDevEnabled", false),
                removeBuildExpiredPopup = getBoolean("removeBuildExpiredPopup", false),
                isGhostModeEnabled = getBoolean("isGhostModeEnabled", false),
                isGhostSeen = getBoolean("isGhostSeen", false),
                markTextsSeenAfterReply = getBoolean("markTextsSeenAfterReply", false),
                isGhostTyping = getBoolean("isGhostTyping", false),
                isGhostStory = getBoolean("isGhostStory", false),
                storyInteractionSendsSeen = getBoolean("storyInteractionSendsSeen", false),
                isGhostLive = getBoolean("isGhostLive", false),
                hideVoiceMessageSeen = getBoolean("hideVoiceMessageSeen", false),
                allowScreenshots = getBoolean("allowScreenshots", false),
                isGhostScreenshot = getBoolean("isGhostScreenshot", false),
                isGhostViewOnce = getBoolean("isGhostViewOnce", false),
                enableUnlimitedReplays = getBoolean("enableUnlimitedReplays", false),
                permanentViewMode = getBoolean("permanentViewMode", false),
                keepEphemeralMessages = getBoolean("keepEphemeralMessages", false),
                keepUnsentMessages = getBoolean("keepUnsentMessages", true),
                quickToggleSeen = getBoolean("quickToggleSeen", false),
                quickToggleTyping = getBoolean("quickToggleTyping", false),
                quickToggleScreenshot = getBoolean("quickToggleScreenshot", false),
                quickToggleViewOnce = getBoolean("quickToggleViewOnce", false),
                quickToggleStory = getBoolean("quickToggleStory", false),
                quickToggleLive = getBoolean("quickToggleLive", false),
                quickToggleEphemeral = getBoolean("quickToggleEphemeral", false),
                quickToggleUnsend = getBoolean("quickToggleUnsend", true),
                quickToggleReplays = getBoolean("quickToggleReplays", false),
                quickTogglePermanentView = getBoolean("quickTogglePermanentView", false),
                quickToggleAllowScreenshots = getBoolean("quickToggleAllowScreenshots", false),
                isExtremeMode = getBoolean("isExtremeMode", false),
                isDistractionFree = getBoolean("isDistractionFree", false),
                disableStories = getBoolean("disableStories", false),
                disableFeed = getBoolean("disableFeed", false),
                disableReels = getBoolean("disableReels", false),
                disableReelsExceptDM = getBoolean("disableReelsExceptDM", false),
                disableExplore = getBoolean("disableExplore", false),
                disableComments = getBoolean("disableComments", false),
                disableRepost = getBoolean("disableRepost", false),
                isAdBlockEnabled = getBoolean("isAdBlockEnabled", false),
                isAnalyticsBlocked = getBoolean("isAnalyticsBlocked", false),
                disableTrackingLinks = getBoolean("disableTrackingLinks", false),
                stripShareTrackingParameters = getBoolean("stripShareTrackingParameters", false),
                openLinksExternally = getBoolean("openLinksExternally", false),
                replaceShareLinkDomain = getBoolean("replaceShareLinkDomain", false),
                hideSuggestionsInFeed = getBoolean("hideSuggestionsInFeed", false),
                doNotSaveRecentSearches = getBoolean("doNotSaveRecentSearches", false),
                blockDmReelNotifications = getBoolean("blockDmReelNotifications", false),
                blockDmPostNotifications = getBoolean("blockDmPostNotifications", false),
                enablePostDownload = getBoolean("enablePostDownload", false),
                enableStoryDownload = getBoolean("enableStoryDownload", false),
                enableReelDownload = getBoolean("enableReelDownload", false),
                enableProfileDownload = getBoolean("enableProfileDownload", false),
                enableDmContextMenuOptions = false,
                enableReelThumbnailDownload = getBoolean("enableReelThumbnailDownload", false),
                enableStoryMarkSeenButton = getBoolean("enableStoryMarkSeenButton", false),
                enableStoryRepostButton = getBoolean("enableStoryRepostButton", false),
                enableCopyBio = getBoolean("enableCopyBio", false),
                enableHighQualityStoryUpload = getBoolean("enableHighQualityStoryUpload", false),
                enableDmAnyFileUpload = getBoolean("enableDmAnyFileUpload", false),
                enableGifCommentDownload = getBoolean("enableGifCommentDownload", false),
                downloaderUsernameFolder = getBoolean("downloaderUsernameFolder", false),
                downloaderAddTimestamp = getBoolean("downloaderAddTimestamp", false),
                isMiscEnabled = getBoolean("isMiscEnabled", false),
                disableStoryFlipping = getBoolean("disableStoryFlipping", false),
                disableVideoAutoPlay = getBoolean("disableVideoAutoPlay", false),
                feedVideosStartWithSound = getBoolean("feedVideosStartWithSound", false),
                storiesStartWithSound = getBoolean("storiesStartWithSound", false),
                disableDoubleTapLike = getBoolean("disableDoubleTapLike", false),
                enableConfirmRefresh = getBoolean("enableConfirmRefresh", false),
                enableMonetTheme = getBoolean("enableMonetTheme", false),
                customEmojiFontEnabled = getBoolean("customEmojiFontEnabled", false),
                enableShareSheetEmojiShortcuts = getBoolean("enableShareSheetEmojiShortcuts", false),
                enableNavigationTabCustomization = getBoolean("enableNavigationTabCustomization", false),
                enableTeenAppIcons = getBoolean("enableTeenAppIcons", false),
                enableStoryTrayLongPressActions = getBoolean("enableStoryTrayLongPressActions", false),
                captureUiElementIdsEnabled = getBoolean("captureUiElementIdsEnabled", false),
                showFollowerToast = getBoolean("showFollowerToast", false),
                showFeatureToasts = getBoolean("showFeatureToasts", false),
                enableStoryMentions = getBoolean("enableStoryMentions", false),
                disableDiscoverPeople = getBoolean("disableDiscoverPeople", false),
                enableHideChats = getBoolean("enableHideChats", false),
                enableActivityHistory = getBoolean("enableActivityHistory", false),
                enableCopyComment = getBoolean("enableCopyComment", false),
                enableCustomDateFormat = getBoolean("enableCustomDateFormat", false),
                customDateFormatFeed = getBoolean("customDateFormatFeed", true),
                customDateFormatComments = getBoolean("customDateFormatComments", true),
                customDateFormatReels = getBoolean("customDateFormatReels", true),
                customDateFormatStories = getBoolean("customDateFormatStories", true),
                customDateFormatDirect = getBoolean("customDateFormatDirect", true),
                enableNotesLocationSpoof = getBoolean("enableNotesLocationSpoof", false),
                dmMarkSeenControlMode = getString("dmMarkSeenControlMode", "eye"),
                shareLinkReplacementDomain = getString("shareLinkReplacementDomain", "kkinstagram.com"),
                downloaderCustomPath = getString("downloaderCustomPath", ""),
                downloaderCustomUri = getString("downloaderCustomUri", ""),
                customEmojiFontPath = getString("customEmojiFontPath", ""),
                customEmojiFontName = getString("customEmojiFontName", ""),
                customEmojiFontUri = getString("customEmojiFontUri", ""),
                navigationTabOrder = getString("navigationTabOrder", "home,search,reels,create,direct,shop,profile"),
                navigationTabHidden = getString("navigationTabHidden", ""),
                navigationDefaultTab = getString("navigationDefaultTab", "home"),
                storyRingSize = getString("storyRingSize", "default"),
                hiddenUiElementIds = normalizeUiElementIds(getString("hiddenUiElementIds", "")),
                hiddenUiElementSelectors = normalizeUiElementSelectors(getString("hiddenUiElementSelectors", "")),
                hiddenChatNames = getString("hiddenChatNames", ""),
                knownChatNames = getString("knownChatNames", ""),
                customDateFormat = getString("customDateFormat", "yyyy-MM-dd HH:mm"),
                notesSpoofLatitude = getString("notesSpoofLatitude", ""),
                notesSpoofLongitude = getString("notesSpoofLongitude", ""),
                source = source
            )
        }

        fun normalizeUiElementIds(rawIds: String?): String {
            if (rawIds.isNullOrBlank()) return ""
            return rawIds.lineSequence()
                .map { normalizeUiElementId(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")
        }

        fun normalizeUiElementId(rawId: String?): String {
            var clean = rawId?.trim().orEmpty()
            if (clean.isEmpty()) return ""
            clean = clean.substringBefore('\t').trim()
            clean = clean.substringBefore(' ').trim()
            val slashIndex = clean.lastIndexOf('/')
            if (slashIndex >= 0 && slashIndex < clean.length - 1) clean = clean.substring(slashIndex + 1).trim()
            val dotIndex = clean.lastIndexOf(".id.")
            if (dotIndex >= 0 && dotIndex + 4 < clean.length) clean = clean.substring(dotIndex + 4).trim()
            return clean
        }

        fun normalizeUiElementSelectors(rawSelectors: String?): String {
            if (rawSelectors.isNullOrBlank()) return ""
            return rawSelectors.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("selector:v1|") }
                .distinct()
                .joinToString("\n")
        }

        private fun logLoadedState(state: InstagramFeatureState) {
            log(
                "Instagram feature state loaded from ${state.source}: " +
                    "privacy=${state.isGhostSeen || state.isGhostTyping || state.isGhostStory || state.isGhostScreenshot}, " +
                    "downloads=${state.enablePostDownload || state.enableStoryDownload || state.enableReelDownload || state.enableProfileDownload}, " +
                    "uiHiddenIds=${state.hiddenUiElementIdSet.size}, uiSelectors=${state.hiddenUiElementSelectorSet.size}, " +
                    "network=${state.isAdBlockEnabled || state.isAnalyticsBlocked || state.disableTrackingLinks}"
            )
        }

        private fun log(message: String) {
            XposedBridge.log("[$TAG] $message")
            CoreLogger.xposedLog(message, TAG)
            InstagramAppLogWriter.info(null, TAG, message)
        }
    }
}

object InstagramFeatureStateStore {
    @Volatile
    var current: InstagramFeatureState = InstagramFeatureState()
        private set

    fun update(state: InstagramFeatureState) {
        current = state
    }
}
