package me.eternal.purrfectsnap.ui.setup.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfectsnap.RemoteSideContext
import me.eternal.purrfectsnap.SharedContextHolder
import me.eternal.purrfectsnap.common.ui.theme.LocalPurrfectSkin

abstract class SetupScreen {
    lateinit var context: RemoteSideContext
    lateinit var allowNext: (canGoNext: Boolean) -> Unit
    lateinit var goNext: () -> Unit
    lateinit var route: String
    var isFirstRunFlow: Boolean = false

    @Composable
    private fun isAphelion(): Boolean {
        val context = LocalContext.current
        return remember(context) { 
            SharedContextHolder.remote(context).config.root.global.uiSettings.managerTheme.get() == "APHELION"
        }
    }

    protected val glowPrimary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.glowPrimary else Color(0xFF8C7BFF)
    protected val glowSecondary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.glowSecondary else Color(0xFF5FD8FF)
    protected val textSecondary: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.textSecondary else Color(0xFFD9D3FF)
    protected val cardOverlayColor: Color @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.cardOverlayColor else Color(0xFF1B152E)
    protected val cardOverlay: Brush @Composable get() = if (isAphelion()) LocalPurrfectSkin.current.cardOverlay else SolidColor(Color(0xFF1B152E))

    @Composable
    fun DialogText(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).then(modifier)
        )
    }

    @Composable
    fun StepTitle(
        title: String,
        subtitle: String? = null,
        modifier: Modifier = Modifier,
        textAlign: TextAlign = TextAlign.Start
    ) {
        val horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
        androidx.compose.foundation.layout.Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = textAlign
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary,
                    lineHeight = 18.sp,
                    textAlign = textAlign
                )
            }
        }
    }

    @Composable
    fun SetupCard(
        modifier: Modifier = Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val glowPrimary = this.glowPrimary
        val glowSecondary = this.glowSecondary
        val cardOverlay = this.cardOverlay

        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.04f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        glowPrimary.copy(alpha = 0.42f),
                        glowSecondary.copy(alpha = 0.32f)
                    )
                )
            )
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .background(cardOverlay)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    glowSecondary.copy(alpha = 0.4f),
                                    glowPrimary.copy(alpha = 0.4f)
                                )
                            ),
                            shape = RoundedCornerShape(50)
                        )
                )
                content()
            }
        }
    }

    open fun init() {}
    open fun onLeave() {}
    open fun onNext(navigate: () -> Unit) { navigate() }

    @Composable
    abstract fun Content()
}
