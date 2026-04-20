package me.eternal.purrfectsnap.core.features.impl.ui

import me.eternal.purrfectsnap.common.data.MessagingRuleType
import me.eternal.purrfectsnap.common.data.RuleState
import me.eternal.purrfectsnap.core.features.MessagingRuleFeature
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.Hooker
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.hook.hookConstructor
import me.eternal.purrfectsnap.core.util.ktx.getObjectFieldOrNull
import me.eternal.purrfectsnap.core.util.ktx.setObjectField
import me.eternal.purrfectsnap.core.wrapper.impl.SnapUUID
import me.eternal.purrfectsnap.mapper.impl.CallbackMapper
import java.util.Collections

class PinConversations : MessagingRuleFeature("PinConversations", MessagingRuleType.PIN_CONVERSATION) {
    companion object {
        // 3-year offset for persistent local conversation sorting
        private const val PIN_OFFSET = 100000000000L 
    }

    private fun forcePinsInFeed(entries: ArrayList<Any>) {
        val now = System.currentTimeMillis()
        // Capture stable timestamp once to prevent jitter during the sweep
        val stableTimestamp = now + PIN_OFFSET
        
        entries.forEach { entry ->
            val conversationIdObject = entry.getObjectFieldOrNull("mConversationId") ?: return@forEach
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    // Apply identical timestamp lead to all pinned items
                    entry.setObjectField("mPinnedTimestampMs", stableTimestamp)
                } else {
                    // Reset timestamp if it's currently a "Future" timestamp but shouldn't be pinned
                    val currentTs = entry.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                    if (currentTs > now + (PIN_OFFSET / 2)) {
                        entry.setObjectField("mPinnedTimestampMs", now)
                    }
                }
            }
        }

        // Manual sort to ensure stable UI transition and prevent list jumping
        runCatching {
            Collections.sort(entries) { a, b ->
                val tsA = a.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                val tsB = b.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                tsB.compareTo(tsA)
            }
        }
    }

    override fun init() {
        if (!context.config.messaging.unlimitedConversationPinning.get()) return

        // Intercept native pinning requests and bypass server-side limits
        context.classCache.feedManager.hook("setPinnedConversationStatus", HookStage.BEFORE) { param ->
            val conversationUUID = SnapUUID(param.arg(0))
            val isPinned = param.arg<Any>(1).toString() == "PINNED"
            setState(conversationUUID.toString(), isPinned)

            // Callback forcing to suppress "Can't pin conversation" errors for both PIN and UNPIN
            val callback = param.arg<Any>(2)
            mutableSetOf<() -> Unit>().apply {
                addAll(Hooker.ephemeralHookObjectMethod(callback::class.java, callback, "onSuccess", HookStage.BEFORE) {
                    forEach { it() }
                })
                addAll(Hooker.ephemeralHookObjectMethod(callback::class.java, callback, "onError", HookStage.BEFORE) { methodParam ->
                    methodParam.setResult(null)
                    // Manually trigger success to bypass server-side limit rejections
                    callback::class.java.getDeclaredMethod("onSuccess").invoke(callback)
                })
            }
        }

        // Active feed sweep to ensure pinned conversations remain at the top
        context.mappings.useMapper(CallbackMapper::class) {
            val callbackMap = callbacks.getAsMap().orEmpty()
            callbackMap.entries.forEach { (_, className) ->
                val clazz = runCatching { findClass(className!!) }.getOrNull() ?: return@forEach
                clazz.methods.forEach { method ->
                    if (method.name.startsWith("on") && method.name.endsWith("Complete") && method.parameterTypes.any { it == ArrayList::class.java }) {
                        clazz.hook(method.name, HookStage.BEFORE) { param ->
                            (param.args().firstOrNull { it is ArrayList<*> } as? ArrayList<Any>)?.let { forcePinsInFeed(it) }
                        }
                    }
                }
            }
        }

        // Apply pinning lead to newly created conversation objects
        context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationIdObject = instance.getObjectFieldOrNull("mConversationId") ?: return@hookConstructor
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    instance.setObjectField("mPinnedTimestampMs", System.currentTimeMillis() + PIN_OFFSET)
                }
            }
        }

        // Apply pinning lead to newly created feed entry objects
        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationIdObject = instance.getObjectFieldOrNull("mConversationId") ?: return@hookConstructor
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    instance.setObjectField("mPinnedTimestampMs", System.currentTimeMillis() + PIN_OFFSET)
                }
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}
