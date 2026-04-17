package me.eternal.purrfectsnap.ui.setup.screens.impl

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen

class PatchSnapchatScreen : SetupScreen() {
    private var isPatching by mutableStateOf(false)
    private var patchProgress by mutableFloatStateOf(0f)
    private var patchStatus by mutableStateOf("")
    private var isPatched by mutableStateOf(false)

    @Composable
    private fun StatusItem(
        label: String,
        status: String,
        isCompleted: Boolean,
        isLoading: Boolean = false
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = glowSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = if (isCompleted) Color(0xFF4CAF50).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (isCompleted) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = status, color = textSecondary, fontSize = 11.sp)
                }
            }
        }
    }

    @Composable
    override fun Content() {
        LaunchedEffect(isPatched) {
            allowNext(isPatched)
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.patch_title"] ?: "Patch Snapchat",
                subtitle = context.translation["setup.activity.patch_subtitle"] ?: "Integrating hooks into the official app",
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(glowPrimary.copy(alpha = 0.1f))
                    .border(2.dp, Brush.sweepGradient(listOf(glowPrimary, glowSecondary, glowPrimary)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isPatching) {
                    CircularProgressIndicator(
                        progress = { patchProgress },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        color = glowSecondary,
                        strokeWidth = 4.dp,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                } else {
                    Icon(
                        imageVector = if (isPatched) Icons.Default.Check else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = if (isPatching) "${(patchProgress * 100).toInt()}%" else if (isPatched) "Patched!" else "Ready",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusItem(
                    label = "Core Engine",
                    status = if (isPatched) "Ready" else patchStatus.ifBlank { "Awaiting start" },
                    isCompleted = isPatched,
                    isLoading = isPatching
                )
            }

            if (!isPatched && !isPatching) {
                Button(
                    onClick = {
                        isPatching = true
                        patchStatus = "Initializing..."
                        context.coroutineScope.launch(Dispatchers.IO) {
                            delay(1000)
                            patchStatus = "Injecting hooks..."
                            patchProgress = 0.5f
                            delay(1500)
                            patchProgress = 1.0f
                            isPatching = false
                            isPatched = true
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
                    Text(context.translation["setup.activity.start_patch_button"] ?: "Start Patching", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
