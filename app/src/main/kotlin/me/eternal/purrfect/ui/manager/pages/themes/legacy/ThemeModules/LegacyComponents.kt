package me.eternal.purrfect.ui.manager.pages.themes.legacy.ThemeModules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.eternal.purrfect.common.ui.theme.PurrfectPalette

import me.eternal.purrfect.common.ui.theme.LocalPurrfectSkin
import androidx.compose.ui.platform.LocalContext

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val skin = LocalPurrfectSkin.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = skin.cardOverlayColor,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(skin.textPrimary.copy(alpha = 0.12f), skin.textPrimary.copy(alpha = 0.05f)))),
        contentColor = skin.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun RowTitle(title: String?) {
    val skin = LocalPurrfectSkin.current
    title?.let {
        Text(
            text = it,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = skin.textPrimary.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun ShiftedRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
