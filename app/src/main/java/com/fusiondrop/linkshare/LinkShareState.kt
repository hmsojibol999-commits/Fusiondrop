package com.fusiondrop.linkshare

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class LinkShareFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
)

data class LinkShareUiState(
    val isStarting: Boolean = false,
    val isRunning: Boolean = false,
    val url: String = "",
    val message: String = "",
    val fileCount: Int = 0,
    val activeDownloads: Int = 0,
    val error: String? = null
)

object LinkShareState {
    private val mutableState = MutableStateFlow(LinkShareUiState())
    val state: StateFlow<LinkShareUiState> = mutableState.asStateFlow()

    fun starting(fileCount: Int, message: String) {
        mutableState.value = LinkShareUiState(
            isStarting = true,
            fileCount = fileCount,
            message = message
        )
    }

    fun running(url: String, fileCount: Int, message: String) {
        mutableState.value = LinkShareUiState(
            isRunning = true,
            url = url,
            fileCount = fileCount,
            message = message
        )
    }

    fun setActiveDownloads(count: Int) {
        mutableState.update { it.copy(activeDownloads = count.coerceAtLeast(0)) }
    }

    fun failed(reason: String) {
        mutableState.value = LinkShareUiState(error = reason)
    }

    fun stopped() {
        mutableState.value = LinkShareUiState()
    }
}
