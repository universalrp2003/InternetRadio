package com.universalrp.cleansweep.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.AppNetworkTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * On-device local loopback VpnService for real-time app connection & tracker inspection.
 *
 * It does NOT send any data to external servers or act as an internet proxy.
 * It establishes a local tun interface, parses outgoing IPv4 IP/TCP/UDP packet headers
 * to capture destination IPs, ports, and domains (DNS queries), and records them in
 * [AppNetworkTracker.recordVpnConnection].
 */
class CleanSweepVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            // Android 14+ FGS restrictions or notification error
        }

        val success = startPacketInspection()
        if (!success) {
            stopVpn()
            return START_NOT_STICKY
        }

        isVpnRunning = true
        return START_STICKY
    }

    private fun startPacketInspection(): Boolean {
        return try {
            val builder = Builder()
                .setSession("CleanSweep Local Tracker Monitor")
                .addAddress("10.120.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(false)

            // Exclude CleanSweep itself to avoid any loop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    // Ignore if not supported
                }
            }

            vpnInterface = builder.establish()
            val tunFd = vpnInterface?.fileDescriptor ?: return false

            val inputStream = FileInputStream(tunFd)

            scope.launch {
                val packet = ByteBuffer.allocate(32767)
                val buffer = packet.array()

                while (isActive && isVpnRunning) {
                    val length = try {
                        inputStream.read(buffer)
                    } catch (e: Exception) {
                        break
                    }
                    if (length <= 0) {
                        kotlinx.coroutines.delay(10)
                        continue
                    }

                    // Parse IP packet header
                    val version = (buffer[0].toInt() shr 4) and 0x0F
                    if (version == 4 && length >= 20) {
                        val protocol = buffer[9].toInt() and 0xFF
                        val destIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
                        
                        var destPort = 0
                        if ((protocol == 6 || protocol == 17) && length >= 24) { // TCP or UDP
                            val ihl = (buffer[0].toInt() and 0x0F) * 4
                            if (length >= ihl + 4) {
                                destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                            }
                        }

                        // Check DNS query (UDP port 53)
                        var queryDomain: String? = null
                        if (protocol == 17 && destPort == 53 && length > 28) {
                            queryDomain = parseDnsDomain(buffer, length)
                        }

                        if (destIp != "0.0.0.0" && destIp != "127.0.0.1" && !destIp.startsWith("10.120.")) {
                            AppNetworkTracker.recordVpnConnection(
                                context = applicationContext,
                                remoteIp = destIp,
                                remotePort = destPort,
                                domain = queryDomain,
                            )
                        }
                    }

                    packet.clear()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun parseDnsDomain(buffer: ByteArray, length: Int): String? {
        return try {
            val udpHeaderEnd = 28 // 20 IP + 8 UDP
            var ptr = udpHeaderEnd + 12 // Skip 12 byte DNS header
            val sb = StringBuilder()
            while (ptr < length) {
                val labelLen = buffer[ptr].toInt() and 0xFF
                if (labelLen == 0) break
                ptr++
                if (ptr + labelLen > length) break
                if (sb.isNotEmpty()) sb.append(".")
                for (i in 0 until labelLen) {
                    sb.append(buffer[ptr + i].toInt().toChar())
                }
                ptr += labelLen
            }
            if (sb.isNotEmpty()) sb.toString() else null
        } catch (e: Exception) {
            null
        }
    }

    private fun stopVpn() {
        isVpnRunning = false
        scope.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // Ignored
        }
        vpnInterface = null
        try {
            stopForeground(true)
        } catch (e: Exception) {
            // Ignored
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotification(): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            try {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "App Connection Inspector",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Local on-device live app network & tracker monitoring" }
                )
            } catch (e: Exception) {
                // Channel already exists
            }
        }

        val openIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, CleanSweepVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("CleanSweep Tracker Inspector Active")
            .setContentText("Local zero-traffic VPN inspecting active app connections")
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_battery, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.universalrp.cleansweep.vpn.STOP"
        private const val CHANNEL_ID = "cs_vpn_tracker_inspector"
        private const val NOTIFICATION_ID = 7111

        @Volatile
        var isVpnRunning = false
            private set

        fun stop(context: Context) {
            val intent = Intent(context, CleanSweepVpnService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }
}
