package me.eternal.purrfectsnap.ui.manager.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin
import org.intellij.lang.annotations.Language

/**
 * Premium components for the Aether skin, implementing Material 3 Expressive logic.
 */
object AetherComponents {

    @Language("AGSL")
    private const val WAVY_SHADER = """
        uniform float2 size;
        uniform float time;
        uniform float progress;
        layout(color) uniform half4 color;

        half4 main(float2 coord) {
            float x = coord.x / size.x;
            if (x > progress) return half4(color.rgb, 0.1);

            // Sine wave modulation
            float wave = sin(x * 12.0 - time * 4.0) * 0.5 + 0.5;
            float alpha = 0.6 + wave * 0.4;
            
            return half4(color.rgb, alpha * color.a);
        }
    """

    @Composable
    fun WavyProgress(
        progress: Float,
        modifier: Modifier = Modifier,
        color: Color = LocalPurrfectSkin.current.glowPrimary
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val infiniteTransition = rememberInfiniteTransition(label = "wavy")
            val time by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 6.28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "time"
            )

            val shader = remember { RuntimeShader(WAVY_SHADER) }
            val brush = remember(shader) { ShaderBrush(shader) }

            Canvas(modifier = modifier.height(10.dp).fillMaxWidth()) {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("progress", progress)
                shader.setColorUniform("color", color.toArgb())
                
                // Track
                drawRoundRect(
                    color = color.copy(alpha = 0.12f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
                
                // Animated Wave
                drawRoundRect(
                    brush = brush,
                    size = androidx.compose.ui.geometry.Size(size.width * progress, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
        } else {
            // Fallback for older Android versions
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = modifier.height(8.dp).fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}
