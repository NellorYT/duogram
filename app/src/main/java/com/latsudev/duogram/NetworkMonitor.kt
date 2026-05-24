package com.latsudev.duogram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val prefs by lazy { getSharedPreferences("duogram_prefs", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(71, buildNotification("Network monitor starting..."))
        scope.launch {
            while (isActive) {
                val status = getConnectionStatus()
                val mode = prefs.getString("active_transport", TransportMode.WEBRTC.name) ?: TransportMode.WEBRTC.name
                val health = when (TransportMode.valueOf(mode)) {
                    TransportMode.VPS -> VpsRelayConnector(
                        prefs.getString("vps_host", "").orEmpty(),
                        prefs.getString("vps_token", null)
                    ).healthCheck()

                    TransportMode.SOCKS, TransportMode.TOR -> SocksConnector.testConnection(
                        SocksConfig(
                            host = prefs.getString("socks_host", "127.0.0.1").orEmpty(),
                            port = prefs.getString("socks_port", "1080")?.toIntOrNull() ?: 1080,
                            username = prefs.getString("socks_login", null),
                            password = prefs.getString("socks_password", null)
                        ),
                        "https://example.com"
                    )

                    TransportMode.WEBRTC, TransportMode.LAN -> true
                }
                notifyStatus("$status • $mode ${if (health) "✅" else "❌"}")
                delay(30_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun getConnectionStatus(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("duogram_network", "Duogram Network", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
    }

    private fun notifyStatus(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(71, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "duogram_network")
            .setSmallIcon(R.drawable.ic_duogram)
            .setContentTitle("Duogram Transport")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
}
