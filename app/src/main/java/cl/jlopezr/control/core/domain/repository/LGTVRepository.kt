package cl.jlopezr.control.core.domain.repository

import cl.jlopezr.control.core.domain.model.ConnectionState
import cl.jlopezr.control.core.domain.model.TVDevice
import kotlinx.coroutines.flow.Flow

interface LGTVRepository {
    fun getConnectionState(): Flow<ConnectionState>
    suspend fun connectToTV(tvDevice: TVDevice): Result<Unit>
    suspend fun sendPairingCode(code: String): Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: String): Result<Unit>
}