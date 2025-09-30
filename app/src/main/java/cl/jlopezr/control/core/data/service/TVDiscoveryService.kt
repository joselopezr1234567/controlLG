package cl.jlopezr.control.core.data.service

import android.content.Context
import android.util.Log
import android.net.wifi.WifiManager
import cl.jlopezr.control.core.domain.model.DiscoveredTV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TVDiscoveryService @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DISCOVERY_TIMEOUT = 5000L
        private const val SSDP_SEARCH_MESSAGE = 
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "ST: upnp:rootdevice\r\n" +
            "MX: 3\r\n\r\n"
    }

    fun discoverTVs(): Flow<List<DiscoveredTV>> = flow {
        val discoveredDevices = mutableListOf<DiscoveredTV>()
        
        Log.d("TVDiscoveryService", "Iniciando descubrimiento de TVs...")
        
        // Agregar TV simulado para pruebas en emulador
        val simulatedTV = DiscoveredTV(
            id = "simulated_lg_tv",
            name = "LG TV Simulado",
            ipAddress = "192.168.1.100",
            port = 3001,
            modelName = "LG OLED55C1",
            manufacturer = "LG Electronics",
            location = "http://192.168.1.100:1400/description.xml",
            isLGTV = true
        )
        discoveredDevices.add(simulatedTV)
        Log.d("TVDiscoveryService", "TV simulado agregado: ${simulatedTV.name} - ${simulatedTV.ipAddress}")
        
        try {
            // Habilitar multicast
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val multicastLock = wifiManager.createMulticastLock("TVDiscovery")
            multicastLock.acquire()

            try {
                val socket = MulticastSocket()
                socket.soTimeout = DISCOVERY_TIMEOUT.toInt()
                
                // Enviar mensaje SSDP
                val searchMessage = SSDP_SEARCH_MESSAGE.toByteArray()
                val searchPacket = DatagramPacket(
                    searchMessage,
                    searchMessage.size,
                    InetAddress.getByName(SSDP_ADDRESS),
                    SSDP_PORT
                )
                
                socket.send(searchPacket)
                
                // Escuchar respuestas
                val startTime = System.currentTimeMillis()
                val buffer = ByteArray(1024)
                
                while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        
                        val response = String(packet.data, 0, packet.length)
                        val device = parseSSDP(response, packet.address.hostAddress)
                        
                        if (device != null && !discoveredDevices.any { it.ipAddress == device.ipAddress }) {
                            discoveredDevices.add(device)
                            emit(discoveredDevices.toList())
                        }
                    } catch (e: Exception) {
                        // Timeout o error de socket, continuar
                    }
                }
                
                socket.close()
            } finally {
                multicastLock.release()
            }
            
        } catch (e: Exception) {
            Log.e("TVDiscoveryService", "Error en descubrimiento SSDP: ${e.message}", e)
            // Error en descubrimiento, emitir lista vacía
            emit(emptyList())
        }
        
        // Emitir resultado final
        Log.d("TVDiscoveryService", "Descubrimiento completado. TVs encontrados: ${discoveredDevices.size}")
        discoveredDevices.forEach { tv ->
            Log.d("TVDiscoveryService", "TV: ${tv.name} - IP: ${tv.ipAddress}")
        }
        emit(discoveredDevices.toList())
    }.flowOn(Dispatchers.IO)

    private suspend fun parseSSDP(response: String, ipAddress: String?): DiscoveredTV? {
        if (ipAddress == null) return null
        
        val lines = response.split("\r\n")
        var location: String? = null
        var server: String? = null
        var usn: String? = null
        
        for (line in lines) {
            when {
                line.startsWith("LOCATION:", ignoreCase = true) -> {
                    location = line.substringAfter(":").trim()
                }
                line.startsWith("SERVER:", ignoreCase = true) -> {
                    server = line.substringAfter(":").trim()
                }
                line.startsWith("USN:", ignoreCase = true) -> {
                    usn = line.substringAfter(":").trim()
                }
            }
        }
        
        // Verificar si es un dispositivo LG
        val isLGDevice = server?.contains("LG", ignoreCase = true) == true ||
                        usn?.contains("LG", ignoreCase = true) == true
        
        if (!isLGDevice && location != null) {
            // Intentar obtener más información del dispositivo
            val deviceInfo = getDeviceInfo(location)
            if (deviceInfo?.manufacturer?.contains("LG", ignoreCase = true) == true) {
                return deviceInfo.copy(ipAddress = ipAddress, isLGTV = true)
            }
        }
        
        if (isLGDevice) {
            return DiscoveredTV(
                id = usn ?: "lg_${ipAddress.replace(".", "_")}",
                name = "LG TV ($ipAddress)",
                ipAddress = ipAddress,
                port = 3001,
                location = location,
                manufacturer = "LG",
                isLGTV = true
            )
        }
        
        return null
    }
    
    private suspend fun getDeviceInfo(location: String): DiscoveredTV? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(location)
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: return@withContext null
                parseDeviceXML(xml, location)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseDeviceXML(xml: String, location: String): DiscoveredTV? {
        try {
            val manufacturer = extractXMLValue(xml, "manufacturer")
            val modelName = extractXMLValue(xml, "modelName")
            val friendlyName = extractXMLValue(xml, "friendlyName")
            val udn = extractXMLValue(xml, "UDN")
            
            val isLG = manufacturer?.contains("LG", ignoreCase = true) == true ||
                      modelName?.contains("LG", ignoreCase = true) == true ||
                      friendlyName?.contains("LG", ignoreCase = true) == true
            
            if (isLG) {
                val ipAddress = extractIPFromLocation(location)
                return DiscoveredTV(
                    id = udn ?: "lg_${ipAddress?.replace(".", "_") ?: "unknown"}",
                    name = friendlyName ?: modelName ?: "LG TV",
                    ipAddress = ipAddress ?: "",
                    port = 3000,
                    modelName = modelName,
                    manufacturer = manufacturer,
                    location = location,
                    isLGTV = true
                )
            }
        } catch (e: Exception) {
            // Error parsing XML
        }
        
        return null
    }
    
    private fun extractXMLValue(xml: String, tagName: String): String? {
        val startTag = "<$tagName>"
        val endTag = "</$tagName>"
        val startIndex = xml.indexOf(startTag, ignoreCase = true)
        if (startIndex == -1) return null
        
        val valueStart = startIndex + startTag.length
        val endIndex = xml.indexOf(endTag, valueStart, ignoreCase = true)
        if (endIndex == -1) return null
        
        return xml.substring(valueStart, endIndex).trim()
    }
    
    private fun extractIPFromLocation(location: String): String? {
        return try {
            val url = java.net.URL(location)
            url.host
        } catch (e: Exception) {
            null
        }
    }
}