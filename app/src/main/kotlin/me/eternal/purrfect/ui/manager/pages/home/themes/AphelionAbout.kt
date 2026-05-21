package me.eternal.purrfect.ui.manager.pages.home.themes

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
import androidx.compose.material3.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.R
import me.eternal.purrfect.common.util.ktx.openLink
import me.eternal.purrfect.ui.manager.Routes
import me.eternal.purrfect.ui.manager.chimaera.ChimaeraDiscovery
import me.eternal.purrfect.ui.manager.components.FloatingTopBar
import me.eternal.purrfect.ui.manager.pages.home.HomeAbout
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.ui.util.PurrfectMarqueeText
import me.eternal.purrfect.ui.util.headerHeightTracker
import me.eternal.purrfect.ui.util.scaleOnPress

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun HomeAbout.AphelionAboutContent(nav: NavBackStackEntry) {
    val avenirNext = remember {
        FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium))
    }
    val scrollState = rememberScrollState()
    val aboutStory = remember { translation["about_story"]?.trim() ?: "" }
    val horizontalPadding = 24.dp
    val bottomPadding = routes.bottomPadding
    val tapSource = remember { MutableInteractionSource() }
    val tapTimeoutMs = 1500L
    val tapCount = remember { mutableIntStateOf(0) }
    val lastTapTime = remember { mutableLongStateOf(0L) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val managerTheme = context.config.root.global.uiSettings.managerTheme.get()
    val activeSkin = LocalPurrfectSkin.current
    val skin = remember(managerTheme, activeSkin) { if (managerTheme == "APHELION") activeSkin else me.eternal.purrfect.common.ui.theme.PurrfectPalette }

    val isRedditMode = remember(context.activeTargetApp) { context.activeTargetApp == me.eternal.purrfect.common.TargetApp.REDDIT }
    val targetAccent = if (isRedditMode) Color(0xFFFF4500) else Color(0xFFFFE100)
    val targetSuffix = if (isRedditMode) "Reddit" else "Snap"
    val aboutTagline = remember(isRedditMode) {
        if (isRedditMode) {
            (translation["about_tagline"] ?: "").replace("Snapchat", "Reddit")
        } else {
            translation["about_tagline"] ?: ""
        }
    }

    LaunchedEffect(scrollState.value) {
        routes.navigation?.globalScrollOffset = scrollState.value
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skin.backgroundGradient)
    ) {
        var controlsHeight by remember { mutableStateOf(100.dp) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = bottomPadding + 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(controlsHeight))

            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.1f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(skin.panelGradient)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append("Purrfect")
                                withStyle(SpanStyle(color = targetAccent)) {
                                    append(targetSuffix)
                                }
                            },
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = skin.textPrimary,
                            fontFamily = avenirNext,
                            modifier = Modifier.clickable(
                                interactionSource = tapSource,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastTapTime.longValue > tapTimeoutMs) {
                                        tapCount.intValue = 0
                                    }
                                    tapCount.intValue += 1
                                    lastTapTime.longValue = now
                                    if (tapCount.intValue >= 5) {
                                        tapCount.intValue = 0
                                        routes.retroGame.navigate()
                                    }
                                }
                            )
                        )
                        Text(
                            text = aboutTagline,
                            fontSize = 14.sp,
                            color = skin.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = translation["about_lead_developers_title"] ?: "Lead Developers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = skin.textPrimary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeveloperCard(name = "ΞTΞRNAL", imageRes = R.drawable.pfp_external, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                            DeveloperCard(name = "ᴋᴀʟᴀᴅɪɴ", imageRes = R.drawable.pfp_kaladin, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeveloperCard(name = "𝚜𝚌𝚑𝚛𝚘𝚍𝚒𝚗𝚐𝚎𝚛𝚜𝚙𝚎𝚝", imageRes = R.drawable.pfp_schrodingerspet, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                            DeveloperCard(name = "<RSR/>", imageRes = R.drawable.pfp_rsr, avenirNext = avenirNext, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = skin.cardOverlayColor,
                border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.08f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PurrfectMarqueeText(
                        text = translation["about_story_title"] ?: "About Us",
                        color = skin.textPrimary,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 4.dp),
                        thickness = 1.2.dp,
                        color = skin.textPrimary.copy(alpha = 0.3f)
                    )

                    Text(
                        text = aboutStory,
                        fontSize = 15.sp,
                        color = skin.textSecondary,
                        textAlign = TextAlign.Justify,
                        style = LocalTextStyle.current.copy(
                            lineHeight = 22.sp,
                            textIndent = TextIndent(firstLine = 24.sp),
                            fontFamily = avenirNext
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = skin.cardOverlayColor,
                border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.12f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = translation["about_thanks_title"] ?: "Special Thanks", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = skin.textPrimary, textAlign = TextAlign.Center, fontFamily = avenirNext)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://github.com/particle-box/Purrfect", context.translation["toast_open_link_failed"] ?: "") }, colors = ButtonDefaults.buttonColors(containerColor = skin.textPrimary, contentColor = skin.cardOverlayColor), shape = RoundedCornerShape(14.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation["github_button"] ?: "GitHub", maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = avenirNext)
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { context.androidContext.openLink("https://t.me/purrfect_tg", context.translation["toast_open_link_failed"] ?: "") }, border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.textPrimary), shape = RoundedCornerShape(14.dp)) {   
                            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram), contentDescription = null, modifier = Modifier.size(18.dp), tint = skin.textPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation["telegram_button"] ?: "Telegram", maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = avenirNext)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        FloatingTopBar(
            title = routeInfo.translatedKey?.value ?: translation["manager.routes.home_about"] ?: "About Us",
            onBack = { routes.navController.popBackStack() },
            scrollOffset = scrollState.value,
            enableMorph = true,
            modifier = Modifier.headerHeightTracker { controlsHeight = it }
        )
    }
}
