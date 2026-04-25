package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.eternal.purrfectsnap.R
import me.eternal.purrfectsnap.ui.manager.theme.PurrfectPalette
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen

class IntroShowcaseScreen : SetupScreen() {
    private val slides = listOf(
        R.drawable.setup_slide_plus to "Unlock Snapchat Plus for free!",
        R.drawable.setup_slide_upload_tag to "Bypass the Media Upload tag!",
        R.drawable.setup_slide_downloads to "Download Snaps,  & Spotlights!"
    )

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) { allowNext(true) }
        var currentIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % slides.size
            }
        }

        SetupCard {
            StepTitle(
                title = "Welcome to PurrfectSnap",
                subtitle = "A quick look before setup begins",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center
            )
            AnimatedContent(targetState = currentIndex, label = "setupShowcase") { index ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = PurrfectPalette.cardOverlayColor,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Image(
                            painter = painterResource(slides[index].first),
                            contentDescription = slides[index].second,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        )
                    }
                    Text(
                        text = slides[index].second,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (index == currentIndex) PurrfectPalette.glowPrimary else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
