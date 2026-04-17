package me.eternal.purrfectsnap.ui.manager.pages.home.themes.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin

import androidx.compose.foundation.rememberScrollState

/**
 * Skin picker displayed in the Aphelion section of HomeSettings.
 * Only shown when Aphelion layout is active.
 *
 * Shows five options: Umbra, Nox, Lux, Lumina, Iris.
 *
 * @param currentSkinId  The currently active skin ID from config.
 * @param onSkinSelected Callback with the new skin ID when the user selects one.
 */
@Composable
fun AphelionSkinPicker(
    currentSkinId: String,
    onSkinSelected: (String) -> Unit
) {
    val skin = LocalPurrfectSkin.current
    val scrollState = rememberScrollState()

    val skins = listOf(
        SkinOption(
            id = "UMBRA",
            name = "Umbra",
            description = "Deep space nebula",
            previewColors = listOf(Color(0xFF261F58), Color(0xFF302A6D), Color(0xFF8C7BFF)),
            available = true
        ),
        SkinOption(
            id = "NOX",
            name = "Nox",
            description = "Pure AMOLED black",
            previewColors = listOf(Color.Black, Color(0xFF0D0D0D), Color(0xFF8C7BFF)),
            available = true
        ),
        SkinOption(
            id = "LUX",
            name = "Lux",
            description = "White frosted glass",
            previewColors = listOf(Color(0xFFFAFAFF), Color(0xFFF0F0F8), Color(0xFF7B4FDF)),
            available = true
        ),
        SkinOption(
            id = "LUMINA",
            name = "Lumina",
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Lux with wallpaper colors"
            else
                "Requires Android 12+",
            previewColors = listOf(Color(0xFFFAFAFF), Color(0xFFF0F0F8), Color(0xFF7B4FDF)),
            available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ),
        SkinOption(
            id = "AETHER",
            name = "Aether",
            description = "Solid Material 3 Expressive",
            previewColors = listOf(Color(0xFFCA9EE6), Color(0xFF85C1DC), Color(0xFFF4B8E4)),
            available = true
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "Aphelion Skin",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (skin.isDark) Color.White else Color(0xFF1A1A2E)
            )

            Text(
                text = "Choose the color theme for your Aphelion experience.",
                fontSize = 12.sp,
                color = skin.textSecondary,
                lineHeight = 16.sp
            )
        }

        // Horizontally scrollable row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            skins.forEach { option ->
                SkinCard(
                    option = option,
                    isSelected = currentSkinId == option.id,
                    onSelect = { if (option.available) onSkinSelected(option.id) },
                    modifier = Modifier.width(160.dp) // Fixed width for uniformity
                )
            }
            // End padding spacer
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun SkinCard(
    option: SkinOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalPurrfectSkin.current

    val borderBrush = if (isSelected) {
        Brush.linearGradient(
            listOf(skin.glowPrimary, skin.glowSecondary)
        )
    } else {
        Brush.linearGradient(
            listOf(
                skin.glassBorder,
                skin.glassBorder
            )
        )
    }

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) skin.glowPrimary.copy(alpha = 0.12f) else skin.glassSurface,
        animationSpec = tween(220),
        label = "skin_card_bg"
    )

    val unavailableAlpha = if (!option.available) 0.4f else 1f

    Surface(
        modifier = modifier
            .clickable(enabled = option.available) { onSelect() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Color preview dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.alpha(unavailableAlpha)
            ) {
                option.previewColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier.alpha(unavailableAlpha),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = option.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (skin.isDark) Color.White else Color(0xFF1A1A2E)
                    )
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = skin.glowPrimary.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = skin.glowPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // API badge for Lumina on older devices
                    if (!option.available) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB347).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Android 12+",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFB347),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = option.description,
                    fontSize = 11.sp,
                    color = skin.textSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private data class SkinOption(
    val id: String,
    val name: String,
    val description: String,
    val previewColors: List<Color>,
    val available: Boolean
)
