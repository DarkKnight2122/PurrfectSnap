package me.eternal.purrfectsnap.ui.manager.pages.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.common.data.MessagingFriendInfo
import me.eternal.purrfectsnap.common.data.MessagingGroupInfo
import me.eternal.purrfectsnap.common.data.SocialScope
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.util.snap.BitmojiSelfie
import me.eternal.purrfectsnap.storage.*
import me.eternal.purrfectsnap.ui.manager.Routes
import me.eternal.purrfectsnap.ui.manager.ManagerTheme
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfectsnap.ui.util.coil.BitmojiImage
import androidx.compose.ui.platform.LocalContext

internal object SocialSkinPalette {
    @Composable
    internal fun isAphelion(): Boolean {
        val context = LocalContext.current
        return remember(context) { 
            me.eternal.purrfectsnap.SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get() == "APHELION"
        }
    }

    val glowPrimary: Color @Composable get() = LocalPurrfectSkin.current.glowPrimary
    val glowSecondary: Color @Composable get() = LocalPurrfectSkin.current.glowSecondary
    val backgroundGradient: Brush @Composable get() = LocalPurrfectSkin.current.backgroundGradient
    val cardOverlay: Brush @Composable get() = LocalPurrfectSkin.current.cardOverlay
    val textPrimary: Color @Composable get() = LocalPurrfectSkin.current.textPrimary
    val textSecondary: Color @Composable get() = LocalPurrfectSkin.current.textSecondary
    val cardOverlayColor: Color @Composable get() = LocalPurrfectSkin.current.cardOverlayColor
}

class SocialRootSection : Routes.Route() {
    internal var friendList: List<MessagingFriendInfo> by mutableStateOf(emptyList())
    internal var groupList: List<MessagingGroupInfo> by mutableStateOf(emptyList())

    internal fun updateScopeLists() {
        context.coroutineScope.launch {
            friendList = context.database.getFriends(descOrder = true)
            groupList = context.database.getGroups()
        }
    }

    internal fun requestLatestSnapshot() {
        context.requestSocialSnapshotRefresh()
    }

    @Composable
    internal fun ScopeList(
        scope: SocialScope,
        friends: List<MessagingFriendInfo>,
        groups: List<MessagingGroupInfo>
    ) {
        val remainingHours = remember { context.config.root.streaksReminder.remainingHours.get() }
        val list = when (scope) {
            SocialScope.GROUP -> groups
            SocialScope.FRIEND -> friends
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = routes.bottomPadding + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val listSize = list.size

            if (listSize == 0) {
                item {
                    EmptyState(scope)
                }
            }

            items(listSize) { index ->
                val friend = if (scope == SocialScope.FRIEND) list[index] as MessagingFriendInfo else null
                val group = if (scope == SocialScope.GROUP) list[index] as MessagingGroupInfo else null
                val id = friend?.userId ?: group?.conversationId.orEmpty()

                SocialCard(
                    scope = scope,
                    friend = friend,
                    group = group,
                    onManage = {
                        routes.manageScope.navigate {
                            put("id", id)
                            put("scope", scope.key)
                        }
                    },
                    onPreview = {
                        routes.messagingPreview.navigate {
                            put("id", id)
                            put("scope", scope.key)
                        }
                    },
                    remainingHours = remainingHours
                )
            }
        }
    }

    override val floatingActionButton: @Composable () -> Unit = {
        var addFriendDialog by remember { mutableStateOf(null as AddFriendDialog?) }

        if (addFriendDialog != null) {
            addFriendDialog?.Content {
                addFriendDialog = null
            }
            DisposableEffect(Unit) {
                onDispose {
                    updateScopeLists()
                }
            }
        }

        FloatingActionButton(
            onClick = {
                addFriendDialog = AddFriendDialog(
                    context,
                    AddFriendDialog.Actions(
                        onFriendState = { friend, state ->
                            if (state) {
                                context.bridgeService?.triggerScopeSync(
                                    SocialScope.FRIEND,
                                    friend.userId
                                )
                            } else {
                                context.database.deleteFriend(friend.userId)
                            }
                        },
                        onGroupState = { group, state ->
                            if (state) {
                                context.bridgeService?.triggerScopeSync(
                                    SocialScope.GROUP,
                                    group.conversationId
                                )
                            } else {
                                context.database.deleteGroup(group.conversationId)
                            }
                        },
                        getFriendState = { friend -> context.database.getFriendInfo(friend.userId) != null },
                        getGroupState = { group -> context.database.getGroupInfo(group.conversationId) != null }
                    ),
                    pinnedIds = (friendList.map { it.userId } + groupList.map { it.conversationId }).reversed(),
                )
            },
            modifier = Modifier
                .padding(10.dp)
                .shadow(14.dp, RoundedCornerShape(20.dp), clip = false),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            val glowPrimary = SocialSkinPalette.glowPrimary
            val glowSecondary = SocialSkinPalette.glowSecondary
            val borderBrush = remember(glowPrimary, glowSecondary) {
                Brush.linearGradient(listOf(glowPrimary, glowSecondary))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = borderBrush,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = SocialSkinPalette.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = { nav ->
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }

        key(themeId) {
            with(ManagerTheme.fromId(themeId).theme) {
                this@SocialRootSection.SocialScreen(nav)
            }
        }
    }

    @Composable
    internal fun SocialCard(
        scope: SocialScope,
        friend: MessagingFriendInfo?,
        group: MessagingGroupInfo?,
        onManage: () -> Unit,
        onPreview: () -> Unit,
        remainingHours: Int
    ) {
        val glowPrimary = SocialSkinPalette.glowPrimary
        val glowSecondary = SocialSkinPalette.glowSecondary
        val cardGradient = remember(glowPrimary, glowSecondary) {
            Brush.linearGradient(
                listOf(
                    glowPrimary.copy(alpha = 0.22f),
                    glowSecondary.copy(alpha = 0.16f)
                )
            )
        }
        val shape = RoundedCornerShape(20.dp)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .border(1.dp, cardGradient, shape),
            shape = shape,
            onClick = onManage,
            colors = CardDefaults.elevatedCardColors(
                containerColor = Color.Transparent
            )
        ) {
            Row(
                modifier = Modifier
                    .background(SocialSkinPalette.cardOverlay, shape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (scope) {
                    SocialScope.GROUP -> {
                        val groupInfo = group ?: return@Row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SocialSkinPalette.textPrimary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = SocialSkinPalette.textPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = groupInfo.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                color = SocialSkinPalette.textPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = translation["groups_tab"],
                                color = SocialSkinPalette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    SocialScope.FRIEND -> {
                        val friendInfo = friend ?: return@Row
                        val streaks by rememberAsyncMutableState(defaultValue = friendInfo.streaks) {
                            context.database.getFriendStreaks(friendInfo.userId)
                        }

                        BitmojiImage(
                            context = context,
                            url = BitmojiSelfie.getBitmojiSelfie(
                                friendInfo.selfieId,
                                friendInfo.bitmojiId,
                                BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D
                            )
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = friendInfo.displayName ?: friendInfo.mutableUsername,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                color = SocialSkinPalette.textPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = friendInfo.mutableUsername,
                                maxLines = 1,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                color = SocialSkinPalette.textSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                streaks?.takeIf { it.notify }?.let { streaks ->
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.streak_icon),
                                        contentDescription = null,
                                        modifier = Modifier.height(18.dp),
                                        tint = if (streaks.isAboutToExpire(remainingHours))
                                            Color(0xFFFF6B9B)
                                        else SocialSkinPalette.glowSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = translation.format(
                                            "streaks_expiration_short",
                                            "hours" to (((streaks.expirationTimestamp - System.currentTimeMillis()) / 3600000).toInt().takeIf { it > 0 } ?: 0)
                                                .toString()
                                        ),
                                        maxLines = 1,
                                        fontWeight = FontWeight.Medium,
                                        color = SocialSkinPalette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    onClick = onPreview,
                    shape = RoundedCornerShape(16.dp),
                    color = SocialSkinPalette.textPrimary.copy(alpha = 0.08f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.16f))
                ) {
                    val previewGradient = remember(glowPrimary, glowSecondary) {
                        Brush.linearGradient(
                            listOf(
                                glowPrimary.copy(alpha = 0.22f),
                                glowSecondary.copy(alpha = 0.2f)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                previewGradient,
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RemoveRedEye,
                            contentDescription = null,
                            tint = SocialSkinPalette.textPrimary
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun SocialHeader(
        titles: List<String>,
        pagerState: androidx.compose.foundation.pager.PagerState,
        onTabSelected: (Int) -> Unit,
        friendCount: Int,
        groupCount: Int,
        searchActive: Boolean,
        onSearchToggle: () -> Unit
    ) {
        val glowPrimary = SocialSkinPalette.glowPrimary
        val glowSecondary = SocialSkinPalette.glowSecondary
        val topBorderBrush = remember(glowPrimary, glowSecondary) {
            Brush.linearGradient(
                listOf(
                    glowPrimary.copy(alpha = 0.55f),
                    glowSecondary.copy(alpha = 0.35f)
                )
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            shape = RoundedCornerShape(26.dp),
            color = SocialSkinPalette.textPrimary.copy(alpha = 0.07f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, topBorderBrush)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = translation["manager.routes.social"],
                            color = SocialSkinPalette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatPill(label = translation["friends_tab"], value = friendCount)
                        StatPill(label = translation["groups_tab"], value = groupCount)
                        IconButton(onClick = onSearchToggle) {
                            Icon(
                                imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchActive) translation["close_search_button_description"] else translation["search_button_description"],
                                tint = SocialSkinPalette.textPrimary
                            )
                        }
                    }
                }
                SocialTabSwitcher(
                    titles = titles,
                    pagerState = pagerState,
                    onTabSelected = onTabSelected
                )
            }
        }
    }

    @Composable
    internal fun SocialTabSwitcher(
        titles: List<String>,
        pagerState: androidx.compose.foundation.pager.PagerState,
        onTabSelected: (Int) -> Unit
    ) {
        val glowPrimary = SocialSkinPalette.glowPrimary
        val glowSecondary = SocialSkinPalette.glowSecondary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            titles.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val indicatorBrush = remember(glowPrimary, glowSecondary) {
                    Brush.linearGradient(listOf(glowPrimary, glowSecondary))
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) SocialSkinPalette.textPrimary.copy(alpha = 0.12f) else SocialSkinPalette.textPrimary.copy(alpha = 0.06f),
                    border = if (selected) BorderStroke(1.dp, indicatorBrush) else BorderStroke(     
                        1.dp,
                        SocialSkinPalette.textPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onTabSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (index == 0) Icons.Filled.People else Icons.Filled.Groups,
                            contentDescription = null,
                            tint = SocialSkinPalette.textPrimary
                        )
                        Text(
                            text = title,
                            color = SocialSkinPalette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    @Composable
    internal fun EmptyState(scope: SocialScope) {
        val title = when (scope) {
            SocialScope.FRIEND -> translation.getOrNull("friends_empty_title") ?: translation["empty_hint"]
            SocialScope.GROUP -> translation.getOrNull("groups_empty_title") ?: translation["empty_hint"]
        }
        val glowSecondary = SocialSkinPalette.glowSecondary
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = SocialSkinPalette.textPrimary.copy(alpha = 0.05f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    imageVector = if (scope == SocialScope.FRIEND) Icons.Filled.People else Icons.Filled.Groups,
                    contentDescription = null,
                    tint = glowSecondary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = title ?: "",
                    color = SocialSkinPalette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = translation["social_empty_hint"] ?: "",
                    color = SocialSkinPalette.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    internal fun StatPill(label: String, value: Int) {
        Surface(
            shape = RoundedCornerShape(50),
            color = SocialSkinPalette.textPrimary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, SocialSkinPalette.textPrimary.copy(alpha = 0.16f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value.toString(),
                    color = SocialSkinPalette.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = label,
                    color = SocialSkinPalette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
