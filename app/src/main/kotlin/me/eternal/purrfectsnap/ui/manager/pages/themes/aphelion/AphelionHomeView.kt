package me.eternal.purrfectsnap.ui.manager.pages.themes.aphelion

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.common.BuildConfig
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableState
import me.eternal.purrfectsnap.common.ui.rememberAsyncMutableStateList
import me.eternal.purrfectsnap.common.util.ktx.openLink
import me.eternal.purrfectsnap.storage.getQuickTiles
import me.eternal.purrfectsnap.storage.setQuickTiles
import me.eternal.purrfectsnap.ui.manager.components.AestheticDialog
import me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader
import me.eternal.purrfectsnap.ui.manager.data.Updater
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeRootSection
import me.eternal.purrfectsnap.ui.manager.pages.home.HomeState
import me.eternal.purrfectsnap.ui.manager.pages.home.QuickActionsDialog
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfectsnap.ui.util.Motion
import me.eternal.purrfectsnap.ui.util.PurrfectMarqueeText
import me.eternal.purrfectsnap.ui.util.headerHeightTracker
import me.eternal.purrfectsnap.ui.util.scaleOnPress
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeRootSection.AphelionHomeView(
    nav: NavBackStackEntry,
    state: HomeState,
    routes: me.eternal.purrfectsnap.ui.manager.Routes
) {
    val skin = LocalPurrfectSkin.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var controlsHeight by remember { mutableStateOf(100.dp) }

    @Composable
    fun LivingPurrAura(isActive: Boolean, haptic: HapticFeedback) {
        val infiniteTransition = rememberInfiniteTransition(label = "aura")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.88f, targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulse"
        )
        val glow1 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "g1"
        )
        val glow2 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, delayMillis = 1100, easing = LinearEasing), RepeatMode.Restart),
            label = "g2"
        )
        val glow3 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, delayMillis = 2200, easing = LinearEasing), RepeatMode.Restart),
            label = "g3"
        )
        val coreColor by animateColorAsState(
            targetValue = if (isActive) skin.glowPrimary else (if (skin.isDark) Color(0xFF8C8CA3) else Color(0xFFC0C0C0)),
            animationSpec = tween(800), label = "coreColor"
        )
        val secondaryColor by animateColorAsState(
            targetValue = if (isActive) skin.glowSecondary else (if (skin.isDark) Color(0xFF6B6B7A) else Color(0xFFAAAAAA)),
            animationSpec = tween(800), label = "secondaryColor"
        )
        Canvas(modifier = Modifier.size(44.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = 6.dp.toPx()
            fun drawAuroraGlow(progress: Float, alphaMultiplier: Float) {
                if (!isActive || progress <= 0f) return
                val auroraRadius = baseRadius * (1.2f + 4.5f * progress)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to coreColor.copy(alpha = 0.25f * (1f - progress) * alphaMultiplier),
                        0.6f to secondaryColor.copy(alpha = 0.12f * (1f - progress) * alphaMultiplier),
                        1.0f to Color.Transparent,
                        center = center, radius = auroraRadius
                    ),
                    radius = auroraRadius, center = center
                )
            }
            drawAuroraGlow(glow1, 0.8f)
            drawAuroraGlow(glow2, 0.5f)
            drawAuroraGlow(glow3, 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, secondaryColor),
                    center = center, radius = baseRadius * pulseScale
                ),
                radius = baseRadius * pulseScale, center = center
            )
            drawCircle(
                color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                radius = (baseRadius * pulseScale) * 0.25f,
                center = Offset(center.x - (baseRadius * pulseScale) * 0.3f, center.y - (baseRadius * pulseScale) * 0.3f)
            )
        }
    }

    @Composable
    fun AphelionTopBarActionChip(
        icon: ImageVector,
        label: String? = null,
        contentDescription: String? = label,
        shrinkFactor: Float = 1f,
        haptic: HapticFeedback,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
            shape = RoundedCornerShape(40),
            color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.06f),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        skin.glowPrimary.copy(alpha = 0.55f),
                        skin.glowSecondary.copy(alpha = 0.35f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(40))
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
                    .padding(vertical = 6.dp, horizontal = lerp(10.dp, 12.dp, shrinkFactor)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon, contentDescription = contentDescription, tint = skin.textPrimary,
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        val s = 0.82f + (0.18f * shrinkFactor)
                        scaleX = s; scaleY = s
                    }
                )
                if (label != null) {
                    val labelAlpha = (shrinkFactor - 0.1f).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.width((8 * shrinkFactor).dp))
                    Text(
                        text = label,
                        color = skin.textPrimary.copy(alpha = labelAlpha),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .graphicsLayer { alpha = labelAlpha; translationX = (-4 * (1f - shrinkFactor)).dp.toPx() }
                            .widthIn(max = (75 * shrinkFactor).dp)
                    )
                }
            }
        }
    }

    @Composable
    fun RowScope.AphelionHomeActionChips(scrollState: ScrollState, haptic: HapticFeedback) {
        val focusFactor = (scrollState.value.toFloat() / Motion.HEADER_MORPH_THRESHOLD).coerceIn(0f, 1f)
        val shrinkFactor = (1f - focusFactor).coerceIn(0f, 1f)
        AphelionTopBarActionChip(
            icon = Icons.Filled.BugReport, label = context.translation["manager.routes.home_logs"],
            shrinkFactor = shrinkFactor, haptic = haptic
        ) { routes.homeLogs.navigate() }
        AphelionTopBarActionChip(
            icon = Icons.Filled.Settings, label = context.translation["manager.routes.home_settings"],
            shrinkFactor = shrinkFactor, haptic = haptic
        ) { routes.settings.navigate() }
    }

    @Composable
    fun HeroBadge(text: String, onClick: () -> Unit = {}) {
        Text(
            text = text,
            color = skin.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onClick() }
                .background((if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }

    @Composable
    fun ExternalLinkIcon(
        imageVector: ImageVector,
        onClick: () -> Unit,
        tint: Color,
        containerColor: Color,
        haptic: HapticFeedback
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .scaleOnPress(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
    }

    @Composable
    fun AphelionHeroSection(
        versionName: String,
        latestUpdate: me.eternal.purrfectsnap.ui.manager.data.Updater.LatestRelease?,
        downloadState: UpdateDownloader.DownloadState,
        downloadProgress: Float,
        onUpdateAction: () -> Unit,
        channelLabel: String,
        isPurrAuraActive: Boolean,
        onAboutClick: () -> Unit,
        avenirNext: FontFamily,
        scrollOffset: () -> Int,
        haptic: HapticFeedback
    ) {
        val heroShape = RoundedCornerShape(36.dp)
        val gitHashShort = remember { (context.installationSummary.modInfo?.gitHash ?: BuildConfig.GIT_HASH).take(7) }
        
        Box(
            modifier = Modifier
                .padding(horizontal = HomeRootSection.cardMargin, vertical = 6.dp)
                .clip(heroShape)
                .background(skin.panelGradient)
                .border(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.1f), heroShape)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PurrfectSnap", color = skin.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = avenirNext)
                    Text(text = "By ΞTΞRNAL", color = skin.textPrimary.copy(alpha = 0.75f), fontSize = 14.sp, fontFamily = avenirNext)
                    Text(text = translation["hero_tagline"] ?: "", color = skin.textPrimary.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var versionTapCount by remember { mutableIntStateOf(0) }
                    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

                    HeroBadge(
                        text = translation.format("hero_version_label", "version" to versionName, "channel" to channelLabel),
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastVersionTapTime > 500) {
                                versionTapCount = 1
                            } else {
                                versionTapCount++
                            }
                            lastVersionTapTime = now
                            if (versionTapCount >= 5) {
                                versionTapCount = 0
                                val discovery = me.eternal.purrfectsnap.ui.manager.chimaera.ChimaeraDiscovery
                                discovery.load(context.sharedPreferences) // Ensure fresh state
                                if (discovery.unlocked) {
                                    routes.navigation?.pendingTransmission = discovery.welcomeBackMessage
                                } else {
                                    discovery.triggerStage1(context.sharedPreferences)
                                    routes.navigation?.pendingTransmission = discovery.transmissionMessage
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                    gitHashShort.takeIf { it.isNotBlank() && it.lowercase() != "unknown" }?.let {
                        HeroBadge(translation.format("hero_build_label", "build" to it))
                    }
                }

                if (latestUpdate != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.14f)),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = translation["update_title"] ?: "Update Available",
                                    color = skin.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = translation.format(
                                        "update_content",
                                        "version" to latestUpdate.versionName
                                    ),
                                    color = skin.textPrimary.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            androidx.compose.animation.AnimatedContent(
                                targetState = downloadState,
                                label = "UpdateDownloadHero"
                            ) { state ->
                                when (state) {
                                    me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader.DownloadState.IDLE,
                                    me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader.DownloadState.FAILED -> {
                                        Button(
                                            onClick = onUpdateAction,
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = skin.textPrimary,
                                                contentColor = skin.backgroundGradient.let { Color.Black } 
                                            ),
                                            border = BorderStroke(1.dp, skin.textPrimary.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = translation["download_icon_description"],
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader.DownloadState.DOWNLOADING -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { downloadProgress },
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 3.dp,
                                                color = skin.textPrimary
                                            )
                                            Text(
                                                text = "${(downloadProgress * 100).toInt()}%",
                                                color = skin.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    me.eternal.purrfectsnap.ui.manager.data.UpdateDownloader.DownloadState.COMPLETED -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = translation["completed_icon_description"],
                                                tint = Color(0xFFA3F0C2)
                                            )
                                            Text(
                                                text = translation["update_ready_label"] ?: "Ready to Install",
                                                color = skin.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.10f))
                ) {
                    val unifiedButtonWidth = 180.dp
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.10f)),
                            modifier = Modifier.width(unifiedButtonWidth).height(46.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                    LivingPurrAura(isActive = isPurrAuraActive, haptic = haptic)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPurrAuraActive) translation["purr_aura_active_label"] ?: "" else translation["purr_aura_inactive_label"] ?: "",
                                    color = skin.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAboutClick() },
                            border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.06f), contentColor = skin.textPrimary),
                            modifier = Modifier.width(unifiedButtonWidth).height(46.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = skin.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = translation.getOrNull("about_meet_team_button") ?: "About Us", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val androidContext = context.androidContext
                        Button(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://purrfectsnap.me", context.translation["toast_open_link_failed"]) },
                            colors = ButtonDefaults.buttonColors(containerColor = skin.textPrimary, contentColor = skin.cardOverlayColor),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                PurrfectMarqueeText(text = "Site", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = skin.cardOverlayColor)
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://github.com/particle-box/PurrfectSnap", context.translation["toast_open_link_failed"]) },
                            border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.textPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                PurrfectMarqueeText(text = translation["github_button"] ?: "", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = skin.textPrimary)
                            }
                        }
                        ExternalLinkIcon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); androidContext.openLink("https://t.me/purrfectsnap_official", context.translation["toast_open_link_failed"]) },
                            tint = skin.textPrimary, containerColor = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.14f),
                            haptic = haptic
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(scrollState.value) { routes.navigation?.globalScrollOffset = scrollState.value }

    val borderPath = remember { Path() }
    val uPath = remember { Path() }
    val quickActionsGradientColors = listOf(skin.glowPrimary.copy(alpha = 0.15f), skin.glowSecondary.copy(alpha = 0.05f))

    Box(modifier = Modifier.fillMaxSize().background(skin.backgroundGradient)) {

        val focusFactor by remember(scrollState.value) {
            derivedStateOf { (scrollState.value.toFloat() / Motion.HEADER_MORPH_THRESHOLD).coerceIn(0f, 1f) }
        }
        val stickyBrandingAlpha by remember(scrollState.value) {
            derivedStateOf { ((scrollState.value.toFloat() - 50f) / 100f).coerceIn(0f, 1f) }
        }
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val headerHeight = lerp(54.dp, 56.dp, focusFactor)
        val containerTopPadding = lerp(statusBarHeight + 2.dp, 0.dp, focusFactor)
        val internalTopPadding = lerp(0.dp, statusBarHeight, focusFactor)
        val topCorners = lerp(26.dp, 0.dp, focusFactor)
        val bottomCorners = lerp(26.dp, 28.dp, focusFactor)

        Box(modifier = Modifier.fillMaxWidth().zIndex(10f)) {
            val refractiveColor = skin.refractiveColor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = containerTopPadding)
                    .height(internalTopPadding + headerHeight + 32.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to refractiveColor.copy(alpha = 0.95f * focusFactor),
                            0.6f to refractiveColor.copy(alpha = 0.85f * focusFactor),
                            1.0f to Color.Transparent
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = containerTopPadding)
                    .headerHeightTracker { controlsHeight = it }
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val brush = Brush.linearGradient(
                            listOf(
                                skin.glowPrimary.copy(alpha = focusFactor * 0.6f),
                                skin.glowSecondary.copy(alpha = focusFactor * 0.4f)
                            )
                        )
                        val tr = topCorners.toPx()
                        val br = bottomCorners.toPx()
                        if (focusFactor > 0.9f) {
                            uPath.reset()
                            uPath.apply {
                                moveTo(0f, 0f)
                                lineTo(0f, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2 * br, 2 * br, size.height), 180f, -90f, false)
                                lineTo(size.width - br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * br, size.height - 2 * br, size.width, size.height), 90f, -90f, false)
                                lineTo(size.width, 0f)
                            }
                            drawPath(uPath, brush, style = Stroke(strokeWidth))
                        } else if (focusFactor > 0.01f) {
                            borderPath.reset()
                            borderPath.apply {
                                moveTo(tr, 0f)
                                lineTo(size.width - tr, 0f)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * tr, 0f, size.width, 2 * tr), 270f, 90f, false)
                                lineTo(size.width, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * br, size.height - 2 * br, size.width, size.height), 0f, 90f, false)
                                lineTo(br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2 * br, 2 * br, size.height), 90f, 90f, false)
                                lineTo(0f, tr)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * tr, 2 * tr), 180f, 90f, false)
                            }
                            drawPath(borderPath, brush, style = Stroke(strokeWidth))
                        }
                    },
                shape = RoundedCornerShape(topStart = topCorners, topEnd = topCorners, bottomStart = bottomCorners, bottomEnd = bottomCorners),
                color = skin.cardOverlayColor.copy(alpha = focusFactor * 0.95f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = internalTopPadding)
                        .padding(horizontal = 16.dp)
                        .height(headerHeight)
                ) {
                    Text(
                        text = "PurrfectSnap",
                        color = skin.textPrimary.copy(alpha = stickyBrandingAlpha),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = state.avenirNext,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    val announcementShift by remember(focusFactor) { derivedStateOf { (-6 * focusFactor).dp } }
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = announcementShift.toPx() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AphelionTopBarActionChip(
                            icon = Icons.Filled.Notifications, label = null,
                            shrinkFactor = (1f - focusFactor).coerceIn(0f, 1f),
                            contentDescription = translation["announcements_button_description"],
                            haptic = haptic
                        ) { state.onShowAnnouncements() }
                        AphelionTopBarActionChip(
                            icon = Icons.Filled.Description, label = null,
                            shrinkFactor = (1f - focusFactor).coerceIn(0f, 1f),
                            contentDescription = translation.getOrNull("changelog_button_description") ?: "Open full changelog",
                            haptic = haptic
                        ) { state.onShowFullChangelog() }
                    }
                    val settingsShift by remember(focusFactor) { derivedStateOf { (6 * focusFactor).dp } }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { translationX = settingsShift.toPx() },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AphelionHomeActionChips(scrollState = scrollState, haptic = haptic)
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = routes.bottomPadding + 4.dp)) {
            Spacer(Modifier.height(controlsHeight + containerTopPadding))

            AphelionHeroSection(
                versionName = BuildConfig.VERSION_NAME,
                latestUpdate = state.latestUpdate,
                downloadState = state.downloadState,
                downloadProgress = state.downloadProgress,
                onUpdateAction = state.onUpdateAction,
                channelLabel = state.channelLabel,
                isPurrAuraActive = state.isPurrAuraActive,
                onAboutClick = { routes.about.navigate() },
                avenirNext = state.avenirNext,
                scrollOffset = { scrollState.value },
                haptic = haptic
            )

            Spacer(Modifier.height(12.dp))

            AnimatedContent(targetState = state.selectedTiles.isNotEmpty(), label = "QuickActions") { hasQuickActions ->
                Surface(
                    modifier = Modifier.padding(horizontal = HomeRootSection.cardMargin, vertical = 10.dp),
                    shape = RoundedCornerShape(34.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(quickActionsGradientColors)).padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!hasQuickActions) {
                            Text(translation["quick_actions_title"] ?: "Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = skin.textPrimary.copy(alpha = 0.85f))
                            Spacer(Modifier.height(24.dp))
                            Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(72.dp), tint = skin.textPrimary)
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = state.onShowQuickActionsMenu,
                                colors = ButtonDefaults.buttonColors(containerColor = skin.textPrimary, contentColor = skin.cardOverlayColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(translation["quick_actions_add_tile_button"] ?: "Add Tile")
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(translation["quick_actions_title"] ?: "Quick Actions", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = skin.textPrimary)
                                Text(translation.format("quick_actions_count_label", "count" to state.selectedTiles.size.toString()), fontSize = 13.sp, color = skin.textPrimary.copy(alpha = 0.75f))
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = state.onShowQuickActionsMenu,
                                    border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.textPrimary)
                                ) {
                                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_manage), contentDescription = null, modifier = Modifier.size(18.dp))      
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(translation["quick_actions_manage_button"] ?: "Manage")
                                }
                            }
                            var gridIsVisible by remember { mutableStateOf(false) }
                            var animationPhase by remember { mutableIntStateOf(1) }
                            LaunchedEffect(gridIsVisible) {
                                if (gridIsVisible) {
                                    delay(600); animationPhase = 2
                                    delay(1200); animationPhase = 3
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                                    val windowHeight = context.androidContext.resources.displayMetrics.heightPixels
                                    val posY = coords.localToWindow(Offset.Zero).y
                                    if (posY > 0 && posY < windowHeight * 0.95f) gridIsVisible = true
                                }
                            ) {
                                val columns = (maxWidth / 110.dp).toInt().coerceIn(2, 4)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = columns
                                ) {
                                    state.selectedTiles.forEach { tileName ->
                                        val cardEntry = cards.entries.find { it.key.first == tileName } ?: return@forEach
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val animatedIconSize by animateDpAsState(
                                            targetValue = if (animationPhase >= 2) 28.dp else 44.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                                            label = "iconShrink"
                                        )
                                        Surface(
                                            modifier = Modifier.width(100.dp).aspectRatio(1.05f).scaleOnPress(interactionSource)
                                                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cardEntry.value(routes) },
                                            shape = RoundedCornerShape(18.dp),
                                            color = (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.06f),
                                            border = BorderStroke(1.dp, (if (skin.isDark) Color.White else Color.Black).copy(alpha = 0.16f))
                                        ) {
                                            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(skin.glowPrimary.copy(alpha = 0.3f), skin.glowSecondary.copy(alpha = 0.22f)))).clipToBounds()) {
                                                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                    Icon(cardEntry.key.second, contentDescription = null, tint = skin.textPrimary, modifier = Modifier.size(animatedIconSize))
                                                    Spacer(Modifier.height(8.dp))
                                                    PurrfectMarqueeText(
                                                        text = cardEntry.key.first,
                                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                        color = skin.textPrimary,
                                                        modifier = Modifier.fillMaxWidth()
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

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
