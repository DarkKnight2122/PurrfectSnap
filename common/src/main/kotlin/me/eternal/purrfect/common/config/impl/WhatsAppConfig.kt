package me.eternal.purrfect.common.config.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PrivacyTip
import me.eternal.purrfect.common.config.ConfigContainer

class WhatsAppConfig : ConfigContainer() {
    inner class Channels : ConfigContainer() {
        val hideChannelRecommendations = boolean("hide_channel_recommendations") { requireRestart() }
    }

    inner class Privacy : ConfigContainer() {
        val hideTypingIndicators = boolean("hide_typing_indicators") { requireRestart() }
        val hideRecordingAudio = boolean("hide_recording_audio") { requireRestart() }
        val hideViewOnceSeen = boolean("hide_view_once_seen") { requireRestart() }
        val hideDelivered = boolean("hide_delivered") { requireRestart() }
        val hideAudioSeen = boolean("hide_audio_seen") { requireRestart() }
        val hideBlueTicksGroups = boolean("hide_blue_ticks_groups") { requireRestart() }
        val hideBlueTicks = boolean("hide_blue_ticks") { requireRestart() }
    }

    inner class Messages : ConfigContainer() {
        val unlimitedViewOnce = boolean("unlimited_view_once") { requireRestart() }
        val showDeletedMessages = boolean("show_deleted_messages") { requireRestart() }
    }

    val channels = container("channels", Channels()) { icon = Icons.Default.Forum }
    val privacy = container("privacy", Privacy()) { icon = Icons.Default.PrivacyTip }
    val messages = container("messages", Messages()) { icon = Icons.Default.MarkChatUnread }

    fun hideChannelRecommendationsEnabled() = channels.hideChannelRecommendations.get()
    fun hideTypingIndicatorsEnabled() = privacy.hideTypingIndicators.get()
    fun hideRecordingAudioEnabled() = privacy.hideRecordingAudio.get()
    fun hideViewOnceSeenEnabled() = privacy.hideViewOnceSeen.get()
    fun hideDeliveredEnabled() = privacy.hideDelivered.get()
    fun hideAudioSeenEnabled() = privacy.hideAudioSeen.get()
    fun unlimitedViewOnceEnabled() = messages.unlimitedViewOnce.get()
    fun hideBlueTicksGroupsEnabled() = privacy.hideBlueTicksGroups.get()
    fun hideBlueTicksEnabled() = privacy.hideBlueTicks.get()
    fun showDeletedMessagesEnabled() = messages.showDeletedMessages.get()
}
