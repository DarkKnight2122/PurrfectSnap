package me.eternal.purrfect.common.config.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNotifications
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import me.eternal.purrfect.common.config.ConfigContainer
import me.eternal.purrfect.common.config.ConfigFlag
import me.eternal.purrfect.common.config.ConfigParams
import me.eternal.purrfect.common.config.FeatureNotice

class InstagramConfig : ConfigContainer() {
    private fun ConfigParams.keepTranslationFrom(section: String, property: String) {
        customTranslationPath = "features.properties.instagram.properties.$section.properties.$property"
    }

    inner class Developer : ConfigContainer() {
        val isDevEnabled = boolean("isDevEnabled") { requireRestart() }
        val removeBuildExpiredPopup = boolean("removeBuildExpiredPopup") { requireRestart() }
    }

    inner class QuickToggle : ConfigContainer() {
        val quickToggleSeen = boolean("quickToggleSeen")
        val quickToggleTyping = boolean("quickToggleTyping")
        val quickToggleScreenshot = boolean("quickToggleScreenshot")
        val quickToggleViewOnce = boolean("quickToggleViewOnce")
        val quickToggleStory = boolean("quickToggleStory")
        val quickToggleLive = boolean("quickToggleLive")
        val quickToggleEphemeral = boolean("quickToggleEphemeral")
        val quickToggleUnsend = boolean("quickToggleUnsend", defaultValue = true)
        val quickToggleReplays = boolean("quickToggleReplays")
        val quickTogglePermanentView = boolean("quickTogglePermanentView")
        val quickToggleAllowScreenshots = boolean("quickToggleAllowScreenshots")
    }

    inner class GhostSettings : ConfigContainer() {
        val quickToggle = container("quick_toggle", QuickToggle()) {
            icon = Icons.Default.Tune
            customTranslationPath = "features.properties.instagram.properties.quick_toggle"
        }
        val isGhostModeEnabled = boolean("isGhostModeEnabled") {
            requireRestart()
            addFlags(ConfigFlag.HIDDEN)
        }
        val isGhostSeen = boolean("isGhostSeen") { requireRestart() }
        val markTextsSeenAfterReply = boolean("markTextsSeenAfterReply") { requireRestart() }
        val isGhostTyping = boolean("isGhostTyping") { requireRestart() }
        val isGhostStory = boolean("isGhostStory") { requireRestart() }
        val storyInteractionSendsSeen = boolean("storyInteractionSendsSeen") { requireRestart() }
        val isGhostLive = boolean("isGhostLive") { requireRestart() }
        val hideVoiceMessageSeen = boolean("hideVoiceMessageSeen") { requireRestart() }
        val allowScreenshots = boolean("allowScreenshots") { requireRestart() }
        val isGhostScreenshot = boolean("isGhostScreenshot") { requireRestart() }
        val isGhostViewOnce = boolean("isGhostViewOnce") { requireRestart() }
        val enableUnlimitedReplays = boolean("enableUnlimitedReplays") { requireRestart() }
        val permanentViewMode = boolean("permanentViewMode") {
            requireRestart()
            addNotices(FeatureNotice.UNSTABLE)
        }
        val keepEphemeralMessages = boolean("keepEphemeralMessages") { requireRestart() }
        val keepUnsentMessages = boolean("keepUnsentMessages", defaultValue = true) { requireRestart() }
        val dmMarkSeenControlMode = string("dmMarkSeenControlMode", defaultValue = "eye") {
            requireRestart()
            inputCheck = { it == "eye" || it == "hold_gallery" }
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class DistractionFree : ConfigContainer() {
        val isExtremeMode = boolean("isExtremeMode") {
            requireRestart()
            addNotices(FeatureNotice.UNSTABLE)
        }
        val isDistractionFree = boolean("isDistractionFree") {
            requireRestart()
            addFlags(ConfigFlag.HIDDEN)
        }
        val disableStories = boolean("disableStories") { requireRestart() }
        val disableFeed = boolean("disableFeed") { requireRestart() }
        val disableReels = boolean("disableReels") { requireRestart() }
        val disableReelsExceptDM = boolean("disableReelsExceptDM") { requireRestart() }
        val disableExplore = boolean("disableExplore") { requireRestart() }
        val disableComments = boolean("disableComments") { requireRestart() }
    }

    inner class AdsAndAnalytics : ConfigContainer() {
        val isAdBlockEnabled = boolean("isAdBlockEnabled")
        val isAnalyticsBlocked = boolean("isAnalyticsBlocked")
        val disableTrackingLinks = boolean("disableTrackingLinks")
    }

    inner class CleanFeed : ConfigContainer() {
        val hideSuggestionsInFeed = boolean("hideSuggestionsInFeed") { requireRestart() }
    }

    inner class HiddenUiElements : ConfigContainer() {
        val captureUiElementIdsEnabled = boolean("captureUiElementIdsEnabled") {
            keepTranslationFrom("interface", "captureUiElementIdsEnabled")
        }
        val hiddenUiElementIds = string("hiddenUiElementIds") {
            keepTranslationFrom("interface", "hiddenUiElementIds")
            inputCheck = { true }
            addFlags(ConfigFlag.HIDDEN)
        }
        val hiddenUiElementSelectors = string("hiddenUiElementSelectors") {
            keepTranslationFrom("interface", "hiddenUiElementSelectors")
            inputCheck = { true }
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class MiscDateFormat : ConfigContainer() {
        val customDateFormat = string("customDateFormat", defaultValue = "yyyy-MM-dd HH:mm") {
            keepTranslationFrom("social_and_text", "customDateFormat")
        }
        val customDateFormatFeed = boolean("customDateFormatFeed", defaultValue = true) {
            keepTranslationFrom("social_and_text", "customDateFormatFeed")
        }
        val customDateFormatComments = boolean("customDateFormatComments", defaultValue = true) {
            keepTranslationFrom("social_and_text", "customDateFormatComments")
        }
        val customDateFormatReels = boolean("customDateFormatReels", defaultValue = true) {
            keepTranslationFrom("social_and_text", "customDateFormatReels")
        }
        val customDateFormatStories = boolean("customDateFormatStories", defaultValue = true) {
            keepTranslationFrom("social_and_text", "customDateFormatStories")
        }
        val customDateFormatDirect = boolean("customDateFormatDirect", defaultValue = true) {
            keepTranslationFrom("social_and_text", "customDateFormatDirect")
        }
    }

    inner class MiscShareLinkDomain : ConfigContainer() {
        val shareLinkReplacementDomain = string("shareLinkReplacementDomain", defaultValue = "ddinstagram.com") {
            keepTranslationFrom("ads_and_links", "shareLinkReplacementDomain")
            inputCheck = { raw ->
                var value = raw.trim().lowercase()
                value = value.removePrefix("https://").removePrefix("http://")
                val slash = value.indexOf('/')
                if (slash >= 0) value = value.substring(0, slash)
                value = value.replace(Regex("[^a-z0-9.-]"), "")
                value.isNotEmpty() && value.contains(".")
            }
        }
    }

    inner class MiscNotificationFilters : ConfigContainer() {
        val blockDmReelNotifications = boolean("blockDmReelNotifications") {
            requireRestart()
            keepTranslationFrom("feed_and_search", "blockDmReelNotifications")
        }
        val blockDmPostNotifications = boolean("blockDmPostNotifications") {
            requireRestart()
            keepTranslationFrom("feed_and_search", "blockDmPostNotifications")
        }
    }

    inner class MiscNotesLocation : ConfigContainer() {
        val notesSpoofLatitude = string("notesSpoofLatitude") {
            keepTranslationFrom("social_and_text", "notesSpoofLatitude")
            inputCheck = { it.isBlank() || it.toDoubleOrNull() != null }
        }
        val notesSpoofLongitude = string("notesSpoofLongitude") {
            keepTranslationFrom("social_and_text", "notesSpoofLongitude")
            inputCheck = { it.isBlank() || it.toDoubleOrNull() != null }
        }
    }

    inner class MiscHiddenChats : ConfigContainer() {
        val hiddenChatNames = string("hiddenChatNames") {
            keepTranslationFrom("social_and_text", "hiddenChatNames")
        }
        val knownChatNames = string("knownChatNames") {
            keepTranslationFrom("social_and_text", "knownChatNames")
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class MiscNavigationUi : ConfigContainer() {
        val navigationTabHidden = string("navigationTabHidden") {
            keepTranslationFrom("interface", "navigationTabHidden")
        }
        val navigationTabOrder = string("navigationTabOrder", defaultValue = "home,search,reels,create,direct,shop,profile") {
            keepTranslationFrom("interface", "navigationTabOrder")
        }
        val navigationDefaultTab = string("navigationDefaultTab", defaultValue = "home") {
            keepTranslationFrom("interface", "navigationDefaultTab")
        }
    }

    inner class MiscStoryUi : ConfigContainer() {
        val storyRingSize = string("storyRingSize", defaultValue = "default") {
            keepTranslationFrom("interface", "storyRingSize")
        }
    }

    inner class MiscEmojiFont : ConfigContainer() {
        val customEmojiFontPath = string("customEmojiFontPath") {
            keepTranslationFrom("interface", "customEmojiFontPath")
            addFlags(ConfigFlag.HIDDEN)
        }
        val customEmojiFontName = string("customEmojiFontName") {
            keepTranslationFrom("interface", "customEmojiFontName")
        }
        val customEmojiFontUri = string("customEmojiFontUri") {
            keepTranslationFrom("interface", "customEmojiFontUri")
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class Misc : ConfigContainer() {
        val isMiscEnabled = boolean("isMiscEnabled") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "isMiscEnabled")
            addFlags(ConfigFlag.HIDDEN)
        }
        val disableStoryFlipping = boolean("disableStoryFlipping") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "disableStoryFlipping")
        }
        val disableVideoAutoPlay = boolean("disableVideoAutoPlay") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "disableVideoAutoPlay")
        }
        val feedVideosStartWithSound = boolean("feedVideosStartWithSound") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "feedVideosStartWithSound")
        }
        val storiesStartWithSound = boolean("storiesStartWithSound") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "storiesStartWithSound")
        }
        val disableRepost = boolean("disableRepost") {
            requireRestart()
            keepTranslationFrom("distraction_free", "disableRepost")
        }
        val showFollowerToast = boolean("showFollowerToast") {
            requireRestart()
            keepTranslationFrom("social_and_text", "showFollowerToast")
        }
        val showFeatureToasts = boolean("showFeatureToasts") {
            keepTranslationFrom("social_and_text", "showFeatureToasts")
        }
        val enableStoryMentions = boolean("enableStoryMentions") {
            requireRestart()
            keepTranslationFrom("social_and_text", "enableStoryMentions")
        }
        val disableDiscoverPeople = boolean("disableDiscoverPeople") {
            requireRestart()
            keepTranslationFrom("social_and_text", "disableDiscoverPeople")
        }
        val enableCopyComment = boolean("enableCopyComment") {
            requireRestart()
            keepTranslationFrom("social_and_text", "enableCopyComment")
        }
        val enableCopyBio = boolean("enableCopyBio") {
            requireRestart()
            keepTranslationFrom("downloader", "enableCopyBio")
        }
        val disableDoubleTapLike = boolean("disableDoubleTapLike") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "disableDoubleTapLike")
        }
        val enableMonetTheme = boolean("enableMonetTheme") {
            keepTranslationFrom("interface", "enableMonetTheme")
        }
        val customEmojiFontEnabled = boolean("customEmojiFontEnabled") {
            requireRestart()
            keepTranslationFrom("interface", "customEmojiFontEnabled")
        }
        val enableShareSheetEmojiShortcuts = boolean("enableShareSheetEmojiShortcuts") {
            requireRestart()
            keepTranslationFrom("interface", "enableShareSheetEmojiShortcuts")
        }
        val enableActivityHistory = boolean("enableActivityHistory") {
            requireRestart()
            keepTranslationFrom("social_and_text", "enableActivityHistory")
        }
        val enableNavigationTabCustomization = boolean("enableNavigationTabCustomization") {
            requireRestart()
            keepTranslationFrom("interface", "enableNavigationTabCustomization")
        }
        val enableConfirmRefresh = boolean("enableConfirmRefresh") {
            requireRestart()
            keepTranslationFrom("playback_and_gestures", "enableConfirmRefresh")
        }
        val enableNotesLocationSpoof = boolean("enableNotesLocationSpoof") {
            requireRestart()
            keepTranslationFrom("social_and_text", "enableNotesLocationSpoof")
        }
        val enableHideChats = boolean("enableHideChats") {
            keepTranslationFrom("social_and_text", "enableHideChats")
        }
        val stripShareTrackingParameters = boolean("stripShareTrackingParameters") {
            keepTranslationFrom("ads_and_links", "stripShareTrackingParameters")
        }
        val doNotSaveRecentSearches = boolean("doNotSaveRecentSearches") {
            requireRestart()
            keepTranslationFrom("feed_and_search", "doNotSaveRecentSearches")
        }
        val enableCustomDateFormat = boolean("enableCustomDateFormat") {
            keepTranslationFrom("social_and_text", "enableCustomDateFormat")
        }
        val openLinksExternally = boolean("openLinksExternally") {
            keepTranslationFrom("ads_and_links", "openLinksExternally")
        }
        val replaceShareLinkDomain = boolean("replaceShareLinkDomain") {
            keepTranslationFrom("ads_and_links", "replaceShareLinkDomain")
        }
        val enableTeenAppIcons = boolean("enableTeenAppIcons") {
            requireRestart()
            keepTranslationFrom("interface", "enableTeenAppIcons")
        }
        val enableStoryTrayLongPressActions = boolean("enableStoryTrayLongPressActions") {
            requireRestart()
            keepTranslationFrom("interface", "enableStoryTrayLongPressActions")
        }

        val customDateFormat = container("custom_date_format", MiscDateFormat())
        val shareLinkDomain = container("share_link_domain", MiscShareLinkDomain())
        val notificationFilters = container("notification_filters", MiscNotificationFilters())
        val notesLocation = container("notes_location", MiscNotesLocation())
        val hiddenChats = container("hidden_chats", MiscHiddenChats())
        val navigationUi = container("navigation_ui", MiscNavigationUi())
        val storyUi = container("story_ui", MiscStoryUi())
        val customEmojiFont = container("custom_emoji_font", MiscEmojiFont())
    }

    inner class DownloaderMediaQuality : ConfigContainer() {
        val enableHighQualityStoryUpload = boolean("enableHighQualityStoryUpload") {
            requireRestart()
            keepTranslationFrom("downloader", "enableHighQualityStoryUpload")
        }
        val enableHighQualityDmUpload = boolean("enableHighQualityDmUpload") {
            requireRestart()
            keepTranslationFrom("downloader", "enableHighQualityDmUpload")
        }
        val enableDmAnyFileUpload = boolean("enableDmAnyFileUpload") {
            requireRestart()
            keepTranslationFrom("downloader", "enableDmAnyFileUpload")
        }
    }

    inner class DownloaderOptions : ConfigContainer() {
        val downloaderUsernameFolder = boolean("downloaderUsernameFolder") {
            keepTranslationFrom("downloader", "downloaderUsernameFolder")
        }
        val downloaderAddTimestamp = boolean("downloaderAddTimestamp") {
            keepTranslationFrom("downloader", "downloaderAddTimestamp")
        }
    }

    inner class DownloaderFolder : ConfigContainer() {
        val downloaderCustomPath = string("downloaderCustomPath") {
            keepTranslationFrom("downloader", "downloaderCustomPath")
            addFlags(ConfigFlag.FOLDER)
        }
        val downloaderCustomUri = string("downloaderCustomUri") {
            keepTranslationFrom("downloader", "downloaderCustomUri")
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class Downloader : ConfigContainer() {
        val enablePostDownload = boolean("enablePostDownload") { requireRestart() }
        val enableStoryDownload = boolean("enableStoryDownload") { requireRestart() }
        val enableReelDownload = boolean("enableReelDownload") { requireRestart() }
        val enableProfileDownload = boolean("enableProfileDownload") { requireRestart() }
        val enableDmContextMenuOptions = boolean("enableDmContextMenuOptions", defaultValue = true) { requireRestart() }
        val enableReelThumbnailDownload = boolean("enableReelThumbnailDownload") { requireRestart() }
        val enableStoryMarkSeenButton = boolean("enableStoryMarkSeenButton") { requireRestart() }
        val enableStoryRepostButton = boolean("enableStoryRepostButton") { requireRestart() }
        val enableGifCommentDownload = boolean("enableGifCommentDownload") { requireRestart() }

        val mediaQuality = container("media_quality", DownloaderMediaQuality())
        val options = container("options", DownloaderOptions())
        val downloadFolder = container("download_folder", DownloaderFolder())

        val enableHighQualityStoryUpload get() = mediaQuality.enableHighQualityStoryUpload
        val enableHighQualityDmUpload get() = mediaQuality.enableHighQualityDmUpload
        val enableDmAnyFileUpload get() = mediaQuality.enableDmAnyFileUpload
        val downloaderUsernameFolder get() = options.downloaderUsernameFolder
        val downloaderAddTimestamp get() = options.downloaderAddTimestamp
        val downloaderCustomPath get() = downloadFolder.downloaderCustomPath
        val downloaderCustomUri get() = downloadFolder.downloaderCustomUri
    }

    val developer = container("developer", Developer()) { icon = Icons.Default.Code }
    val privacy = container("privacy", GhostSettings()) { icon = Icons.Default.PrivacyTip }
    val adsAndLinks = container("ads_and_links", AdsAndAnalytics()) { icon = Icons.Default.Block }
    val feedAndSearch = container("feed_and_search", CleanFeed()) { icon = Icons.Default.SearchOff }
    val distractionFree = container("distraction_free", DistractionFree()) { icon = Icons.Default.HideImage }
    val hiddenUiElements = container("hidden_ui_elements", HiddenUiElements()) { icon = Icons.Default.HideImage }
    val misc = container("misc", Misc()) { icon = Icons.Default.Tune }
    val downloader = container("downloader", Downloader()) { icon = Icons.Default.Download }

    val quickToggle get() = privacy.quickToggle

    fun featureMap(): Map<String, Any> = linkedMapOf(
        "isDevEnabled" to developer.isDevEnabled.get(),
        "removeBuildExpiredPopup" to developer.removeBuildExpiredPopup.get(),
        "isGhostModeEnabled" to privacy.isGhostModeEnabled.get(),
        "isGhostSeen" to privacy.isGhostSeen.get(),
        "markTextsSeenAfterReply" to privacy.markTextsSeenAfterReply.get(),
        "isGhostTyping" to privacy.isGhostTyping.get(),
        "isGhostStory" to privacy.isGhostStory.get(),
        "storyInteractionSendsSeen" to privacy.storyInteractionSendsSeen.get(),
        "isGhostLive" to privacy.isGhostLive.get(),
        "hideVoiceMessageSeen" to privacy.hideVoiceMessageSeen.get(),
        "allowScreenshots" to privacy.allowScreenshots.get(),
        "isGhostScreenshot" to privacy.isGhostScreenshot.get(),
        "isGhostViewOnce" to privacy.isGhostViewOnce.get(),
        "enableUnlimitedReplays" to privacy.enableUnlimitedReplays.get(),
        "permanentViewMode" to privacy.permanentViewMode.get(),
        "keepEphemeralMessages" to privacy.keepEphemeralMessages.get(),
        "keepUnsentMessages" to privacy.keepUnsentMessages.get(),
        "dmMarkSeenControlMode" to privacy.dmMarkSeenControlMode.get(),
        "quickToggleSeen" to quickToggle.quickToggleSeen.get(),
        "quickToggleTyping" to quickToggle.quickToggleTyping.get(),
        "quickToggleScreenshot" to quickToggle.quickToggleScreenshot.get(),
        "quickToggleViewOnce" to quickToggle.quickToggleViewOnce.get(),
        "quickToggleStory" to quickToggle.quickToggleStory.get(),
        "quickToggleLive" to quickToggle.quickToggleLive.get(),
        "quickToggleEphemeral" to quickToggle.quickToggleEphemeral.get(),
        "quickToggleUnsend" to quickToggle.quickToggleUnsend.get(),
        "quickToggleReplays" to quickToggle.quickToggleReplays.get(),
        "quickTogglePermanentView" to quickToggle.quickTogglePermanentView.get(),
        "quickToggleAllowScreenshots" to quickToggle.quickToggleAllowScreenshots.get(),
        "isExtremeMode" to distractionFree.isExtremeMode.get(),
        "isDistractionFree" to distractionFree.isDistractionFree.get(),
        "disableStories" to distractionFree.disableStories.get(),
        "disableFeed" to distractionFree.disableFeed.get(),
        "disableReels" to distractionFree.disableReels.get(),
        "disableReelsExceptDM" to distractionFree.disableReelsExceptDM.get(),
        "disableExplore" to distractionFree.disableExplore.get(),
        "disableComments" to distractionFree.disableComments.get(),
        "isAdBlockEnabled" to adsAndLinks.isAdBlockEnabled.get(),
        "isAnalyticsBlocked" to adsAndLinks.isAnalyticsBlocked.get(),
        "disableTrackingLinks" to adsAndLinks.disableTrackingLinks.get(),
        "hideSuggestionsInFeed" to feedAndSearch.hideSuggestionsInFeed.get(),
        "captureUiElementIdsEnabled" to hiddenUiElements.captureUiElementIdsEnabled.get(),
        "hiddenUiElementIds" to hiddenUiElements.hiddenUiElementIds.getNullable().orEmpty(),
        "hiddenUiElementSelectors" to hiddenUiElements.hiddenUiElementSelectors.getNullable().orEmpty(),
        "isMiscEnabled" to misc.isMiscEnabled.get(),
        "disableStoryFlipping" to misc.disableStoryFlipping.get(),
        "disableVideoAutoPlay" to misc.disableVideoAutoPlay.get(),
        "feedVideosStartWithSound" to misc.feedVideosStartWithSound.get(),
        "storiesStartWithSound" to misc.storiesStartWithSound.get(),
        "disableRepost" to misc.disableRepost.get(),
        "showFollowerToast" to misc.showFollowerToast.get(),
        "showFeatureToasts" to misc.showFeatureToasts.get(),
        "enableStoryMentions" to misc.enableStoryMentions.get(),
        "disableDiscoverPeople" to misc.disableDiscoverPeople.get(),
        "enableCopyComment" to misc.enableCopyComment.get(),
        "enableCopyBio" to misc.enableCopyBio.get(),
        "disableDoubleTapLike" to misc.disableDoubleTapLike.get(),
        "enableMonetTheme" to misc.enableMonetTheme.get(),
        "customEmojiFontEnabled" to misc.customEmojiFontEnabled.get(),
        "enableShareSheetEmojiShortcuts" to misc.enableShareSheetEmojiShortcuts.get(),
        "enableActivityHistory" to misc.enableActivityHistory.get(),
        "enableNavigationTabCustomization" to misc.enableNavigationTabCustomization.get(),
        "enableConfirmRefresh" to misc.enableConfirmRefresh.get(),
        "enableNotesLocationSpoof" to misc.enableNotesLocationSpoof.get(),
        "enableHideChats" to misc.enableHideChats.get(),
        "stripShareTrackingParameters" to misc.stripShareTrackingParameters.get(),
        "doNotSaveRecentSearches" to misc.doNotSaveRecentSearches.get(),
        "enableCustomDateFormat" to misc.enableCustomDateFormat.get(),
        "openLinksExternally" to misc.openLinksExternally.get(),
        "replaceShareLinkDomain" to misc.replaceShareLinkDomain.get(),
        "enableTeenAppIcons" to misc.enableTeenAppIcons.get(),
        "enableStoryTrayLongPressActions" to misc.enableStoryTrayLongPressActions.get(),
        "customDateFormat" to misc.customDateFormat.customDateFormat.get(),
        "customDateFormatFeed" to misc.customDateFormat.customDateFormatFeed.get(),
        "customDateFormatComments" to misc.customDateFormat.customDateFormatComments.get(),
        "customDateFormatReels" to misc.customDateFormat.customDateFormatReels.get(),
        "customDateFormatStories" to misc.customDateFormat.customDateFormatStories.get(),
        "customDateFormatDirect" to misc.customDateFormat.customDateFormatDirect.get(),
        "shareLinkReplacementDomain" to misc.shareLinkDomain.shareLinkReplacementDomain.get(),
        "blockDmReelNotifications" to misc.notificationFilters.blockDmReelNotifications.get(),
        "blockDmPostNotifications" to misc.notificationFilters.blockDmPostNotifications.get(),
        "notesSpoofLatitude" to misc.notesLocation.notesSpoofLatitude.getNullable().orEmpty(),
        "notesSpoofLongitude" to misc.notesLocation.notesSpoofLongitude.getNullable().orEmpty(),
        "hiddenChatNames" to misc.hiddenChats.hiddenChatNames.getNullable().orEmpty(),
        "knownChatNames" to misc.hiddenChats.knownChatNames.getNullable().orEmpty(),
        "navigationTabHidden" to misc.navigationUi.navigationTabHidden.getNullable().orEmpty(),
        "navigationTabOrder" to misc.navigationUi.navigationTabOrder.get(),
        "navigationDefaultTab" to misc.navigationUi.navigationDefaultTab.get(),
        "storyRingSize" to misc.storyUi.storyRingSize.get(),
        "customEmojiFontPath" to misc.customEmojiFont.customEmojiFontPath.getNullable().orEmpty(),
        "customEmojiFontName" to misc.customEmojiFont.customEmojiFontName.getNullable().orEmpty(),
        "customEmojiFontUri" to misc.customEmojiFont.customEmojiFontUri.getNullable().orEmpty(),
        "enablePostDownload" to downloader.enablePostDownload.get(),
        "enableStoryDownload" to downloader.enableStoryDownload.get(),
        "enableReelDownload" to downloader.enableReelDownload.get(),
        "enableProfileDownload" to downloader.enableProfileDownload.get(),
        "enableDmContextMenuOptions" to downloader.enableDmContextMenuOptions.get(),
        "enableReelThumbnailDownload" to downloader.enableReelThumbnailDownload.get(),
        "enableStoryMarkSeenButton" to downloader.enableStoryMarkSeenButton.get(),
        "enableStoryRepostButton" to downloader.enableStoryRepostButton.get(),
        "enableGifCommentDownload" to downloader.enableGifCommentDownload.get(),
        "enableHighQualityStoryUpload" to downloader.enableHighQualityStoryUpload.get(),
        "enableHighQualityDmUpload" to downloader.enableHighQualityDmUpload.get(),
        "enableDmAnyFileUpload" to downloader.enableDmAnyFileUpload.get(),
        "downloaderUsernameFolder" to downloader.downloaderUsernameFolder.get(),
        "downloaderAddTimestamp" to downloader.downloaderAddTimestamp.get(),
        "downloaderCustomPath" to downloader.downloaderCustomPath.getNullable().orEmpty(),
        "downloaderCustomUri" to downloader.downloaderCustomUri.getNullable().orEmpty()
    )
}
