package fr.openfbx.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.model.DiscoveredBox
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.permissionLabel

@Composable
fun SetupFlow(pairingRequired: Boolean) {
    val viewModel: SetupViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscovery by rememberSaveable { mutableStateOf(false) }

    if (pairingRequired && !showDiscovery) {
        PairingScreen(
            state = state,
            onStart = viewModel::startPairing,
            onRestart = viewModel::restartPairing,
            onChangeBox = { showDiscovery = true },
        )
    } else {
        DiscoveryScreen(
            state = state,
            onDiscover = viewModel::discover,
            onDiscoverManual = viewModel::discoverManual,
            onContinue = viewModel::saveDiscoveredBox,
        )
    }
}

@Composable
private fun DiscoveryScreen(
    state: SetupUiState,
    onDiscover: () -> Unit,
    onDiscoverManual: (String, Int?) -> Unit,
    onContinue: () -> Unit,
) {
    var manualDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp),
            )
            Text("Bienvenue dans OpenFbx", style = MaterialTheme.typography.headlineMedium)
            Text(
                "OpenFbx pilote votre Freebox par son API officielle (Freebox OS). " +
                    "Connectez ce téléphone au Wi-Fi de la Freebox, puis lancez la recherche.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = "Comment ça marche") {
                Step(number = "1", text = "OpenFbx recherche la Freebox sur le réseau local.")
                Step(number = "2", text = "Vous validez la demande d'appairage sur l'écran de la Freebox.")
                Step(number = "3", text = "L'application mémorise un jeton chiffré pour se reconnecter seule.")
            }

            if (state.discoveryError != null) {
                InlineError(state.discoveryError)
            }

            Button(
                onClick = onDiscover,
                enabled = !state.discovering,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.discovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.discovering) "Recherche en cours…" else "Rechercher ma Freebox")
            }

            OutlinedButton(onClick = { manualDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Saisir une adresse")
            }

            state.discovered?.let { box ->
                DiscoveredBoxCard(box = box, saving = state.saving, onContinue = onContinue)
            }

            if (state.discovered == null && !state.discovering) {
                EmptyPane("Aucune Freebox détectée pour l'instant.")
            }
        }
    }

    if (manualDialog) {
        ManualAddressDialog(
            onDismiss = { manualDialog = false },
            onConfirm = { host, port ->
                manualDialog = false
                onDiscoverManual(host, port)
            },
        )
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiscoveredBoxCard(box: DiscoveredBox, saving: Boolean, onContinue: () -> Unit) {
    SectionCard(title = "Freebox trouvée") {
        InfoRow("Modèle", box.boxModelName ?: box.boxModel)
        InfoRow("Version d'API", box.apiVersion)
        InfoRow("Port HTTPS", box.httpsPort.toString())
        InfoRow("Domaine", box.apiDomain)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onContinue, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "Enregistrement…" else "Utiliser cette Freebox")
        }
    }
}

@Composable
private fun ManualAddressDialog(onDismiss: () -> Unit, onConfirm: (String, Int?) -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adresse de la Freebox") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adresse ou nom de la Freebox sur votre réseau. La connexion se fait en HTTPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Adresse (ex. 192.168.1.254)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port HTTPS (facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(host.trim(), port.toIntOrNull()) },
                enabled = host.isNotBlank(),
            ) { Text("Rechercher") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun PairingScreen(
    state: SetupUiState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onChangeBox: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Appairer OpenFbx", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sur l'écran de la Freebox, une demande d'autorisation doit être validée avec la flèche droite " +
                    "du boîtier. OpenFbx attend votre confirmation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(title = "État de la demande") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.paired) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (state.pairingStarted) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            state.paired -> "Appairage réussi"
                            state.pairingStatus == "granted" -> "Autorisation accordée"
                            state.pairingStatus == "pending" -> "En attente de validation sur la Freebox…"
                            else -> "Aucune demande en cours"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.fingerprint != null) {
                    InfoRow("Empreinte du certificat", state.fingerprint, mono = true)
                    Text(
                        "Cette empreinte identifie votre Freebox : elle sera exigée à chaque connexion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.permissions.isNotEmpty()) {
                    Text("Droits accordés", style = MaterialTheme.typography.titleSmall)
                    state.permissions.filterValues { it }.keys.sorted().forEach { key ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("accordé", PillTone.POSITIVE)
                            Spacer(Modifier.width(8.dp))
                            Text(permissionLabel(key), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val refused = state.permissions.filterValues { !it }.keys
                    if (refused.isNotEmpty()) {
                        Text(
                            "Non accordés : " + refused.sorted().joinToString(", ") { permissionLabel(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.pairingError != null) {
                InlineError(state.pairingError)
            }

            if (!state.pairingStarted) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Demander l'appairage")
                }
            } else if (!state.paired && state.pairingError == null) {
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("Relancer la demande")
                }
            }

            if (state.pairingError != null) {
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("Réessayer")
                }
            }

            TextButton(onClick = onChangeBox, modifier = Modifier.fillMaxWidth()) {
                Text("Choisir une autre Freebox")
            }

            Text(
                "Astuce : l'appairage se fait sur le réseau local. L'accès distant doit être activé " +
                    "dans Freebox OS pour piloter la box hors de chez vous.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
