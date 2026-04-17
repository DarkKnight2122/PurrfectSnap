package me.eternal.purrfectsnap.ui.manager.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.bridge.DownloadCallback
import me.eternal.purrfectsnap.common.bridge.wrapper.ConversationInfo
import me.eternal.purrfectsnap.common.bridge.wrapper.LoggedMessage
import me.eternal.purrfectsnap.common.bridge.wrapper.LoggerWrapper
import me.eternal.purrfectsnap.common.data.ContentType
import me.eternal.purrfectsnap.common.data.download.DownloadMetadata
import me.eternal.purrfectsnap.common.data.download.DownloadRequest
import me.eternal.purrfectsnap.common.data.download.MediaDownloadSource
import me.eternal.purrfectsnap.common.data.download.createNewFilePath
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.ui.transparentTextFieldColors
import me.eternal.purrfectsnap.common.util.ktx.copyToClipboard
import me.eternal.purrfectsnap.common.util.ktx.longHashCode
import me.eternal.purrfectsnap.common.util.protobuf.ProtoReader
import me.eternal.purrfectsnap.core.features.impl.downloader.decoder.DecodedAttachment
import me.eternal.purrfectsnap.core.features.impl.downloader.decoder.MessageDecoder
import me.eternal.purrfectsnap.download.DownloadProcessor
import me.eternal.purrfectsnap.storage.findFriend
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.components.FloatingTopBar
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import java.net.URLDecoder
import java.text.DateFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor

internal object LoggerSkinPalette {
    @Composable
    internal fun isAphelion(): Boolean {
        val context = LocalContext.current
        return remember(context) { 
            me.eternal.purrfectsnap.SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get() == "APHELION"
        }
    }

    val glowPrimary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.glowPrimary else Color(0xFF8C7BFF)
    val glowSecondary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.glowSecondary else Color(0xFF5FD8FF)
    val backgroundGradient: Brush @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.backgroundGradient else Brush.verticalGradient(listOf(Color(0xFF261F58), Color(0xFF302A6D), Color(0xFF241F52)))
    val cardOverlay: Brush @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.cardOverlay else SolidColor(Color(0xFF1B152E))
    val textPrimary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.textPrimary else Color.White
    val textSecondary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.textSecondary else Color(0xFFD9D3FF)
    val cardOverlayColor: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.cardOverlayColor else Color(0xFF1B152E)
}

class LoggerHistoryRoot : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("logger_history") }
    private lateinit var loggerWrapper: LoggerWrapper
    private var selectedConversation by mutableStateOf<String?>(null)
    private var stringFilter by mutableStateOf("")
    private var reverseOrder by mutableStateOf(true)

    private inline fun decodeMessage(message: LoggedMessage, result: (contentType: ContentType, messageReader: ProtoReader, attachments: List<DecodedAttachment>) -> Unit) {   
        runCatching {
            val messageObject = JsonParser.parseString(String(message.messageData, Charsets.UTF_8)).asJsonObject
            val messageContent = messageObject.getAsJsonObject("mMessageContent")
            val messageReader = messageContent.getAsJsonArray("mContent").map { it.asByte }.toByteArray().let { ProtoReader(it) }
            result(ContentType.fromMessageContainer(messageReader) ?: ContentType.UNKNOWN, messageReader, MessageDecoder.decode(messageContent))
        }.onFailure {
            context.log.error("Failed to decode message", it)
        }
    }

    private fun downloadAttachment(creationTimestamp: Long, attachment: DecodedAttachment) {
        context.shortToast(translation["download_started_toast"])
        val attachmentHash = attachment.mediaUniqueId!!.longHashCode().absoluteValue.toString()

        DownloadProcessor(
            remoteSideContext = context,
            callback = object: DownloadCallback.Default() {
                override fun onSuccess(outputPath: String?) {
                    context.shortToast(translation.format("download_success_toast", "path" to outputPath.toString()))
                }

                override fun onFailure(message: String?, throwable: String?) {
                    context.shortToast(translation.format("download_failed_toast", "message" to message.toString()))
                }
            }
        ).enqueue(
            DownloadRequest(
                inputMedias = arrayOf(attachment.createInputMedia()!!)
            ),
            DownloadMetadata(
                mediaIdentifier = attachmentHash,
                outputPath = createNewFilePath(
                    context.config.root,
                    attachment.mediaUniqueId!!,
                    MediaDownloadSource.MESSAGE_LOGGER,
                    attachmentHash,
                    creationTimestamp
                ),
                iconUrl = null,
                mediaAuthor = null,
                downloadSource = MediaDownloadSource.MESSAGE_LOGGER.translate(context.translation),
            )
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun MessageView(message: LoggedMessage) {
        var contentView by remember { mutableStateOf<@Composable () -> Unit>({
            Spacer(modifier = Modifier.height(30.dp))
        }) }

        val glowPrimary = LoggerSkinPalette.glowPrimary
        val glowSecondary = LoggerSkinPalette.glowSecondary
        val borderBrush = remember(glowPrimary, glowSecondary) {
            Brush.linearGradient(
                listOf(
                    glowPrimary.copy(alpha = 0.45f),
                    glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        Surface(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = LoggerSkinPalette.cardOverlayColor,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, borderBrush)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                contentView()

                LaunchedEffect(Unit, message) {
                    runCatching {
                        decodeMessage(message) { contentType, messageReader, attachments ->
                            @Composable
                            fun ContentHeader() {
                                val date = remember { DateFormat.getDateTimeInstance().format(message.sendTimestamp) }
                                Text(
                                    translation.format("log_header_format", "username" to message.username, "type" to contentType.toString().lowercase(), "date" to date),     
                                    modifier = Modifier.padding(end = 4.dp),
                                    fontWeight = FontWeight.ExtraLight,
                                    color = LoggerSkinPalette.textSecondary
                                )
                            }

                            if (contentType == ContentType.CHAT) {
                                val content = messageReader.getString(2, 1) ?: "[${translation["empty_message"]}]"
                                contentView = {
                                    Column {
                                        Text(
                                            content,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                    detectTapGestures(onLongPress = {
                                                        context.androidContext.copyToClipboard(content)
                                                    })
                                                },
                                            color = Color.White
                                        )

                                        val edits by rememberAsyncMutableState(defaultValue = emptyList()) {
                                            loggerWrapper.getChatEdits(selectedConversation!!, message.messageId)
                                        }
                                        edits.forEach { messageEdit ->
                                            val date = remember {
                                                DateFormat.getDateTimeInstance().format(messageEdit.timestamp)
                                            }
                                            Text(
                                                modifier = Modifier.pointerInput(Unit) {
                                                    detectTapGestures(onLongPress = {
                                                        context.androidContext.copyToClipboard(messageEdit.message)
                                                    })
                                                }.fillMaxWidth().padding(start = 4.dp),
                                                text = translation.format("edited_at_text", "message" to messageEdit.message, "date" to date),
                                                fontWeight = FontWeight.Light,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 12.sp,
                                                color = LoggerSkinPalette.textSecondary
                                            )
                                        }
                                        ContentHeader()
                                    }
                                }
                                return@runCatching
                            }
                            contentView = {
                                Column column@{
                                    if (attachments.isEmpty()) return@column

                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        attachments.forEachIndexed { index, attachment ->
                                            Button(
                                                onClick = {
                                                    context.coroutineScope.launch {
                                                        runCatching {
                                                            downloadAttachment(message.sendTimestamp, attachment)
                                                        }.onFailure {
                                                            context.log.error("Failed to download attachment", it)
                                                            context.shortToast(translation["download_attachment_failed_toast"])
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = glowPrimary.copy(alpha = 0.28f),
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = translation["download_button"],
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                Text(translation.format("chat_attachment", "index" to (index + 1).toString()))
                                            }
                                        }
                                    }
                                    ContentHeader()
                                }
                            }
                        }
                    }.onFailure {
                        context.log.error("Failed to parse message", it)
                        contentView = {
                            Text(translation["message_parse_failed"])
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        LaunchedEffect(Unit) {
            val uri = navBackStackEntry.arguments?.getString("uri")?.let {
                runCatching {
                    URLDecoder.decode(it, "UTF-8").toUri()
                }.getOrNull()
            }
            loggerWrapper = LoggerWrapper(context.androidContext, uri)
        }

        val conversationInfoCache = remember { ConcurrentHashMap<String, String?>() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LoggerSkinPalette.backgroundGradient)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FloatingTopBar(
                    title = context.translation["manager.routes.logger_history"] ?: "Logger History",
                    onBack = { routes.navController.popBackStack() }
                )

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    var expanded by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = LoggerSkinPalette.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .background(LoggerSkinPalette.cardOverlay, RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        fun formatConversationInfo(conversationInfo: ConversationInfo?): String? {
                            if (conversationInfo == null) return null

                            return conversationInfo.groupTitle?.let {
                                translation.format("list_group_format", "name" to it)
                            } ?: conversationInfo.usernames.takeIf { it.size > 1 }?.let {
                                translation.format("list_friend_format", "name" to ("(" + it.joinToString(", ") + ")"))
                            } ?: context.database.findFriend(conversationInfo.conversationId)?.let {
                                translation.format("list_friend_format", "name" to "(" + (conversationInfo.usernames + listOf(it.mutableUsername)).toSet().joinToString(", ") + ")")
                            } ?: conversationInfo.usernames.firstOrNull()?.let {
                                translation.format("list_friend_format", "name" to "($it)")
                            }
                        }

                        val selectedConversationInfo by rememberAsyncMutableState(defaultValue = null, keys = arrayOf(selectedConversation)) {
                            selectedConversation?.let {
                                conversationInfoCache.getOrPut(it) {
                                    formatConversationInfo(loggerWrapper.getConversationInfo(it))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = selectedConversationInfo ?: translation["select_conversation_placeholder"],
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = LoggerSkinPalette.glowSecondary
                            )
                        )

                        val conversations by rememberAsyncMutableState(defaultValue = emptyList<String>()) {
                            loggerWrapper.getAllConversations().toMutableList()
                        }

                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            conversations.forEach { conversationId ->
                                DropdownMenuItem(onClick = {
                                    selectedConversation = conversationId
                                    expanded = false
                                }, text = {
                                    val conversationInfo by rememberAsyncMutableState(defaultValue = null, keys = arrayOf(conversationId)) {
                                        conversationInfoCache.getOrPut(conversationId) {
                                            formatConversationInfo(loggerWrapper.getConversationInfo(conversationId))
                                        }
                                    }

                                        Text(
                                            text = remember(conversationInfo) { conversationInfo ?: conversationId },
                                            fontWeight = if (conversationId == selectedConversation) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.White,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = stringFilter,
                        onValueChange = { stringFilter = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = {
                            Text(
                                text = context.translation["manager.dialogs.add_friend.search_hint"] ?: "Search",
                                color = LoggerSkinPalette.textSecondary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = LoggerSkinPalette.textSecondary
                            )
                        },
                        trailingIcon = if (stringFilter.isNotBlank()) {
                            {
                                IconButton(onClick = { stringFilter = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = translation["close_button_description"],
                                        tint = LoggerSkinPalette.textSecondary
                                    )
                                }
                            }
                        } else null,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = LoggerSkinPalette.glowSecondary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            translation["reverse_order_checkbox"],
                            color = LoggerSkinPalette.textSecondary,
                            fontSize = 13.sp
                        )
                        Checkbox(
                            checked = reverseOrder,
                            onCheckedChange = { reverseOrder = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LoggerSkinPalette.glowPrimary,
                                checkmarkColor = Color.White,
                                uncheckedColor = Color.White.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }

            var hasReachedEnd by remember(selectedConversation, stringFilter, reverseOrder) { mutableStateOf(false) }
            var lastFetchMessageTimestamp by remember(selectedConversation, stringFilter, reverseOrder) { mutableLongStateOf(if (reverseOrder) Long.MAX_VALUE else Long.MIN_VALUE) }
            val messages = remember(selectedConversation, stringFilter, reverseOrder) { mutableStateListOf<LoggedMessage>() }

            LazyColumn(
                contentPadding = PaddingValues(bottom = routes.bottomPadding)
            ) {
                items(messages) { message ->
                    MessageView(message)
                }
                item {
                    if (selectedConversation != null) {
                        if (hasReachedEnd) {
                            Text(translation["no_more_messages"], modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(), textAlign = TextAlign.Center, color = Color.White)
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .padding(8.dp),
                                    color = LoggerSkinPalette.glowSecondary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    LaunchedEffect(Unit, selectedConversation, stringFilter, reverseOrder) {
                        withContext(Dispatchers.IO) {
                            val newMessages = loggerWrapper.fetchMessages(
                                selectedConversation ?: return@withContext,
                                lastFetchMessageTimestamp,
                                30,
                                reverseOrder
                            ) { messageData ->
                                if (stringFilter.isEmpty()) return@fetchMessages true
                                var isMatch = false
                                decodeMessage(messageData) { contentType, messageReader, _ ->
                                    if (contentType == ContentType.CHAT) {
                                        val content = messageReader.getString(2, 1) ?: return@decodeMessage
                                        isMatch = content.contains(stringFilter, ignoreCase = true)
                                    }
                                }
                                isMatch
                            }
                            if (newMessages.isEmpty()) {
                                hasReachedEnd = true
                                return@withContext
                            }
                            lastFetchMessageTimestamp = newMessages.lastOrNull()?.sendTimestamp ?: return@withContext
                            withContext(Dispatchers.Main) {
                                messages.addAll(newMessages)
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }
}
