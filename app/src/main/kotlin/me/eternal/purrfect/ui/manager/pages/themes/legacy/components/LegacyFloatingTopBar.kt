package me.eternal.purrfect.ui.manager.pages.themes.legacy.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.eternal.purrfect.common.ui.theme.PurrfectPalette
import androidx.compose.ui.platform.LocalContext
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin

@Composable
fun LegacyFloatingTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onHeightMeasured: (androidx.compose.ui.unit.Dp) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val skin = LocalPurrfectSkin.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shape = RoundedCornerShape(26.dp)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .zIndex(10f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    // Measure the actual visible height of the header card
                    onHeightMeasured(with(density) { coordinates.size.height.toDp() })
                },
            shape = shape,
            color = skin.cardOverlayColor,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        skin.glowPrimary.copy(alpha = 0.6f),
                        skin.glowSecondary.copy(alpha = 0.52f)
                    )
                )
            ),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(skin.cardOverlay)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = skin.textPrimary
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = skin.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                color = skin.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        content = actions
                    )
                }
            }
        }
    }
}
