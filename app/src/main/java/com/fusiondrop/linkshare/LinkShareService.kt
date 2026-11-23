package com.fusiondrop.linkshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fusiondrop.MainActivity
import com.fusiondrop.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LinkShareService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var server: LinkShareHttpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FusionDrop:LinkShareWifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
        try {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FusionDrop:LinkShare").apply {
                setReferenceCounted(false)
                acquire(6L * 60L * 60L * 1000L)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSharing()
                return START_NOT_STICKY
            }
            ACTION_START -> startSharing(intent)
            else -> {
                LinkShareState.failed("The share request was empty. Select files and try again.")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startSharing(intent: Intent) {
        // A repeated Start request replaces the previous temporary share cleanly.
        server?.close()
        server = null
        val uris = readUris(intent)
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty().trim().take(180)
        if (uris.isEmpty()) {
            LinkShareState.failed("Choose at least one file before starting Link Share.")
            stopSelf()
            return
        }

        LinkShareState.starting(uris.size, message)
        startForeground(NOTIFICATION_ID, buildNotification("Preparing your private share link…"))

        serviceScope.launch {
            try {
                val files = uris.map { uri -> describeFile(uri) }
                val httpServer = LinkShareHttpServer(
                    context = applicationContext,
                    files = files,
                    welcomeMessage = message,
                    onActiveDownloadsChanged = LinkShareState::setActiveDownloads
                )
                val url = httpServer.start()
                server = httpServer
                LinkShareState.running(url, files.size, message)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification("Link ready · tap Stop to close sharing"))
            } catch (error: Exception) {
                server?.close()
                server = null
                LinkShareState.failed(error.message ?: "Could not start Link Share.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun describeFile(uri: Uri): LinkShareFile {
        var name = "Shared file"
        var size = -1L
        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0L } ?: -1L
            }
        }
        if (size < 0L) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    if (descriptor.length >= 0L) size = descriptor.length
                }
            } catch (_: Exception) {}
        }
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        return LinkShareFile(uri, name, size, mime)
    }

    @Suppress("DEPRECATION")
    private fun readUris(intent: Intent): ArrayList<Uri> {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java) ?: arrayListOf()
        } else {
            intent.getParcelableArrayListExtra(EXTRA_URIS) ?: arrayListOf()
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "fusiondrop-link-share"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Link Share", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = Intent(this, LinkShareService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            82,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            83,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("FusionDrop · Link Share")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop sharing", stopPendingIntent)
            .build()
    }

    private fun stopSharing() {
        server?.close()
        server = null
        LinkShareState.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSharing()
    }

    override fun onDestroy() {
        server?.close()
        server = null
        serviceScope.cancel()
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        if (LinkShareState.state.value.isRunning || LinkShareState.state.value.isStarting) {
            LinkShareState.stopped()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.fusiondrop.linkshare.START"
        const val ACTION_STOP = "com.fusiondrop.linkshare.STOP"
        private const val EXTRA_URIS = "link_share_uris"
        private const val EXTRA_MESSAGE = "link_share_welcome_message"
        private const val NOTIFICATION_ID = 82

        fun start(context: android.content.Context, uris: List<Uri>, welcomeMessage: String) {
            val intent = Intent(context, LinkShareService::class.java)
                .setAction(ACTION_START)
                .putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                .putExtra(EXTRA_MESSAGE, welcomeMessage)
            LinkShareState.starting(uris.size, welcomeMessage)
            try {
                if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
                else context.startService(intent)
            } catch (error: Exception) {
                LinkShareState.failed(error.message ?: "Android could not start Link Share.")
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, LinkShareService::class.java).setAction(ACTION_STOP)
            try { context.startService(intent) } catch (_: Exception) { LinkShareState.stopped() }
        }
    }
}
