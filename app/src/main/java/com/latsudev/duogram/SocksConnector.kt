package com.latsudev.duogram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

data class SocksConfig(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val version: SocksVersion = SocksVersion.SOCKS5
)

enum class SocksVersion { SOCKS4, SOCKS5 }

object SocksConnector {

    fun createClient(config: SocksConfig): OkHttpClient {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port))
        val builder = OkHttpClient.Builder().proxy(proxy)

        if (!config.username.isNullOrBlank() && !config.password.isNullOrBlank()) {
            val credential = Credentials.basic(config.username, config.password)
            builder.proxyAuthenticator { _, response ->
                response.request.newBuilder().header("Proxy-Authorization", credential).build()
            }
        }
        return builder.build()
    }

    suspend fun testConnection(config: SocksConfig, url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            createClient(config).newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
