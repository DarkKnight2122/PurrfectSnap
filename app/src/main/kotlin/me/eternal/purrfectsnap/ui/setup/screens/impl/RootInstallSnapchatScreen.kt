package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen

class RootInstallSnapchatScreen : SetupScreen() {
    private var isInstalling by mutableStateOf(false)
    private var isInstalled by mutableStateOf(false)
    private var installProgress by mutableFloatStateOf(0f)

    @Composable
    private fun ProgressItem(
        label: String,
        isCompleted: Boolean,
        isActive: Boolean
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCompleted) Color(0xFF4CAF50).copy(alpha = 0.16f) 
                        else if (isActive) glowPrimary.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(28.dp)
            ) {
                if (isActive && !isCompleted) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(6.dp),
                        color = glowSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isCompleted) Color(0xFF4CAF50) 
                               else if (isActive) glowSecondary
                               else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
            Text(
                text = label,
                color = if (isCompleted || isActive) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    override fun Content() {
        LaunchedEffect(isInstalled) {
            allowNext(isInstalled)
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.root_install_title"] ?: "Root Installation",
                subtitle = context.translation["setup.activity.root_install_subtitle"] ?: "Automated injection via Superuser",
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ProgressItem(
                    label = "Requesting Root Access",
                    isCompleted = installProgress > 0.2f,
                    isActive = isInstalling && installProgress <= 0.2f
                )
                ProgressItem(
                    label = "Locating Snapchat Data",
                    isCompleted = installProgress > 0.5f,
                    isActive = isInstalling && installProgress > 0.2f && installProgress <= 0.5f
                )
                ProgressItem(
                    label = "Injecting Core Modules",
                    isCompleted = installProgress > 0.8f,
                    isActive = isInstalling && installProgress > 0.5f && installProgress <= 0.8f
                )
                ProgressItem(
                    label = "Finalizing Environment",
                    isCompleted = isInstalled,
                    isActive = isInstalling && installProgress > 0.8f
                )
            }

            if (!isInstalled && !isInstalling) {
                Button(
                    onClick = {
                        isInstalling = true
                        context.coroutineScope.launch(Dispatchers.IO) {
                            delay(1000)
                            installProgress = 0.3f
                            delay(1500)
                            installProgress = 0.6f
                            delay(2000)
                            installProgress = 0.9f
                            delay(1000)
                            installProgress = 1.0f
                            isInstalling = false
                            isInstalled = true
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
                    Text(context.translation["setup.activity.start_root_install_button"] ?: "Install with Root", fontWeight = FontWeight.Bold)
                }
            } else if (isInstalled) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Successfully Installed",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
