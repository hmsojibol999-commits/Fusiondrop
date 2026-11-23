package com.fusiondrop.connection

import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wi-Fi Direct setup is isolated from the byte-stream transfer engine. */
class WifiDirectConnection(context: Context) : TransferConnection {
    private val manager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context.applicationContext, context.mainLooper, null)

    override suspend fun openSender(port: Int): ServerSocket = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        ServerSocket(port).apply { soTimeout = 120_000; receiveBufferSize = 2 * 1024 * 1024 }
    }

    suspend fun createGroup(): Triple<String, String, String> {
        if (Build.VERSION.SDK_INT >= 29) {
            val passphrase = (1..16).map { "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".random() }.joinToString("")
            suspendCancellableCoroutine<Unit> { continuation ->
                fun create(band: Int) {
                    val config = WifiP2pConfig.Builder()
                        .setNetworkName("DIRECT-FusionDrop-${System.currentTimeMillis().toString(36).takeLast(4)}")
                        .setPassphrase(passphrase)
                        .setGroupOperatingBand(band)
                        .build()
                    manager.createGroup(channel, config, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                        override fun onFailure(reason: Int) {
                            if (band == WifiP2pConfig.GROUP_OWNER_BAND_5GHZ) create(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
                            else if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Wi-Fi Direct group failed ($reason)"))
                        }
                    })
                }
                create(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
            }
        } else {
            suspendCancellableCoroutine<Unit> { continuation ->
                manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                    override fun onFailure(reason: Int) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Wi-Fi Direct group failed ($reason)")) }
                })
            }
        }
        repeat(25) {
            val group = requestGroupInfo()
            if (group != null && !group.owner.deviceAddress.isNullOrBlank()) {
                val ssid = if (Build.VERSION.SDK_INT >= 29) group.networkName ?: "" else ""
                val passphrase = if (Build.VERSION.SDK_INT >= 29) group.passphrase ?: "" else ""
                return Triple(ssid, passphrase, group.owner.deviceAddress)
            }
            delay(400)
        }
        throw IllegalStateException("Wi-Fi Direct group details were not available")
    }

    private suspend fun requestGroupInfo(): WifiP2pGroup? = suspendCancellableCoroutine { continuation ->
        manager.requestGroupInfo(channel) { group -> if (continuation.isActive) continuation.resume(group) }
    }

    override suspend fun connectReceiver(host: String, port: Int): Socket = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Socket(host, port).apply { tcpNoDelay = false; receiveBufferSize = 2 * 1024 * 1024 }
    }

    override fun close() { try { manager.removeGroup(channel, null) } catch (_: Exception) {} }
}
