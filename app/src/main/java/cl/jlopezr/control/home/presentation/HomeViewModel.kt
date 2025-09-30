package cl.jlopezr.control.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.jlopezr.control.core.domain.model.ConnectionState
import cl.jlopezr.control.core.domain.model.DiscoveredTV
import cl.jlopezr.control.core.domain.repository.LGTVRepository
import cl.jlopezr.control.home.domain.usecase.ConnectToTVUseCase
import cl.jlopezr.control.home.domain.usecase.DisconnectFromTVUseCase
import cl.jlopezr.control.home.domain.usecase.DiscoverTVsUseCase
import cl.jlopezr.control.home.domain.usecase.SendPairingCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val tvIpAddress: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val discoveredTVs: List<DiscoveredTV> = emptyList(),
    val isDiscovering: Boolean = false,
    val selectedTV: DiscoveredTV? = null,
    val pairingCode: String = "",
    val showPairingDialog: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectToTVUseCase: ConnectToTVUseCase,
    private val disconnectFromTVUseCase: DisconnectFromTVUseCase,
    private val discoverTVsUseCase: DiscoverTVsUseCase,
    private val sendPairingCodeUseCase: SendPairingCodeUseCase,
    private val lgtvRepository: LGTVRepository
) : ViewModel() {

    private val _tvIpAddress = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _discoveredTVs = MutableStateFlow<List<DiscoveredTV>>(emptyList())
    private val _isDiscovering = MutableStateFlow(false)
    private val _selectedTV = MutableStateFlow<DiscoveredTV?>(null)
    private val _pairingCode = MutableStateFlow("")
    private val _showPairingDialog = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        lgtvRepository.getConnectionState(),
        _tvIpAddress,
        _errorMessage,
        _isLoading,
        _discoveredTVs,
        _isDiscovering,
        _selectedTV,
        _pairingCode,
        _showPairingDialog
    ) { flows ->
        val connectionState = flows[0] as ConnectionState
        val ipAddress = flows[1] as String
        val errorMessage = flows[2] as String?
        val isLoading = flows[3] as Boolean
        val discoveredTVs = flows[4] as List<DiscoveredTV>
        val isDiscovering = flows[5] as Boolean
        val selectedTV = flows[6] as DiscoveredTV?
        val pairingCode = flows[7] as String
        val showPairingDialog = flows[8] as Boolean
        
        HomeUiState(
            connectionState = connectionState,
            tvIpAddress = ipAddress,
            errorMessage = errorMessage,
            isLoading = isLoading,
            discoveredTVs = discoveredTVs,
            isDiscovering = isDiscovering,
            selectedTV = selectedTV,
            pairingCode = pairingCode,
            showPairingDialog = showPairingDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun updateIpAddress(ipAddress: String) {
        _tvIpAddress.value = ipAddress
        _errorMessage.value = null
    }

    fun connectToTV() {
        if (_tvIpAddress.value.isBlank()) {
            _errorMessage.value = "Por favor ingresa la IP del TV"
            Log.w("HomeViewModel", "Intento de conexión sin IP")
            return
        }

        val ipAddress = _tvIpAddress.value
        Log.d("HomeViewModel", "Intentando conectar a TV en IP: $ipAddress")

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            connectToTVUseCase(ipAddress)
                .onSuccess {
                    Log.d("HomeViewModel", "Conexión exitosa a TV en $ipAddress")
                }
                .onFailure { exception ->
                    val errorMsg = "Error al conectar: ${exception.message}"
                    Log.e("HomeViewModel", "Error de conexión a $ipAddress: ${exception.message}", exception)
                    _errorMessage.value = errorMsg
                }

            _isLoading.value = false
        }
    }

    fun disconnectFromTV() {
        viewModelScope.launch {
            disconnectFromTVUseCase()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun discoverTVs() {
        viewModelScope.launch {
            _isDiscovering.value = true
            _errorMessage.value = null
            
            try {
                discoverTVsUseCase().collect { tvs ->
                    _discoveredTVs.value = tvs
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al buscar TVs: ${e.message}"
            } finally {
                _isDiscovering.value = false
            }
        }
    }

    fun selectTV(tv: DiscoveredTV) {
        _selectedTV.value = tv
        _tvIpAddress.value = tv.ipAddress
        _errorMessage.value = null
    }

    fun connectToSelectedTV() {
        val selectedTV = _selectedTV.value
        if (selectedTV != null) {
            connectToTV()
        } else if (_tvIpAddress.value.isNotBlank()) {
            connectToTV()
        } else {
            _errorMessage.value = "Por favor selecciona un TV o ingresa una IP"
        }
    }

    // Iniciar descubrimiento automáticamente
    fun updatePairingCode(code: String) {
        _pairingCode.value = code
    }

    fun sendPairingCode() {
        viewModelScope.launch {
            try {
                sendPairingCodeUseCase(_pairingCode.value)
                _showPairingDialog.value = false
                _pairingCode.value = ""
            } catch (e: Exception) {
                _errorMessage.value = "Error al enviar código de emparejamiento: ${e.message}"
            }
        }
    }

    fun showPairingDialog() {
        _showPairingDialog.value = true
    }

    fun hidePairingDialog() {
        _showPairingDialog.value = false
        _pairingCode.value = ""
    }

    init {
        discoverTVs()
        
        // Observar el estado de conexión para mostrar el diálogo de emparejamiento
        viewModelScope.launch {
            lgtvRepository.getConnectionState().collect { state ->
                if (state == ConnectionState.PAIRING) {
                    _showPairingDialog.value = true
                }
            }
        }
    }
}