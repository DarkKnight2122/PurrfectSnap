package me.eternal.purrfectsnap.ui.manager.pages.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.common.data.MessagingFriendInfo
import me.eternal.purrfectsnap.common.data.MessagingGroupInfo
import me.eternal.purrfectsnap.common.util.snap.BitmojiSelfie
import me.eternal.purrfectsnap.storage.getFriends
import me.eternal.purrfectsnap.storage.getGroups
import me.eternal.purrfectsnap.ui.util.coil.BitmojiImage

class AddFriendDialog(
    private val context: RemoteSideContext,
    private val actionHandler: Actions,
    private val pinnedIds: List<String>? = null
) {
    class Actions(
        val onFriendState: (friend: MessagingFriendInfo, state: Boolean) -> Unit,
        val onGroupState: (group: MessagingGroupInfo, state: Boolean) -> Unit,
        val getFriendState: (friend: MessagingFriendInfo) -> Boolean,
        val getGroupState: (group: MessagingGroupInfo) -> Boolean,
    )

    private val stateCache = mutableStateMapOf<String, Boolean>()
    private val translation by lazy { context.translation.getCategory("manager.dialogs.add_friend")}

    @Composable
    private fun ListCardEntry(
        id: String,
        bitmoji: String? = null,
        name: String,
        participantsCount: Int? = null,
        getCurrentState: () -> Boolean,
        onState: (Boolean) -> Unit = {},
    ) {
        val cachedState = stateCache[id]
        LaunchedEffect(id, cachedState) {
            if (cachedState == null) {
                stateCache[id] = getCurrentState()
            }
        }
        val currentState = stateCache[id] ?: false
        val coroutineScope = rememberCoroutineScope()

        val cardShape = RoundedCornerShape(18.dp)
        val glowPrimary = SocialSkinPalette.glowPrimary
        val glowSecondary = SocialSkinPalette.glowSecondary
        val textPrimary = SocialSkinPalette.textPrimary
        val borderBrush = remember(glowPrimary, glowSecondary, currentState, textPrimary) {
            if (currentState) Brush.linearGradient(listOf(glowPrimary, glowSecondary))
            else SolidColor(textPrimary.copy(alpha = 0.08f))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    val nextState = !currentState
                    stateCache[id] = nextState
                    coroutineScope.launch(Dispatchers.IO) { onState(nextState) }
                },
            shape = cardShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, borderBrush)
        ) {
            Row(
                modifier = Modifier
                    .background(SocialSkinPalette.cardOverlay, cardShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BitmojiImage(
                    context = this@AddFriendDialog.context,
                    url = bitmoji,
                    modifier = Modifier
                        .padding(end = 2.dp),
                    size = 40,
                )

                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = SocialSkinPalette.textPrimary
                    )

                    participantsCount?.let {
                        Text(
                            text = translation.format("participants_text", "count" to it.toString()),
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            color = SocialSkinPalette.textSecondary
                        )
                    }
                }

                Switch(
                    checked = currentState,
                    onCheckedChange = {
                        stateCache[id] = it
                        coroutineScope.launch(Dispatchers.IO) { onState(it) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = glowPrimary.copy(alpha = 0.6f),
                        uncheckedThumbColor = SocialSkinPalette.textPrimary.copy(alpha = 0.8f),
                        uncheckedTrackColor = SocialSkinPalette.textPrimary.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }

    @Composable
    private fun DialogHeader(searchKeyword: MutableState<String>) {
        val glowPrimary = SocialSkinPalette.glowPrimary
        val glowSecondary = SocialSkinPalette.glowSecondary
        val headerBrush = remember(glowPrimary, glowSecondary) {
            Brush.linearGradient(
                listOf(
                    glowPrimary.copy(alpha = 0.4f),
                    glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = headerBrush,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = translation["title"],
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SocialSkinPalette.textPrimary
            )
        }

        val cardOverlayColor = SocialSkinPalette.cardOverlayColor
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = cardOverlayColor,
            border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            TextField(
                value = searchKeyword.value,
                onValueChange = { searchKeyword.value = it },
                placeholder = {
                    Text(text = translation["search_hint"], color = SocialSkinPalette.textSecondary)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = translation["search_icon_description"])
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardOverlayColor.copy(alpha = 0.9f),
                    unfocusedContainerColor = cardOverlayColor.copy(alpha = 0.8f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = SocialSkinPalette.textPrimary,
                    focusedTextColor = SocialSkinPalette.textPrimary,
                    unfocusedTextColor = SocialSkinPalette.textPrimary,
                    focusedLeadingIconColor = SocialSkinPalette.textPrimary,
                    unfocusedLeadingIconColor = SocialSkinPalette.textPrimary.copy(alpha = 0.85f),
                    focusedPlaceholderColor = SocialSkinPalette.textSecondary,
                    unfocusedPlaceholderColor = SocialSkinPalette.textSecondary
                ),
                textStyle = LocalTextStyle.current.copy(color = SocialSkinPalette.textPrimary)
            )
        }
    }


    @Composable
    fun Content(dismiss: () -> Unit = { }) {
        var cachedFriends by remember { mutableStateOf(null as List<MessagingFriendInfo>?) }
        var cachedGroups by remember { mutableStateOf(null as List<MessagingGroupInfo>?) }

        val coroutineScope = rememberCoroutineScope()

        var timeoutJob: Job? = null
        var hasFetchError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            fun applySnapshot(
                friends: List<MessagingFriendInfo>,
                groups: List<MessagingGroupInfo>
            ) {
                cachedFriends = friends.run {
                    if (pinnedIds != null) {
                        sortedBy { -pinnedIds.indexOf(it.userId) }
                    } else {
                        this
                    }
                }
                cachedGroups = groups.run {
                    if (pinnedIds != null) {
                        sortedBy { -pinnedIds.indexOf(it.conversationId) }
                    } else {
                        this
                    }
                }
                if (friends.isNotEmpty() || groups.isNotEmpty()) {
                    timeoutJob?.cancel()
                    hasFetchError = false
                }
            }

            val updateSnapshot: (List<MessagingFriendInfo>, List<MessagingGroupInfo>) -> Unit = { friends, groups ->
                coroutineScope.launch {
                    applySnapshot(friends, groups)
                }
            }

            withContext(Dispatchers.IO) {
                applySnapshot(
                    context.database.getFriends(descOrder = true),
                    context.database.getGroups()
                )
            }

            context.database.receiveMessagingDataCallback = updateSnapshot
            context.requestSocialSnapshotRefresh()

            coroutineScope.launch(Dispatchers.IO) {
                repeat(25) {
                    delay(1000)
                    val dbFriends = context.database.getFriends(descOrder = true)
                    val dbGroups = context.database.getGroups()
                    if (dbFriends.isNotEmpty() || dbGroups.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            applySnapshot(dbFriends, dbGroups)
                        }
                        return@launch
                    }
                }
            }

            timeoutJob = coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    delay(25000)
                    if ((cachedFriends?.isNullOrEmpty() != false) && (cachedGroups?.isNullOrEmpty() != false)) {
                        hasFetchError = true
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                timeoutJob?.cancel()
                context.bridgeService?.clearEphemeralSocialSnapshotRequest()
                context.database.receiveMessagingDataCallback = { _, _ -> }
            }
        }

        me.eternal.purrfectsnap.ui.util.Dialog(
            onDismissRequest = {
                timeoutJob?.cancel()
                dismiss()
            },
            properties = me.eternal.purrfectsnap.ui.util.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val dialogShape = RoundedCornerShape(24.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = dialogShape,
                color = SocialSkinPalette.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.12f))
            ) {
                Column {
                    if (cachedGroups == null || cachedFriends == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasFetchError) {
                                Text(
                                    text = translation["fetch_error"],
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp, top = 10.dp),
                                    color = SocialSkinPalette.textPrimary
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = SocialSkinPalette.glowSecondary
                                )
                            }
                        }
                        return@Surface
                    }

                    val searchKeyword = remember { mutableStateOf("") }

                    val filteredGroups = cachedGroups!!.takeIf { searchKeyword.value.isNotBlank() }?.filter {
                        it.name.contains(searchKeyword.value, ignoreCase = true)
                    } ?: cachedGroups!!

                    val filteredFriends = cachedFriends!!.takeIf { searchKeyword.value.isNotBlank() }?.filter {
                        it.mutableUsername.contains(searchKeyword.value, ignoreCase = true) ||
                        it.displayName?.contains(searchKeyword.value, ignoreCase = true) == true
                    } ?: cachedFriends!!
                    val selectedFriendCount by remember(filteredFriends) {
                        derivedStateOf {
                            filteredFriends.count { friend ->
                                stateCache[friend.userId] ?: actionHandler.getFriendState(friend)
                            }
                        }
                    }
                    val hasFriendsSelected = selectedFriendCount > 0
                    val allFriendsSelected = filteredFriends.isNotEmpty() && selectedFriendCount == filteredFriends.size

                    DialogHeader(searchKeyword)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        item {
                            if (filteredGroups.isNotEmpty()) {
                                Text(
                                    text = translation["category_groups"],
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .padding(bottom = 8.dp, top = 8.dp),
                                    color = SocialSkinPalette.textPrimary
                                )
                            }
                        }

                        items(filteredGroups.size) {
                            val group = filteredGroups[it]
                            ListCardEntry(
                                id = group.conversationId,
                                name = group.name,
                                participantsCount = group.participantsCount,
                                getCurrentState = { actionHandler.getGroupState(group) }
                            ) { state ->
                                actionHandler.onGroupState(group, state)
                            }
                        }

                        item {
                            if (filteredFriends.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp, top = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = translation["category_friends"],
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SocialSkinPalette.textPrimary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    filteredFriends.forEach { friend ->
                                                        stateCache[friend.userId] = true
                                                        actionHandler.onFriendState(friend, true)
                                                    }
                                                }
                                            },
                                            enabled = !allFriendsSelected
                                        ) {
                                            val glowSecondary = SocialSkinPalette.glowSecondary
                                            Text(
                                                text = context.translation["manager.dialogs.messaging_action.select_all_button"],
                                                color = if (allFriendsSelected) {
                                                    SocialSkinPalette.textPrimary.copy(alpha = 0.45f)
                                                } else {
                                                    glowSecondary
                                                }
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    filteredFriends.forEach { friend ->
                                                        stateCache[friend.userId] = false
                                                        actionHandler.onFriendState(friend, false)
                                                    }
                                                }
                                            },
                                            enabled = hasFriendsSelected
                                        ) {
                                            val glowPrimary = SocialSkinPalette.glowPrimary
                                            Text(
                                                text = translation["unselect_all_button"],
                                                color = if (hasFriendsSelected) {
                                                    glowPrimary
                                                } else {
                                                    SocialSkinPalette.textPrimary.copy(alpha = 0.45f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(filteredFriends.size) { index ->
                            val friend = filteredFriends[index]

                            ListCardEntry(
                                id = friend.userId,
                                bitmoji = friend.takeIf { it.bitmojiId != null }?.let {
                                    BitmojiSelfie.getBitmojiSelfie(it.selfieId, it.bitmojiId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D)
                                },
                                name = friend.displayName?.takeIf { name -> name.isNotBlank() } ?: friend.mutableUsername,
                                getCurrentState = { actionHandler.getFriendState(friend) }
                            ) { state ->
                                actionHandler.onFriendState(friend, state)
                            }
                        }
                    }
                }
            }
        }
    }
}
