package cl.jlopezr.control.voice.presentation

import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.jlopezr.control.core.domain.model.ConnectionState
import cl.jlopezr.control.core.domain.repository.LGTVRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class VoiceControlUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isListening: Boolean = false,
    val lastCommand: String = "",
    val lastResult: String = "",
    val errorMessage: String? = null,
    val isSpeechRecognitionAvailable: Boolean = true
)

@HiltViewModel
class VoiceControlViewModel @Inject constructor(
    application: Application,
    private val lgtvRepository: LGTVRepository
) : AndroidViewModel(application) {

    private val _isListening = MutableStateFlow(false)
    private val _lastCommand = MutableStateFlow("")
    private val _lastResult = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSpeechRecognitionAvailable = MutableStateFlow(true)

    val uiState: StateFlow<VoiceControlUiState> = combine(
        lgtvRepository.getConnectionState(),
        _isListening,
        _lastCommand,
        _lastResult,
        _errorMessage,
        _isSpeechRecognitionAvailable
    ) { flows ->
        val connectionState = flows[0] as ConnectionState
        val isListening = flows[1] as Boolean
        val lastCommand = flows[2] as String
        val lastResult = flows[3] as String
        val errorMessage = flows[4] as String?
        val isSpeechRecognitionAvailable = flows[5] as Boolean

        VoiceControlUiState(
            connectionState = connectionState,
            isListening = isListening,
            lastCommand = lastCommand,
            lastResult = lastResult,
            errorMessage = errorMessage,
            isSpeechRecognitionAvailable = isSpeechRecognitionAvailable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceControlUiState()
    )

    init {
        checkSpeechRecognitionAvailability()
    }

    private fun checkSpeechRecognitionAvailability() {
        val isAvailable = SpeechRecognizer.isRecognitionAvailable(getApplication())
        _isSpeechRecognitionAvailable.value = isAvailable
        if (!isAvailable) {
            _errorMessage.value = "Reconocimiento de voz no disponible en este dispositivo"
        }
    }

    fun startListening() {
        if (!_isSpeechRecognitionAvailable.value) {
            _errorMessage.value = "Reconocimiento de voz no disponible"
            return
        }

        _isListening.value = true
        _errorMessage.value = null
        Log.d("VoiceControlViewModel", "Iniciando reconocimiento de voz")
    }

    fun stopListening() {
        _isListening.value = false
        Log.d("VoiceControlViewModel", "Deteniendo reconocimiento de voz")
    }

    fun onSpeechResult(results: List<String>) {
        if (results.isNotEmpty()) {
            val command = results[0].lowercase(Locale.getDefault())
            _lastCommand.value = command
            _isListening.value = false
            
            Log.d("VoiceControlViewModel", "Comando de voz recibido: $command")
            processVoiceCommand(command)
        }
    }

    fun onSpeechError(error: String) {
        _isListening.value = false
        _errorMessage.value = "Error de reconocimiento: $error"
        Log.e("VoiceControlViewModel", "Error de reconocimiento de voz: $error")
    }

    private fun processVoiceCommand(command: String) {
        val tvCommand = mapVoiceCommandToTVCommand(command)
        
        if (tvCommand != null) {
            _lastResult.value = "Ejecutando: $tvCommand"
            sendCommandToTV(tvCommand)
        } else {
            _lastResult.value = "Comando no reconocido: $command"
            _errorMessage.value = "Comando '$command' no reconocido"
        }
    }

    private fun mapVoiceCommandToTVCommand(voiceCommand: String): String? {
        return when {
            // Comandos de volumen
            voiceCommand.contains("subir volumen") || voiceCommand.contains("más volumen") -> "VOLUMEUP"
            voiceCommand.contains("bajar volumen") || voiceCommand.contains("menos volumen") -> "VOLUMEDOWN"
            voiceCommand.contains("silenciar") || voiceCommand.contains("mute") -> "MUTE"
            
            // Comandos de canales
            voiceCommand.contains("canal arriba") || voiceCommand.contains("siguiente canal") -> "CHANNELUP"
            voiceCommand.contains("canal abajo") || voiceCommand.contains("canal anterior") -> "CHANNELDOWN"
            
            // Comandos de navegación
            voiceCommand.contains("arriba") -> "UP"
            voiceCommand.contains("abajo") -> "DOWN"
            voiceCommand.contains("izquierda") -> "LEFT"
            voiceCommand.contains("derecha") -> "RIGHT"
            voiceCommand.contains("ok") || voiceCommand.contains("enter") || voiceCommand.contains("seleccionar") -> "ENTER"
            voiceCommand.contains("atrás") || voiceCommand.contains("volver") -> "BACK"
            voiceCommand.contains("home") || voiceCommand.contains("inicio") -> "HOME"
            voiceCommand.contains("menú") -> "MENU"
            
            // Comandos de reproducción
            voiceCommand.contains("play") || voiceCommand.contains("reproducir") -> "PLAY"
            voiceCommand.contains("pausa") || voiceCommand.contains("pausar") -> "PAUSE"
            voiceCommand.contains("stop") || voiceCommand.contains("parar") -> "STOP"
            voiceCommand.contains("adelantar") -> "FASTFORWARD"
            voiceCommand.contains("retroceder") -> "REWIND"
            
            // Comandos de encendido/apagado
            voiceCommand.contains("encender") -> "POWER"
            voiceCommand.contains("apagar") -> "POWER"
            
            // Comandos de números
            voiceCommand.contains("uno") || voiceCommand.contains("1") -> "1"
            voiceCommand.contains("dos") || voiceCommand.contains("2") -> "2"
            voiceCommand.contains("tres") || voiceCommand.contains("3") -> "3"
            voiceCommand.contains("cuatro") || voiceCommand.contains("4") -> "4"
            voiceCommand.contains("cinco") || voiceCommand.contains("5") -> "5"
            voiceCommand.contains("seis") || voiceCommand.contains("6") -> "6"
            voiceCommand.contains("siete") || voiceCommand.contains("7") -> "7"
            voiceCommand.contains("ocho") || voiceCommand.contains("8") -> "8"
            voiceCommand.contains("nueve") || voiceCommand.contains("9") -> "9"
            voiceCommand.contains("cero") || voiceCommand.contains("0") -> "0"
            
            else -> null
        }
    }

    private fun sendCommandToTV(command: String) {
        viewModelScope.launch {
            lgtvRepository.sendCommand(command)
                .onSuccess {
                    Log.d("VoiceControlViewModel", "Comando enviado exitosamente: $command")
                    _lastResult.value = "Comando '$command' enviado exitosamente"
                }
                .onFailure { exception ->
                    val errorMsg = "Error enviando comando: ${exception.message}"
                    Log.e("VoiceControlViewModel", errorMsg, exception)
                    _errorMessage.value = errorMsg
                    _lastResult.value = "Error enviando comando '$command'"
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createSpeechRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di un comando para la TV...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }
}