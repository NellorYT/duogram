package com.latsudev.duogram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocketFactory

class VpsRelayConnector(
    private val hostPort: String,
    private val accessToken: String? = null
) {

    suspend fun healthCheck(timeoutMs: Int = 4_000): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (host, port) = parseHostPort(hostPort)
            java.net.Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    suspend fun sendViaVps(contactId: String, payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (host, port) = parseHostPort(hostPort)
            val sslSocket = SSLSocketFactory.getDefault().createSocket(host, port)
            sslSocket.use { socket ->
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val encoded = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
                val line = """{"token":"${accessToken.orEmpty()}","contactId":"$contactId","payload":"$encoded"}"""
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
            true
        }.getOrDefault(false)
    }

    private fun parseHostPort(value: String): Pair<String, Int> {
        val parts = value.split(":")
        val host = parts.firstOrNull().orEmpty().ifBlank { "127.0.0.1" }
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
        return host to port
    }
}
