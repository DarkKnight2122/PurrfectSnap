package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen
import me.eternal.purrfectsnap.ui.util.scaleOnPress

enum class InstallMode {
    ROOT, NON_ROOT
}

class InstallModeScreen(
    private val onModeChosen: (InstallMode) -> Unit,
    private val onSkipAutoSetup: () -> Unit
) : SetupScreen() {
    private var selectedMode by mutableStateOf<InstallMode?>(null)

    @Composable
    private fun ModeItem(
        mode: InstallMode,
        title: String,
        description: String,
        icon: ImageVector
    ) {
        val isSelected = selectedMode == mode
        val interactionSource = remember { MutableInteractionSource() }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scaleOnPress(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    selectedMode = mode
                    onModeChosen(mode)
                },
            shape = RoundedCornerShape(22.dp),
            color = if (isSelected) glowPrimary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
            border = BorderStroke(
                1.dp,
                if (isSelected) glowPrimary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = if (isSelected) 12.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) glowPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = description, color = textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }

                if (isSelected) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = glowSecondary)
                }
            }
        }
    }

    @Composable
    override fun Content() {
        LaunchedEffect(selectedMode) {
            allowNext(selectedMode != null)
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.install_mode_title"] ?: "Select Environment",
                subtitle = context.translation["setup.activity.install_mode_subtitle"] ?: "How should we integrate with Snapchat?",
                textAlign = TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeItem(
                    mode = InstallMode.ROOT,
                    title = context.translation["setup.activity.mode_root_title"] ?: "Direct Injection (Root)",
                    description = context.translation["setup.activity.mode_root_desc"] ?: "Automatic patching via system access. Recommended for experts.",
                    icon = Icons.Default.Memory
                )

                ModeItem(
                    mode = InstallMode.NON_ROOT,
                    title = context.translation["setup.activity.mode_non_root_title"] ?: "External Patching (LSPatch)",
                    description = context.translation["setup.activity.mode_non_root_desc"] ?: "Requires re-installing a modified Snapchat APK.",
                    icon = Icons.Default.Laptop
                )
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onSkipAutoSetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.translation["setup.activity.skip_setup_button"] ?: "I'll handle setup manually",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
