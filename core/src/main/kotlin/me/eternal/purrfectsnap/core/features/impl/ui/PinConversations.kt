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
        // Year 2030 Static Baseline to prevent jitter/jumbling during feed refreshes
        private const val STATIC_PIN_TIMESTAMP = 1893456000000L 
    }

    private fun forcePinsInFeed(entries: ArrayList<Any>) {
        entries.forEach { entry ->
            val conversationIdObject = entry.getObjectFieldOrNull("mConversationId") ?: return@forEach
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    // Apply identical STATIC timestamp lead to all pinned items
                    entry.setObjectField("mPinnedTimestampMs", STATIC_PIN_TIMESTAMP)
                } else {
                    // Reset timestamp if it was previously forced to our static baseline but shouldn't be pinned
                    val currentTs = entry.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                    if (currentTs == STATIC_PIN_TIMESTAMP) {
                        entry.setObjectField("mPinnedTimestampMs", 0L)
                    }
                }
            }
        }

        // Manual sort with stable tie-breaker to ensure UI consistency
        runCatching {
            Collections.sort(entries) { a, b ->
                val tsA = a.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                val tsB = b.getObjectFieldOrNull("mPinnedTimestampMs") as? Long ?: 0L
                
                if (tsA == tsB) {
                    // Stable Tie-Breaker: Use interaction timestamp if available, otherwise fallback to ID
                    val interactionA = a.getObjectFieldOrNull("mLastInteractionTimestamp") as? Long 
                        ?: a.getObjectFieldOrNull("mLastMessageTimestamp") as? Long ?: 0L
                    val interactionB = b.getObjectFieldOrNull("mLastInteractionTimestamp") as? Long 
                        ?: b.getObjectFieldOrNull("mLastMessageTimestamp") as? Long ?: 0L
                    
                    if (interactionA == interactionB) {
                        a.toString().compareTo(b.toString())
                    } else {
                        interactionB.compareTo(interactionA)
                    }
                } else {
                    tsB.compareTo(tsA)
                }
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

        // Apply static pinning lead to newly created objects
        context.classCache.conversation.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationIdObject = instance.getObjectFieldOrNull("mConversationId") ?: return@hookConstructor
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    instance.setObjectField("mPinnedTimestampMs", STATIC_PIN_TIMESTAMP)
                }
            }
        }

        context.classCache.feedEntry.hookConstructor(HookStage.AFTER) { param ->
            val instance = param.thisObject<Any>()
            val conversationIdObject = instance.getObjectFieldOrNull("mConversationId") ?: return@hookConstructor
            runCatching {
                val conversationUUID = SnapUUID(conversationIdObject)
                if (getState(conversationUUID.toString())) {
                    instance.setObjectField("mPinnedTimestampMs", STATIC_PIN_TIMESTAMP)
                }
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}
