package me.eternal.purrfectsnap.core.features.impl.ui

import android.view.View
import me.eternal.purrfectsnap.common.data.MessagingRuleType
import me.eternal.purrfectsnap.common.data.RuleState
import me.eternal.purrfectsnap.core.event.events.impl.BindViewEvent
import me.eternal.purrfectsnap.core.features.MessagingRuleFeature
import me.eternal.purrfectsnap.core.ui.hideViewCompletely
import me.eternal.purrfectsnap.core.util.dataBuilder
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.ktx.getObjectField
import me.eternal.purrfectsnap.core.wrapper.impl.SnapUUID
import me.eternal.purrfectsnap.mapper.impl.CallbackMapper
import java.util.ArrayList

class HideFriendFeedEntry : MessagingRuleFeature("HideFriendFeedEntry", ruleType = MessagingRuleType.HIDE_FRIEND_FEED) {
    @Volatile
    private var cachedRuleIds: Set<String> = emptySet()

    @Volatile
    private var cachedRuleIdsAt = 0L

    private fun createDeletedFeedEntry(conversationIdInstance: Any) = findClass("com.snapchat.client.messaging.DeletedFeedEntry").dataBuilder {
        from("mFeedEntryIdentifier") {
            set("mConversationId", conversationIdInstance)
        }
        set("mReason", "CLEAR_CONVERSATION")
    }

    private fun getRuleIdsSnapshot(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - cachedRuleIdsAt <= 1_000L) return cachedRuleIds

        return context.bridgeClient.getRuleIds(ruleType).toSet().also {
            cachedRuleIds = it
            cachedRuleIdsAt = now
        }
    }

    private fun resolveRuleTargets(conversationId: String): Set<String> {
        val targets = linkedSetOf(conversationId)
        context.database.getDMOtherParticipant(conversationId)?.let { targets.add(it) }
        context.database.getFeedEntryByConversationId(conversationId)?.let { entry ->
            entry.friendUserId?.let { targets.add(it) }
            entry.participants?.forEach { targets.add(it) }
        }
        return targets
    }

    private fun shouldHideConversation(
        conversationId: String,
        ruleIds: Set<String>,
        ruleState: RuleState?
    ): Boolean {
        if (ruleState == null) return false
        val isExplicitRuleMatch = resolveRuleTargets(conversationId).any { it in ruleIds }
        return if (ruleState == RuleState.BLACKLIST) !isExplicitRuleMatch else isExplicitRuleMatch
    }

    private fun filterFriendFeed(
        entries: ArrayList<Any>,
        ruleIds: Set<String>,
        ruleState: RuleState?,
        deletedEntries: ArrayList<Any>? = null
    ) {
        if (ruleState == null || entries.isEmpty()) return
        entries.removeIf { feedEntry ->
            val conversationIdInstance = feedEntry.getObjectField("mConversationId") ?: return@removeIf false
            val conversationId = SnapUUID(conversationIdInstance).toString()
            if (shouldHideConversation(conversationId, ruleIds, ruleState)) {
                deletedEntries?.add(createDeletedFeedEntry(conversationIdInstance)!!)
                true
            } else {
                false
            }
        }
    }

    private fun hideBoundChatFeedRow(view: View) {
        var current: View? = view
        repeat(4) {
            val parent = current?.parent as? View
            // Safety: Never hide the actual list container
            if (parent?.javaClass?.name?.contains("RecyclerView") == true) {
                current?.hideViewCompletely()
                return
            }
            current?.hideViewCompletely()
            current = parent
        }
    }

    private fun hookCallbackMethod(
        hookedCallbacks: MutableSet<String>,
        callbackClassName: String,
        methodName: String,
        block: (param: me.eternal.purrfectsnap.core.util.hook.HookAdapter) -> Unit
    ) {
        val hookKey = "$callbackClassName#$methodName"
        if (!hookedCallbacks.add(hookKey)) return
        runCatching {
            findClass(callbackClassName).hook(methodName, HookStage.BEFORE) { param ->
                block(param)
            }
        }.onFailure {
            context.log.warn("Failed to hook $methodName on $callbackClassName")
        }
    }

    override fun init() {
        if (!context.config.userInterface.hideFriendFeedEntry.get()) return

        context.event.subscribe(BindViewEvent::class) { event ->
            event.friendFeedItem { conversationId ->
                if (shouldHideConversation(conversationId, getRuleIdsSnapshot(), getRuleState())) {
                    hideBoundChatFeedRow(event.view)
                }
            }
        }

        context.mappings.useMapper(CallbackMapper::class) {
            classLoader = context.androidContext.classLoader
            val hasFetchAndSyncCallback = callbacks.getAsMap()?.entries?.any {
                it.key.startsWith("FetchAndSyncFeed") && it.key.endsWith("Callback")
            } == true
            if (callbacks.getClass("SyncFeedCallback") == null || !hasFetchAndSyncCallback) {
                runCatching { context.mappings.refresh() }.onFailure {
                    context.log.error("Failed to refresh mappings for HideFriendFeedEntry callbacks", it)
                }
                classLoader = context.androidContext.classLoader
            }
            val callbackMap = callbacks.getAsMap().orEmpty()
            val hookedCallbacks = mutableSetOf<String>()

            callbackMap.entries.forEach { (callbackName, callbackClassName) ->
                when {
                    callbackName.startsWith("FetchAndSyncFeed") && callbackName.endsWith("Callback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchAndSyncFeedComplete") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            val deletedConversations = param.argNullable<ArrayList<Any>>(2)
                            val ruleIds = getRuleIdsSnapshot()
                            val ruleState = getRuleState()
                            filterFriendFeed(entries, ruleIds, ruleState, deletedConversations)

                            if (deletedConversations?.isNotEmpty() == true) {
                                param.setArg(4, true)
                            }
                        }
                    }

                    callbackName.contains("SyncFeed") && callbackName.endsWith("Callback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onSyncFeedComplete") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState(), param.argNullable(2))
                        }
                    }

                    callbackName == "FetchFeedCallback" || callbackName.contains("FetchFeedCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchFeedComplete") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState())
                        }
                    }

                    callbackName == "FetchFeedEntriesCallback" || callbackName.contains("FetchFeedEntriesCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFetchFeedEntriesComplete") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState())
                        }
                    }

                    callbackName == "QueryFeedCallback" || callbackName.contains("QueryFeedCallback") -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onQueryFeedComplete") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState())
                        }
                    }

                    callbackName == "FeedManagerDelegate" -> {
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onFeedEntriesUpdated") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState())
                        }
                        hookCallbackMethod(hookedCallbacks, callbackClassName ?: return@forEach, "onInternalSyncFeed") { param ->
                            val entries = param.argNullable<ArrayList<Any>>(0) ?: return@hookCallbackMethod
                            filterFriendFeed(entries, getRuleIdsSnapshot(), getRuleState())
                        }
                    }
                }
            }

            if (callbackMap.entries.none { it.key.startsWith("FetchAndSyncFeed") && it.key.endsWith("Callback") }) {
                context.log.warn("Failed to hook FetchAndSyncFeedCallback")
            }
            if (callbackMap.entries.none { it.key.contains("SyncFeed") && it.key.endsWith("Callback") }) {
                context.log.warn("Failed to hook SyncFeedCallback")
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}
