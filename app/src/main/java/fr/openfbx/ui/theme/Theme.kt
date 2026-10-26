package fr.openfbx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.openfbx.core.log.AppLog
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.ThemeMode
import java.util.Locale

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
 * Trois modes propres (Clair, Sombre, OLED) et, sur Android 12+, les couleurs
 * dynamiques Material You — y compris en OLED, où seuls les fonds passent au
 * noir absolu et la teinte du système est conservée.
 */
@Composable
fun OpenFbxTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }

    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicActive = settings.dynamicColor && dynamicSupported
    val context = LocalContext.current

    val colors = when {
        dynamicActive -> {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (settings.themeMode == ThemeMode.OLED) base.withOledSurfaces() else base
        }
        settings.themeMode == ThemeMode.OLED -> oledSchemeFor(settings.accent)
        dark -> darkSchemeFor(settings.accent)
        else -> lightSchemeFor(settings.accent)
    }

    LaunchedEffect(colors.primary, colors.background, settings.themeMode, dynamicActive, settings.accent) {
        val primary = String.format(Locale.FRANCE, "#%06X", colors.primary.toArgb() and 0xFFFFFF)
        val background = String.format(Locale.FRANCE, "#%06X", colors.background.toArgb() and 0xFFFFFF)
        AppLog.d(
            "Thème",
            "Appliqué : ${settings.themeMode.name.lowercase()} ${if (dark) "sombre" else "clair"}, " +
                "dynamique=${if (dynamicActive) "oui" else "non"}, accent=${settings.accent.label}, " +
                "primary=$primary, fond=$background",
        )
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = OpenFbxShapes,
        content = content,
    )
}
