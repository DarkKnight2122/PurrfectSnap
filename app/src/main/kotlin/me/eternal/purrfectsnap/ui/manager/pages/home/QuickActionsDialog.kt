package me.eternal.purrfectsnap.ui.manager.pages.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.eternal.purrfectsnap.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import me.eternal.purrfectsnap.ui.util.purrfectSwitchColors
import androidx.compose.ui.window.Dialog

internal object QuickActionsSkinPalette {
    @Composable
    private fun isAphelion(): Boolean {
        val context = LocalContext.current
        return remember(context) { 
            me.eternal.purrfectsnap.SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get() == "APHELION"
        }
    }

    val glowPrimary: Color @Composable get() = LocalPurrfectSkin.current.glowPrimary
    val glowSecondary: Color @Composable get() = LocalPurrfectSkin.current.glowSecondary
    val backgroundGradient: Brush @Composable get() = LocalPurrfectSkin.current.backgroundGradient
    val cardOverlay: Brush @Composable get() = LocalPurrfectSkin.current.cardOverlay
    val textPrimary: Color @Composable get() = LocalPurrfectSkin.current.textPrimary
    val textSecondary: Color @Composable get() = LocalPurrfectSkin.current.textSecondary
    val cardOverlayColor: Color @Composable get() = LocalPurrfectSkin.current.cardOverlayColor
    val panelGradient: Brush @Composable get() = LocalPurrfectSkin.current.cardOverlay
}


@Composable
fun QuickActionsDialog(
    quickActions: Map<Pair<String, ImageVector>, Any>,
    selectedQuickActions: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    translation: LocaleWrapper
) {
    val selected = remember { mutableStateListOf(*selectedQuickActions.toTypedArray()) }

    Dialog(onDismissRequest = onDismiss) {
        val dialogShape = RoundedCornerShape(24.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = dialogShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        QuickActionsSkinPalette.glowPrimary.copy(alpha = 0.55f),
                        QuickActionsSkinPalette.glowSecondary.copy(alpha = 0.45f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .background(QuickActionsSkinPalette.cardOverlay, dialogShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        QuickActionsSkinPalette.glowPrimary.copy(alpha = 0.4f),
                                        QuickActionsSkinPalette.glowSecondary.copy(alpha = 0.35f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = QuickActionsSkinPalette.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = translation["manager.dialogs.quick_actions_dialog.title"],
                            style = MaterialTheme.typography.titleLarge,
                            color = QuickActionsSkinPalette.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = translation["manager.dialogs.quick_actions_dialog.subtitle"],
                            style = MaterialTheme.typography.bodyMedium,
                            color = QuickActionsSkinPalette.textSecondary
                        )
                    }
                }

                quickActions.keys.forEach { (name, icon) ->
                    val isSelected = selected.contains(name)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selected.remove(name) else selected.add(name)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = QuickActionsSkinPalette.cardOverlayColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, QuickActionsSkinPalette.textPrimary.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = QuickActionsSkinPalette.glowPrimary.copy(alpha = 0.18f),
                                tonalElevation = 0.dp
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    modifier = Modifier.padding(10.dp),
                                    tint = QuickActionsSkinPalette.textPrimary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    name,
                                    color = QuickActionsSkinPalette.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSelected) translation["enabled"] else translation["disabled"],
                                    color = QuickActionsSkinPalette.textSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Switch(
                                checked = isSelected,
                                onCheckedChange = { toggled ->
                                    if (toggled) selected.add(name) else selected.remove(name)
                                },
                                colors = purrfectSwitchColors()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(translation["button.cancel"], color = QuickActionsSkinPalette.textSecondary)
                    }
                    Button(
                        onClick = { onSave(selected.toList()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = QuickActionsSkinPalette.glowPrimary.copy(alpha = 0.35f),
                            contentColor = QuickActionsSkinPalette.textPrimary
                        )
                    ) {
                        Text(translation["button.save"])
                    }
                }
            }
        }
    }
}
