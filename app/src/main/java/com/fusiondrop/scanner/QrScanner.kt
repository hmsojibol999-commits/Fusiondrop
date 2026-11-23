package com.fusiondrop

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun QrScanner(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var showExplanation by remember { mutableStateOf(!granted) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        denied = !allowed
        showExplanation = false
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Camera access") },
            text = { Text("FusionDrop uses the camera only to scan the sender's QR code. It does not record or upload video.") },
            confirmButton = { TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("Not now") } }
        )
    }
    if (!granted) {
        Column(Modifier.fillMaxWidth().height(480.dp), verticalArrangement = Arrangement.Center) {
            Text(if (denied) "Camera permission was denied." else "Camera permission is needed to scan the QR code.")
            TextButton(onClick = { showExplanation = true }) { Text(if (denied) "Try again" else "Allow camera") }
        }
        return
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val delivered = AtomicBoolean(false)
                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
                        }
                        analysis.setAnalyzer(executor) { image ->
                            try {
                                val plane = image.planes.first()
                                val buffer = plane.buffer
                                val bytes = ByteArray(image.width * image.height)
                                for (row in 0 until image.height) {
                                    for (col in 0 until image.width) {
                                        bytes[row * image.width + col] = buffer.get(row * plane.rowStride + col * plane.pixelStride)
                                    }
                                }
                                val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
                                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                                if (delivered.compareAndSet(false, true)) onCode(result.text)
                            } catch (_: Exception) {
                            } finally {
                                reader.reset()
                                image.close()
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = Modifier.fillMaxWidth().height(480.dp)
    )
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
}
