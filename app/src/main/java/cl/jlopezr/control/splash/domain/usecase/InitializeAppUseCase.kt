package cl.jlopezr.control.splash.domain.usecase

import kotlinx.coroutines.delay
import javax.inject.Inject

class InitializeAppUseCase @Inject constructor() {
    
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // Simular inicialización de la app (verificar configuraciones, etc.)
            delay(1000) // 1 segundo de carga
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}