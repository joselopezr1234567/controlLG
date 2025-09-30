package cl.jlopezr.control.home.domain.usecase

import cl.jlopezr.control.core.domain.model.TVDevice
import cl.jlopezr.control.core.domain.repository.LGTVRepository
import javax.inject.Inject

class ConnectToTVUseCase @Inject constructor(
    private val lgtvRepository: LGTVRepository
) {
    suspend operator fun invoke(ipAddress: String): Result<Unit> {
        val tvDevice = TVDevice(
            id = "lg_tv_1",
            name = "LG TV",
            ipAddress = ipAddress,
            port = 3001
        )
        return lgtvRepository.connectToTV(tvDevice)
    }
}