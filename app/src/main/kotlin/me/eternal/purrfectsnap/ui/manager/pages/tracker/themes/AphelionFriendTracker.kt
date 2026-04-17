package me.eternal.purrfectsnap.ui.manager.pages.tracker.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfectsnap.ui.manager.components.FloatingTopBar
import me.eternal.purrfectsnap.ui.manager.pages.tracker.FriendTrackerManagerRoot
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfectsnap.ui.util.Motion
import me.eternal.purrfectsnap.ui.util.headerHeightTracker

@Composable
fun FriendTrackerManagerRoot.AphelionFriendTrackerContent(nav: NavBackStackEntry) {
    val listState = rememberLazyListState()
    var controlsHeight by remember { mutableStateOf(100.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalPurrfectSkin.current.backgroundGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = controlsHeight)) {
            // Placeholder until tracker tabs are restored
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Friend Tracker Content (Restoring...)", color = Color.White)
            }
        }

        FloatingTopBar(
            title = translation["manager.routes.home_tracker"] ?: "Friend Tracker",
            onBack = { routes.navController.popBackStack() },
            scrollOffset = if (listState.firstVisibleItemIndex > 0) Motion.HEADER_MORPH_THRESHOLD.toInt() else listState.firstVisibleItemScrollOffset,
            enableMorph = true,
            modifier = Modifier.headerHeightTracker { controlsHeight = it }
        )
    }
}
