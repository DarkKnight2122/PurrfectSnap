package me.eternal.purrfect.ui.setup.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfect.RemoteSideContext
import me.eternal.purrfect.ui.manager.theme.PurrfectPalette
import kotlin.math.roundToInt

internal val LocalSetupScrollState = compositionLocalOf<ScrollState?> { null }
internal val LocalSetupViewportHeight = compositionLocalOf<Dp?> { null }

abstract class SetupScreen {
    lateinit var context: RemoteSideContext
    lateinit var allowNext: (canGoNext: Boolean) -> Unit
    lateinit var goNext: () -> Unit
    lateinit var route: String
    var isFirstRunFlow: Boolean = false

    @Composable
    fun DialogText(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = PurrfectPalette.textSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).then(modifier)
        )
    }

    @Composable
    fun StepTitle(
        title: String,
        subtitle: String? = null,
        modifier: Modifier = Modifier,
        textAlign: TextAlign = TextAlign.Start
    ) {
        val horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
        androidx.compose.foundation.layout.Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PurrfectPalette.textPrimary,
                textAlign = textAlign
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurrfectPalette.textSecondary,
                    lineHeight = 18.sp,
                    textAlign = textAlign
                )
            }
        }
    }

    @Composable
    fun SetupCard(
        modifier: Modifier = Modifier,
        setupScrollEnabled: Boolean = true,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val scrollState = if (setupScrollEnabled) LocalSetupScrollState.current else null
        val viewportHeight = if (setupScrollEnabled) LocalSetupViewportHeight.current else null
        val hasScrollbar = (scrollState?.maxValue ?: 0) > 0
        val heightModifier = viewportHeight?.let { Modifier.heightIn(max = it) } ?: Modifier
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(heightModifier),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PurrfectPalette.glowPrimary.copy(alpha = 0.42f),
                        PurrfectPalette.glowSecondary.copy(alpha = 0.32f)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PurrfectPalette.cardOverlay)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(scrollState?.let { Modifier.verticalScroll(it) } ?: Modifier)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .padding(end = if (hasScrollbar) 20.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        PurrfectPalette.glowSecondary.copy(alpha = 0.4f),
                                        PurrfectPalette.glowPrimary.copy(alpha = 0.4f)
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    content()
                }

                scrollState?.let {
                    SetupCardScrollbar(
                        scrollState = it,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 24.dp, end = 6.dp, bottom = 24.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }

    open fun init() {}
    open fun onLeave() {}
    open fun onNext(navigate: () -> Unit) { navigate() }

    @Composable
    abstract fun Content()
}

@Composable
private fun SetupCardScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return

    BoxWithConstraints(
        modifier = modifier
            .width(12.dp)
            .fillMaxHeight()
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val minThumbHeightPx = with(density) { 58.dp.toPx() }.coerceAtMost(trackHeightPx)
        val viewportHeightPx = trackHeightPx.coerceAtLeast(1f)
        val contentHeightPx = viewportHeightPx + maxScroll.toFloat()
        val thumbHeightPx = (trackHeightPx * (viewportHeightPx / contentHeightPx))
            .coerceIn(minThumbHeightPx, trackHeightPx)
        val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = maxThumbOffsetPx * (scrollState.value.toFloat() / maxScroll.toFloat())

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                .width(5.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PurrfectPalette.glowSecondary,
                            PurrfectPalette.glowPrimary
                        )
                    ),
                    CircleShape
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = CircleShape
                )
        )
    }
}
