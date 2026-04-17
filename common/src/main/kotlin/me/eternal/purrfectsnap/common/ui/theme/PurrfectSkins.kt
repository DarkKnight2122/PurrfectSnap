package me.eternal.purrfectsnap.common.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Static definitions for all four Aphelion skins, featuring the Liquid Glass properties.
 * Refactored to implement the Catppuccin Designer System and Material 3 Expressive (Aether).
 */
object PurrfectSkins {

    private fun Color.darken(factor: Float): Color {
        return Color(
            red = red * (1 - factor),
            green = green * (1 - factor),
            blue = blue * (1 - factor),
            alpha = 1.0f
        )
    }

    // Helper to generate the Stepped Depth stack based on a Catppuccin flavor
    private fun steppedDepth(flavor: Catppuccin.Flavor, isDark: Boolean, id: String): PurrfectColorSet {
        return PurrfectColorSet(
            id = id,
            isDark = isDark,
            backgroundGradient = Brush.verticalGradient(listOf(flavor.base, flavor.mantle)),
            panelGradient = Brush.linearGradient(listOf(flavor.mantle, flavor.crust)),
            cardOverlay = SolidColor(flavor.surface0.copy(alpha = 0.85f)),
            cardOverlayColor = flavor.surface0,
            glassSurface = flavor.text.copy(alpha = 0.05f),
            glassBorder = flavor.text.copy(alpha = 0.12f),
            glassSpecular = Color.White.copy(alpha = if (isDark) 0.15f else 0.85f),
            blurTint = flavor.base.copy(alpha = 0.55f),
            refractiveColor = flavor.mantle,
            vibrancyFactor = 1.15f,
            refractionIntensity = 0.6f,
            laserBorder = flavor.mauve.copy(alpha = 0.2f),
            specularAlpha = if (isDark) 0.15f else 0.85f,
            glowPrimary = flavor.mauve,
            glowSecondary = flavor.lavender,
            textPrimary = flavor.text,
            textSecondary = flavor.subtext1,
            iconTint = flavor.text
        )
    }

    // —— UMBRA (Catppuccin Mocha) ——————————————————————————————————————————————————————
    val umbra = steppedDepth(Catppuccin.mocha, true, "UMBRA")

    // —— NOX (AMOLED Ivory Pop) ———————————————————————————————————————————————————————
    val nox = PurrfectColorSet(
        id = "NOX",
        isDark = true,
        backgroundGradient = Brush.verticalGradient(listOf(Color.Black, Color.Black)),
        panelGradient = Brush.linearGradient(listOf(Color.Black, Color(0xFF11111B))), // Mocha Crust pop
        cardOverlay = SolidColor(Color(0xFF11111B).copy(alpha = 0.95f)),
        cardOverlayColor = Color(0xFF11111B),
        glassSurface = Color.White.copy(alpha = 0.03f),
        glassBorder = Catppuccin.mocha.rosewater.copy(alpha = 0.15f), // Ivory border
        glassSpecular = Color.White.copy(alpha = 0.12f),
        blurTint = Color.Black.copy(alpha = 0.80f),
        refractiveColor = Color.Black,
        vibrancyFactor = 1.25f,
        refractionIntensity = 0.9f,
        laserBorder = Catppuccin.mocha.rosewater.copy(alpha = 0.25f),
        specularAlpha = 0.15f,
        glowPrimary = Catppuccin.mocha.rosewater, // Ivory Glow
        glowSecondary = Catppuccin.mocha.overlay1, // Charcoal Grey Glow
        textPrimary = Catppuccin.mocha.rosewater, // Ivory Text
        textSecondary = Catppuccin.mocha.overlay1, // Grey Subtext
        iconTint = Catppuccin.mocha.rosewater
    )

    // —— LUX (Catppuccin Latte) ———————————————————————————————————————————————————————
    val lux = steppedDepth(Catppuccin.latte, false, "LUX")

    // —— LUMINA (Dynamic Adaptive) —————————————————————————————————————————————————————
    fun lumina(
        mode: String,
        accentName: String,
        isSystemDark: Boolean
    ): PurrfectColorSet {
        val forceDark = mode == "DARK"
        val forceLight = mode == "LIGHT"
        val isDark = if (forceDark) true else if (forceLight) false else isSystemDark
        
        val flavor = if (isDark) Catppuccin.macchiato else Catppuccin.latte
        val accent = flavor.getAccent(accentName)
        
        return steppedDepth(flavor, isDark, "LUMINA").copy(
            glowPrimary = accent,
            glowSecondary = flavor.lavender,
            laserBorder = accent.copy(alpha = 0.25f)
        )
    }

    // —— AETHER (Material 3 Expressive Solid) ———————————————————————————————————————
    fun aether(
        mode: String,
        accentName: String,
        isAmoled: Boolean,
        isSystemDark: Boolean
    ): PurrfectColorSet {
        val forceDark = mode == "DARK"
        val forceLight = mode == "LIGHT"
        val isDark = if (forceDark) true else if (forceLight) false else isSystemDark
        
        val flavor = if (isDark) Catppuccin.frappe else Catppuccin.latte
        val accent = flavor.getAccent(accentName)

        val backgroundColor = if (isAmoled && isDark) {
            Color.Black
        } else if (isDark) {
            accent.darken(0.9f)
        } else {
            if (accentName == "MAUVE" || accentName == "PINK" || accentName == "ESPRESSO") 
                Color(0xFFFBF8FF) 
            else 
                Color(0xFFFFF8F6)
        }

        return PurrfectColorSet(
            id = "AETHER",
            isDark = isDark,
            backgroundGradient = SolidColor(backgroundColor),
            panelGradient = SolidColor(backgroundColor),
            cardOverlay = SolidColor(flavor.surface0),
            cardOverlayColor = flavor.surface0,
            glassSurface = Color.Transparent,
            glassBorder = accent,
            glassSpecular = Color.Transparent,
            blurTint = backgroundColor,
            refractiveColor = backgroundColor,
            vibrancyFactor = 1.0f,
            refractionIntensity = 0.0f,
            laserBorder = accent,
            glowPrimary = accent,
            glowSecondary = flavor.pink,
            textPrimary = flavor.text,
            textSecondary = flavor.subtext0,
            iconTint = flavor.text
        )
    }

    /**
     * Returns the correct [PurrfectColorSet] for the given skin ID.
     * Modified to pass through user Lumina and Aether preferences.
     */
    fun fromId(
        skinId: String,
        colorScheme: ColorScheme? = null,
        isSystemDark: Boolean = true,
        luminaMode: String = "AUTO",
        luminaAccent: String = "MAUVE",
        aetherMode: String = "AUTO",
        aetherAccent: String = "MAUVE",
        aetherAmoled: Boolean = false
    ): PurrfectColorSet = when (skinId) {
        "NOX"    -> nox
        "LUX"    -> lux
        "LUMINA" -> lumina(luminaMode, luminaAccent, isSystemDark)
        "AETHER" -> aether(aetherMode, aetherAccent, aetherAmoled, isSystemDark)
        else     -> umbra
    }
}
