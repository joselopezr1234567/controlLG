package cl.jlopezr.control.home.domain.usecase

import cl.jlopezr.control.core.data.service.TVDiscoveryService
import cl.jlopezr.control.core.domain.model.DiscoveredTV
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DiscoverTVsUseCase @Inject constructor(
    private val tvDiscoveryService: TVDiscoveryService
) {
    operator fun invoke(): Flow<List<DiscoveredTV>> {
        return tvDiscoveryService.discoverTVs()
    }
}