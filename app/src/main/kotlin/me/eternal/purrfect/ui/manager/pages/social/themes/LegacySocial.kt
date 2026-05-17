package me.eternal.purrfect.ui.manager.pages.social.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfect.common.data.SocialScope
import me.eternal.purrfect.ui.manager.pages.social.SocialRootSection
import me.eternal.purrfect.ui.manager.pages.social.sortSocialFriends
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import me.eternal.purrfect.ui.manager.pages.themes.legacy.components.LegacyBackground
import me.eternal.purrfect.ui.util.headerHeightTracker

import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialRootSection.LegacySocialContent(nav: NavBackStackEntry) {
    val managerTheme = remember { context.config.root.global.uiSettings.managerTheme.get() }
    val skin = if (managerTheme == "APHELION") LocalPurrfectSkin.current else PurrfectPalette
    SocialDataController()

    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val titles = listOf(translation["friends_tab"], translation["groups_tab"])

    val density = androidx.compose.ui.platform.LocalDensity.current
    var controlsHeight by remember { mutableStateOf(96.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        LegacyBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + controlsHeight + 8.dp))
            
            if (searchActive) {
                val searchHint = context.translation["manager.dialogs.add_friend.search_hint"]
                val searchShape = RoundedCornerShape(18.dp)
                val searchBorder = Brush.linearGradient(
                    listOf(
                        skin.glowPrimary.copy(alpha = 0.45f),
                        skin.glowSecondary.copy(alpha = 0.35f)
                    )
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = searchShape,
                    color = skin.textPrimary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, searchBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(skin.cardOverlay, searchShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = searchHint,
                            tint = skin.textSecondary
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = skin.textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(skin.glowSecondary),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchHint ?: "",
                                    color = skin.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = skin.textPrimary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                modifier = Modifier
                    .fillMaxSize(),
                state = pagerState
            ) { page ->
                when (page) {
                    0 -> ScopeList(SocialScope.FRIEND, friendList.filter { (it.displayName ?: it.userId).contains(searchQuery, true) || it.userId.contains(searchQuery, true) }, groupList.filter { it.name.contains(searchQuery, true) })
                    1 -> ScopeList(SocialScope.GROUP, friendList.filter { (it.displayName ?: it.userId).contains(searchQuery, true) || it.userId.contains(searchQuery, true) }, groupList.filter { it.name.contains(searchQuery, true) })
                }
            }
        }

        // Floating Header (v1.3.9 Layout)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 0.dp)
                .zIndex(1f)
                .onGloballyPositioned { coordinates ->
                    val newHeight = with(density) { coordinates.size.height.toDp() }
                    if (newHeight != controlsHeight) controlsHeight = newHeight
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = skin.cardOverlayColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            skin.glowPrimary.copy(alpha = 0.6f),
                            skin.glowSecondary.copy(alpha = 0.52f)
                        )
                    )
                )
            ) {
                Box {
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(skin.cardOverlay))
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    text = translation["manager.routes.social"] ?: "Social",
                                    color = skin.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                            Row(
                                modifier = Modifier.wrapContentWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatPill(label = translation["friends_tab"], value = friendList.size)
                                StatPill(label = translation["groups_tab"], value = groupList.size)
                                IconButton(onClick = {
                                    searchActive = !searchActive
                                    if (!searchActive) searchQuery = ""
                                }) {
                                    Icon(
                                        imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = skin.textPrimary
                                    )
                                }
                            }
                        }
                        
                        // SocialTabSwitcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            titles.forEachIndexed { index, title ->
                                val selected = pagerState.currentPage == index
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) skin.textPrimary.copy(alpha = 0.12f) else skin.textPrimary.copy(alpha = 0.06f),
                                    border = if (selected) BorderStroke(1.dp, Brush.linearGradient(listOf(skin.glowPrimary, skin.glowSecondary))) else BorderStroke(
                                        1.dp,
                                        skin.textPrimary.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (index == 0) Icons.Filled.People else Icons.Filled.Groups,
                                            contentDescription = null,
                                            tint = skin.textPrimary
                                        )
                                        Text(
                                            text = title ?: "",
                                            color = skin.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium
                                        )
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

@Composable
private fun StatPill(label: String?, value: Int) {
    val skin = LocalPurrfectSkin.current
    Surface(
        shape = RoundedCornerShape(50),
        color = skin.textPrimary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.16f)),
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
                color = skin.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label ?: "",
                color = skin.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
