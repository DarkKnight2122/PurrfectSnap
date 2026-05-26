package me.eternal.purrfect.core.features.impl.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
@Composable
fun OperaStorySnapJumpDialog(
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf((currentIndex + 1).toFloat()) }
    val selectedSnap = sliderValue.roundToInt()

    Dialog(onDismissRequest = onDismiss) {
        val colorScheme = MaterialTheme.colorScheme

        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(
                    color = colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$selectedSnap",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = " / $totalCount",
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
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
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        activeTickColor = Color.Transparent,
                        inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.12f),
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = colorScheme.primary,
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
                            color = colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
