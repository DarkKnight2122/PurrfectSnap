package me.eternal.purrfectsnap.ui.manager.theme

import android.os.Build
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.eternal.purrfectsnap.common.ui.theme.PurrfectColorSet
import me.eternal.purrfectsnap.common.ui.theme.AetherShaders

fun Modifier.liquidBlur(
    radius: Dp = 25.dp,
    vibrancyFactor: Float = 1.0f
): Modifier = this // Simplified for now to prevent build complexity

fun Modifier.aetherGlass(
    skin: PurrfectColorSet,
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    focusFactor: Float = 1.0f
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    this.drawWithCache {
        val dispersionShader = RuntimeShader(AetherShaders.DISPERSION)
        val highlightShader = RuntimeShader(AetherShaders.HIGHLIGHT)
        
        val dispersionBrush = ShaderBrush(dispersionShader)
        val highlightBrush = ShaderBrush(highlightShader)

        onDrawWithContent {
            val tr = topStart.toPx()
            val br = bottomStart.toPx()
            
            // Background Layer: Legibility Gradient (Opaque center, transparent edges for refraction)
            val legibilityGradient = Brush.radialGradient(
                colors = listOf(
                    skin.cardOverlayColor.copy(alpha = 0.92f * focusFactor),
                    skin.cardOverlayColor.copy(alpha = 0.35f * focusFactor)
                ),
                center = center,
                radius = size.maxDimension * 0.7f
            )
            drawRect(legibilityGradient)
            
            // Specular Highlight Layer
            if (focusFactor > 0.1f) {
                drawRect(highlightBrush, alpha = skin.specularAlpha * focusFactor)
            }
            
            // Refraction Layer
            if (focusFactor > 0.1f) {
                dispersionShader.setFloatUniform("refractionAmount", 15f * focusFactor)
                drawRect(dispersionBrush)
            }
            
            drawContent()
        }
    }
} else {
    this.background(skin.cardOverlayColor.copy(alpha = 0.85f * focusFactor))
}
