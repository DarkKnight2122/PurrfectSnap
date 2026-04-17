package me.eternal.purrfectsnap.ui.manager.pages.themes.legacy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The persistent vertical gradient background used across all screens in the Legacy (v1.3.9) theme.
 */
@Composable
fun LegacyBackground(modifier: Modifier = Modifier) {
    val pageBackgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF261F58),
            Color(0xFF302A6D),
            Color(0xFF241F52)
        )
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackgroundGradient)
    )
}
