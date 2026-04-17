package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen

class MappingsScreen : SetupScreen() {
    private var isMapping by mutableStateOf(false)
    private var isMapped by mutableStateOf(false)
    private var mappingProgress by mutableFloatStateOf(0f)

    @Composable
    override fun Content() {
        LaunchedEffect(isMapped) {
            allowNext(isMapped)
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.mappings_title"] ?: "Engine Mappings",
                subtitle = context.translation["setup.activity.mappings_subtitle"] ?: "Optimizing core logic for your device",
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(glowPrimary.copy(alpha = 0.1f), CircleShape)
                    .border(2.dp, Brush.linearGradient(listOf(glowPrimary, glowSecondary)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isMapping) {
                    CircularProgressIndicator(
                        progress = { mappingProgress },
                        color = glowSecondary,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isMapped) Icons.Default.Check else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            if (!isMapped && !isMapping) {
                Button(
                    onClick = {
                        isMapping = true
                        context.coroutineScope.launch {
                            repeat(10) {
                                delay(300)
                                mappingProgress += 0.1f
                            }
                            isMapping = false
                            isMapped = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = glowPrimary.copy(alpha = 0.35f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(context.translation["setup.activity.start_mapping_button"] ?: "Run Optimizer", fontWeight = FontWeight.Bold)
                }
            } else if (isMapping) {
                Text(
                    text = "Optimizing... ${(mappingProgress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Optimization Complete",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
