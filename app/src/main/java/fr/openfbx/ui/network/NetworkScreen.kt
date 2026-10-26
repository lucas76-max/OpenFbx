package fr.openfbx.ui.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.openfbx.core.data.dto.LanHost
import fr.openfbx.core.data.dto.StaticLease
import fr.openfbx.ui.components.Dot
import fr.openfbx.ui.components.EmptyPane
import fr.openfbx.ui.components.InfoRow
import fr.openfbx.ui.components.InlineError
import fr.openfbx.ui.components.InlineNotice
import fr.openfbx.ui.components.LoadingPane
import fr.openfbx.ui.components.PillTone
import fr.openfbx.ui.components.SectionCard
import fr.openfbx.ui.components.StatusPill
import fr.openfbx.ui.formatBytes
import fr.openfbx.ui.formatLinkSpeed
import fr.openfbx.ui.formatRate
import fr.openfbx.ui.formatTimestamp
import fr.openfbx.ui.formatUptime
import fr.openfbx.ui.humanMedia
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(viewModel: NetworkViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedHost by remember { mutableStateOf<LanHost?>(null) }

    // Les appareils et leurs débits sont rafraîchis en direct tant que l'écran est visible.
    LaunchedEffect(Unit) {
        viewModel.refresh()
        while (true) {
            delay(5_000)
            viewModel.refreshLive()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réseau") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Connexion", "Appareils", "DHCP").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { InlineNotice(it) }
                state.error?.let { InlineError(it) { viewModel.refresh() } }

                if (state.loading) {
                    LoadingPane()
                } else {
                    when (tab) {
                        0 -> ConnectionTab(state)
                        1 -> HostsTab(
                            state = state,
                            onWake = viewModel::wakeOnLan,
                            onSelect = { selectedHost = it },
                        )
                        else -> DhcpTab(
                            state = state,
                            onAddLease = viewModel::addStaticLease,
                            onDeleteLease = viewModel::deleteStaticLease,
                        )
                    }
                }
            }
        }
    }

    selectedHost?.let { host ->
        HostDetailDialog(
            host = host,
            rate = host.mac?.lowercase()?.let { state.rates[it] },
            busy = state.busy,
            onWake = viewModel::wakeOnLan,
            onDismiss = { selectedHost = null },
        )
    }
}

@Composable
private fun ConnectionTab(state: NetworkUiState) {
    val connection = state.connection
    if (connection == null) {
        EmptyPane("État de la connexion indisponible.")
        return
    }

    SectionCard(title = "Connexion Internet") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = if (connection.isUp) "connectée" else "hors ligne",
                tone = if (connection.isUp) PillTone.POSITIVE else PillTone.NEGATIVE,
            )
            Spacer(Modifier.width(8.dp))
            Text(humanMedia(connection.media), style = MaterialTheme.typography.bodyMedium)
        }
        InfoRow("Type de lien", connection.type)
        InfoRow("Adresse IPv4", connection.ipv4, mono = true)
        connection.ipv6?.let { InfoRow("Adresse IPv6", it, mono = true) }
        InfoRow("Débit maximal ↓", formatLinkSpeed(connection.bandwidthDown))
        InfoRow("Débit maximal ↑", formatLinkSpeed(connection.bandwidthUp))
        InfoRow("Débit instantané", "${formatBytes(connection.rateDown)}/s ↓ · ${formatBytes(connection.rateUp)}/s ↑")
        InfoRow("Trafic depuis le démarrage", "${formatBytes(connection.bytesDown)} ↓ · ${formatBytes(connection.bytesUp)} ↑")
    }

    state.ftth?.let { ftth ->
        SectionCard(title = "Fibre (FTTH)") {
            InfoRow("Module SFP présent", if (ftth.sfpPresent == true) "oui" else "non")
            InfoRow("Signal optique", if (ftth.sfpHasSignal == true) "présent" else "absent")
            InfoRow("Liaison active", if (ftth.link == true) "oui" else "non")
            ftth.sfpVendor?.let { InfoRow("Module", it) }
            ftth.sfpSerial?.let { InfoRow("Numéro de série", it, mono = true) }
        }
    }

    state.xdsl?.let { xdsl ->
        SectionCard(title = "Ligne xDSL") {
            InfoRow("État de la ligne", xdsl.status?.lineStatus ?: xdsl.status?.status)
            InfoRow("Synchronisée depuis", formatUptime(xdsl.status?.uptime))
            xdsl.down?.rate?.let { InfoRow("Débit descendant", formatLinkSpeed(it)) }
            xdsl.up?.rate?.let { InfoRow("Débit montant", formatLinkSpeed(it)) }
            xdsl.down?.snr?.let { InfoRow("Marge de bruit", "%.1f dB".format(it)) }
            xdsl.down?.attenuation?.let { InfoRow("Atténuation", "%.1f dB".format(it)) }
        }
    }
}

@Composable
private fun HostsTab(
    state: NetworkUiState,
    onWake: (String) -> Unit,
    onSelect: (LanHost) -> Unit,
) {
    SectionCard(title = "Appareils sur le réseau local") {
        Text(
            "${state.activeHosts} actifs sur ${state.hosts.size} connus · mis à jour en continu",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "↓ ${formatRate(state.totalDown)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "↑ ${formatRate(state.totalUp)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Touchez un appareil pour sa fiche détaillée.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.hosts.isEmpty()) {
        EmptyPane("Aucun appareil remonté par la Freebox.")
    }

    state.hosts.take(80).forEach { host ->
        HostCard(
            host = host,
            rate = host.mac?.lowercase()?.let { state.rates[it] },
            onWake = onWake,
            onSelect = { onSelect(host) },
        )
    }
}

@Composable
private fun HostCard(
    host: LanHost,
    rate: HostRate?,
    onWake: (String) -> Unit,
    onSelect: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(host.active)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = host.primaryName ?: host.primaryAddr ?: "Appareil inconnu",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(host.primaryAddr, host.mac).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rate != null && (rate.rx != null || rate.tx != null)) {
                    Text(
                        text = "↓ ${formatRate(rate.rx)} · ↑ ${formatRate(rate.tx)}" +
                            if (rate.viaWifi) " · Wi-Fi" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    host.vendorName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val mac = host.mac
            if (mac != null) {
                IconButton(onClick = { onWake(mac) }) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Réveiller $mac")
                }
            }
        }
    }
}

@Composable
private fun HostDetailDialog(
    host: LanHost,
    rate: HostRate?,
    busy: Boolean,
    onWake: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                host.primaryName ?: host.primaryAddr ?: "Appareil",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (host.active) "connecté" else "hors ligne",
                        tone = if (host.active) PillTone.POSITIVE else PillTone.NEUTRAL,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(host.hostType ?: "appareil", style = MaterialTheme.typography.bodySmall)
                }
                InfoRow("Adresse IP", host.primaryAddr ?: host.ipv4)
                host.ipv6?.let { InfoRow("IPv6", it, mono = true) }
                InfoRow("Adresse MAC", host.mac, mono = true)
                InfoRow("Constructeur", host.vendorName)
                InfoRow("Interface", host.iface)
                InfoRow("Dernière activité", formatTimestamp(host.lastActivity ?: host.lastTimeReachable))
                InfoRow("Débit descendant", formatRate(rate?.rx))
                InfoRow("Débit montant", formatRate(rate?.tx))
                if (rate == null) {
                    Text(
                        "Cette Freebox n'expose pas de débit par appareil : les valeurs sont " +
                            "disponibles pour les clients Wi-Fi et sur certains firmwares filaires.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            val mac = host.mac
            if (mac != null) {
                Button(
                    onClick = {
                        onWake(mac)
                        onDismiss()
                    },
                    enabled = !busy,
                ) { Text("Réveiller") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun DhcpTab(
    state: NetworkUiState,
    onAddLease: (String, String, String?) -> Unit,
    onDeleteLease: (String) -> Unit,
) {
    var addDialog by rememberSaveable { mutableStateOf(false) }

    state.dhcp?.let { dhcp ->
        SectionCard(title = "Serveur DHCP") {
            InfoRow("Passerelle", dhcp.gateway, mono = true)
            InfoRow("Masque", dhcp.netmask, mono = true)
            InfoRow("Plage d'adresses", "${dhcp.rangeStart ?: "—"} → ${dhcp.rangeEnd ?: "—"}", mono = true)
            if (!dhcp.dns.isNullOrEmpty()) {
                InfoRow("DNS", dhcp.dns.joinToString(" "), mono = true)
            }
            InfoRow("Domaine", dhcp.domain)
            InfoRow("Baux fixes automatiques", if (dhcp.stickyAssign == true) "activés" else "désactivés")
        }
    }

    SectionCard(title = "Baux dynamiques (${state.leases.size})") {
        if (state.leases.isEmpty()) {
            EmptyPane("Aucun bail actif.")
        } else {
            state.leases.take(30).forEach { lease ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            lease.hostname ?: lease.ip ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(lease.ip, lease.mac).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        formatUptime(lease.leaseRemaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SectionCard(title = "Baux statiques (${state.staticLeases.size})") {
        if (state.staticLeases.isEmpty()) {
            EmptyPane("Aucun bail statique configuré.")
        } else {
            state.staticLeases.forEach { lease ->
                StaticLeaseRow(lease = lease, onDelete = onDeleteLease)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { addDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter un bail statique")
        }
    }

    if (addDialog) {
        AddStaticLeaseDialog(
            onDismiss = { addDialog = false },
            onConfirm = { mac, ip, comment ->
                addDialog = false
                onAddLease(mac, ip, comment)
            },
        )
    }
}

@Composable
private fun StaticLeaseRow(lease: StaticLease, onDelete: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                lease.hostname ?: lease.ip ?: lease.mac ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(lease.ip, lease.mac, lease.comment).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        val id = lease.id
        if (id != null) {
            IconButton(onClick = { onDelete(id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer le bail")
            }
        }
    }
}

@Composable
private fun AddStaticLeaseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
) {
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bail statique") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("Adresse MAC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Adresse IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Commentaire (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mac.trim(), ip.trim(), comment.trim().ifBlank { null }) },
                enabled = mac.isNotBlank() && ip.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
