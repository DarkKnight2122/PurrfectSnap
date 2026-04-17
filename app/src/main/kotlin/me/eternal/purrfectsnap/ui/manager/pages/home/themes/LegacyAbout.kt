package me.eternal.purrfectsnap.ui.manager.pages.home.themes

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.common.util.ktx.openLink
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeAbout
import me.eternal.purrfectsnap.common.ui.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components.LegacyBackground
import me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components.LegacyFloatingTopBar

/**
 * Modularized About screen for the "Shell Swap" architecture.
 * 
 * This module implements the legacy visual aesthetic as an extension function of the base logic class.
 * All state management and core logic are hosted within [HomeAbout], while this file
 * handles only the specific layout and components required for this theme.
 */
@Composable
fun HomeAbout.LegacyAboutContent(nav: NavBackStackEntry) {
    val avenirNext = remember { FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium)) }
    val scrollState = rememberScrollState()
    val aboutStory = remember { translation["about_story"] ?: "" }
    val pagePadding = 14.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    var controlsHeight by remember { mutableStateOf(96.dp) }
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = routes.bottomPadding + navigationBarPadding + 24.dp
    val tapSource = remember { MutableInteractionSource() }
    val tapCount = remember { mutableIntStateOf(0) }
    val lastTapTime = remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize()) {
        LegacyBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = bottomPadding)
        ) {
            Spacer(modifier = Modifier.height(statusBarHeight + controlsHeight + 8.dp))

            Surface(
                modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.background(PurrfectPalette.panelGradient).padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = translation["about_title"] ?: "About",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PurrfectPalette.textPrimary,
                        fontFamily = avenirNext,
                        modifier = Modifier.clickable(interactionSource = tapSource, indication = null) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTapTime.value > 1500L) { tapCount.intValue = 0 }
                            tapCount.intValue += 1
                            lastTapTime.value = now
                            if (tapCount.intValue >= 5) { tapCount.intValue = 0; routes.retroGame.navigate() }
                        }
                    )
                    Text(text = translation["about_tagline"] ?: "", fontSize = 13.sp, color = PurrfectPalette.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(text = translation["about_lead_developers_title"] ?: "Lead Developers", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        DeveloperCard(name = "ΞTΞRNAL", imageRes = R.drawable.pfp_external, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                        DeveloperCard(name = "<RSR/>", imageRes = R.drawable.pfp_rsr, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                tonalElevation = 0.dp, shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.background(PurrfectPalette.cardOverlay).padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = translation["about_story_title"] ?: "Our Story", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = aboutStory, fontSize = 14.sp, color = PurrfectPalette.textSecondary, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.padding(horizontal = pagePadding).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 0.dp, shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = translation["about_thanks_title"] ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://github.com/particle-box/PurrfectSnap", context.translation["toast_open_link_failed"] ?: "") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B152E))) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation["github_button"] ?: "GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"] ?: "") }, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation["telegram_button"] ?: "Telegram", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        LegacyFloatingTopBar(
            title = routeInfo.translatedKey?.value ?: translation["manager.routes.home_about"] ?: "About",
            onBack = { routes.navController.popBackStack() },
            onHeightMeasured = { height -> 
                if (height != controlsHeight) controlsHeight = height
            }
        )
    }
}

@Composable
private fun DeveloperCard(
    name: String,
    imageRes: Int,
    avenirNext: FontFamily,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    val imageRing = Brush.linearGradient(
        listOf(
            PurrfectPalette.glowPrimary,
            PurrfectPalette.glowSecondary
        )
    )

    Surface(
        modifier = modifier,
        shape = cardShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(82.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).border(2.dp, imageRing, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = avenirNext, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
