package me.eternal.purrfect.ui.manager

import android.app.Activity
import android.os.Build
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import me.eternal.purrfect.common.ui.theme.PurrfectColorSet
import me.eternal.purrfect.ui.manager.theme.aphelion.ThemeRevealState
import me.eternal.purrfect.common.ui.util.G2RoundedRectangle
import kotlin.math.PI
import kotlin.math.sin
import me.eternal.purrfect.ui.manager.theme.aetherGlass
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import me.eternal.purrfect.ui.manager.components.AestheticDialog
import me.eternal.purrfect.ui.util.headerHeightTracker
import me.eternal.purrfect.ui.util.Motion

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)
class Navigation(
    internal val context: RemoteSideContext,
    private val navController: NavHostController,
    val routes: Routes = Routes(context).also { it.navController = navController }
) {
    private val translation by lazy { context.translation.getCategory("manager.navigation") }
    var openBottomBarCustomization by mutableStateOf(false)
    var globalScrollOffset by mutableIntStateOf(0)
    var pendingTransmission by mutableStateOf<String?>(null)
    var isFirstUnlock by mutableStateOf(false)
    var showCinematic by mutableStateOf(false)
    var showGame by mutableStateOf(false)
    val themeRevealState = ThemeRevealState()

    @Composable
    fun TopBar() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        if (currentRoute?.routeInfo?.hasOwnTopBar == true) return

        val shrinkThreshold = Motion.HEADER_MORPH_THRESHOLD
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val focusFactor = if (isAphelion) (globalScrollOffset / shrinkThreshold).coerceIn(0f, 1f) else 0f

        val springFocusFactor by animateFloatAsState(
            targetValue = focusFactor,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
            label = "aetherSpring"
        )
        val activeFocus = if (skin.id == "AETHER") springFocusFactor else focusFactor
        val headerHeight = lerp(64.dp, 48.dp, activeFocus)

        val canGoBack = remember(navBackStackEntry) {
            currentRoute?.let { !it.routeInfo.primary || it.routeInfo.childIds.contains(routes.currentDestination) } == true
        }
        val haptic = LocalHapticFeedback.current

        val topBarModifier = Modifier
            .height(headerHeight)
            .then(
                if (skin.id == "AETHER" || skin.id == "LUMINA") {
                    Modifier.background(skin.cardOverlayColor, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .border(2.dp, skin.glassBorder, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                } else {
                    Modifier.aetherGlass(skin = skin, bottomStart = 22.dp, bottomEnd = 22.dp, focusFactor = activeFocus)
                }
            )

        TopAppBar(
            modifier = topBarModifier,
            title = {
                currentRoute?.apply {
                    title?.invoke() ?: routeInfo.translatedKey?.value?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 1f - (activeFocus * 0.05f)
                                scaleY = 1f - (activeFocus * 0.05f)
                                translationY = (-2 * activeFocus).dp.toPx()
                            }
                        )
                    }
                }
            },
            navigationIcon = {
                val backButtonAnimation by animateFloatAsState(if (canGoBack) 1f else 0f, label = "backButton")
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = backButtonAnimation
                            scaleX = 1f - (activeFocus * 0.1f)
                            scaleY = 1f - (activeFocus * 0.1f)
                        }
                        .width(lerp(0.dp, 48.dp, backButtonAnimation))
                        .height(48.dp)
                ) {
                    IconButton(onClick = {
                        if (canGoBack) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            actions = {
                currentRoute?.topBarActions?.invoke(this)
                if (currentRoute?.routeInfo?.id == routes.settings.routeInfo.id) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        openBottomBarCustomization = true
                    }) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                    }
                }
            }
        )
    }

    @Composable
    fun FloatingBottomBar() {
        val haptic = LocalHapticFeedback.current
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }
        val isLimitedTargetMode = context.isLimitedTargetMode
        val availableRoutes = remember(isLimitedTargetMode) {
            if (isLimitedTargetMode) {
                listOf(routes.home, routes.features)
            } else {
                listOf(routes.tasks, routes.features, routes.home, routes.social, routes.scripting, routes.friendTracker)
            }
        }
        val availableRouteMap = remember(availableRoutes) { availableRoutes.associateBy { it.routeInfo.id } }

        val shrinkThreshold = Motion.HEADER_MORPH_THRESHOLD
        val isAphelion = context.config.root.global.uiSettings.managerTheme.get() == "APHELION"
        val skin = if (isAphelion) LocalPurrfectSkin.current else PurrfectPalette
        val focusFactor = if (isAphelion) (globalScrollOffset / shrinkThreshold).coerceIn(0f, 1f) else 0f
        val barHeight = lerp(82.dp, 64.dp, focusFactor)
        val labelAlpha = (1f - (focusFactor * 2.5f)).coerceIn(0f, 1f)
        val iconTranslationY = (10 * focusFactor).dp

        val isMechanicalMode = skin.id == "AETHER" || skin.id == "LUMINA"

        val prefs = remember { context.sharedPreferences }
        val defaultOrder = remember(isLimitedTargetMode) {
            if (isLimitedTargetMode) listOf("home", "features") else listOf("tasks", "features", "home", "social", "scripts")
        }
        fun loadSelected(): List<String> {
            if (isLimitedTargetMode) return defaultOrder
            val raw = prefs.getString("manager_nav_tabs", null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val cleaned = raw.filter { availableRouteMap.containsKey(it) }
            val list = (if (cleaned.isNotEmpty()) cleaned else defaultOrder).distinct()
            return list.take(5)
        }
        var defaultTabId by remember(isLimitedTargetMode) { mutableStateOf(if (isLimitedTargetMode) "home" else prefs.getString("manager_default_tab", "home") ?: "home") }
        fun saveDefault(id: String) {
            if (isLimitedTargetMode) return
            defaultTabId = id
            prefs.edit().putString("manager_default_tab", id).apply()
        }
        fun saveSelected(ids: List<String>) {
            if (isLimitedTargetMode) return
            if (defaultTabId !in ids) {
                val candidate = when {
                    "home" in ids -> "home"
                    ids.isNotEmpty() -> ids.first()
                    else -> defaultTabId
                }
                saveDefault(candidate)
            }
            prefs.edit().putString("manager_nav_tabs", ids.joinToString(",")).apply()
        }
        var selectedTabIds by remember(isLimitedTargetMode) { mutableStateOf(loadSelected()) }
        val selectedRoutes = remember(selectedTabIds) { selectedTabIds.mapNotNull { availableRouteMap[it] } }

        // GATED GEOMETRY: Restore 28.dp for LUX/NOX, bold 32.dp for Performance
        val barShape = remember(isMechanicalMode, isAphelion) {
            if (isMechanicalMode) G2RoundedRectangle(32.dp)
            else if (isAphelion) RoundedCornerShape(28.dp)
            else RoundedCornerShape(32.dp)
        }

        val barBorder = remember(skin) {
            Brush.linearGradient(
                listOf(
                    skin.glowPrimary.copy(alpha = 0.9f),
                    skin.glowSecondary.copy(alpha = 0.85f)
                )
            )
        }

        // SLAB GRADIENT: High-opacity vertical gradient for premium feel
        val slabGradient = remember(skin) {
            Brush.verticalGradient(
                listOf(
                    skin.cardOverlayColor.copy(alpha = 0.98f),
                    skin.cardOverlayColor.copy(alpha = 0.88f)
                )
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val baseItemWidth = 92.dp
            val containerPadding = 24.dp
            val targetBarWidth = if (selectedRoutes.size < 5) baseItemWidth * selectedRoutes.size.toFloat() + containerPadding else null
            val animatedBarWidth by animateDpAsState(targetValue = targetBarWidth ?: 0.dp, label = "barWidth")

            Surface(
                shape = barShape,
                color = Color.Transparent,
                contentColor = skin.textPrimary,
                border = BorderStroke(
                    if (isMechanicalMode) 2.dp else 1.dp,
                    if (isMechanicalMode) SolidColor(skin.glowPrimary) else barBorder
                ),
                modifier = Modifier
                    .then(if (targetBarWidth != null) Modifier.width(animatedBarWidth) else Modifier.fillMaxWidth())
                    .then(
                        if (isMechanicalMode) {
                            Modifier.background(skin.cardOverlayColor, barShape)
                        } else if (isAphelion) {
                            Modifier.background(slabGradient, barShape)
                        } else {
                            Modifier.aetherGlass(skin = skin, cornerRadius = 32.dp, focusFactor = 1f)
                        }
                    )
                    .drawBehind {
                        if (isMechanicalMode) return@drawBehind
                        
                        // REFRACTIVE SCATTERING: Premium mesh glow effect for Aphelion
                        if (isAphelion) {
                            val radius = size.width * 0.85f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        skin.glowPrimary.copy(alpha = 0.15f),
                                        skin.glowSecondary.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center
                            )
                        } else {
                            val radius = size.width * 0.62f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        skin.glowPrimary.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center
                            )
                        }
                    }
                    .shadow(
                        elevation = if (isMechanicalMode) 0.dp else 28.dp,
                        shape = barShape,
                        spotColor = skin.glowPrimary.copy(alpha = 0.35f),
                        ambientColor = skin.glowSecondary.copy(alpha = 0.26f)
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(barShape)
                ) {
                    Box(Modifier.fillMaxWidth().height(barHeight)) {
                        var barWidthPx by remember { mutableStateOf(0f) }
                        val itemCount = selectedRoutes.size.coerceAtLeast(1)
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val selectedIndex = remember(currentRoute, selectedRoutes) {
                            val index = selectedRoutes.indexOf(currentRoute)
                            if (index >= 0) index else null
                        }

                        selectedIndex?.let { 
                            val itemWidthPx =
                                remember(barWidthPx, itemCount) { if (itemCount > 0) barWidthPx / itemCount else 0f }
                            val offsetAnim = remember { Animatable(0f) }
                            var lastSelectedIndex by remember { mutableStateOf(selectedIndex) }
                            LaunchedEffect(itemWidthPx) {
                                if (itemWidthPx > 0f) {
                                    offsetAnim.snapTo(selectedIndex * itemWidthPx)
                                }
                            }
                            LaunchedEffect(selectedIndex, itemWidthPx) {
                                if (itemWidthPx <= 0f) return@LaunchedEffect
                                val dist = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                                val damping = when {
                                    dist >= 3 -> 0.65f
                                    dist == 2 -> 0.75f
                                    else -> 0.90f
                                }
                                val stiffness = if (isMechanicalMode) 450f else Spring.StiffnessMediumLow
                                offsetAnim.animateTo(
                                    targetValue = selectedIndex * itemWidthPx,
                                    animationSpec = spring(dampingRatio = damping, stiffness = stiffness)
                                )
                                lastSelectedIndex = selectedIndex
                            }
                            val horizontalInset = 2.dp
                            val indicatorWidth = (with(density) { itemWidthPx.toDp() } - horizontalInset * 2)
                                .coerceAtLeast(0.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { barWidthPx = it.size.width.toFloat() }
                            ) {
                                val motionProgress = remember { Animatable(1f) }
                                LaunchedEffect(selectedIndex) {
                                    motionProgress.snapTo(0f)
                                    val dist = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                                    val dur = when {
                                        dist >= 3 -> 440
                                        dist == 2 -> 380
                                        else -> 320
                                    }
                                    motionProgress.animateTo(
                                        1f,
                                        animationSpec = tween(durationMillis = dur, easing = FastOutSlowInEasing)
                                    )
                                }
                                val pulse = sin(PI * motionProgress.value).toFloat()
                                val distForScale = kotlin.math.abs(selectedIndex - lastSelectedIndex).coerceAtLeast(1)
                                val scaleXBase = 0.18f
                                val scaleXExtra = 0.06f
                                val scaleYBase = 0.06f
                                val scaleYExtra = 0.02f
                                val mult = (distForScale - 1).coerceAtLeast(0)
                                val scaleXAnim = 1f + (scaleXBase + scaleXExtra * mult) * pulse
                                val scaleYAnim = 1f - (scaleYBase + scaleYExtra * mult) * pulse
                                if (barWidthPx > 0f && itemCount > 0) {
                                    val offsetX = with(density) { offsetAnim.value.toDp() } + horizontalInset
                                    
                                    // DYNAMIC GEOMETRY: 18.dp Squircle (Expanded) -> 24.dp Pill (Shrunk)
                                    val indicatorShape = if (isMechanicalMode) {
                                        G2RoundedRectangle(lerp(18.dp, 24.dp, focusFactor))
                                    } else {
                                        RoundedCornerShape(18.dp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(indicatorWidth.coerceAtLeast(0.dp))
                                            .offset(x = offsetX)
                                            .padding(vertical = if (isMechanicalMode) 12.dp else lerp(10.dp, 8.dp, focusFactor), horizontal = 2.dp)
                                            .graphicsLayer { scaleX = scaleXAnim; scaleY = scaleYAnim }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(indicatorShape)
                                                .background(
                                                    if (isMechanicalMode) SolidColor(skin.glowPrimary)
                                                    else Brush.linearGradient(
                                                        listOf(
                                                            skin.glowPrimary.copy(alpha = 0.42f),
                                                            skin.glowSecondary.copy(alpha = 0.38f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp)
                        ) {
                            selectedRoutes.forEach { route ->
                                val isSelected = currentRoute == route
                                val selectionProgress by animateFloatAsState(if (isSelected) 1f else 0f, label = "${route.routeInfo.id}-selection")
                                
                                // SCATTERED GRADIENT: Illuminated text/icon visuals for Aphelion
                                val itemTint = if (isMechanicalMode && isSelected) {
                                    skin.cardOverlayColor
                                } else if (isAphelion && isSelected) {
                                    skin.glowPrimary // Illuminated feel
                                } else {
                                    skin.textPrimary
                                }

                                NavigationBarItem(
                                    alwaysShowLabel = true,
                                    icon = {
                                        Icon(
                                            imageVector = route.routeInfo.icon,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(22.dp + 2.dp * selectionProgress)
                                                .graphicsLayer { 
                                                    alpha = if (isMechanicalMode && isSelected) 1f else (0.65f + 0.35f * selectionProgress)
                                                    translationY = iconTranslationY.toPx()
                                                },
                                            tint = itemTint
                                        )
                                    },
                                    label = {
                                        val label = context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"] ?: ""
                                        val isLong = label.length > 11
                                        Text(
                                            text = label,
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = itemTint.copy(alpha = (0.6f + 0.4f * selectionProgress) * labelAlpha),
                                            maxLines = if (isLong) 2 else 1,
                                            overflow = if (isLong) TextOverflow.Ellipsis else TextOverflow.Clip,
                                            softWrap = isLong,
                                            modifier = (if (isLong) Modifier.widthIn(max = 90.dp).wrapContentWidth(Alignment.CenterHorizontally) else Modifier.wrapContentWidth(Alignment.CenterHorizontally))
                                                .graphicsLayer {
                                                    alpha = labelAlpha
                                                    translationY = (-10 * focusFactor).dp.toPx()
                                                }
                                        )
                                    },
                                    selected = isSelected,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.Transparent, // Managed by manual tint
                                        unselectedIconColor = skin.textPrimary.copy(alpha = 0.72f),
                                        selectedTextColor = Color.Transparent, // Managed by manual color
                                        unselectedTextColor = skin.textPrimary.copy(alpha = 0.72f),
                                        indicatorColor = Color.Transparent
                                    ),
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        route.navigateReset() 
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (openBottomBarCustomization) {
                AestheticDialog(
                    onDismissRequest = { openBottomBarCustomization = false },
                    title = translation["customization.title"] ?: "Navigation Tabs",
                    text = translation["customization.description"] ?: "Select up to 5 tabs to show in the bottom bar.",
                    icon = Icons.Default.Tune,
                    showCloseButton = true,
                    confirmButtonText = context.translation["button.save"] ?: "Save",
                    onConfirm = {
                        saveSelected(selectedTabIds)
                        openBottomBarCustomization = false
                        navController.navigate(navController.currentDestination?.id ?: 0) {
                            popUpTo(0)
                        }
                    },
                    customContent = {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            availableRoutes.forEach { route ->
                                val isSelected = selectedTabIds.contains(route.routeInfo.id)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newIds = selectedTabIds.toMutableList()
                                            if (isSelected) {
                                                if (newIds.size > 1) newIds.remove(route.routeInfo.id)
                                            } else {
                                                if (newIds.size < 5) newIds.add(route.routeInfo.id)
                                            }
                                            selectedTabIds = newIds
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) skin.glowPrimary.copy(alpha = 0.12f) else skin.textPrimary.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, if (isSelected) skin.glowPrimary.copy(alpha = 0.5f) else skin.textPrimary.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = route.routeInfo.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) skin.glowPrimary else skin.textPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"] ?: "",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = skin.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) skin.glowPrimary else skin.textPrimary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun Fab() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        remember(navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry) }?.floatingActionButton?.invoke()
    }
    @Composable fun NavContent(paddingValues: PaddingValues, startDestination: String) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            routes.getRoutes().filter { it.parentRoute == null }.forEach { route ->
                val children = routes.getRoutes().filter { it.parentRoute == route }
                if (children.isEmpty()) {
                    val isSummaryScreen = route.routeInfo.id == Routes.CONFIG_IMPORT_CONFIRMATION_ROUTE ||
                            route.routeInfo.id == Routes.CONFIG_EXPORT_SUMMARY_ROUTE ||
                            route.routeInfo.id == Routes.FRIEND_TRACKER_CONFIG_EXPORT_ROUTE ||
                            route.routeInfo.id == Routes.FRIEND_TRACKER_CONFIG_IMPORT_ROUTE
                    val animatedRoutes = setOf("friend_tracker_catalog", "manage_friend_tracker_repos", "manage_script_repos", "manage_repos")
                    val isAnimatedRoute = animatedRoutes.contains(route.routeInfo.id)
                    composable(
                        route.routeInfo.id,
                        enterTransition = { if (isSummaryScreen) slideInHorizontally { it } else if (isAnimatedRoute) slideInHorizontally { it } else fadeIn(tween(100)) },   
                        exitTransition = { if (isSummaryScreen) slideOutHorizontally { -it } else if (isAnimatedRoute) slideOutHorizontally { -it } else fadeOut(tween(100)) },
                        popEnterTransition = { if (isSummaryScreen) slideInHorizontally { -it } else if (isAnimatedRoute) slideInHorizontally { -it } else fadeIn(tween(100)) },
                        popExitTransition = { if (isSummaryScreen) slideOutHorizontally { it } else if (isAnimatedRoute) slideOutHorizontally { it } else fadeOut(tween(100)) }
                    ) { route.content.invoke(it) }
                    route.customComposables.invoke(this)
                } else {
                    navigation("main_" + route.routeInfo.id, route.routeInfo.id) {
                        composable("main_" + route.routeInfo.id) { route.content.invoke(it) }
                        children.forEach { child ->
                            composable(child.routeInfo.id) { child.content.invoke(it) }
                        }
                        route.customComposables.invoke(this)
                    }
                }
            }
        }
    }

    @Composable fun FloatingActionButton() = Fab()
    @Composable fun Content(paddingValues: PaddingValues, startDestination: String) = NavContent(paddingValues, startDestination)
}
