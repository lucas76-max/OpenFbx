package fr.openfbx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.ThemeMode

/** Coins généreusement arrondis : c'est la signature visuelle d'OpenFbx. */
val OpenFbxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Thème OpenFbx.
 *
 * Trois modes propres (Clair, Sombre, OLED) et, sur Android 12+, la palette du
 * système si l'utilisateur l'a choisie. Hors couleurs dynamiques, la couleur
 * d'accent vient de [fr.openfbx.core.model.AccentPalette].
 */
@Composable
fun OpenFbxTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }

    val dynamicAllowed = settings.dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        settings.themeMode != ThemeMode.OLED

    val colors = when {
        dynamicAllowed -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        settings.themeMode == ThemeMode.OLED -> oledSchemeFor(settings.accent)
        dark -> darkSchemeFor(settings.accent)
        else -> lightSchemeFor(settings.accent)
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = OpenFbxShapes,
        content = content,
    )
}
