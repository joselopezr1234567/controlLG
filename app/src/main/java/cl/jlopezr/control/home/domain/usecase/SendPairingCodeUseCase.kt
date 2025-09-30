package cl.jlopezr.control.home.domain.usecase

import cl.jlopezr.control.core.domain.repository.LGTVRepository
import javax.inject.Inject

class SendPairingCodeUseCase @Inject constructor(
    private val lgtvRepository: LGTVRepository
) {
    suspend operator fun invoke(code: String): Result<Unit> {
        return lgtvRepository.sendPairingCode(code)
    }
}