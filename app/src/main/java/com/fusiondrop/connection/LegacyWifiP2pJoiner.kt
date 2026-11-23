package com.fusiondrop.connection

import android.content.Context
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android 7–9 peer association using the sender's Wi-Fi Direct device address. */
class LegacyWifiP2pJoiner(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context.applicationContext, context.mainLooper, null)

    suspend fun connect(ownerAddress: String, port: Int): Socket = withTimeout(20_000) {
        val config = WifiP2pConfig().apply {
            deviceAddress = ownerAddress
            wps = WpsInfo().apply { setup = WpsInfo.PBC }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onFailure(reason: Int) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not connect to sender ($reason)")) }
            })
        }
        repeat(30) {
            delay(500)
            val info = suspendCancellableCoroutine<WifiP2pInfo> { continuation ->
                manager.requestConnectionInfo(channel) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
            if (info.groupFormed && info.groupOwnerAddress != null) {
                return@withTimeout Socket(info.groupOwnerAddress, port).apply { receiveBufferSize = 2 * 1024 * 1024 }
            }
        }
        throw java.net.SocketTimeoutException("Wi-Fi Direct connection timed out")
    }
}
