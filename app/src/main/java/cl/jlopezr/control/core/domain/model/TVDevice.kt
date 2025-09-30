package cl.jlopezr.control.core.domain.model

data class TVDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 3000,
    val isConnected: Boolean = false
)