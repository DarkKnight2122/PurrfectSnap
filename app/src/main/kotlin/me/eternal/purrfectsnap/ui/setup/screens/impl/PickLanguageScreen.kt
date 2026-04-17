package me.eternal.purrfectsnap.ui.setup.screens.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import me.eternal.purrfectsnap.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfectsnap.ui.setup.screens.SetupScreen
import me.eternal.purrfectsnap.ui.util.Motion
import me.eternal.purrfectsnap.ui.util.ObservableMutableState
import me.eternal.purrfectsnap.ui.util.scaleOnPress

class PickLanguageScreen : SetupScreen() {
    private val availableLocales by lazy {
        LocaleWrapper.fetchAvailableLocales(context.androidContext)
    }
    private lateinit var selectedLocale: ObservableMutableState<String>
    private fun getLocaleDisplayName(locale: String): String {
        val displayLocale = when (locale) {
            "zh-Hans" -> Locale.forLanguageTag("zh-Hans")
            "zh_TW" -> Locale.TRADITIONAL_CHINESE
            else -> try {
                Locale.forLanguageTag(locale.replace('_', '-'))
            } catch (e: Exception) {
                Locale.getDefault()
            }
        }
        return displayLocale.getDisplayName(Locale.getDefault())
    }
    private fun reloadTranslation(selectedLocale: String) {
        context.translation.reload(selectedLocale, isSetup = true)
    }
    private fun setLocale(locale: String) {
        with(context) {
            config.locale = locale
            config.writeConfig()
            reloadTranslation(locale)
        }
    }
    override fun onLeave() {
        context.config.locale = selectedLocale.value
        context.config.writeConfig()
    }
    override fun init() {
        val deviceLocale = Locale.getDefault().toString()
        selectedLocale =
            ObservableMutableState(
                defaultValue = availableLocales.firstOrNull { locale -> locale == deviceLocale }
                    ?: LocaleWrapper.DEFAULT_LOCALE
            ) { _, newValue ->
                setLocale(newValue)
            }.also { reloadTranslation(it.value) }
    }

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) { allowNext(true) }
        val deviceLocale = remember { Locale.getDefault().toString() }
        var isDialog by remember { mutableStateOf(false) }

        SetupCard {
            StepTitle(
                title = context.translation["setup.activity.language_title"] ?: "Choose Language",
                subtitle = context.translation["setup.activity.language_subtitle"] ?: "Select your preferred language",
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
                        .clickable { isDialog = true }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = glowPrimary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getLocaleDisplayName(selectedLocale.value),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = context.translation["setup.dialogs.select_language"] ?: "Change language",
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (isDialog) {
            Dialog(onDismissRequest = { isDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 40.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(glowPrimary.copy(alpha = 0.6f), glowSecondary.copy(alpha = 0.4f))))
                ) {
                    Column(
                        modifier = Modifier
                            .background(cardOverlayColor)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = context.translation["setup.dialogs.select_language"] ?: "Select Language",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableLocales) { locale ->
                                val isSelected = selectedLocale.value == locale
                                val interactionSource = remember { MutableInteractionSource() }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scaleOnPress(interactionSource)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            selectedLocale.value = locale
                                            isDialog = false
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) glowPrimary.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) glowPrimary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = getLocaleDisplayName(locale),
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, null, tint = glowSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { isDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(context.translation["button.cancel"] ?: "Cancel", color = textSecondary)
                        }
                    }
                }
            }
        }
    }
}
