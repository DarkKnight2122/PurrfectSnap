package me.eternal.purrfect.core.whatsapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Message
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import me.eternal.purrfect.core.logger.CoreLogger
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class WhatsAppPrivacyHooks(
    private val androidContext: Context,
    private val appClassLoader: ClassLoader = androidContext.classLoader
) {
    private val featureState get() = WhatsAppFeatureStateStore.current

    fun init() {
        if (!installed.compareAndSet(false, true)) return

        hookConcreteReadReceipts()
        hookConcretePlayedReceipts()
        hookConcreteChatStateSender()
        hookReadReceiptManager()
        hookMessageClientProtocolSends()
        hookConnectionTransport()
        hookRevokeStore()
        hookSQLiteMutationSurface()

        logLifecycle("Installed WhatsApp privacy hooks")
    }

    private fun hookConcreteReadReceipts() {
        val cls = appClassLoader.findClassOrNull("com.whatsapp.messaging.receipts.jobqueue.job.SendReadReceiptJob")
            ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "A0F",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("SendReadReceiptJob.A0F") {
                            val job = param.thisObject ?: return@runSafe
                            val receiptClass = job.fieldValue("receiptClass")?.toString().orEmpty()
                            val jid = job.fieldValue("jid")?.toString().orEmpty()
                            if (shouldSuppressReadReceipt(receiptClass, jid)) {
                                param.result = null
                                logPrivacy(
                                    "hide_read_receipt_job",
                                    "suppressed",
                                    "receiptClass=$receiptClass jid=${redactJid(jid)}"
                                )
                            }
                        }
                    }
                }
            )
            logLifecycle("Hooked WhatsApp read receipt job")
        }.onFailure { logError("Failed to hook read receipt job", it) }
    }

    private fun hookConcretePlayedReceipts() {
        val cls = appClassLoader.findClassOrNull("com.whatsapp.messaging.receipts.jobqueue.job.SendPlayedReceiptJobV2")
            ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "A0F",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("SendPlayedReceiptJobV2.A0F") {
                            if (!featureState.hideAudioSeen) return@runSafe
                            val job = param.thisObject ?: return@runSafe
                            val jid = job.fieldValue("toRawJid")?.toString().orEmpty()
                            param.result = null
                            logPrivacy("hide_audio_seen_job", "suppressed", "jid=${redactJid(jid)}")
                        }
                    }
                }
            )
            logLifecycle("Hooked WhatsApp played receipt job")
        }.onFailure { logError("Failed to hook played receipt job", it) }
    }

    private fun hookConcreteChatStateSender() {
        val cls = appClassLoader.findClassOrNull("X.C165927bw")
            ?: appClassLoader.findClassOrNull("X.7bw")
            ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "A01",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("HandleMeComposing.A01") {
                            val mediaArg = param.args.getOrNull(2) as? Int ?: return@runSafe
                            val isAudio = mediaArg == 1
                            val shouldSuppress = if (isAudio) {
                                featureState.hideRecordingAudio
                            } else {
                                featureState.hideTypingIndicators
                            }
                            if (!shouldSuppress) return@runSafe

                            param.result = null
                            logPrivacy(
                                if (isAudio) "hide_recording_audio_chatstate" else "hide_typing_chatstate",
                                "suppressed",
                                "media=$mediaArg"
                            )
                        }
                    }
                }
            )
            logLifecycle("Hooked WhatsApp composing or recording sender")
        }.onFailure { logError("Failed to hook composing sender", it) }
    }

    private fun hookMessageClientProtocolSends() {
        val cls = appClassLoader.findClassOrNull("X.C05010My")
            ?: appClassLoader.findClassOrNull("X.0My")
            ?: return
        runCatching {
            var hooked = 0
            cls.declaredMethods
                .filter { it.name in MESSAGE_CLIENT_SEND_METHODS }
                .forEach { method ->
                    method.isAccessible = true
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam<*>) {
                                runSafe("MessageClient.${method.name}") {
                                    val decision = suppressionForOutgoingArgs(param.args) ?: return@runSafe
                                    suppressReturn(param, decision, "suppressed_message_client_${method.name}")
                                }
                            }
                        }
                    )
                    hooked++
                }
            logLifecycle("Hooked WhatsApp protocol send surface methods=$hooked")
        }.onFailure { logError("Failed to hook protocol send surface", it) }
    }

    private fun hookReadReceiptManager() {
        val cls = appClassLoader.findClassOrNull("X.C16570ox")
            ?: appClassLoader.findClassOrNull("X.0ox")
            ?: return
        runCatching {
            hookAllIfPresent(cls, "A0E") { param ->
                if (!shouldSuppressReadReceiptForMessage(param.args.firstOrNull())) return@hookAllIfPresent
                param.result = null
                logPrivacy("hide_read_receipt_manager", "suppressed_prepare", describeMessageIdentity(param.args.firstOrNull()))
            }
            hookAllIfPresent(cls, "A0F") { param ->
                if (!shouldSuppressReadReceiptForMessage(param.args.firstOrNull())) return@hookAllIfPresent
                param.result = null
                logPrivacy("hide_read_receipt_manager", "suppressed_send", describeMessageIdentity(param.args.firstOrNull()))
            }
            hookAllIfPresent(cls, "A0R") { param ->
                if (!shouldSuppressReadReceiptForMessage(param.args.firstOrNull())) return@hookAllIfPresent
                param.result = true
                logPrivacy("hide_read_receipt_manager", "suppressed_retry", describeMessageIdentity(param.args.firstOrNull()))
            }
            listOf("A0L", "A0M", "A0N", "A0O").forEach { methodName ->
                hookAllIfPresent(cls, methodName) { param ->
                    if (!featureState.hideDelivered) return@hookAllIfPresent
                    param.result = null
                    logPrivacy("hide_delivered_manager", "suppressed_$methodName", describeOutgoingArgs(param.args))
                }
            }
            logLifecycle("Hooked WhatsApp read and delivery receipt manager")
        }.onFailure { logError("Failed to hook read and delivery receipt manager", it) }
    }

    private fun hookConnectionTransport() {
        val cls = appClassLoader.findClassOrNull("X.HandlerC242915e")
            ?: appClassLoader.findClassOrNull("X.15e")
            ?: return
        runCatching {
            hookAllIfPresent(cls, "C7c") { param ->
                val decision = suppressionForOutgoingArgs(param.args) ?: return@hookAllIfPresent
                param.result = null
                logPrivacy(decision.type, "suppressed_connection_queue", decision.detail)
            }
            hookAllIfPresent(cls, "handleMessage") { param ->
                val message = param.args.firstOrNull() as? Message ?: return@hookAllIfPresent
                if (message.what != CONNECTION_SEND_WHAT) return@hookAllIfPresent
                val decision = suppressionForOutgoingArgs(arrayOf<Any?>(message)) ?: return@hookAllIfPresent
                param.result = null
                logPrivacy(decision.type, "suppressed_connection_handle", decision.detail)
            }
            logLifecycle("Hooked WhatsApp connection transport send surface")
        }.onFailure { logError("Failed to hook connection transport", it) }
    }

    private fun hookRevokeStore() {
        val cls = appClassLoader.findClassOrNull("X.C235211z")
            ?: appClassLoader.findClassOrNull("X.11z")
            ?: return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "A04",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("RevokeStore.A04") {
                            if (!featureState.showDeletedMessages) return@runSafe
                            val message = param.args.firstOrNull() ?: return@runSafe
                            if (message.isFromMeMessage()) return@runSafe
                            param.result = false
                            logPrivacy("show_deleted_messages_revoke_store", "suppressed", message.safeClassName())
                        }
                    }
                }
            )
            logLifecycle("Hooked WhatsApp revoke store")
        }.onFailure { logError("Failed to hook revoke store", it) }
    }

    private fun hookSQLiteMutationSurface() {
        runCatching {
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "insert",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        handleInsertLike(param)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "insertWithOnConflict",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        handleInsertLike(param)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "update",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        handleUpdateLike(param)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "updateWithOnConflict",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        handleUpdateLike(param)
                    }
                }
            )
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "delete",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("SQLiteDatabase.delete") {
                            if (!featureState.showDeletedMessages) return@runSafe
                            val table = param.args.firstOrNull()?.toString().orEmpty()
                            if (!isRevokeTable(table)) return@runSafe
                            param.result = 0
                            logPrivacy("show_deleted_messages_revoke_table", "suppressed_delete", table)
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                SQLiteDatabase::class.java,
                "execSQL",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam<*>) {
                        runSafe("SQLiteDatabase.execSQL") {
                            val sql = param.args.firstOrNull()?.toString().orEmpty()
                            val lower = sql.lowercase(Locale.ROOT)
                            if (
                                featureState.showDeletedMessages &&
                                lower.contains("message_revoked") &&
                                (lower.contains("delete") || lower.contains("update"))
                            ) {
                                param.result = null
                                logPrivacy("show_deleted_messages_revoke_sql", "suppressed_exec", sql.take(120))
                            }
                        }
                    }
                }
            )
            logLifecycle("Hooked WhatsApp SQLite privacy mutation surface")
        }.onFailure { logError("Failed to hook SQLite mutation surface", it) }
    }

    private fun handleInsertLike(param: XC_MethodHook.MethodHookParam<*>) {
        runSafe("SQLiteDatabase.insert") {
            val table = param.args.firstOrNull()?.toString().orEmpty()
            val values = param.args.firstInstance<ContentValues>() ?: return@runSafe
            if (featureState.showDeletedMessages && shouldSuppressRevokeMutation(table, values)) {
                param.result = -1L
                logPrivacy("show_deleted_messages_db", "suppressed_insert", describeValues(table, values))
            }
        }
    }

    private fun handleUpdateLike(param: XC_MethodHook.MethodHookParam<*>) {
        runSafe("SQLiteDatabase.update") {
            val table = param.args.firstOrNull()?.toString().orEmpty()
            val values = param.args.firstInstance<ContentValues>() ?: return@runSafe
            if (featureState.showDeletedMessages && shouldSuppressRevokeMutation(table, values)) {
                param.result = 0
                logPrivacy("show_deleted_messages_db", "suppressed_update", describeValues(table, values))
                return@runSafe
            }
            normalizeViewOnceUpdateValues(table, values)
        }
    }

    private fun shouldSuppressReadReceipt(receiptClass: String, jid: String): Boolean {
        val type = receiptClass.lowercase(Locale.ROOT)
        if (type.isBlank()) return false
        if (!type.contains("read") && !type.contains("view_once_read")) return false
        if (featureState.hideViewOnceSeen && type.contains("view_once")) return true
        val isGroup = isGroupText(jid)
        return if (isGroup) featureState.hideBlueTicksGroups else featureState.hideBlueTicks
    }

    private fun suppressionForProtocolText(raw: String, keyHint: String?): SuppressionDecision? {
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.isBlank()) return null

        if (lower.contains("chatstate") && lower.contains("composing")) {
            val isAudio = lower.contains("media='audio'") ||
                lower.contains("media=\"audio\"") ||
                lower.contains(" media=audio") ||
                lower.contains("audio")
            if (isAudio && featureState.hideRecordingAudio) {
                return SuppressionDecision("hide_recording_audio_protocol", redactProtocol(raw))
            }
            if (!isAudio && featureState.hideTypingIndicators) {
                return SuppressionDecision("hide_typing_protocol", redactProtocol(raw))
            }
        }

        val isReceipt = lower.contains("<receipt") ||
            lower.contains("cls=receipt") ||
            lower.contains(" receipt") ||
            lower.contains("receipt,") ||
            lower.contains("receipt)") ||
            lower.contains("read-receipt")
        if (!isReceipt) return null

        if (featureState.hideDelivered && lower.hasAnyType("delivery", "received")) {
            return SuppressionDecision("hide_delivered_protocol", redactProtocol(raw))
        }

        if (featureState.hideAudioSeen && lower.hasAnyType("played", "played-self")) {
            return SuppressionDecision("hide_audio_seen_protocol", redactProtocol(raw))
        }

        if (featureState.hideViewOnceSeen && lower.contains("view_once_read")) {
            return SuppressionDecision("hide_view_once_seen_protocol", redactProtocol(raw))
        }

        if (lower.hasAnyType("read", "read-self")) {
            val source = keyHint ?: raw
            val isGroup = isGroupText(source)
            val hasJid = source.contains("@") || source.contains("remotejid=", ignoreCase = true)
            val suppress = when {
                isGroup -> featureState.hideBlueTicksGroups
                !hasJid -> featureState.hideBlueTicks || featureState.hideBlueTicksGroups
                else -> featureState.hideBlueTicks
            }
            if (suppress) return SuppressionDecision("hide_read_receipt_protocol", redactProtocol(raw))
        }

        return null
    }

    private fun shouldSuppressRevokeMutation(table: String, values: ContentValues): Boolean {
        if (isRevokeTable(table)) return true
        return false
    }

    private fun normalizeViewOnceUpdateValues(table: String, values: ContentValues) {
        if (!shouldNormalizeViewOnce()) return
        if (!isMessageTable(table) || !values.containsKey("view_mode")) return
        if (values.size() > VIEW_ONCE_UPDATE_VALUE_LIMIT) return
        val viewMode = values.getAsInteger("view_mode")
            ?: values.getAsLong("view_mode")?.toInt()
            ?: values.getAsString("view_mode")?.toIntOrNull()
            ?: return
        if (viewMode == 0) return

        values.put("view_mode", 0)
        logPrivacy(
            if (featureState.unlimitedViewOnce) "unlimited_view_once_db" else "hide_view_once_seen_db",
            "normalized_view_mode_update",
            "table=$table old=$viewMode"
        )
    }

    private fun shouldNormalizeViewOnce(): Boolean {
        return featureState.unlimitedViewOnce || featureState.hideViewOnceSeen
    }

    private fun completedFutureOrNull(): Any? {
        return runCatching {
            val futureClass = appClassLoader.findClassOrNull("X.FutureC23163AMr") ?: return null
            val future = futureClass.getDeclaredConstructor().newInstance()
            val complete = futureClass.methods.firstOrNull {
                it.name == "BTq" && it.parameterTypes.size == 1
            } ?: return null
            complete.invoke(future, null)
            future
        }.onFailure { logError("Failed to create completed WhatsApp future", it) }.getOrNull()
    }

    private fun suppressionForOutgoingArgs(args: Array<Any?>): SuppressionDecision? {
        val raw = describeOutgoingArgs(args)
        val decision = suppressionForProtocolText(raw, raw)
        if (decision == null) logObservedPrivacyCandidate(raw)
        return decision
    }

    private fun suppressReturn(
        param: XC_MethodHook.MethodHookParam<*>,
        decision: SuppressionDecision,
        action: String
    ): Boolean {
        val returnType = (param.method as? Method)?.returnType ?: Void.TYPE
        val result = when {
            returnType == Void.TYPE -> null
            returnType == java.lang.Boolean.TYPE || returnType == Boolean::class.javaObjectType -> true
            returnType.name.contains("FutureC23163AMr") -> completedFutureOrNull() ?: return false
            else -> return false
        }
        param.result = result
        logPrivacy(decision.type, action, decision.detail)
        return true
    }

    private fun hookAllIfPresent(
        cls: Class<*>,
        methodName: String,
        block: (XC_MethodHook.MethodHookParam<*>) -> Unit
    ) {
        if (cls.declaredMethods.none { it.name == methodName }) return
        XposedBridge.hookAllMethods(
            cls,
            methodName,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam<*>) {
                    runSafe("${cls.name}.$methodName") { block(param) }
                }
            }
        )
    }

    private fun shouldSuppressReadReceiptForMessage(message: Any?): Boolean {
        if (message == null) return featureState.hideBlueTicks || featureState.hideBlueTicksGroups
        val identity = describeMessageIdentity(message)
        if (featureState.hideViewOnceSeen && identity.contains("view_once", ignoreCase = true)) return true
        val isGroup = isGroupText(identity)
        val hasJid = identity.contains("@") || identity.contains("jid", ignoreCase = true)
        return when {
            isGroup -> featureState.hideBlueTicksGroups
            !hasJid -> featureState.hideBlueTicks || featureState.hideBlueTicksGroups
            else -> featureState.hideBlueTicks
        }
    }

    private fun describeMessageIdentity(message: Any?): String {
        if (message == null) return ""
        val key = message.fieldValue("A0i")
        return listOfNotNull(
            key?.fieldValue("A00"),
            key?.fieldValue("A01"),
            key?.fieldValue("A02"),
            message.fieldValue("A0p"),
            message.fieldValue("A0h"),
            message.safeClassName(),
            message.toString()
        ).joinToString("|") { it.toString() }.take(500)
    }

    private fun describeOutgoingArgs(args: Array<Any?>): String {
        return args.joinToString(" | ") { describeOutgoingArg(it) }.take(2400)
    }

    private fun describeOutgoingArg(arg: Any?): String {
        return when (arg) {
            null -> ""
            is Message -> {
                val data = runCatching { arg.peekData()?.toString().orEmpty() }.getOrDefault("")
                "Message{what=${arg.what},arg1=${arg.arg1},arg2=${arg.arg2},data=$data,obj=${describeOutgoingArg(arg.obj)}}"
            }
            is ContentValues -> describeValues("", arg)
            else -> "${arg.safeClassName()}:$arg".take(1200)
        }
    }

    private fun logObservedPrivacyCandidate(raw: String) {
        if (!hasAnyPrivacySendFeature()) return
        val lower = raw.lowercase(Locale.ROOT)
        if (
            lower.contains("chatstate") ||
            lower.contains("composing") ||
            lower.contains("receipt") ||
            lower.contains("delivery") ||
            lower.contains("played") ||
            lower.contains("read-receipt")
        ) {
            logPrivacy("privacy_outgoing_candidate", "observed_unsuppressed", redactProtocol(raw))
        }
    }

    private fun hasAnyPrivacySendFeature(): Boolean {
        return featureState.hideTypingIndicators ||
            featureState.hideRecordingAudio ||
            featureState.hideDelivered ||
            featureState.hideAudioSeen ||
            featureState.hideViewOnceSeen ||
            featureState.hideBlueTicksGroups ||
            featureState.hideBlueTicks
    }

    private fun isMessageTable(table: String): Boolean {
        return table.equals("message", ignoreCase = true) ||
            table.equals("available_message_view", ignoreCase = true)
    }

    private fun isRevokeTable(table: String): Boolean {
        return table.equals("message_revoked", ignoreCase = true) ||
            table.equals("message_orphaned_edit", ignoreCase = true)
    }

    private fun isGroupText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("@g.us") || lower.contains("g.us") || lower.contains("groupjid")
    }

    private fun String.hasAnyType(vararg types: String): Boolean {
        return types.any { type ->
            contains("type=$type") ||
                contains("type='$type'") ||
                contains("type=\"$type\"") ||
                contains(" $type ") ||
                contains(">$type<")
        }
    }

    private fun Any.isFromMeMessage(): Boolean {
        val key = fieldValue("A0i") ?: return false
        return key.fieldValue("A02") as? Boolean ?: false
    }

    private fun Any.fieldValue(name: String): Any? {
        var cls: Class<*>? = javaClass
        while (cls != null) {
            runCatching {
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                return field.get(this)
            }
            cls = cls.superclass
        }
        return null
    }

    private inline fun <reified T> Array<Any?>.firstInstance(): T? {
        return firstOrNull { it is T } as? T
    }

    private fun ClassLoader.findClassOrNull(name: String): Class<*>? {
        return runCatching { loadClass(name) }.getOrNull()
    }

    private fun Any.safeClassName(): String = javaClass.name

    private fun describeValues(table: String, values: ContentValues): String {
        return "table=$table keys=${values.keySet().joinToString(",").take(160)}"
    }

    private fun redactJid(jid: String): String {
        if (jid.isBlank()) return ""
        val suffix = jid.substringAfter('@', missingDelimiterValue = "")
        return if (suffix.isBlank()) "<jid>" else "<jid>@$suffix"
    }

    private fun redactProtocol(raw: String): String {
        return raw
            .replace(Regex("[0-9]{5,}(@[a-z.]+)?"), "<id>$1")
            .take(240)
    }

    private fun runSafe(area: String, block: () -> Unit) {
        runCatching(block).onFailure { logError("WhatsApp privacy hook failed in $area", it) }
    }

    private fun logLifecycle(message: String) {
        XposedBridge.log("[$TAG] $message")
        CoreLogger.xposedLog(message, TAG)
        WhatsAppAppLogWriter.info(androidContext, TAG, message)
    }

    private fun logPrivacy(type: String, action: String, detail: String) {
        val key = "$type|$action|$detail".take(512)
        val count = privacyEvents.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
        if (count > 3 && count != 10 && count != 25 && count % 100 != 0) return
        logLifecycle("WhatsApp privacy hook: type=$type action=$action detail=${detail.take(900)} count=$count")
    }

    private fun logError(message: String, throwable: Throwable) {
        XposedBridge.log("[$TAG] $message: ${throwable.stackTraceToString()}")
        CoreLogger.xposedLog("$message: ${throwable.message}", TAG)
        WhatsAppAppLogWriter.error(androidContext, TAG, "$message: ${throwable.stackTraceToString()}")
    }

    private data class SuppressionDecision(
        val type: String,
        val detail: String
    )

    companion object {
        private const val TAG = "PurrfectWA.Privacy"
        private const val CONNECTION_SEND_WHAT = 4
        private const val VIEW_ONCE_UPDATE_VALUE_LIMIT = 3
        private val MESSAGE_CLIENT_SEND_METHODS = setOf(
            "A04",
            "A05",
            "A06",
            "A08",
            "A09",
            "A0A",
            "A0B",
            "A0C",
            "A0D",
            "A0I",
            "A0J",
            "A0K",
            "A0L",
            "A0M",
            "A0N",
            "A0P",
            "A0Q"
        )
        private val installed = AtomicBoolean(false)
        private val privacyEvents = ConcurrentHashMap<String, AtomicInteger>()
    }
}
