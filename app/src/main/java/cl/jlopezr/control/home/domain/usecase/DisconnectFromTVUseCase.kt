package cl.jlopezr.control.home.domain.usecase

import cl.jlopezr.control.core.domain.repository.LGTVRepository
import javax.inject.Inject

class DisconnectFromTVUseCase @Inject constructor(
    private val lgtvRepository: LGTVRepository
) {
    suspend operator fun invoke() {
        lgtvRepository.disconnect()
    }
}