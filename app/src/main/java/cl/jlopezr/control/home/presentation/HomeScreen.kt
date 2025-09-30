package cl.jlopezr.control.home.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.jlopezr.control.core.domain.model.ConnectionState
import cl.jlopezr.control.core.domain.model.DiscoveredTV

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Control Remoto LG TV",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        // Estado de conexión
        ConnectionStatusCard(
            connectionState = uiState.connectionState,
            modifier = Modifier.fillMaxWidth()
        )

        // Sección de TVs descubiertos
        DiscoveredTVsSection(
            discoveredTVs = uiState.discoveredTVs,
            selectedTV = uiState.selectedTV,
            isDiscovering = uiState.isDiscovering,
            onTVSelected = viewModel::selectTV,
            onRefresh = viewModel::discoverTVs,
            modifier = Modifier.fillMaxWidth()
        )

        // Campo de IP
        OutlinedTextField(
            value = uiState.tvIpAddress,
            onValueChange = viewModel::updateIpAddress,
            label = { Text("IP del TV (ej: 192.168.1.100)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.connectionState == ConnectionState.DISCONNECTED && !uiState.isLoading,
            singleLine = true
        )

        // Mensaje de error
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        // Botones de conexión
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.connectionState) {
                ConnectionState.DISCONNECTED -> {
                    Button(
                        onClick = viewModel::connectToSelectedTV,
                        enabled = !uiState.isLoading && (uiState.selectedTV != null || uiState.tvIpAddress.isNotBlank()),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Conectar")
                    }
                }
                ConnectionState.CONNECTING -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conectando...")
                    }
                }
                ConnectionState.PAIRING -> {
                    Button(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Esperando código...")
                    }
                }
                ConnectionState.CONNECTED -> {
                    OutlinedButton(
                        onClick = viewModel::disconnectFromTV,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Desconectar")
                    }
                }
                ConnectionState.ERROR -> {
                    Button(
                        onClick = viewModel::connectToSelectedTV,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        }

        // Área de controles (solo visible cuando está conectado)
        if (uiState.connectionState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.height(16.dp))
            RemoteControlsSection()
        }
    }

    // Diálogo de código de emparejamiento
    if (uiState.showPairingDialog) {
        PairingCodeDialog(
            pairingCode = uiState.pairingCode,
            onPairingCodeChange = viewModel::updatePairingCode,
            onSendCode = viewModel::sendPairingCode,
            onDismiss = viewModel::hidePairingDialog
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ConnectionState.CONNECTING -> Color(0xFFFF9800).copy(alpha = 0.1f)
                ConnectionState.PAIRING -> Color(0xFF2196F3).copy(alpha = 0.1f)
                ConnectionState.ERROR -> Color(0xFFF44336).copy(alpha = 0.1f)
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estado de Conexión",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (connectionState) {
                    ConnectionState.DISCONNECTED -> "Desconectado"
                    ConnectionState.CONNECTING -> "Conectando..."
                    ConnectionState.PAIRING -> "Esperando código de emparejamiento..."
                    ConnectionState.CONNECTED -> "Conectado"
                    ConnectionState.ERROR -> "Error de conexión"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> Color(0xFFFF9800)
                    ConnectionState.PAIRING -> Color(0xFF2196F3)
                    ConnectionState.ERROR -> Color(0xFFF44336)
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun DiscoveredTVsSection(
    discoveredTVs: List<DiscoveredTV>,
    selectedTV: DiscoveredTV?,
    isDiscovering: Boolean,
    onTVSelected: (DiscoveredTV) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TVs Disponibles",
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(
                    onClick = onRefresh,
                    enabled = !isDiscovering
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(20.dp)
                                .width(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Buscar TVs"
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (discoveredTVs.isEmpty() && !isDiscovering) {
                Text(
                    text = "No se encontraron TVs LG en la red",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(discoveredTVs) { tv ->
                        TVItem(
                            tv = tv,
                            isSelected = tv == selectedTV,
                            onSelected = { onTVSelected(tv) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TVItem(
    tv: DiscoveredTV,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "TV",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tv.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = tv.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}

@Composable
private fun RemoteControlsSection() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Controles Remotos",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Aquí se pueden agregar los botones de control remoto
            Text(
                text = "Controles del TV aparecerán aquí",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PairingCodeDialog(
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Código de Emparejamiento",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "La TV está solicitando un código de emparejamiento. Ingresa el código que aparece en la pantalla de la TV:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = onPairingCodeChange,
                    label = { Text("Código de emparejamiento") },
                    placeholder = { Text("Ej: 123456") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSendCode,
                enabled = pairingCode.isNotBlank()
            ) {
                Text("Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}