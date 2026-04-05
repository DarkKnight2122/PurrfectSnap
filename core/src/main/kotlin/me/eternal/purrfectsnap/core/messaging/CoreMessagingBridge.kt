package me.eternal.purrfectsnap.core.messaging

import me.eternal.purrfectsnap.bridge.snapclient.MessagingBridge
import me.eternal.purrfectsnap.core.ModContext
import me.eternal.purrfectsnap.core.features.impl.experiments.AutoOpenSnaps
import me.eternal.purrfectsnap.core.features.impl.messaging.Messaging

class CoreMessagingBridge(
    private val context: ModContext
) : MessagingBridge.Stub() {
    private var sessionStartListener: me.eternal.purrfectsnap.bridge.snapclient.SessionStartListener? = null

    fun triggerSessionStart() {
        runCatching { sessionStartListener?.onConnected() }
        sessionStartListener = null
    }

    override fun isSessionStarted(): Boolean = context.feature(Messaging::class).conversationManager != null

    override fun registerSessionStartListener(listener: me.eternal.purrfectsnap.bridge.snapclient.SessionStartListener?) {
        sessionStartListener = listener
    }

    override fun getMyUserId(): String = context.database.myUserId

    override fun fetchMessage(conversationId: String?, clientMessageId: String?): me.eternal.purrfectsnap.bridge.snapclient.types.Message? = null

    override fun fetchMessageByServerId(conversationId: String?, serverMessageId: String?): me.eternal.purrfectsnap.bridge.snapclient.types.Message? = null

    override fun fetchConversationWithMessagesPaginated(conversationId: String?, limit: Int, beforeMessageId: Long): MutableList<me.eternal.purrfectsnap.bridge.snapclient.types.Message>? = null

    override fun updateMessage(conversationId: String?, clientMessageId: Long, messageUpdate: String?): String? = null

    override fun getOneToOneConversationId(userId: String?): String? = userId?.let { context.database.getDMConversationId(it) }

    override fun getAutoOpenInterface(): me.eternal.purrfectsnap.bridge.AutoOpenInterface? {
        return context.feature(AutoOpenSnaps::class).getInterface()
    }
}
