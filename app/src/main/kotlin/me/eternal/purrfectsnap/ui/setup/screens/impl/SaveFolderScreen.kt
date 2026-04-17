package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen
import me.eternal.purrfectsnap.ui.util.ActivityLauncherHelper
import me.eternal.purrfectsnap.ui.util.chooseFolder

class SaveFolderScreen : SetupScreen() {
    private var selectedPath by mutableStateOf<String?>(null)

    @Composable
    override fun Content() {
        val activity = LocalContext.current as ComponentActivity
        val helper = remember { ActivityLauncherHelper(activity) }

        LaunchedEffect(selectedPath) {
            allowNext(!selectedPath.isNullOrBlank())
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.dialogs.save_folder"] ?: "Media Storage",
                subtitle = context.translation["setup.activity.save_folder_subtitle"] ?: "Choose where your snaps will be saved",
                textAlign = TextAlign.Center
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Brush.linearGradient(listOf(glowPrimary.copy(alpha = 0.5f), glowSecondary.copy(alpha = 0.4f)))),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            helper.chooseFolder { uri ->
                                if (uri.isEmpty()) return@chooseFolder
                                selectedPath = uri
                                context.config.root.downloader.saveFolder.set(uri)
                                context.config.writeConfig()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = glowPrimary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = if (selectedPath == null) Icons.Default.Folder else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedPath == null) "No Folder Selected" else "Selected Storage",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = selectedPath ?: "Click to pick a directory",
                            color = textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (selectedPath != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Access Granted",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
