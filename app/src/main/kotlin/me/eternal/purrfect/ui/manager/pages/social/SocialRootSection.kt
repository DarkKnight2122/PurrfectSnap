package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import me.eternal.purrfect.bridge.BridgeService
import me.eternal.purrfect.common.Constants
import me.eternal.purrfect.common.ui.rememberAsyncMutableState
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.ui.manager.ManagerTheme
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.data.MessagingFriendInfo
import me.eternal.purrfect.ui.manager.data.MessagingGroupInfo

class SocialRootSection : Routes.Route() {
    override val translation by lazy { context.translation.getCategory("manager.sections.social") }

    enum class SocialScope(val key: String, val icon: ImageVector) {
        FRIEND("friend", Icons.Default.Person),
        GROUP("group", Icons.Default.Group)
    }

    @Composable
    private fun EmptyState(scope: SocialScope) {
        val skin = LocalPurrfectSkin.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(skin.textPrimary.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = scope.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = skin.textPrimary.copy(alpha = 0.2f)
                )
            }
            Text(
                text = translation["empty_state_title"] ?: "Nothing here yet",
                color = skin.textPrimary.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = translation["empty_state_subtitle"] ?: "Use the + button to add entries manually or wait for the bridge to sync data.",
                color = skin.textPrimary.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 240.dp)
            )
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val themeId by produceState(initialValue = context.config.root.global.uiSettings.managerTheme.get()) {
            while (true) {
                delay(300)
                value = context.config.root.global.uiSettings.managerTheme.get()
            }
        }
        key(themeId) {
            with(ManagerTheme.fromId(themeId).theme) {
                this@SocialRootSection.SocialScreen()
            }
        }
    }

    override val floatingActionButton: @Composable () -> Unit = {
        val skin = LocalPurrfectSkin.current
        var addFriendDialog by remember { mutableStateOf(null as AddFriendDialog?) }

        if (addFriendDialog != null) {
            addFriendDialog?.Content {
                addFriendDialog = null
            }
        }

        FloatingActionButton(
            onClick = {
                addFriendDialog = AddFriendDialog(context)
            },
            containerColor = skin.glowPrimary,
            contentColor = skin.cardOverlayColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }

    @Composable
    internal fun SocialCard(
        scope: SocialScope,
        friend: MessagingFriendInfo?,
        group: MessagingGroupInfo?,
        onManage: () -> Unit,
        onPreview: () -> Unit,
        remainingHours: Long? = null
    ) {
        val skin = LocalPurrfectSkin.current
        val title = friend?.displayName ?: group?.name ?: "Unknown"
        val subtitle = if (scope == SocialScope.FRIEND) {
            friend?.username?.let { "@$it" } ?: friend?.userId
        } else {
            group?.participantCount?.let { "$it participants" } ?: group?.conversationId
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManage),
            shape = RoundedCornerShape(22.dp),
            color = skin.cardOverlayColor.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, skin.glassBorder.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = skin.textPrimary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = scope.icon,
                            contentDescription = null,
                            tint = skin.glowPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = skin.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle ?: "",
                        color = skin.textPrimary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onPreview,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, skin.glowPrimary.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Preview",
                        color = skin.glowPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
