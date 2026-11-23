package com.fusiondrop.connection

import java.net.Socket

interface TransferConnection { suspend fun openSender(port: Int): java.net.ServerSocket; suspend fun connectReceiver(host: String, port: Int): Socket; fun close() }
