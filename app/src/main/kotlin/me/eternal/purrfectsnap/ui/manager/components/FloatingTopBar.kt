package me.eternal.purrfectsnap.ui.manager.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import me.eternal.purrfectsnap.ui.util.PurrfectMarqueeText
import me.eternal.purrfectsnap.ui.util.Motion
import me.eternal.purrfectsnap.ui.util.headerHeightTracker

import me.eternal.purrfectsnap.ui.manager.theme.aetherGlass

/**
 * Unified Floating Top Bar for Aphelion.
 * Handles the signature morphing animation and provides a "Bottom Content" slot.
 * 
 * Literal Restoration from 'dev' branch with Skin Mapping.
 */
@Composable
fun FloatingTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    scrollOffset: Int = 0,
    enableMorph: Boolean = false,
    containerAlpha: Float = 1f,
    titleAlignment: Alignment.Horizontal = Alignment.Start,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable ColumnScope.(Float) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val context = LocalContext.current
    val managerTheme by produceState(
        initialValue = me.eternal.purrfectsnap.SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get()
    ) {
        while (true) {
            kotlinx.coroutines.delay(250)
            value = me.eternal.purrfectsnap.SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get()
        }
    }
    val isAphelion = managerTheme == "APHELION"
    val skin = if (isAphelion) LocalPurrfectSkin.current else me.eternal.purrfectsnap.common.ui.theme.PurrfectPalette

    val focusFactor by remember(scrollOffset, enableMorph) {
        derivedStateOf {
            if (!enableMorph) 0f
            else (scrollOffset.toFloat() / Motion.HEADER_MORPH_THRESHOLD).coerceIn(0f, 1f)
        }
    }

    // Spring-based physics for Aether
    val springFocusFactor by animateFloatAsState(
        targetValue = focusFactor,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "aetherSpring"
    )

    val activeFocus = if (skin.id == "AETHER") springFocusFactor else focusFactor

    val morphingParams by remember(activeFocus, statusBarHeight) {
        derivedStateOf {
            object {
                val headerHeight = lerp(64.dp, 56.dp, activeFocus)
                val sidePadding = lerp(14.dp, 0.dp, activeFocus)
                val containerTopPadding = lerp(statusBarHeight + 4.dp, 0.dp, activeFocus)
                val internalTopPadding = lerp(0.dp, statusBarHeight, activeFocus)
                val internalVerticalPadding = lerp(8.dp, 0.dp, activeFocus)
                val topCorners = lerp(26.dp, 0.dp, activeFocus)
                val bottomCorners = lerp(26.dp, 28.dp, activeFocus)
                val subtitleAlpha = (1f - (activeFocus * 2.5f)).coerceIn(0f, 1f)
                val subtitleTranslationY = lerp(0.dp, (-10).dp, activeFocus)
                val iconScale = 1f - (0.12f * activeFocus)
                val horizontalShift = (6 * activeFocus).dp
            }
        }
    }

    var hasSnapped by remember { mutableStateOf(false) }
    LaunchedEffect(activeFocus) {
        if (activeFocus >= 1f && !hasSnapped && scrollOffset > 10) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasSnapped = true
        } else if (activeFocus < 0.5f) {
            hasSnapped = false
        }
    }

    val shape = remember(morphingParams.topCorners, morphingParams.bottomCorners) {
        RoundedCornerShape(
            topStart = morphingParams.topCorners,
            topEnd = morphingParams.topCorners,
            bottomStart = morphingParams.bottomCorners,
            bottomEnd = morphingParams.bottomCorners
        )
    }

    val borderPath = remember { Path() }
    val uPath = remember { Path() }
    val refractiveColor = skin.refractiveColor

    Box(modifier = modifier.fillMaxWidth().zIndex(10f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = morphingParams.sidePadding)
                .padding(top = morphingParams.containerTopPadding)
                .height(morphingParams.internalTopPadding + morphingParams.headerHeight + 32.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to refractiveColor.copy(alpha = 0.95f * activeFocus),
                        0.6f to refractiveColor.copy(alpha = 0.85f * activeFocus),
                        1.0f to Color.Transparent
                    )
                )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = morphingParams.sidePadding)
                .padding(top = morphingParams.containerTopPadding)
                .graphicsLayer {
                    alpha = containerAlpha
                }
                .then(
                    if (skin.id == "AETHER") {
                        Modifier.aetherGlass(
                            skin = skin,
                            topStart = morphingParams.topCorners,
                            topEnd = morphingParams.topCorners,
                            bottomStart = morphingParams.bottomCorners,
                            bottomEnd = morphingParams.bottomCorners,
                            focusFactor = activeFocus
                        )
                    } else Modifier
                ),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = (4 * activeFocus).dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (skin.id != "AETHER") {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        skin.cardOverlayColor.copy(alpha = 0.85f + (0.1f * activeFocus)),
                                        refractiveColor.copy(alpha = 0.85f + (0.1f * activeFocus))
                                    )
                                )
                            )
                        } else Modifier
                    )
                    .drawBehind {
                        if (skin.id == "AETHER") return@drawBehind
                        val strokeWidth = 1.dp.toPx()
                        val brush = Brush.linearGradient(listOf(skin.glowPrimary.copy(alpha = 0.6f), skin.glowSecondary.copy(alpha = 0.4f)))
                        val tr = morphingParams.topCorners.toPx()
                        val br = morphingParams.bottomCorners.toPx()

                        if (activeFocus > 0.9f) {
                            uPath.reset()
                            uPath.apply {
                                moveTo(0f, 0f)
                                lineTo(0f, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2*br, 2*br, size.height), 180f, -90f, false)
                                lineTo(size.width - br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2*br, size.height - 2*br, size.width, size.height), 90f, -90f, false)
                                lineTo(size.width, 0f)
                            }
                            drawPath(uPath, brush, style = Stroke(strokeWidth))
                        } else {
                            borderPath.reset()
                            borderPath.apply {
                                moveTo(tr, 0f)
                                lineTo(size.width - tr, 0f)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2*tr, 0f, size.width, 2*tr), 270f, 90f, false)
                                lineTo(size.width, size.height - br)
                                arcTo(androidx.compose.ui.geometry.Rect(size.width - 2*br, size.height - 2*br, size.width, size.height), 0f, 90f, false)
                                lineTo(br, size.height)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, size.height - 2*br, 2*br, size.height), 90f, 90f, false)
                                lineTo(0f, tr)
                                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * tr, 2 * tr), 180f, 90f, false)
                            }
                            drawPath(borderPath, brush, style = Stroke(strokeWidth))
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = morphingParams.internalTopPadding)
                            .padding(horizontal = 16.dp, vertical = morphingParams.internalVerticalPadding)
                            .height(morphingParams.headerHeight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBack()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .graphicsLayer {
                                        scaleX = morphingParams.iconScale
                                        scaleY = morphingParams.iconScale
                                        translationX = -morphingParams.horizontalShift.toPx()
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = skin.textPrimary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = titleAlignment
                        ) {
                            Text(
                                text = title,
                                color = skin.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = if (titleAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!subtitle.isNullOrBlank() && morphingParams.subtitleAlpha > 0.01f) {
                                PurrfectMarqueeText(
                                    text = subtitle,
                                    color = skin.textSecondary.copy(alpha = morphingParams.subtitleAlpha),
                                    style = TextStyle(fontSize = 13.sp),
                                    textAlign = if (titleAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start,
                                    contentAlignment = if (titleAlignment == Alignment.CenterHorizontally) Alignment.Center else Alignment.CenterStart,
                                    enabled = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            translationY = morphingParams.subtitleTranslationY.toPx()
                                            alpha = morphingParams.subtitleAlpha
                                        }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .graphicsLayer {
                                    scaleX = morphingParams.iconScale
                                    scaleY = morphingParams.iconScale
                                    if (onBack != null) {
                                        translationX = morphingParams.horizontalShift.toPx()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            actions()
                        }
                    }

                    bottomContent(focusFactor)
                }
            }
        }
    }
}
