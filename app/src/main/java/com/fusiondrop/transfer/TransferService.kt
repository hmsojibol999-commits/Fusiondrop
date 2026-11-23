package com.fusiondrop.transfer

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TransferService : Service() {
 private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
 private var wakeLock: android.os.PowerManager.WakeLock? = null
 override fun onCreate() {
  super.onCreate()
  try { val wm = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager; wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "FusionDrop:Wifi").apply { setReferenceCounted(false); acquire() } } catch (_: Exception) {}
  try { val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager; wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "FusionDrop:Transfer").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) } } catch (_: Exception) {}
 }
 override fun onDestroy() { try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}; try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}; super.onDestroy() }
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  val channelId="fusiondrop-transfer"; if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channelId,"File transfer",NotificationManager.IMPORTANCE_LOW))
  val notification=NotificationCompat.Builder(this,channelId).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("FusionDrop transfer").setContentText("Transfer is running").setOngoing(true).build()
  if(Build.VERSION.SDK_INT>=29) startForeground(7,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(7,notification)
  return START_NOT_STICKY
 }
}
