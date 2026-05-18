package me.eternal.purrfect.core.features.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import kotlin.math.roundToInt

@Composable
fun OperaStorySnapJumpDialog(
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf((currentIndex + 1).toFloat()) }
    val selectedSnap = sliderValue.roundToInt().coerceIn(1, totalCount)
    val skin = LocalPurrfectSkin.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(
                    color = skin.cardOverlayColor.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$selectedSnap",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = skin.textPrimary
                    )
                    Text(
                        text = " / $totalCount",
                        fontSize = 14.sp,
                        color = skin.textSecondary,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..totalCount.toFloat(),
                    steps = if (totalCount > 2) totalCount - 2 else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = skin.glowPrimary,
                        activeTrackColor = skin.glowPrimary,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = skin.textPrimary.copy(alpha = 0.12f),
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = skin.textPrimary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            color = skin.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = skin.glowPrimary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onDismiss()
                                onJump(selectedSnap - 1)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Go",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = skin.cardOverlayColor
                        )
                    }
                }
            }
        }
    }
}
