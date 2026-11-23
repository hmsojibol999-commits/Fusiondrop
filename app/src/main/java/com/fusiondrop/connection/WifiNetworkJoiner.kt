package com.fusiondrop.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Joins the sender's advertised local-only Wi-Fi network on Android 10+. */
@RequiresApi(Build.VERSION_CODES.Q)
class WifiNetworkJoiner(private val context: Context) {
    suspend fun connect(ssid: String, passphrase: String, host: String, port: Int): Pair<Socket, Network> =
        suspendCancellableCoroutine { continuation ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val handler = Handler(Looper.getMainLooper())
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            lateinit var callback: ConnectivityManager.NetworkCallback
            val timeout = Runnable {
                if (finished.compareAndSet(false, true)) {
                    try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
                    continuation.resumeWithException(java.net.SocketTimeoutException("Wi-Fi connection timed out"))
                }
            }
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!finished.compareAndSet(false, true)) return
                    handler.removeCallbacks(timeout)
                    try {
                        val socket = network.socketFactory.createSocket(host, port).apply {
                            receiveBufferSize = 2 * 1024 * 1024
                            sendBufferSize = 2 * 1024 * 1024
                            tcpNoDelay = false
                        }
                        continuation.resume(socket to network)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    } finally {
                        try { cm.unregisterNetworkCallback(this) } catch (_: Exception) {}
                    }
                }
                override fun onUnavailable() {
                    if (finished.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeout)
                        continuation.resumeWithException(IllegalStateException("Could not join the sender's Wi-Fi network"))
                    }
                }
            }
            try {
                cm.requestNetwork(request, callback)
                handler.postDelayed(timeout, 20_000)
            } catch (error: Exception) {
                if (finished.compareAndSet(false, true)) continuation.resumeWithException(error)
            }
            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeout)
                if (finished.compareAndSet(false, true)) try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            }
        }
}
