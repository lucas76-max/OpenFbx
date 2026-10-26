package fr.openfbx.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.BuildConfig
import fr.openfbx.core.model.AccentPalette
import fr.openfbx.core.model.AppSettings
import fr.openfbx.core.model.ThemeMode
import fr.openfbx.ui.components.ConfirmDialog
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.ScreenScaffold
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.components.SwitchRow
import fr.openfbx.ui.permissionLabel
import fr.openfbx.ui.theme.FlowerBadge
import fr.openfbx.ui.theme.darkSchemeFor
import fr.openfbx.ui.theme.lightSchemeFor
import fr.openfbx.ui.theme.swatch

@Composable
fun SettingsScreen(
    onOpenLogs: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Réglages", onRefresh = viewModel::load) {
        state.message?.let { InlineNotice(it) }
        state.error?.let { InlineError(it) { viewModel.load() } }

        if (state.loading) {
            LoadingPane()
            return@ScreenScaffold
        }

        AppearanceCard(
            settings = state.settings,
            onThemeMode = viewModel::setThemeMode,
            onAccent = viewModel::setAccent,
            onDynamicColor = viewModel::setDynamicColor,
        )

        state.config?.let { config ->
            SectionCard(title = "Freebox") {
                InfoRow("Nom", config.displayName)
                InfoRow("Adresse", "${config.host}:${config.httpsPort}", mono = true)
                InfoRow("Version d'API", config.apiVersion ?: "v${config.apiMajor}")
                config.apiDomain?.let { InfoRow("Domaine distant", it, mono = true) }
                config.certFingerprint?.let { fingerprint ->
                    FingerprintBlock(fingerprint)
                }
            }
        }

        SectionCard(title = "Droits accordés") {
            if (state.permissions.isEmpty()) {
                EmptyPane("Aucun droit lisible pour l'instant.")
            } else {
                state.permissions.entries
                    .sortedBy { it.key }
                    .forEach { (key, granted) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = if (granted) "accordé" else "refusé",
                                tone = if (granted) PillTone.POSITIVE else PillTone.NEGATIVE,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(permissionLabel(key), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                Text(
                    "Ces droits se modifient dans Freebox OS → Paramètres → Gestion des accès.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Sécurité") {
            OutlinedButton(
                onClick = viewModel::resetPin,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Oublier l'empreinte du certificat")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { confirmForget = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Oublier cette Freebox")
            }
        }

        SectionCard(title = "Journaux") {
            Text(
                "Toutes les requêtes envoyées à la Freebox, leurs erreurs et les événements de " +
                    "l'application sont conservés en mémoire (800 entrées).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir les journaux")
            }
        }

        SectionCard(title = "À propos") {
            InfoRow("Application", "OpenFbx")
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("API", "Freebox OS (locale)")
            Text(
                "OpenFbx est un client non officiel. Il n'est ni édité ni soutenu par Free. " +
                    "Les fonctions disponibles dépendent du modèle de Freebox, du firmware et des droits " +
                    "accordés à l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmForget) {
        ConfirmDialog(
            title = "Oublier la Freebox ?",
            text = "Le jeton d'appairage chiffré sera supprimé de ce téléphone et il faudra refaire " +
                "l'appairage (validation sur l'écran de la Freebox).",
            confirmLabel = "Oublier",
            onConfirm = viewModel::forgetBox,
            onDismiss = { confirmForget = false },
        )
    }
}

@Composable
private fun AppearanceCard(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentPalette) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowerBadge(icon = Icons.Filled.Palette, size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Apparence", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Thème, couleurs et formes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "Thème",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemePreview(
                label = ThemeMode.SYSTEM.label,
                caption = "Suit Android",
                backgrounds = listOf(Color.White, Color(0xFF150E20)),
                selected = settings.themeMode == ThemeMode.SYSTEM,
                accent = settings.accent,
                onClick = { onThemeMode(ThemeMode.SYSTEM) },
            )
            ThemePreview(
                label = ThemeMode.LIGHT.label,
                caption = "Fond clair",
                backgrounds = listOf(Color.White),
                selected = settings.themeMode == ThemeMode.LIGHT,
                accent = settings.accent,
                light = true,
                onClick = { onThemeMode(ThemeMode.LIGHT) },
            )
            ThemePreview(
                label = ThemeMode.DARK.label,
                caption = "Violet profond",
                backgrounds = listOf(Color(0xFF150E20)),
                selected = settings.themeMode == ThemeMode.DARK,
                accent = settings.accent,
                onClick = { onThemeMode(ThemeMode.DARK) },
            )
            ThemePreview(
                label = ThemeMode.OLED.label,
                caption = "Noir absolu",
                backgrounds = listOf(Color.Black),
                selected = settings.themeMode == ThemeMode.OLED,
                accent = settings.accent,
                onClick = { onThemeMode(ThemeMode.OLED) },
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "Palette",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentPalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = settings.accent == palette && !settings.dynamicColor,
                    onClick = { onAccent(palette) },
                )
            }
        }

        if (settings.dynamicColor) {
            Text(
                "Couleurs dynamiques actives : Android impose sa palette. Toucher une couleur " +
                    "ci-dessus reprend la main.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Palette « ${settings.accent.label} » appliquée.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        SwitchRow(
            title = "Couleurs dynamiques",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Reprendre la palette de l'écran (Material You)"
            } else {
                "Nécessite Android 12 ou plus : la palette manuelle est utilisée"
            },
            checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onCheckedChange = onDynamicColor,
        )
    }
}

/**
 * Aperçu d'un thème : une vignette qui montre le fond réel du mode et les blocs
 * de couleur de l'accent choisi, comme une miniature de l'application.
 */
@Composable
private fun ThemePreview(
    label: String,
    caption: String,
    backgrounds: List<Color>,
    selected: Boolean,
    accent: AccentPalette,
    onClick: () -> Unit,
    light: Boolean = false,
) {
    val accentColor = if (light) lightSchemeFor(accent).primary else darkSchemeFor(accent).primary
    val blockColor = if (light) lightSchemeFor(accent).primaryContainer else darkSchemeFor(accent).primaryContainer

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = backgrounds.first(),
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.width(112.dp).height(76.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (backgrounds.size > 1) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 56.dp)
                            .background(backgrounds.last()),
                    )
                }
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(20.dp).background(accentColor, CircleShape))
                    Box(Modifier.width(60.dp).height(9.dp).background(blockColor, MaterialTheme.shapes.extraSmall))
                    Box(Modifier.width(38.dp).height(9.dp).background(blockColor.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun FingerprintBlock(fingerprint: String) {
    val clipboard = LocalClipboardManager.current
    val formatted = formatFingerprint(fingerprint)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Empreinte du certificat",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(formatted)) }) {
            Text("Copier l'empreinte")
        }
        Text(
            "Toute autre empreinte est refusée : c'est ce qui protège contre une usurpation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** SHA-256 hexadécimal → paires séparées par deux-points, huit paires par ligne. */
private fun formatFingerprint(raw: String): String {
    val clean = raw.filter { it.isLetterOrDigit() }.uppercase()
    if (clean.length < 4) return raw
    return clean.chunked(2).chunked(8).joinToString("\n") { group -> group.joinToString(":") }
}

@Composable
private fun PaletteSwatch(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .background(palette.swatch(), CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
