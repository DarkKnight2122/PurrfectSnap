package me.eternal.purrfect.core.instagram

import android.content.Context
import android.widget.Toast
import org.json.JSONObject

internal object InstagramFeatureStatus {
    @Volatile private var toastShown = false

    fun maybeShowLoadedToast(context: Context, state: InstagramFeatureState) {
        if (!state.showFeatureToasts || toastShown) return
        val rows = entries.mapNotNull { entry ->
            if (entry.enabled(state)) "${if (entry.hooked(state)) "\u2705" else "\u274c"} ${entry.label}" else null
        }
        if (rows.isEmpty()) return
        toastShown = true
        Toast.makeText(
            context.applicationContext ?: context,
            buildString {
                append("InstaEclipse Loaded \uD83C\uDFAF")
                rows.forEach { append('\n').append(it) }
            },
            Toast.LENGTH_LONG
        ).show()
    }

    private val entries = listOf(
        Entry("Developer Options") { it.isDevEnabled },
        Entry("Hide DM Seen") { it.isGhostSeen },
        Entry("Hide Typing Indicator") { it.isGhostTyping },
        Entry("Bypass Screenshot Detection") { it.isGhostScreenshot },
        Entry("Hide View Once Opened") { it.isGhostViewOnce },
        Entry("Unlimited View-Once Replays") { it.enableUnlimitedReplays },
        Entry("Hide Story Views") { it.isGhostStory },
        Entry("Hide Live Presence") { it.isGhostLive },
        Entry("Hide Voice Message Listens") { it.hideVoiceMessageSeen },
        Entry("Allow Screenshots in DMs") { it.allowScreenshots },
        Entry("Keep Disappearing Messages") { it.keepEphemeralMessages },
        Entry("Keep Unsent Messages") { it.keepUnsentMessages },
        Entry("Permanent View Once Media") { it.permanentViewMode },
        Entry("Mark texts as seen after reply") { it.markTextsSeenAfterReply },
        Entry("Seen on Story Interaction") { it.storyInteractionSendsSeen },
        Entry("Hide Suggestions in Feed") { it.hideSuggestionsInFeed },
        Entry("Disable Tracking Links") { it.disableTrackingLinks },
        Entry("Strip tracing parameters when sharing links") { it.stripShareTrackingParameters },
        Entry("Do Not Save Recent Searches") { it.doNotSaveRecentSearches },
        Entry("Custom date format") { it.enableCustomDateFormat },
        Entry("Open links in external browser") { it.openLinksExternally },
        Entry("Replace domain in shared links") { it.replaceShareLinkDomain },
        Entry("Teen app icons") { it.enableTeenAppIcons },
        Entry("Story tray long-press actions") { it.enableStoryTrayLongPressActions },
        Entry("DM Notification Filters") { it.blockDmReelNotifications || it.blockDmPostNotifications },
        Entry("Show Follower Toast") { it.showFollowerToast },
        Entry("View Story Mentions") { it.enableStoryMentions },
        Entry("Disable Discover People") { it.disableDiscoverPeople },
        Entry("Remove Build Expired Popup") { it.removeBuildExpiredPopup },
        Entry("Download Posts") { it.enablePostDownload },
        Entry("Download Stories") { it.enableStoryDownload },
        Entry("Download Reels") { it.enableReelDownload },
        Entry("Download Profile Pictures") { it.enableProfileDownload },
        Entry("DM visual message context menu") { it.enableDmContextMenuOptions },
        Entry("Download Reel Thumbnails") { it.enableReelThumbnailDownload },
        Entry("Story \"Mark as Seen\" Button") { it.enableStoryMarkSeenButton },
        Entry("Story Repost Button") { it.enableStoryRepostButton },
        Entry("Copy Profile Bio") { it.enableCopyBio },
        Entry("High Quality Story Upload") { it.enableHighQualityStoryUpload },
        Entry("High Quality DM Photos") { it.enableHighQualityDmUpload },
        Entry("Disable Double Tap to Like") { it.disableDoubleTapLike },
        Entry("Monet theme") { it.enableMonetTheme },
        Entry("Custom Emoji Font") { it.customEmojiFontEnabled },
        Entry("Share sheet emoji shortcuts") { it.enableShareSheetEmojiShortcuts },
        Entry("Activity History Logging") { it.enableActivityHistory },
        Entry("Confirm before refreshing Feed/Reels") { it.enableConfirmRefresh },
        Entry("Location spoof for Notes tray") { it.enableNotesLocationSpoof },
        Entry("Hide conversations") { it.enableHideChats },
        Entry("DM any-file upload picker") { it.enableDmAnyFileUpload },
        Entry("Download GIF comments") { it.enableGifCommentDownload },
        Entry("Start Feed Videos With Sound") { it.feedVideosStartWithSound || it.storiesStartWithSound },
        Entry("Navigation Tab Customizer") { it.enableNavigationTabCustomization },
        Entry("Story Ring Size") { it.storyRingSize != "default" },
        Entry("Hide UI Elements") { it.hiddenUiElementIdSet.isNotEmpty() || it.hiddenUiElementSelectorSet.isNotEmpty() },
        Entry("Capture UI Element ID Overlay") { it.captureUiElementIdsEnabled }
    )

    private data class Entry(
        val label: String,
        val hooked: (InstagramFeatureState) -> Boolean = { true },
        val enabled: (InstagramFeatureState) -> Boolean
    )
}

internal object InstagramSettingsBackup {
    private const val VERSION = 1

    fun toJson(state: InstagramFeatureState): String {
        val settings = JSONObject()
        putFeatureValues(settings, state)
        return JSONObject()
            .put("version", VERSION)
            .put("settings", settings)
            .toString(2)
    }

    fun featureJson(state: InstagramFeatureState): String {
        return JSONObject().apply { putFeatureValues(this, state) }.toString()
    }

    private fun putFeatureValues(settings: JSONObject, state: InstagramFeatureState) {
        val stateClass = InstagramFeatureState::class.java
        (InstagramFeatureState.booleanFeatureKeys + InstagramFeatureState.stringFeatureKeys).forEach { key ->
            readFeatureField(stateClass, state, key)?.let { settings.put(key, it) }
        }
    }

    fun restoredStateFromJson(json: String, current: InstagramFeatureState): Pair<InstagramFeatureState, JSONObject> {
        val settings = settingsObject(json)
        val merged = JSONObject()
        val stateClass = InstagramFeatureState::class.java
        InstagramFeatureState.booleanFeatureKeys.forEach { key ->
            val value = if (settings.has(key)) settings.optBoolean(key, defaultBoolean(key)) else {
                readFeatureField(stateClass, current, key) as? Boolean ?: defaultBoolean(key)
            }
            merged.put(key, value)
        }
        InstagramFeatureState.stringFeatureKeys.forEach { key ->
            val value = if (settings.has(key)) settings.optString(key, defaultString(key)) else {
                readFeatureField(stateClass, current, key) as? String ?: defaultString(key)
            }
            merged.put(key, value)
        }
        return InstagramFeatureState.fromJson(merged.toString(), "settings-restore") to settings
    }

    fun settingsObject(json: String): JSONObject {
        val root = JSONObject(json)
        return root.optJSONObject("settings") ?: root
    }

    fun forEachKnownSetting(settings: JSONObject, block: (key: String, value: Any, isString: Boolean) -> Unit) {
        InstagramFeatureState.booleanFeatureKeys.forEach { key ->
            if (settings.has(key)) block(key, settings.optBoolean(key, defaultBoolean(key)), false)
        }
        InstagramFeatureState.stringFeatureKeys.forEach { key ->
            if (settings.has(key)) block(key, settings.optString(key, defaultString(key)), true)
        }
    }

    private fun readFeatureField(stateClass: Class<InstagramFeatureState>, state: InstagramFeatureState, key: String): Any? {
        return runCatching {
            stateClass.getDeclaredField(key).apply { isAccessible = true }.get(state)
        }.getOrNull()
    }

    private fun defaultBoolean(key: String): Boolean {
        return key == "keepUnsentMessages" ||
            key == "quickToggleUnsend" ||
            key == "enableDmContextMenuOptions" ||
            key == "customDateFormatFeed" ||
            key == "customDateFormatComments" ||
            key == "customDateFormatReels" ||
            key == "customDateFormatStories" ||
            key == "customDateFormatDirect"
    }

    private fun defaultString(key: String): String {
        return when (key) {
            "dmMarkSeenControlMode" -> "eye"
            "shareLinkReplacementDomain" -> "ddinstagram.com"
            "navigationTabOrder" -> "home,search,reels,create,direct,shop,profile"
            "navigationDefaultTab" -> "home"
            "storyRingSize" -> "default"
            "customDateFormat" -> "yyyy-MM-dd HH:mm"
            else -> ""
        }
    }
}
