package cl.jlopezr.control.voice.presentation

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.jlopezr.control.core.domain.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: VoiceControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Launcher para solicitar permisos de micrófono
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            viewModel.onSpeechError("Permisos de micrófono requeridos")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control por Voz") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Estado de conexión
            ConnectionStatusCard(connectionState = uiState.connectionState)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botón de micrófono principal
            MicrophoneButton(
                isListening = uiState.isListening,
                isEnabled = uiState.connectionState == ConnectionState.CONNECTED && uiState.isSpeechRecognitionAvailable,
                onClick = {
                    if (uiState.isListening) {
                        viewModel.stopListening()
                    } else {
                        // Solicitar permisos y iniciar reconocimiento
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            
            // Estado del reconocimiento
            if (uiState.isListening) {
                Text(
                    text = "Escuchando... Di tu comando",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Error
            uiState.errorMessage?.let { error ->
                CommandCard(
                    title = "Error:",
                    content = error,
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    textColor = MaterialTheme.colorScheme.onErrorContainer
                )
                
                Button(
                    onClick = { viewModel.clearError() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Limpiar Error")
                }
            }
            
            // Botones de prueba para emulador
            if (uiState.connectionState == ConnectionState.CONNECTED) {
                TestCommandsSection(
                    viewModel = viewModel
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Comandos disponibles
            VoiceCommandsHelp()
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
    val (text, color) = when (connectionState) {
        ConnectionState.CONNECTED -> "Conectado a la TV" to Color(0xFF4CAF50)
        ConnectionState.CONNECTING -> "Conectando..." to Color(0xFFFF9800)
        ConnectionState.DISCONNECTED -> "Desconectado" to Color(0xFFF44336)
        ConnectionState.ERROR -> "Error de conexión" to Color(0xFFF44336)
        ConnectionState.PAIRING -> "Emparejando..." to Color(0xFFFF9800)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MicrophoneButton(
    isListening: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.outline
        isListening -> Color(0xFFE53E3E)
        else -> MaterialTheme.colorScheme.primary
    }
    
    val iconColor = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isEnabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "Detener" else "Hablar",
            tint = iconColor,
            modifier = Modifier.size(48.dp)
        )
    }
    
    Text(
        text = when {
            !isEnabled -> "No disponible"
            isListening -> "Toca para detener"
            else -> "Toca para hablar"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun CommandCard(
    title: String,
    content: String,
    backgroundColor: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun TestCommandsSection(
    viewModel: VoiceControlViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🧪 Prueba de Comandos (Para Emulador)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Botones de aplicaciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.openNetflix() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Netflix", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.openYouTube() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                ) {
                    Text("YouTube", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.powerOff() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575))
                ) {
                    Text("Apagar", color = Color.White, fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Botones direccionales organizados alrededor de OK
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Botón Arriba
                Button(
                    onClick = { viewModel.sendDirectionalCommand("UP") },
                    modifier = Modifier.size(60.dp, 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("↑", fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Fila con Izquierda, OK, Derecha
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.sendDirectionalCommand("LEFT") },
                        modifier = Modifier.size(60.dp, 40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("←", fontSize = 16.sp)
                    }
                    
                    Button(
                        onClick = { viewModel.sendDirectionalCommand("ENTER") },
                        modifier = Modifier.size(80.dp, 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OK", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { viewModel.sendDirectionalCommand("RIGHT") },
                        modifier = Modifier.size(60.dp, 40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("→", fontSize = 16.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Botón Abajo
                Button(
                    onClick = { viewModel.sendDirectionalCommand("DOWN") },
                    modifier = Modifier.size(60.dp, 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("↓", fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Comandos de volumen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.sendDirectionalCommand("VOLUMEUP") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Vol +", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.sendDirectionalCommand("VOLUMEDOWN") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Vol -", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.sendDirectionalCommand("MUTE") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Mute", fontSize = 12.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Comandos de reproducción y otros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.sendDirectionalCommand("PLAY") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Play", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.sendDirectionalCommand("PAUSE") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Pause", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.sendDirectionalCommand("BACK") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Back", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VoiceCommandsHelp() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Comandos disponibles:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val commands = listOf(
                "• Volumen: 'subir volumen', 'bajar volumen', 'silenciar'",
                "• Canales: 'canal arriba', 'canal abajo'",
                "• Navegación: 'arriba', 'abajo', 'izquierda', 'derecha', 'ok'",
                "• Control: 'play', 'pausa', 'atrás', 'home', 'menú'",
                "• Números: 'uno', 'dos', 'tres'... o '1', '2', '3'...",
                "• Encendido: 'encender', 'apagar'"
            )
            
            commands.forEach { command ->
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}