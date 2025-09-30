package cl.jlopezr.control.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.jlopezr.control.splash.domain.usecase.InitializeAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val initializeAppUseCase: InitializeAppUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Ejecutar el caso de uso de inicialización
            val result = initializeAppUseCase()
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    progress = 1f
                )
                
                // Esperar 500ms adicionales para completar los 1.5 segundos totales
                delay(500)
                
                _uiState.value = _uiState.value.copy(shouldNavigateToHome = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Error desconocido"
                )
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.value = _uiState.value.copy(shouldNavigateToHome = false)
    }
}

data class SplashUiState(
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val shouldNavigateToHome: Boolean = false,
    val error: String? = null
)