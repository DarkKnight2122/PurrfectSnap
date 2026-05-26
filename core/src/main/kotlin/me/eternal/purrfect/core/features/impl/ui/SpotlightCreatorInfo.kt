package me.eternal.purrfect.core.features.impl.ui

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.eternal.purrfect.common.data.FriendLinkType
import me.eternal.purrfect.common.ui.createComposeView
import me.eternal.purrfect.common.util.ktx.copyToClipboard
import me.eternal.purrfect.core.event.events.impl.AddViewEvent
import me.eternal.purrfect.core.event.events.impl.BindViewEvent
import me.eternal.purrfect.core.features.Feature
import me.eternal.purrfect.core.features.impl.downloader.MediaDownloader
import me.eternal.purrfect.core.features.impl.messaging.Messaging
import me.eternal.purrfect.core.ui.PurrfectOverlayPalette
import me.eternal.purrfect.core.ui.PurrfectOverlayTheme
import me.eternal.purrfect.core.util.hook.HookStage
import me.eternal.purrfect.core.util.hook.hook
import me.eternal.purrfect.core.util.ktx.getObjectField
import me.eternal.purrfect.core.util.ktx.vibrateLongPress
import me.eternal.purrfect.core.wrapper.impl.Snapchatter
import me.eternal.purrfect.core.wrapper.impl.media.opera.Layer
import me.eternal.purrfect.core.wrapper.impl.media.opera.ParamMap
import me.eternal.purrfect.mapper.impl.OperaPageViewControllerMapper

private const val SPOTLIGHT_CREATOR_INFO_TAG = "spotlight_creator_info"

private fun View.topOnScreen(): Int {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    return loc[1]
}

private fun ViewGroup.collectSpotlightCreatorTaggedViews(tag: Any, out: MutableList<View>) {
    if (this.tag == tag) out.add(this)
    for (i in 0 until childCount) {
        val c = getChildAt(i)
        if (c is ViewGroup) c.collectSpotlightCreatorTaggedViews(tag, out)
        else if (c.tag == tag) out.add(c)
    }
}

private fun dedupeSpotlightCreatorInfoComposeViews(root: ViewGroup) {
    val found = mutableListOf<View>()
    root.collectSpotlightCreatorTaggedViews(SPOTLIGHT_CREATOR_INFO_TAG, found)
    val ours = found.filterIsInstance<ComposeView>()
    if (ours.size <= 1) return
    val laidOut = ours.filter { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
    when {
        laidOut.size >= 2 -> {
            val minTop = laidOut.minOf { it.topOnScreen() }
            val keep = laidOut.filter { it.topOnScreen() == minTop }.last()
            ours.filter { it !== keep }.forEach { v ->
                (v.parent as? ViewGroup)?.removeView(v)
            }
        }
        laidOut.size == 1 -> {
            val keep = laidOut.first()
            ours.filter { it !== keep }.forEach { v ->
                (v.parent as? ViewGroup)?.removeView(v)
            }
        }
        else -> {
            ours.dropLast(1).forEach { v ->
                (v.parent as? ViewGroup)?.removeView(v)
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val ts = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}

private fun ParamMap.parseCreatorUserId(): String? {
    return this["USER_ID"]?.toString()
        ?: this["CREATOR_USER_ID"]?.toString()
        ?: this["TOPIC_SNAP_CREATOR_USER_ID"]?.toString()
        ?: this["PLAYABLE_STORY_SNAP_RECORD"]?.toString()?.let { record ->
            record.substringAfter("userId=", "").substringBefore(",").substringBefore(")")
                .takeIf { it.isNotBlank() && it != "null" }
        }
}

private fun ParamMap.parseTimestamp(): Long? {
    val record = this["PLAYABLE_STORY_SNAP_RECORD"]?.toString()
    val creation = record?.substringAfter("creationTimestamp=")?.substringBefore(",")?.toLongOrNull()
    return creation ?: this["SNAP_TIMESTAMP"]?.toString()?.toLongOrNull()
}

class SpotlightCreatorInfo : Feature("SpotlightCreatorInfo") {
    private data class CreatorInfo(
        val snapId: String,
        val creatorDisplayName: String,
        val creatorUserId: String?,
        val timestamp: Long?,
        val friendLinkType: FriendLinkType? = null,
    )

    private val creatorInfoState = mutableStateOf<CreatorInfo?>(null)
    private val isInSpotlightState = mutableStateOf(false)
    private val showDialogState = mutableStateOf(false)
    private val buttonViews = Collections.synchronizedSet(mutableSetOf<View>())
    private val userIdCache = ConcurrentHashMap<String, String>()
    private var lastClickTime = 0L

    private fun updateAllButtonVisibility() {
        val visible = isInSpotlightState.value
        context.runOnUiThread {
            synchronized(buttonViews) {
                buttonViews.forEach { view ->
                    runCatching {
                        if (view.isAttachedToWindow) {
                            view.visibility = if (visible) View.VISIBLE else View.GONE
                            if (visible) {
                                view.requestLayout()
                                view.invalidate()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun init() {
        if (!context.config.global.spotlightCreatorInfo.get()) return

        context.event.subscribe(AddViewEvent::class) { event ->
            if (event.parent.javaClass.superclass?.name?.endsWith("OpenLayout") != true) return@subscribe
            val viewGroup = event.view as? ViewGroup ?: return@subscribe
            val isWrapped = viewGroup is FrameLayout && viewGroup.childCount == 1 && viewGroup.getChildAt(0) is ViewGroup
            val actualLayer = if (isWrapped) viewGroup.getChildAt(0) as ViewGroup else viewGroup
            if (viewGroup.findViewWithTag<View>(SPOTLIGHT_CREATOR_INFO_TAG) != null ||
                event.parent.findViewWithTag<View>(SPOTLIGHT_CREATOR_INFO_TAG) != null ||
                actualLayer.javaClass.name.endsWith("ScalableCircleMaskFrameLayout")
            ) return@subscribe
            if (!actualLayer.javaClass.name.contains("OperaShapeView")) {
                val size = context.userInterface.dpToPx(38)
                val topMargin = context.userInterface.dpToPx(10) * 3 + size + context.userInterface.dpToPx(34)
                val topExtra = context.userInterface.dpToPx(2)
                val endExtra = context.userInterface.dpToPx(7)

                val button = createComposeView(viewGroup.context) {
                    val mediaDownloader = remember { context.feature(MediaDownloader::class) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (creatorInfoState.value != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(color = Color.Black.copy(alpha = 0.15f), shape = CircleShape)
                                    .clickable {
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastClickTime < 500) return@clickable
                                        lastClickTime = now
                                        showDialogState.value = true
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveRedEye,
                                    contentDescription = "Creator Info",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        if (context.config.downloader.operaDownloadButton.get()) {
                            @OptIn(ExperimentalFoundationApi::class)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(color = Color.Black.copy(alpha = 0.15f), shape = CircleShape)
                                    .combinedClickable(
                                        onClick = {
                                            mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = false)
                                        },
                                        onLongClick = {
                                            context.androidContext.vibrateLongPress()
                                            mediaDownloader.downloadLastOperaMediaAsync(allowDuplicate = true)
                                        }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }.apply {
                    tag = SPOTLIGHT_CREATOR_INFO_TAG
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        this.topMargin = topMargin + topExtra
                        this.marginEnd = endExtra
                        gravity = Gravity.TOP or Gravity.END
                    }
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            buttonViews.add(v)
                        }
                        override fun onViewDetachedFromWindow(v: View) {
                            buttonViews.remove(v)
                        }
                    })
                }
                viewGroup.addView(button)
                val openLayoutParent = event.parent
                openLayoutParent.post {
                    val decor = (openLayoutParent.rootView as? ViewGroup) ?: openLayoutParent
                    dedupeSpotlightCreatorInfoComposeViews(decor)
                    updateAllButtonVisibility()
                    openLayoutParent.post {
                        dedupeSpotlightCreatorInfoComposeViews(decor)
                        updateAllButtonVisibility()
                    }
                }
            }
        }

        context.event.subscribe(BindViewEvent::class) { event ->
            runCatching {
                val str = event.prevModel.toString()
                if (!str.contains("Spotlight") && !str.contains("SINGLE_SNAP_STORY")) return@subscribe
                val userId = str.substringAfter("userId=", "").substringBefore(",").trim().takeIf { it.isNotBlank() && it != "null" }
                val snapId = when {
                    str.contains("snapId=") -> str.substringAfter("snapId=").substringBefore(",").trim()
                    str.contains("SNAP_ID") -> str.substringAfter("SNAP_ID=").substringBefore(",").trim()
                    else -> null
                }?.takeIf { it.isNotBlank() }
                if (userId != null && snapId != null) userIdCache[snapId] = userId
            }.onFailure { context.log.error("SpotlightCreatorInfo: BindViewEvent error", it) }
        }

        onNextActivityCreate {
            context.inAppOverlay.addCustomComposable {
                val showDialog by showDialogState
                val info by creatorInfoState
                if (showDialog) {
                    info?.let { creator ->
                        CreatorInfoDialog(
                            creatorInfo = creator,
                            onDismiss = { showDialogState.value = false }
                        )
                    }
                }
            }

            context.mappings.useMapper(OperaPageViewControllerMapper::class) {
                arrayOf(onDisplayStateChange, onDisplayStateChangeGesture).forEach { methodName ->
                    classReference.get()?.hook(methodName.get() ?: return@forEach, HookStage.AFTER) { param ->
                        runCatching {
                            val viewStateFieldName = viewStateField.get() ?: return@hook
                            val layerListFieldName = layerListField.get() ?: return@hook
                            val viewState = param.thisObject<Any>().getObjectField(viewStateFieldName)?.toString() ?: return@hook
                            val layers = (param.thisObject<Any>().getObjectField(layerListFieldName) as? ArrayList<*>) ?: return@hook

                            if (layers.isEmpty()) {
                                context.runOnUiThread {
                                    isInSpotlightState.value = false
                                    creatorInfoState.value = null
                                    showDialogState.value = false
                                    updateAllButtonVisibility()
                                }
                                return@hook
                            }

                            val firstLayer = layers.firstOrNull() ?: return@hook
                            val params = Layer(firstLayer).paramMap
                            val source = params["SNAP_SOURCE"]?.toString()
                            if (source != "SINGLE_SNAP_STORY" && source != "SPOTLIGHT" && source != "PUBLIC_STORY") {
                                context.runOnUiThread {
                                    isInSpotlightState.value = false
                                    creatorInfoState.value = null
                                    showDialogState.value = false
                                    updateAllButtonVisibility()
                                }
                                return@hook
                            }

                            if (viewState != "FULLY_DISPLAYED") return@hook

                            val snapId = params["SNAP_ID"]?.toString() ?: return@hook
                            if (snapId == creatorInfoState.value?.snapId) return@hook

                            val displayName = params["CREATOR_DISPLAY_NAME"]?.toString()
                            val userId = params.parseCreatorUserId() ?: userIdCache[snapId]

                            if (displayName == null && userId == null) {
                                context.runOnUiThread {
                                    isInSpotlightState.value = false
                                    creatorInfoState.value = null
                                    showDialogState.value = false
                                    updateAllButtonVisibility()
                                }
                                return@hook
                            }

                            val friendLinkType = userId?.let {
                                runCatching { context.database.getFriendInfo(it)?.friendLinkType }.getOrNull()
                            }?.let { FriendLinkType.fromValue(it) }

                            context.runOnUiThread {
                                creatorInfoState.value = CreatorInfo(
                                    snapId = snapId,
                                    creatorDisplayName = displayName ?: context.translation.getOrNull("common.unknown") ?: "Unknown",
                                    creatorUserId = userId,
                                    timestamp = params.parseTimestamp(),
                                    friendLinkType = friendLinkType
                                )
                                isInSpotlightState.value = true
                                updateAllButtonVisibility()
                            }
                        }.onFailure { context.log.error("SpotlightCreatorInfo: hook error", it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun CreatorInfoDialog(creatorInfo: CreatorInfo, onDismiss: () -> Unit) {
        var snapchatterInfo by remember { mutableStateOf<Snapchatter?>(null) }
        var originalUsername by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        val translation = remember { context.translation.getCategory("spotlight_creator_info") }

        LaunchedEffect(creatorInfo.snapId) {
            snapchatterInfo = null
            originalUsername = null
            isLoading = false
            if (creatorInfo.creatorUserId != null) {
                isLoading = true
                withContext(Dispatchers.IO) {
                    runCatching {
                        val uid = creatorInfo.creatorUserId!!
                        val info = runCatching {
                            context.feature(Messaging::class).fetchSnapchatterInfos(listOf(uid)).firstOrNull()
                        }.getOrNull()
                        val first = runCatching {
                            context.database.getFriendInfo(uid)?.let {
                                context.database.getFriendOriginalUsername(it.mutableUsername ?: "") ?: it.firstCreatedUsername
                            } ?: info?.username?.let { context.database.getFriendOriginalUsername(it) }
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            snapchatterInfo = info
                            originalUsername = first?.takeIf { it.isNotBlank() }
                            isLoading = false
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            }
        }

        Dialog(onDismissRequest = onDismiss) {
            PurrfectOverlayTheme {
                val shape = RoundedCornerShape(20.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = shape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.55f),
                                PurrfectOverlayPalette.glowSecondary.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .background(PurrfectOverlayPalette.cardOverlay, shape)
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = translation["title"] as? String ?: "Creator Info",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurrfectOverlayPalette.textPrimary
                                )
                                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = translation["close"] as? String ?: "Close",
                                        modifier = Modifier.size(20.dp),
                                        tint = PurrfectOverlayPalette.textSecondary
                                    )
                                }
                            }

                            HorizontalDivider(color = PurrfectOverlayPalette.textSecondary.copy(alpha = 0.2f))

                            creatorInfo.timestamp?.let { ts ->
                                InfoRow(
                                    icon = Icons.Default.Schedule,
                                    label = translation["posted_on"] as? String ?: "Posted",
                                    value = formatDate(ts)
                                )
                            }

                            val displayName = snapchatterInfo?.displayName ?: creatorInfo.creatorDisplayName
                            InfoRow(
                                icon = Icons.Default.Person,
                                label = translation["display_name"] as? String ?: "Display name",
                                value = displayName
                            )

                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = PurrfectOverlayPalette.glowPrimary
                                    )
                                    Text(
                                        translation["loading_username"] as? String ?: "Loading…",
                                        fontSize = 13.sp,
                                        color = PurrfectOverlayPalette.textSecondary
                                    )
                                }
                            } else {
                                val username = snapchatterInfo?.username
                                InfoRowWithCopy(
                                    icon = Icons.Default.AccountCircle,
                                    label = translation["username"] as? String ?: "Username",
                                    value = username ?: (context.translation.getOrNull("common.unknown") ?: "—"),
                                    canCopy = username != null,
                                    onCopy = {
                                        username?.let {
                                            context.androidContext.copyToClipboard(it, translation["username"] as? String ?: "Username")
                                            context.inAppOverlay.showStatusToast(
                                                Icons.Default.Check,
                                                translation["username_copied"] as? String ?: "Copied"
                                            )
                                        }
                                    }
                                )
                            }

                            originalUsername?.takeIf { it != snapchatterInfo?.username }?.let { orig ->
                                InfoRowWithCopy(
                                    icon = Icons.Default.Badge,
                                    label = translation["first_created_username"] as? String ?: "First Created Username",
                                    value = orig,
                                    canCopy = true,
                                    onCopy = {
                                        context.androidContext.copyToClipboard(
                                            orig,
                                            translation["first_created_username"] as? String ?: "First Created Username"
                                        )
                                        context.inAppOverlay.showStatusToast(
                                            Icons.Default.Check,
                                            translation["username_copied"] as? String ?: "Copied"
                                        )
                                    }
                                )
                            }

                            InfoRowWithCopy(
                                icon = Icons.Default.Fingerprint,
                                label = translation["user_id"] as? String ?: "User ID",
                                value = creatorInfo.creatorUserId ?: (context.translation.getOrNull("common.unknown") ?: "—"),
                                canCopy = creatorInfo.creatorUserId != null,
                                onCopy = {
                                    creatorInfo.creatorUserId?.let {
                                        context.androidContext.copyToClipboard(it, translation["user_id"] as? String ?: "User ID")
                                        context.inAppOverlay.showStatusToast(
                                            Icons.Default.Check,
                                            translation["user_id_copied"] as? String ?: "Copied"
                                        )
                                    }
                                }
                            )

                            creatorInfo.friendLinkType?.let { linkType ->
                                val statusText = when (linkType) {
                                    FriendLinkType.MUTUAL -> translation["mutual_friend"] as? String ?: "Mutual friend"
                                    FriendLinkType.FOLLOWING -> translation["following"] as? String ?: "Following"
                                    FriendLinkType.OUTGOING -> translation["friend_request_sent"] as? String ?: "Request sent"
                                    FriendLinkType.INCOMING -> translation["friend_request_received"] as? String ?: "Request received"
                                    FriendLinkType.BLOCKED -> translation["blocked"] as? String ?: "Blocked"
                                    FriendLinkType.DELETED -> translation["friend_removed"] as? String ?: "Removed"
                                    else -> null
                                }
                                if (statusText != null) {
                                    InfoRow(
                                        icon = Icons.Default.Person,
                                        label = translation["friend_status"] as? String ?: "Friend status",
                                        value = statusText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoRow(icon: ImageVector, label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.9f)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = PurrfectOverlayPalette.textSecondary
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurrfectOverlayPalette.textPrimary
                )
            }
        }
    }

    @Composable
    private fun InfoRowWithCopy(
        icon: ImageVector,
        label: String,
        value: String,
        canCopy: Boolean,
        onCopy: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = PurrfectOverlayPalette.glowPrimary.copy(alpha = 0.9f)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = PurrfectOverlayPalette.textSecondary
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurrfectOverlayPalette.textPrimary
                )
            }
            if (canCopy) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = PurrfectOverlayPalette.glowSecondary
                    )
                }
            }
        }
    }
}
