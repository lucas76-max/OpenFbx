package fr.openfbx.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import fr.openfbx.core.model.AccentPalette

/**
 * Teintes de base de chaque palette d'accent.
 *
 * `hue` est la teinte dominante (0–360), `chroma` sa vivacité. Les rôles Material 3
 * (primary, containers, tertiary…) sont ensuite dérivés par tons, ce qui garantit
 * des contrastes stables d'une palette à l'autre et dans les trois modes.
 */
internal data class AccentHue(val hue: Float, val chroma: Float)

internal fun AccentPalette.hue(): AccentHue = when (this) {
    AccentPalette.VIOLET -> AccentHue(268f, 0.72f)
    AccentPalette.INDIGO -> AccentHue(242f, 0.62f)
    AccentPalette.BLEU -> AccentHue(210f, 0.68f)
    AccentPalette.TURQUOISE -> AccentHue(184f, 0.60f)
    AccentPalette.VERT -> AccentHue(142f, 0.58f)
    AccentPalette.AMBRE -> AccentHue(45f, 0.80f)
    AccentPalette.ROSE -> AccentHue(336f, 0.62f)
    AccentPalette.ROUGE -> AccentHue(8f, 0.68f)
}

/** Couleur d'aperçu (pastille de la section Palette) : le `primary` du mode sombre. */
fun AccentPalette.swatch(): Color = darkAccent(this).primary

private fun hue(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

private fun tone(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(hue = hue(hue), saturation = saturation.coerceIn(0f, 1f), lightness = lightness.coerceIn(0f, 1f))

// --------------------------------------------------------------------- neutres

/** Neutres du mode clair : blancs légèrement lavés de violet. */
private val LightNeutrals = Neutrals(
    background = tone(272f, 0.32f, 0.985f),
    surface = tone(272f, 0.30f, 0.975f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = tone(272f, 0.28f, 0.955f),
    surfaceContainer = tone(272f, 0.26f, 0.935f),
    surfaceContainerHigh = tone(272f, 0.24f, 0.915f),
    surfaceContainerHighest = tone(272f, 0.22f, 0.895f),
    onSurface = tone(270f, 0.24f, 0.13f),
    surfaceVariant = tone(272f, 0.20f, 0.915f),
    onSurfaceVariant = tone(270f, 0.12f, 0.36f),
    outline = tone(270f, 0.10f, 0.53f),
    outlineVariant = tone(272f, 0.14f, 0.82f),
)

/** Neutres du mode sombre : violet très profond, cartes anthracite. */
private val DarkNeutrals = Neutrals(
    background = tone(268f, 0.36f, 0.065f),
    surface = tone(268f, 0.32f, 0.085f),
    surfaceContainerLowest = tone(268f, 0.36f, 0.045f),
    surfaceContainerLow = tone(268f, 0.26f, 0.115f),
    surfaceContainer = tone(268f, 0.24f, 0.14f),
    surfaceContainerHigh = tone(268f, 0.22f, 0.17f),
    surfaceContainerHighest = tone(268f, 0.20f, 0.20f),
    onSurface = tone(270f, 0.18f, 0.94f),
    surfaceVariant = tone(268f, 0.18f, 0.20f),
    onSurfaceVariant = tone(270f, 0.14f, 0.76f),
    outline = tone(268f, 0.12f, 0.52f),
    outlineVariant = tone(268f, 0.16f, 0.28f),
)

/** Neutres du mode OLED : noir absolu en fond, cartes à peine relevées. */
private val OledNeutrals = DarkNeutrals.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = tone(268f, 0.30f, 0.045f),
    surfaceContainer = tone(268f, 0.28f, 0.065f),
    surfaceContainerHigh = tone(268f, 0.24f, 0.085f),
    surfaceContainerHighest = tone(268f, 0.22f, 0.11f),
    surfaceVariant = tone(268f, 0.20f, 0.13f),
    outlineVariant = tone(268f, 0.16f, 0.22f),
)

private data class Neutrals(
    val background: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

// --------------------------------------------------------------------- schémas

private fun lightAccent(palette: AccentPalette): ColorScheme {
    val (h, c) = palette.hue()
    return with(LightNeutrals) {
        lightColorScheme(
            primary = tone(h, c, 0.40f),
            onPrimary = tone(h, 0.20f, 0.99f),
            primaryContainer = tone(h, c * 0.55f, 0.90f),
            onPrimaryContainer = tone(h, c * 0.85f, 0.18f),
            inversePrimary = tone(h, c, 0.78f),
            secondary = tone(h, 0.18f, 0.42f),
            onSecondary = Color.White,
            secondaryContainer = tone(h, 0.34f, 0.92f),
            onSecondaryContainer = tone(h, 0.24f, 0.18f),
            tertiary = tone(h + 42f, 0.30f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = tone(h + 42f, 0.42f, 0.90f),
            onTertiaryContainer = tone(h + 42f, 0.32f, 0.16f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = tone(272f, 0.24f, 0.97f),
            surfaceDim = tone(272f, 0.24f, 0.90f),
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = tone(268f, 0.22f, 0.16f),
            inverseOnSurface = tone(270f, 0.18f, 0.94f),
            error = tone(6f, 0.66f, 0.42f),
            onError = Color.White,
            errorContainer = tone(6f, 0.70f, 0.92f),
            onErrorContainer = tone(6f, 0.60f, 0.20f),
            scrim = Color.Black,
        )
    }
}

private fun darkAccent(palette: AccentPalette, neutrals: Neutrals = DarkNeutrals): ColorScheme {
    val (h, c) = palette.hue()
    return with(neutrals) {
        darkColorScheme(
            primary = tone(h, c * 0.62f, 0.80f),
            onPrimary = tone(h, c * 0.80f, 0.15f),
            primaryContainer = tone(h, c * 0.55f, 0.28f),
            onPrimaryContainer = tone(h, c * 0.45f, 0.90f),
            inversePrimary = tone(h, c, 0.42f),
            secondary = tone(h, 0.22f, 0.74f),
            onSecondary = tone(h, 0.30f, 0.18f),
            secondaryContainer = tone(h, 0.22f, 0.24f),
            onSecondaryContainer = tone(h, 0.22f, 0.90f),
            tertiary = tone(h + 42f, 0.36f, 0.72f),
            onTertiary = tone(h + 42f, 0.40f, 0.16f),
            tertiaryContainer = tone(h + 42f, 0.30f, 0.26f),
            onTertiaryContainer = tone(h + 42f, 0.32f, 0.90f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceBright = tone(268f, 0.22f, 0.22f),
            surfaceDim = tone(268f, 0.34f, 0.07f),
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = tone(270f, 0.18f, 0.93f),
            inverseOnSurface = tone(268f, 0.26f, 0.14f),
            error = tone(6f, 0.62f, 0.72f),
            onError = tone(6f, 0.70f, 0.16f),
            errorContainer = tone(6f, 0.52f, 0.28f),
            onErrorContainer = tone(6f, 0.60f, 0.92f),
            scrim = Color.Black,
        )
    }
}

fun lightSchemeFor(palette: AccentPalette): ColorScheme = lightAccent(palette)

fun darkSchemeFor(palette: AccentPalette): ColorScheme = darkAccent(palette)

fun oledSchemeFor(palette: AccentPalette): ColorScheme = darkAccent(palette, OledNeutrals)
