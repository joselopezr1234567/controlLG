package cl.jlopezr.control.core.data.repository

import android.util.Log
import cl.jlopezr.control.core.domain.model.ConnectionState
import cl.jlopezr.control.core.domain.model.TVDevice
import cl.jlopezr.control.core.domain.repository.LGTVRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LGTVRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LGTVRepository {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var webSocket: WebSocket? = null

    override fun getConnectionState(): Flow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connectToTV(tvDevice: TVDevice): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("LGTVRepository", "Iniciando conexión a ${tvDevice.ipAddress}:${tvDevice.port}")
                _connectionState.value = ConnectionState.CONNECTING
                
                val request = Request.Builder()
                    .url("wss://${tvDevice.ipAddress}:${tvDevice.port}")
                    .build()

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d("LGTVRepository", "WebSocket conectado exitosamente")
                        _connectionState.value = ConnectionState.CONNECTED
                        
                        // Enviar mensaje de handshake para LG TV (formato correcto basado en prueba exitosa)
                        val handshakeMessage = """
                            {
                                "id": "register_1",
                                "type": "register",
                                "payload": {
                                    "forcePairing": false,
                                    "pairingType": "PROMPT",
                                    "manifest": {
                                        "appId": "cl.jlopezr.control",
                                        "vendor": "Jose",
                                        "version": "1.0",
                                        "localizedAppNames": {
                                            "": "Control App"
                                        },
                                        "permissions": [
                                            "LAUNCH",
                                            "CONTROL_INPUT_TEXT",
                                            "CONTROL_INPUT_MEDIA_RECORDING",
                                            "CONTROL_INPUT_MEDIA_PLAYBACK",
                                            "READ_APP_STATUS",
                                            "READ_CURRENT_CHANNEL",
                                            "READ_INSTALLED_APPS",
                                            "READ_NETWORK_STATE",
                                            "READ_RUNNING_APPS",
                                            "READ_TV_CHANNEL_LIST",
                                            "WRITE_NOTIFICATION_TOAST",
                                            "CONTROL_POWER",
                                            "READ_CURRENT_CHANNEL",
                                            "READ_RUNNING_APPS"
                                        ],
                                        "serial": "123456"
                                    }
                                }
                            }
                        """.trimIndent()
                        
                        Log.d("LGTVRepository", "Enviando handshake al TV")
                        webSocket.send(handshakeMessage)
                        
                        if (continuation.isActive) {
                            continuation.resume(Result.success(Unit))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d("LGTVRepository", "Mensaje recibido del TV: $text")
                        
                        try {
                            val json = JSONObject(text)
                            val type = json.optString("type")
                            
                            when (type) {
                                "response" -> {
                                    val id = json.optString("id")
                                    if (id == "register_1") {
                                        val error = json.optJSONObject("error")
                                        if (error != null) {
                                            val errorCode = error.optString("code")
                                            if (errorCode == "401") {
                                                // TV requiere emparejamiento
                                                Log.d("LGTVRepository", "TV requiere código de emparejamiento")
                                                _connectionState.value = ConnectionState.PAIRING
                                            } else {
                                                Log.e("LGTVRepository", "Error de registro: $error")
                                                _connectionState.value = ConnectionState.ERROR
                                            }
                                        } else {
                                            // Verificar si la TV solicita aprobación del usuario
                                            val payload = json.optJSONObject("payload")
                                            val pairingType = payload?.optString("pairingType")
                                            val returnValue = payload?.optBoolean("returnValue", false)
                                            
                                            if (pairingType == "PROMPT" && returnValue == true) {
                                                // TV solicita confirmación del usuario - enviar automáticamente "Sí"
                                                Log.d("LGTVRepository", "TV solicita confirmación - enviando respuesta automática 'Sí'")
                                                
                                                val confirmationMessage = """
                                                    {
                                                        "type": "response",
                                                        "id": "register_1",
                                                        "payload": {
                                                            "returnValue": true
                                                        }
                                                    }
                                                """.trimIndent()
                                                
                                                webSocket.send(confirmationMessage)
                                                Log.d("LGTVRepository", "Respuesta de confirmación enviada automáticamente")
                                                _connectionState.value = ConnectionState.CONNECTED
                                            } else {
                                                // Registro exitoso
                                                Log.d("LGTVRepository", "Registro exitoso, TV conectado")
                                                _connectionState.value = ConnectionState.CONNECTED
                                            }
                                        }
                                    }
                                }
                                "registered" -> {
                                    // Emparejamiento completado exitosamente
                                    Log.d("LGTVRepository", "Emparejamiento completado, TV conectado")
                                    _connectionState.value = ConnectionState.CONNECTED
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LGTVRepository", "Error procesando mensaje: ${e.message}", e)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Log.d("LGTVRepository", "Mensaje binario recibido del TV")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("LGTVRepository", "WebSocket cerrándose: $code - $reason")
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("LGTVRepository", "Error de conexión WebSocket: ${t.message}", t)
                        _connectionState.value = ConnectionState.ERROR
                        
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(t))
                        }
                    }
                }

                webSocket = okHttpClient.newWebSocket(request, listener)
                
                continuation.invokeOnCancellation {
                    Log.d("LGTVRepository", "Conexión cancelada")
                    webSocket?.close(1000, "Cancelled")
                }
                
            } catch (e: Exception) {
                Log.e("LGTVRepository", "Error al iniciar conexión: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
    }

    override suspend fun sendPairingCode(code: String): Result<Unit> {
        return try {
            val pairingMessage = """
                {
                    "type": "register",
                    "id": "register_1",
                    "payload": {
                        "pairingType": "PROMPT",
                        "client-key": "$code",
                        "manifest": {
                            "manifestVersion": 1,
                            "appVersion": "1.1",
                            "signed": {
                                "created": "20140509",
                                "appId": "com.lge.test",
                                "vendorId": "com.lge",
                                "localizedAppNames": {
                                    "": "LG Remote App",
                                    "ko-KR": "리모컨 앱",
                                    "zxx-XX": "ЛГ Rэмotэ AПП"
                                },
                                "localizedVendorNames": {
                                    "": "LG Electronics"
                                },
                                "permissions": [
                                    "TEST_SECURE",
                                    "CONTROL_INPUT_TV",
                                    "CONTROL_POWER",
                                    "READ_APP_STATUS",
                                    "READ_CURRENT_CHANNEL",
                                    "READ_INPUT_DEVICE_LIST",
                                    "READ_NETWORK_STATE",
                                    "READ_RUNNING_APPS",
                                    "READ_TV_CHANNEL_LIST",
                                    "WRITE_NOTIFICATION_TOAST",
                                    "CONTROL_INPUT_JOYSTICK",
                                    "CONTROL_INPUT_MEDIA_RECORDING",
                                    "CONTROL_INPUT_MEDIA_PLAYBACK",
                                    "CONTROL_INPUT_TV",
                                    "READ_INPUT_DEVICE_LIST",
                                    "READ_NETWORK_STATE",
                                    "READ_TV_CHANNEL_LIST",
                                    "CONTROL_TV_SCREEN",
                                    "CONTROL_TV_STANBY",
                                    "CONTROL_VOLUME"
                                ],
                                "serial": "2f930e2d2cfe083771f68e4fe7bb07"
                            },
                            "permissions": [
                                "TEST_SECURE",
                                "CONTROL_INPUT_TV",
                                "CONTROL_POWER",
                                "READ_APP_STATUS",
                                "READ_CURRENT_CHANNEL",
                                "READ_INPUT_DEVICE_LIST",
                                "READ_NETWORK_STATE",
                                "READ_RUNNING_APPS",
                                "READ_TV_CHANNEL_LIST",
                                "WRITE_NOTIFICATION_TOAST",
                                "CONTROL_INPUT_JOYSTICK",
                                "CONTROL_INPUT_MEDIA_RECORDING",
                                "CONTROL_INPUT_MEDIA_PLAYBACK",
                                "CONTROL_INPUT_TV",
                                "READ_INPUT_DEVICE_LIST",
                                "READ_NETWORK_STATE",
                                "READ_TV_CHANNEL_LIST",
                                "CONTROL_TV_SCREEN",
                                "CONTROL_TV_STANBY",
                                "CONTROL_VOLUME"
                            ]
                        }
                    }
                }
            """.trimIndent()
            
            Log.d("LGTVRepository", "Enviando código de emparejamiento: $code")
            webSocket?.send(pairingMessage)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LGTVRepository", "Error enviando código de emparejamiento: ${e.message}", e)
            Result.failure(e)
        }
    }



    override suspend fun disconnect() {
        webSocket?.close(1000, "Desconexión manual")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(command: String): Result<Unit> {
        return try {
            webSocket?.send(command)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}