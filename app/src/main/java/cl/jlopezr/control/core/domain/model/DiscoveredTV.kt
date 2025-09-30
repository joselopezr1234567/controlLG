package cl.jlopezr.control.core.domain.model

data class DiscoveredTV(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 3001,
    val modelName: String? = null,
    val manufacturer: String? = null,
    val uuid: String? = null,
    val location: String? = null,
    val isLGTV: Boolean = false
) {
    fun toTVDevice(): TVDevice {
        return TVDevice(
            id = id,
            name = name,
            ipAddress = ipAddress,
            port = port,
            isConnected = false
        )
    }
}