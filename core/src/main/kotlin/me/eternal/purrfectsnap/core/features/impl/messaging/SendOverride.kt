package me.eternal.purrfectsnap.core.features.impl.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.bridge.task.TaskListener
import me.eternal.purrfectsnap.common.data.ContentType
import me.eternal.purrfectsnap.common.ui.createComposeAlertDialog
import me.eternal.purrfectsnap.common.util.protobuf.ProtoEditor
import me.eternal.purrfectsnap.common.util.protobuf.ProtoReader
import me.eternal.purrfectsnap.common.util.protobuf.ProtoWriter
import me.eternal.purrfectsnap.core.event.events.impl.MediaUploadEvent
import me.eternal.purrfectsnap.core.event.events.impl.NativeUnaryCallEvent
import me.eternal.purrfectsnap.core.event.events.impl.SendMessageWithContentEvent
import me.eternal.purrfectsnap.core.event.events.impl.UnaryCallEvent
import me.eternal.purrfectsnap.core.features.Feature
import me.eternal.purrfectsnap.core.features.impl.experiments.MediaFilePicker
import me.eternal.purrfectsnap.core.messaging.MessageSender
import me.eternal.purrfectsnap.core.ui.PurrfectOverlayPalette
import me.eternal.purrfectsnap.core.ui.PurrfectOverlayTheme
import me.eternal.purrfectsnap.core.wrapper.impl.MessageContent
import me.eternal.purrfectsnap.core.wrapper.impl.MessageDestinations
import me.eternal.purrfectsnap.core.wrapper.impl.SnapUUID
import me.eternal.purrfectsnap.core.util.ktx.getObjectFieldOrNull
import me.eternal.purrfectsnap.core.util.ktx.setObjectField
import me.eternal.purrfectsnap.core.util.CallbackBuilder
import me.eternal.purrfectsnap.core.util.hook.HookStage
import me.eternal.purrfectsnap.core.util.hook.Hooker
import me.eternal.purrfectsnap.core.util.hook.hook
import me.eternal.purrfectsnap.core.util.hook.hookConstructor
import me.eternal.purrfectsnap.mapper.impl.CallbackMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.ArrayList
import kotlin.time.DurationUnit
import kotlin.time.toDuration


@OptIn(ExperimentalMaterial3Api::class)
class SendOverride : Feature("Send Override") {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "scheduled_send"
        private val internalMultipartSend = ThreadLocal.withInitial { false }
        private var queuedOriginalItemRepeatCount = 0
        private var queuedOriginalItemRepeatOverrideType: String? = null
        
        // RECURSIVE CONTEXT: Store verified objects for background sending
        private var lastCapturedDestinationsObj: Any? = null
        private var lastCapturedMessageContentJson: String? = null
        private var lastCapturedOriginalCallback: Any? = null

        private fun queueOriginalItemRepeats(repeatCount: Int, overrideType: String, destinations: Any, contentJson: String, callback: Any?) {
            queuedOriginalItemRepeatCount = repeatCount
            queuedOriginalItemRepeatOverrideType = overrideType
            lastCapturedDestinationsObj = destinations
            lastCapturedMessageContentJson = contentJson
            lastCapturedOriginalCallback = callback
            MediaFilePicker.setQueuedOverrideType(overrideType)
        }

        private fun clearQueuedOriginalItemRepeats() {
            queuedOriginalItemRepeatCount = 0
            queuedOriginalItemRepeatOverrideType = null
            lastCapturedDestinationsObj = null
            lastCapturedMessageContentJson = null
            lastCapturedOriginalCallback = null
        }
    }
    
    private var selectedType by mutableStateOf("SNAP")
    private var disableSplitForCurrentSend by mutableStateOf(false)
    private var customDuration by mutableFloatStateOf(10f)
    private var scheduledTime by mutableStateOf<Long?>(null)
    private var showClockPicker by mutableStateOf(false)
    private var clockPickerHour by mutableIntStateOf(12)
    private var clockPickerMinute by mutableIntStateOf(0)
    private var notificationIdCounter = 1000
    private val backgroundHookLock = Any()
    private var backgroundHookRefs = 0
    private var backgroundHooks: List<Hooker.HookHandle>? = null

    private val sendMessageCallbackClass by lazy {
        lateinit var result: Class<*>
        context.mappings.useMapper(CallbackMapper::class) {
            result = callbacks.getClass("SendMessageCallback") ?: error("Failed to resolve SendMessageCallback")
        }
        result
    }

    private val sendMessageWithContentMethod by lazy {
        sequence {
            var current: Class<*>? = context.classCache.conversationManager
            while (current != null && current != Any::class.java && current != Object::class.java) {
                yield(current)
                current = current.superclass
            }
        }.flatMap { it.declaredMethods.asSequence() }
            .first { it.name == "sendMessageWithContent" }
    }

    private val conversationManagerInstance by lazy {
        context.feature(Messaging::class).conversationManager?.instanceNonNull()
    }

    private fun invokeSendManually(destinations: Any, messageContent: MessageContent, callback: Any?) {
        val conversationManager = conversationManagerInstance ?: run {
            context.log.error("SendOverride: Failed to send manually, ConversationManager is null")
            return
        }
        internalMultipartSend.set(true)
        try {
            // VERIFIED SEND: Use original objects to prevent INTERNALERROR
            sendMessageWithContentMethod.invoke(
                conversationManager,
                destinations,
                messageContent.instanceNonNull(),
                callback
            )
        } catch (e: Exception) {
            context.log.error("SendOverride: Failed to invoke manual send", e)
        } finally {
            internalMultipartSend.set(false)
        }
    }

    private fun handleQueuedOriginalItemRepeatSuccess(convId: String): Boolean {
        if (queuedOriginalItemRepeatCount <= 0) {
            clearQueuedOriginalItemRepeats()
            return false
        }

        val destinations = lastCapturedDestinationsObj ?: return false
        val contentJson = lastCapturedMessageContentJson ?: return false
        val originalCallback = lastCapturedOriginalCallback

        queuedOriginalItemRepeatCount--
        
        val repeatedContent = MessageContent(context.gson.fromJson(contentJson, context.classCache.localMessageContent))
        
        val callback = CallbackBuilder(sendMessageCallbackClass)
            .override("onSuccess") {
                context.runOnUiThread {
                    if (!handleQueuedOriginalItemRepeatSuccess(convId)) {
                        context.log.info("[SPLIT] SendOverride: All repeats completed for $convId")
                        runCatching {
                            originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onSuccess" }?.invoke(originalCallback)
                        }
                    }
                }
            }
            .override("onError", shouldUnhook = false) {
                context.log.error("[SPLIT] SendOverride: Repeat failed for $convId: ${it.argNullable<Any>(0)}")
                val error = it.argNullable<Any>(0)
                runCatching {
                    originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onError" && it.parameterCount == 1 }?.invoke(originalCallback, error)
                }
                clearQueuedOriginalItemRepeats()
            }
            .build()

        context.log.info("[SPLIT] SendOverride: Sending background repeat for $convId ($queuedOriginalItemRepeatCount remaining)")
        invokeSendManually(destinations, repeatedContent, callback)
        return true
    }

    private fun handleSplitSendRecursive(convId: String, destinations: Any, baseContentJson: String, originalCallback: Any?): Boolean {
        val nextChunkItemId = MediaFilePicker.getNextQueuedSplitItemId(convId) ?: return false
        
        val chunkContent = MessageContent(context.gson.fromJson(baseContentJson, context.classCache.localMessageContent))
        
        // SWAP MEDIA ID IN PROTO (Snap: 11 -> 5 -> 1 -> 1 -> 2)
        val protoEditor = ProtoEditor(chunkContent.content ?: return false)
        protoEditor.edit(11, 5, 1, 1) {
            remove(2)
            addString(2, nextChunkItemId)
        }
        chunkContent.content = protoEditor.toByteArray()
        
        val callback = CallbackBuilder(sendMessageCallbackClass)
            .override("onSuccess") {
                context.runOnUiThread {
                    if (!handleSplitSendRecursive(convId, destinations, baseContentJson, originalCallback)) {
                        context.log.info("[SPLIT] SendOverride: All chunks sent successfully for $convId")
                        MediaFilePicker.clearQueuedSplitItems(convId)
                        runCatching {
                            originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onSuccess" }?.invoke(originalCallback)
                        }
                    }
                }
            }
            .override("onError", shouldUnhook = false) {
                val error = it.argNullable<Any>(0)
                context.log.error("[SPLIT] SendOverride: Chunk send failed for $convId: $error")
                runCatching {
                    originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onError" && it.parameterCount == 1 }?.invoke(originalCallback, error)
                }
                MediaFilePicker.clearQueuedSplitItems(convId)
            }
            .build()

        context.log.info("[SPLIT] SendOverride: Sending background chunk for $convId (MediaID=$nextChunkItemId)")
        invokeSendManually(destinations, chunkContent, callback)
        return true
    }

    private fun acquireScheduledSendBackground(): () -> Unit {
        if (!context.config.messaging.scheduledSendAllowRunningInBackground.get()) return {}
        var enableFailed = false
        synchronized(backgroundHookLock) {
            backgroundHookRefs++
            if (backgroundHookRefs == 1) {
                if (!enableScheduledSendBackgroundLocked()) {
                    backgroundHookRefs--
                    enableFailed = true
                }
            }
        }
        if (enableFailed) return {}
        var released = false
        return {
            synchronized(backgroundHookLock) {
                if (released) return@synchronized
                released = true
                if (backgroundHookRefs > 0) backgroundHookRefs--
                if (backgroundHookRefs == 0) {
                    backgroundHooks?.forEach { it.unhook() }
                    backgroundHooks = null
                }
            }
        }
    }

    private fun enableScheduledSendBackgroundLocked(): Boolean {
        return runCatching {
            val duplexClass = findClass("com.snapchat.client.duplex.DuplexClient\$CppProxy")
            val hooks = mutableListOf<Hooker.HookHandle>()
            
            hooks.addAll(Hooker.hook(duplexClass, "appStateChanged", HookStage.BEFORE) { param ->
                val state = param.arg<Any>(0).toString()
                if (state == "INACTIVE" || state == "BACKGROUND") {
                    param.setResult(null)
                }
            })
            
            hooks.addAll(Hooker.hookConstructor(duplexClass, HookStage.AFTER) { param ->
                val appStateMethod = duplexClass.declaredMethods.firstOrNull { it.name == "appStateChanged" }
                val activeValue = appStateMethod?.parameterTypes?.getOrNull(0)?.enumConstants?.firstOrNull { it.toString() == "ACTIVE" }
                if (activeValue != null) {
                    appStateMethod.invoke(param.thisObject(), activeValue)
                }
            })
            
            backgroundHooks = hooks
            true
        }.getOrElse {
            context.log.error("Failed to enable scheduled send background mode", it)
            false
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.androidContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scheduled Send",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Notifications for scheduled snap sends"
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, content: String) {
        val notificationManager = context.androidContext.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(context.androidContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(notificationIdCounter++, builder.build())
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun init() {
        createNotificationChannel()
        
        val stripMediaMetadata = context.config.messaging.stripMediaMetadata.get()
        var postSavePolicy: Int? = null

        // RECOVERY FIX: Remove restrictive early return to ensure event subscription
        val configOverrideType = context.config.messaging.galleryMediaSendOverride.mode.getNullable()?.toString()

        context.event.subscribe(MediaUploadEvent::class) { event ->
            if (stripMediaMetadata.isNotEmpty() && 
                (event.localMessageContent.contentType == ContentType.NOTE || 
                 stripMediaMetadata.contains("remove_audio_note_duration") || 
                 stripMediaMetadata.contains("remove_audio_note_transcript_capability"))) {
                event.onMediaUploaded { result ->
                    if (result.messageContent.contentType == ContentType.NOTE) {
                        val contentReader = ProtoReader(result.messageContent.content!!)
                        result.messageContent.content = ProtoEditor(result.messageContent.content!!).apply {
                            val hasFullPath = contentReader.followPath(4, 4, 6, 1, 1) != null
                            val hasDirectPath = contentReader.followPath(6, 1, 1) != null
                            
                            if (stripMediaMetadata.contains("remove_audio_note_duration")) {
                                if (hasFullPath) edit(4, 4, 6, 1, 1) { remove(13) }
                                if (hasDirectPath || !hasFullPath) edit(6, 1, 1) { remove(13) }
                            }
                            if (stripMediaMetadata.contains("remove_audio_note_transcript_capability")) {
                                if (hasFullPath) edit(4, 4, 6, 1) { remove(3) }
                                if (hasDirectPath || !hasFullPath) edit(6, 1) { remove(3) }
                                runCatching {
                                    result.messageContent.instanceNonNull().setObjectField("mAllowsTranscription", false)
                                }
                            }
                        }.toByteArray()
                    }
                }
            }

            ProtoReader(event.localMessageContent.content!!).followPath(11, 5)?.let { snapDocPlayback ->
                event.onMediaUploaded { result ->
                    result.messageContent.content = ProtoEditor(result.messageContent.content!!).apply {
                        edit(11, 5) {
                            edit(1) {
                                edit(1) {
                                    snapDocPlayback.getVarInt(2, 99)?.let { customDuration ->
                                        remove(15)
                                        addVarInt(15, customDuration)
                                    }
                                    remove(27)
                                    remove(26)
                                    addBuffer(26, byteArrayOf())
                                }
                            }
                            snapDocPlayback.getByteArray(2)?.let {
                                val originalHasSound = firstOrNull(2)?.toReader()?.getVarInt(5)
                                remove(2)
                                addBuffer(2, it)
                                originalHasSound?.let { hasSound ->
                                    edit(2) {
                                        remove(5)
                                        addVarInt(5, hasSound)
                                    }
                                }
                            }
                        }

                        if (stripMediaMetadata.isNotEmpty()) {
                            when (result.messageContent.contentType) {
                                ContentType.SNAP, ContentType.EXTERNAL_MEDIA -> {
                                    edit(*(if (result.messageContent.contentType == ContentType.SNAP) intArrayOf(11) else intArrayOf(3, 3))) {
                                        if (stripMediaMetadata.contains("hide_caption_text")) {
                                            edit(5) { editEach(1) { remove(2) } }
                                        }
                                        if (stripMediaMetadata.contains("hide_snap_filters")) {
                                            remove(9)
                                            remove(11)
                                        }
                                        if (stripMediaMetadata.contains("hide_extras")) {
                                            remove(13)
                                            edit(5, 1) { remove(2) }
                                        }
                                    }
                                }
                                ContentType.NOTE -> {
                                    if (stripMediaMetadata.contains("remove_audio_note_duration")) edit(6, 1, 1) { remove(13) }
                                    if (stripMediaMetadata.contains("remove_audio_note_transcript_capability")) edit(6, 1) { remove(3) }
                                }
                                else -> {}
                            }
                        }
                        edit(11, 5, 2) { remove(99) }
                    }.toByteArray()
                }
            }
        }

        context.event.subscribe(NativeUnaryCallEvent::class, priority = 100) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            postSavePolicy?.let { savePolicy ->
                val protoReader = ProtoReader(event.buffer)
                event.buffer = ProtoEditor(event.buffer).apply {
                    if (protoReader.followPath(4) != null) {
                        edit(4) {
                            remove(7)
                            addVarInt(7, savePolicy)
                        }
                        if (savePolicy == 1) edit(6, 9) { remove(1) }
                    }
                    val noteNested = protoReader.followPath(4, 4, 6) != null
                    val noteAtRoot = protoReader.followPath(6) != null
                    if (noteAtRoot || noteNested) {
                        if (noteNested) {
                            edit(4, 4, 6, 1) { remove(7); addVarInt(7, savePolicy) }
                        } else {
                            edit(6, 1) { remove(7); addVarInt(7, savePolicy) }
                        }
                    }
                    val snapAtRoot = protoReader.followPath(11) != null
                    val snapNested = protoReader.followPath(4, 4, 11) != null
                    if (snapAtRoot || snapNested) {
                        if (snapNested) edit(4, 4, 11) { remove(7); addVarInt(7, savePolicy) }
                        else edit(11) { remove(7); addVarInt(7, savePolicy) }
                    }
                }.toByteArray()
            }
        }

        context.event.subscribe(UnaryCallEvent::class, priority = 100) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
        }

        context.event.subscribe(SendMessageWithContentEvent::class, priority = -100) { event ->
            if (internalMultipartSend.get() == true) return@subscribe
            postSavePolicy = null
            
            // TRACING LOG: Identify why the menu might be skipped
            context.log.verbose("[SPLIT] SendOverride: Intercepted event. contentType=${event.messageContent.contentType}")

            if (event.destinations.stories?.isNotEmpty() == true && event.destinations.conversations?.isEmpty() == true) return@subscribe
            
            val localMessageContent = event.messageContent
            
            // Leniency Fix: Allow any media that contains external metadata
            val hasMetadata = localMessageContent.instanceNonNull().getObjectFieldOrNull("mExternalContentMetadata") != null
            if (localMessageContent.contentType != ContentType.EXTERNAL_MEDIA && 
                localMessageContent.contentType != ContentType.SNAP && !hasMetadata) {
                context.log.verbose("[SPLIT] SendOverride: Skipping event due to content type mismatch.")
                return@subscribe
            }

            val includeCameraSnaps = context.config.messaging.galleryMediaSendOverride.includeCameraSnaps.get()
            if (localMessageContent.contentType == ContentType.SNAP && !includeCameraSnaps) return@subscribe

            val messageProtoReader = ProtoReader(localMessageContent.content ?: return@subscribe)
            if (messageProtoReader.contains(7)) return@subscribe

            val conversationIds = event.destinations.conversations?.map { it.toString() } ?: return@subscribe
            if (conversationIds.isEmpty()) return@subscribe
            
            val recipientNames = conversationIds.mapNotNull { convId ->
                runCatching {
                    val dmParticipant = context.database.getDMOtherParticipant(convId)
                    if (dmParticipant != null) {
                        context.database.getFriendInfo(dmParticipant)?.displayName ?: context.database.getFriendInfo(dmParticipant)?.mutableUsername
                    } else {
                        context.database.getFeedEntryByConversationId(convId)?.feedDisplayName
                    }
                }.getOrNull()
            }.ifEmpty { listOf("Unknown") }
            
            val recipientName = recipientNames.joinToString(", ")

            event.canceled = true
            event.adapter.setResult(null)

            fun invokeOriginalAndRestoreResult(ev: SendMessageWithContentEvent) {
                val result = ev.adapter.invokeOriginal()
                ev.adapter.setResult(result)
                ev.canceled = false
            }

            val originalCallback = event.adapter.args().getOrNull(2)

            fun invokeCallbackError(callback: Any?, error: Any?) {
                runCatching {
                    callback?.javaClass?.methods?.firstOrNull { method ->
                        method.name == "onError" && method.parameterCount == 1
                    }?.invoke(callback, error)
                }
            }

            fun applyOverride(
                targetMessageContent: MessageContent,
                targetReader: ProtoReader,
                overrideType: String,
                snapDurationMs: Int?
            ): Boolean {
                val bypassLimit = context.config.experimental.nativeHooks.valdiHooks.bypassCameraRollLimit.get()
                if (overrideType != "ORIGINAL" && !bypassLimit && (targetReader.followPath(3)?.getCount(3) ?: 0) > 1) {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Default.WarningAmber,
                        context.translation["gallery_media_send_override.multiple_media_toast"]
                    )
                    return false
                }

                when (overrideType) {
                    "SNAP", "SAVEABLE_SNAP" -> {
                        val savePolicyValue = if (overrideType == "SAVEABLE_SNAP") 2 else 1
                        postSavePolicy = savePolicyValue
                        val extras = targetReader.followPath(3, 3, 13)?.getBuffer()
                        if (targetMessageContent.contentType != ContentType.SNAP) {
                            targetMessageContent.content = ProtoWriter().apply {
                                from(11) {
                                    from(5) {
                                        from(1) {
                                            from(1) { addVarInt(2, 0); addVarInt(12, 0); addVarInt(15, 0) }
                                            addVarInt(6, 1)
                                        }
                                        from(2) {}
                                    }
                                    extras?.let { addBuffer(13, it) }
                                    from(22) {}
                                }
                            }.toByteArray()
                        }
                        targetMessageContent.contentType = ContentType.SNAP
                        targetMessageContent.content = ProtoEditor(targetMessageContent.content!!).apply {
                            edit(11, 5, 2) {
                                arrayOf(6, 7, 8).forEach { remove(it) }
                                addVarInt(5, targetReader.getVarInt(3, 3, 5, 2, 5) ?: targetReader.getVarInt(11, 5, 2, 5) ?: 1)
                                if (snapDurationMs != null && overrideType != "SAVEABLE_SNAP") {
                                    addVarInt(8, snapDurationMs / 1000)
                                    if (snapDurationMs / 1000 <= 0) addVarInt(99, snapDurationMs)
                                } else {
                                    addBuffer(6, byteArrayOf())
                                }
                            }
                            edit(11, 22) { remove(4); addVarInt(4, 5) }
                            edit(11) { remove(7); addVarInt(7, savePolicyValue) }
                        }.toByteArray()
                    }
                    "NOTE" -> {
                        val shouldPreventSave = context.config.messaging.unsaveableMessages.note.get()
                        if (shouldPreventSave) postSavePolicy = 1
                        targetMessageContent.contentType = ContentType.NOTE
                        val stripMeta = context.config.messaging.stripMediaMetadata.get()
                        val omitTranscript = stripMeta.contains("remove_audio_note_transcript_capability")
                        val rawDurationMs = targetReader.getVarInt(3, 3, 5, 1, 1, 15)?.toLong()
                            ?: targetReader.getVarInt(3, 3, 5, 2, 8)?.toLong()?.times(1000)
                            ?: (context.feature(MediaFilePicker::class).lastMediaDuration ?: 0).toLong()
                        val durationForProto = minOf(rawDurationMs, MessageSender.VOICE_NOTE_MAX_DURATION_MS)
                        val audioNoteProto = MessageSender.audioNoteProto(
                            durationForProto,
                            if (omitTranscript) null else Locale.getDefault().toLanguageTag()
                        )
                        targetMessageContent.content = if (shouldPreventSave) {
                            ProtoEditor(audioNoteProto).apply {
                                val hasNestedPath = ProtoReader(audioNoteProto).followPath(6, 1, 1) != null
                                if (hasNestedPath) edit(6, 1, 1) { remove(7); addVarInt(7, 1) }
                                else edit(6, 1) { remove(7); addVarInt(7, 1) }
                            }.toByteArray()
                        } else audioNoteProto
                    }
                }

                if (postSavePolicy != null) {
                    runCatching {
                        val savePolicyEnumClass = Class.forName("com.snapchat.client.messaging.SavePolicy", false, targetMessageContent.instanceNonNull().javaClass.classLoader)
                        if (savePolicyEnumClass.isEnum) {
                            val policyName = if (postSavePolicy == 1) "PROHIBITED" else "VIEWER_SAVABLE"
                            @Suppress("UNCHECKED_CAST")
                            val policyEnum = java.lang.Enum.valueOf(savePolicyEnumClass as Class<out Enum<*>>, policyName)
                            targetMessageContent.instanceNonNull().setObjectField("mSavePolicy", policyEnum)
                        }
                    }
                }
                return true
            }

            fun createMessageContentFromOriginal(): MessageContent {
                return MessageContent(context.gson.fromJson(context.gson.toJson(localMessageContent.instanceNonNull()), context.classCache.localMessageContent)).also { messageContent ->
                    val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                    fun scrubValue(value: Any?) {
                        if (value == null || !visited.add(value)) return
                        if (value is String || value is Number || value is Boolean || value is ByteArray || value is Enum<*>) return
                        if (value is Iterable<*>) { value.forEach { scrubValue(it) }; return }
                        if (value is Map<*, *>) { value.values.forEach { scrubValue(it) }; return }
                        
                        var current: Class<*>? = value.javaClass
                        while (current != null && current != Any::class.java) {
                            current.declaredFields.forEach { field ->
                                runCatching {
                                    field.isAccessible = true
                                    val name = field.name
                                    if (name == "mMessageId" || name == "mQuotedMessageId" || name.contains("AttemptId", ignoreCase = true) || name.contains("ClientMessageId", ignoreCase = true) || name.contains("UUID", ignoreCase = true)) {
                                        when (field.type) {
                                            java.lang.Long.TYPE -> field.setLong(value, 0L)
                                            java.lang.Integer.TYPE -> field.setInt(value, 0)
                                            java.lang.Boolean.TYPE -> field.setBoolean(value, false)
                                            else -> field.set(value, null)
                                        }
                                    } else {
                                        scrubValue(field.get(value))
                                    }
                                }
                            }
                            current = current.superclass
                        }
                    }
                    scrubValue(messageContent.instanceNonNull())
                }
            }

            fun sendMediaManual(
                sourceMessageContent: MessageContent,
                overrideType: String,
                snapDurationMs: Int?,
                completionCallback: Any?
            ): Boolean {
                val sourceReader = ProtoReader(sourceMessageContent.content ?: return false)
                val mediaCount = sourceReader.followPath(3)?.getCount(3) ?: 0
                if (overrideType != "ORIGINAL" && mediaCount > 1) {
                    val mediaBuffers = mutableListOf<ByteArray>()
                    sourceReader.followPath(3)?.eachBuffer { id, buffer -> if (id == 3) mediaBuffers.add(buffer) }
                    if (mediaBuffers.isEmpty()) return false

                    fun sendPart(partIndex: Int) {
                        val partContent = createMessageContentFromOriginal()
                        val metadata = partContent.instanceNonNull().getObjectFieldOrNull("mExternalContentMetadata")
                        val refs = ArrayList(partContent.localMediaReferences ?: arrayListOf<Any>())
                        partContent.content = ProtoEditor(partContent.content!!).apply { edit(3) { remove(3); addBuffer(3, mediaBuffers[partIndex]) } }.toByteArray()
                        if (partIndex < refs.size) partContent.localMediaReferences = arrayListOf(refs[partIndex])
                        
                        if (!applyOverride(partContent, ProtoReader(partContent.content!!), overrideType, snapDurationMs)) return
                        val callback = if (partIndex == mediaCount - 1) completionCallback else CallbackBuilder(sendMessageCallbackClass).override("onSuccess") { sendPart(partIndex + 1) }.override("onError", false) { invokeCallbackError(completionCallback, it.argNullable<Any>(0)) }.build()
                        invokeSendManually(event.adapter.arg(0), partContent, callback)
                    }
                    sendPart(0)
                    return true
                }
                if (!applyOverride(sourceMessageContent, sourceReader, overrideType, snapDurationMs)) return false
                invokeSendManually(event.adapter.arg(0), sourceMessageContent, completionCallback)
                return true
            }

            fun sendRepeatedMediaManual(repeatCount: Int, overrideType: String, snapDurationMs: Int?): Boolean {
                if (repeatCount <= 0) return false
                fun sendIteration(index: Int) {
                    val callback = if (index == repeatCount - 1) originalCallback else CallbackBuilder(sendMessageCallbackClass).override("onSuccess") { sendIteration(index + 1) }.override("onError", false) { invokeCallbackError(originalCallback, it.argNullable<Any>(0)) }.build()
                    val preparedContent = createMessageContentFromOriginal()
                    if (!sendMediaManual(preparedContent, overrideType, snapDurationMs, callback)) invokeCallbackError(originalCallback, "Failed to send")
                }
                sendIteration(0)
                return true
            }

            fun sendMedia(overrideType: String, snapDurationMs: Int?): Boolean {
                postSavePolicy = null
                return applyOverride(localMessageContent, messageProtoReader, overrideType, snapDurationMs)
            }

            fun attachQueuedRepeatCallbacks(sendEvent: SendMessageWithContentEvent) {
                val convId = sendEvent.destinations.conversations?.firstOrNull()?.toString() ?: "unknown"
                val destinations = sendEvent.adapter.arg<Any>(0)
                val baseContentJson = context.gson.toJson(sendEvent.messageContent.instanceNonNull())
                val originalCb = sendEvent.adapter.args().getOrNull(2)
                
                sendEvent.addCallbackResult("onSuccess") {
                    context.runOnUiThread {
                        val handledSplit = handleSplitSendRecursive(convId, destinations, baseContentJson, originalCb)
                        val handledRepeat = if (!handledSplit) handleQueuedOriginalItemRepeatSuccess(convId) else false
                        if (!handledSplit && !handledRepeat) {
                            MediaFilePicker.clearQueuedSplitItems(convId)
                            clearQueuedOriginalItemRepeats()
                        }
                    }
                }
                sendEvent.addCallbackResult("onError") { MediaFilePicker.clearQueuedSplitItems(convId); clearQueuedOriginalItemRepeats() }
            }

            val resolvedOverrideType = MediaFilePicker.getQueuedOverrideType() ?: configOverrideType?.takeIf { it != "always_ask" }
            if (resolvedOverrideType != null) {
                val convId = event.destinations.conversations?.firstOrNull()?.toString() ?: "unknown"
                if (MediaFilePicker.hasPendingSplitCleanup(convId) || MediaFilePicker.getQueuedOverrideType() != null || queuedOriginalItemRepeatCount > 0 || MediaFilePicker.hasQueuedSplitItems(convId)) attachQueuedRepeatCallbacks(event)
                if (sendMedia(resolvedOverrideType, 10000)) if (event.canceled) invokeOriginalAndRestoreResult(event)
                return@subscribe
            }

            context.runOnUiThread {
                val recipientNameForTask = recipientName
                createComposeAlertDialog(context.mainActivity!!) { alertDialog ->
                    PurrfectOverlayTheme {
                        val mainTranslation = remember { context.translation.getCategory("send_override_dialog") }
                        val dialogBackground = remember { Brush.linearGradient(listOf(Color(0xFF2A2452), Color(0xFF1A143A))) }

                        @Composable
                        fun ActionTile(modifier: Modifier = Modifier, selected: Boolean = false, icon: ImageVector, title: String, onClick: () -> Unit) {
                            Card(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF3E3478) else Color(0xFF2F2A5B), contentColor = Color.White), border = if (selected) BorderStroke(1.dp, PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.6f)) else null) {
                                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(icon, contentDescription = title, modifier = Modifier.size(28.dp), tint = if (selected) PurrfectOverlayPalette.glowSecondary else Color.White.copy(alpha = 0.9f))
                                    Spacer(Modifier.height(6.dp))
                                    Text(title, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, softWrap = true, lineHeight = 14.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }

                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color(0xFF2A2452), border = BorderStroke(1.dp, Brush.linearGradient(listOf(PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f), PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f))))) {
                            Column(modifier = Modifier.background(dialogBackground, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val translation = remember { context.translation.getCategory("features.options.gallery_media_send_override") }
                                var scheduleEnabled by remember { mutableStateOf(false) }
                                var continuousSendEnabled by remember { mutableStateOf(false) }
                                var continuousSendCount by remember { mutableStateOf("2") }

                                Text(fontSize = 20.sp, fontWeight = FontWeight.Medium, text = "Send as ${translation[selectedType]}", modifier = Modifier.padding(5.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ActionTile(modifier = Modifier.weight(1f).height(92.dp), selected = selectedType == "ORIGINAL", icon = Icons.Filled.Photo, title = translation["ORIGINAL"]) { selectedType = "ORIGINAL" }
                                    ActionTile(modifier = Modifier.weight(1f).height(92.dp), selected = selectedType == "SNAP" || selectedType == "SAVEABLE_SNAP", icon = Icons.Filled.PhotoCamera, title = translation["SNAP"]) { selectedType = "SNAP" }
                                    ActionTile(modifier = Modifier.weight(1f).height(92.dp), selected = selectedType == "NOTE", icon = Icons.Filled.MusicNote, title = translation["NOTE"]) { selectedType = "NOTE" }
                                }

                                fun convertDuration(duration: Float) = if (duration >= 11f) null else ((duration * 1000).toInt() / 1000) * 1000
                                fun formatTimeText(ms: Long): String { val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = (ms / 3600000) % 24; return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s" }

                                if (selectedType == "SNAP" || selectedType == "SAVEABLE_SNAP") {
                                    Row(modifier = Modifier.fillMaxWidth().clickable { disableSplitForCurrentSend = !disableSplitForCurrentSend }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = disableSplitForCurrentSend, onCheckedChange = { disableSplitForCurrentSend = it }); Text(text = mainTranslation["single_send_hint"], lineHeight = 15.sp) }
                                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedType = if (selectedType == "SAVEABLE_SNAP") "SNAP" else "SAVEABLE_SNAP" }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selectedType == "SAVEABLE_SNAP", onCheckedChange = { selectedType = if (selectedType == "SAVEABLE_SNAP") "SNAP" else "SAVEABLE_SNAP" }); Text(text = mainTranslation["saveable_snap_hint"], lineHeight = 15.sp) }
                                    Slider(modifier = Modifier.fillMaxWidth(), enabled = selectedType != "SAVEABLE_SNAP", value = customDuration, onValueChange = { customDuration = it }, valueRange = -2f..11f)
                                }

                                Row(modifier = Modifier.fillMaxWidth().clickable { continuousSendEnabled = !continuousSendEnabled }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = continuousSendEnabled, onCheckedChange = { continuousSendEnabled = it }); Text(text = mainTranslation["continuous_send_toggle"], lineHeight = 15.sp) }
                                if (continuousSendEnabled) OutlinedTextField(value = continuousSendCount, onValueChange = { continuousSendCount = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(mainTranslation["continuous_send_count_label"]) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it; if (!it) scheduledTime = null }); Text(text = mainTranslation["schedule"], modifier = Modifier.weight(1f)); if (scheduleEnabled) Button(onClick = { showClockPicker = true }) { Text(scheduledTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: context.translation["select"]) } }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    OutlinedButton(onClick = { alertDialog.dismiss() }) { Text(context.translation["button.cancel"]) }
                                    Button(onClick = {
                                        val finalType = selectedType
                                        val repeatCnt = if (continuousSendEnabled) continuousSendCount.toIntOrNull()?.takeIf { it > 0 } else 1
                                        if (repeatCnt == null) { context.inAppOverlay.showStatusToast(Icons.Default.WarningAmber, mainTranslation["continuous_send_invalid_count"]); return@Button }
                                        alertDialog.dismiss()

                                        val mediaPicker = runCatching { context.feature(MediaFilePicker::class) }.getOrNull()
                                        mediaPicker?.setSplitDisabled(disableSplitForCurrentSend)
                                        val convId = event.destinations.conversations?.firstOrNull()?.toString() ?: "unknown"

                                        if (disableSplitForCurrentSend && MediaFilePicker.hasOriginalUnsplitItem()) {
                                            MediaFilePicker.setQueuedOverrideType(finalType)
                                            if (!MediaFilePicker.sendOriginalUnsplitItem()) MediaFilePicker.setQueuedOverrideType(null)
                                            return@Button
                                        } else if (MediaFilePicker.isActiveSplitSession(convId) || MediaFilePicker.hasPendingSplitCleanup(convId)) {
                                            MediaFilePicker.setQueuedOverrideType(finalType)
                                            attachQueuedRepeatCallbacks(event)
                                            invokeOriginalAndRestoreResult(event)
                                            return@Button
                                        } else if ((finalType == "SNAP" || finalType == "ORIGINAL") && !disableSplitForCurrentSend) {
                                            var firstMed: Any? = null
                                            for (arg in event.adapter.args()) {
                                                val cand = when (arg) { is List<*> -> arg.firstOrNull(); is Array<*> -> arg.firstOrNull(); else -> arg }
                                                if (cand?.getObjectFieldOrNull("_item") != null) { firstMed = cand; break }
                                            }
                                            val mItem = firstMed?.getObjectFieldOrNull("_item")
                                            val type = (mItem?.getObjectFieldOrNull("_type") ?: mItem?.getObjectFieldOrNull("type") ?: mItem?.getObjectFieldOrNull("media_type") ?: mItem?.getObjectFieldOrNull("mType"))?.toString()
                                            val durMs = (mItem?.getObjectFieldOrNull("_durationMs") ?: mItem?.getObjectFieldOrNull("duration") ?: mItem?.getObjectFieldOrNull("duration_ms") ?: mItem?.getObjectFieldOrNull("mDurationMs") ?: 0L) as? Number ?: 0L
                                            
                                            val isVid = type?.contains("VIDEO", ignoreCase = true) == true
                                            if (firstMed != null && isVid && (durMs.toLong() > 10000 || durMs.toLong() == 0L)) {
                                                context.log.info("[SPLIT] SendOverride: Long video detected ($durMs ms). Triggering splitting for $convId")
                                                context.inAppOverlay.showStatusToast(Icons.Default.Crop, "Splitting long video...", durationMs = 3000)
                                                MediaFilePicker.triggerSlicing(convId, firstMed) { firstChunk ->
                                                    context.runOnUiThread {
                                                        context.log.info("[SPLIT] SendOverride: Slicing complete. Injecting first chunk for $convId")
                                                        val chunkContent = MessageContent(context.gson.fromJson(context.gson.toJson(event.messageContent.instanceNonNull()), context.classCache.localMessageContent))
                                                        val firstChunkId = (firstChunk.getObjectFieldOrNull("_item")?.getObjectFieldOrNull("_itemId")?.getObjectFieldOrNull("_itemId"))?.toString()
                                                        if (firstChunkId != null) {
                                                            val pe = ProtoEditor(chunkContent.content!!)
                                                            pe.edit(11, 5, 1, 1) { remove(2); addString(2, firstChunkId) }
                                                            chunkContent.content = pe.toByteArray()
                                                        }
                                                        val destinations = event.adapter.arg<Any>(0)
                                                        val baseContentJson = context.gson.toJson(event.messageContent.instanceNonNull())
                                                        val callback = CallbackBuilder(sendMessageCallbackClass).override("onSuccess") { context.runOnUiThread { if (!handleSplitSendRecursive(convId, destinations, baseContentJson, originalCallback)) { context.log.info("[SPLIT] SendOverride: All chunks sent successfully for $convId"); MediaFilePicker.clearQueuedSplitItems(convId); runCatching { originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onSuccess" }?.invoke(originalCallback) } } } }.override("onError", false) { val err = it.argNullable<Any>(0); context.log.error("[SPLIT] SendOverride: First chunk failed: $err"); runCatching { originalCallback?.javaClass?.methods?.firstOrNull { it.name == "onError" && it.parameterCount == 1 }?.invoke(originalCallback, err) }; MediaFilePicker.clearQueuedSplitItems(convId) }.build()
                                                        invokeSendManually(destinations, chunkContent, callback)
                                                    }
                                                }
                                                return@Button
                                            }
                                        }
                                        
                                        if (scheduleEnabled && scheduledTime != null) {
                                            // Handle Schedule...
                                        } else {
                                            if (repeatCnt == 1) {
                                                if (sendMedia(finalType, if (finalType != "SAVEABLE_SNAP") convertDuration(customDuration) else null)) invokeOriginalAndRestoreResult(event)
                                            } else {
                                                queueOriginalItemRepeats(repeatCnt - 1, finalType, event.adapter.arg(0), context.gson.toJson(event.messageContent.instanceNonNull()), originalCallback)
                                                attachQueuedRepeatCallbacks(event)
                                                if (sendMedia(finalType, if (finalType != "SAVEABLE_SNAP") convertDuration(customDuration) else null)) invokeOriginalAndRestoreResult(event)
                                                else clearQueuedOriginalItemRepeats()
                                            }
                                        }
                                    }) { Text(context.translation["button.send"]) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
