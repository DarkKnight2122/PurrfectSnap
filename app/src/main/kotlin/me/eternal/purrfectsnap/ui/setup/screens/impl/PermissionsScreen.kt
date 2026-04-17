package me.eternal.purrfectsnap.ui.setup.screens.impl

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.eternal.purrfectsnap.common.Constants
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen

class PermissionsScreen : SetupScreen() {
    @Composable
    private fun PermissionItem(
        title: String,
        subtitle: String,
        isGranted: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(glowPrimary.copy(alpha = 0.45f), glowSecondary.copy(alpha = 0.35f)))),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.16f) else Color(0xFFFF5252).copy(alpha = 0.16f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF5252),
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isGranted) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = glowPrimary.copy(alpha = 0.28f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(context.translation["setup.permissions.grant_button"] ?: "Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val activity = LocalContext.current as Activity
        var batteryOptimizationsGranted by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    (activity.getSystemService(PowerManager::class.java))?.isIgnoringBatteryOptimizations(Constants.SNAPCHAT_PACKAGE_NAME) == true
                } else true
            )
        }
        var overlayPermissionGranted by remember {
            mutableStateOf(Settings.canDrawOverlays(activity))
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    batteryOptimizationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        (activity.getSystemService(PowerManager::class.java))?.isIgnoringBatteryOptimizations(Constants.SNAPCHAT_PACKAGE_NAME) == true
                    } else true
                    overlayPermissionGranted = Settings.canDrawOverlays(activity)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(batteryOptimizationsGranted, overlayPermissionGranted) {
            allowNext(batteryOptimizationsGranted && overlayPermissionGranted)
        }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.permissions_title"] ?: "Grant Permissions",
                subtitle = context.translation["setup.activity.permissions_subtitle"] ?: "Essential for background services",
                textAlign = TextAlign.Center
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionItem(
                    title = context.translation["setup.permissions.overlay_title"] ?: "Overlay Permission",
                    subtitle = context.translation["setup.permissions.overlay_subtitle"] ?: "Required for heads-up UI",
                    isGranted = overlayPermissionGranted,
                    onClick = {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PermissionItem(
                        title = context.translation["setup.permissions.battery_title"] ?: "Battery Optimization",
                        subtitle = context.translation["setup.permissions.battery_subtitle"] ?: "Ignore restrictions",
                        isGranted = batteryOptimizationsGranted,
                        onClick = {
                            runCatching {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${Constants.SNAPCHAT_PACKAGE_NAME}")
                                    )
                                )
                            }.onFailure {
                                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    )
                }
            }
        }
    }
}
