package com.fusiondrop

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fusiondrop.connection.WifiDirectConnection
import com.fusiondrop.linkshare.LinkShareService
import com.fusiondrop.linkshare.LinkShareState
import com.fusiondrop.linkshare.LinkShareUiState
import com.fusiondrop.transfer.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ServerSocket

private val Blue = Color(0xFF3978F6)
private val Cyan = Color(0xFF24CDE0)
private val AppBackground = Color(0xFF080E1A)
private val Panel = Color(0xFF111C2D)
private val PanelRaised = Color(0xFF17243A)
private val PrimaryText = Color(0xFFF3F7FF)
private val MutedText = Color(0xFF91A3BC)
private val Hairline = Color(0xFF1F2E45)
private val OutlineColor = Color(0xFF2B3D56)
private val Success = Color(0xFF35D49A)
private val ButtonShape = RoundedCornerShape(14.dp)
private val OutlineBorder = BorderStroke(1.dp, OutlineColor)

@Composable
private fun outlinedColors() = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText)

class MainActivity : ComponentActivity() {
    private var selectedUris by mutableStateOf<List<Uri>>(emptyList())
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: SecurityException) {}
            }
            selectedUris = uris
        }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) {
            Toast.makeText(this, "Allow the requested permission in Settings to continue.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.045f)
                .scaleY(1.045f)
                .setDuration(220L)
                .withEndAction { provider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        setContent {
            FusionDropApp(
                files = selectedUris,
                onPick = { purpose ->
                    picker.launch(arrayOf("*/*"))
                },
                onClear = { selectedUris = emptyList() },
                onRequestPermissions = { requestPermissionsFor(it) }
            )
        }
    }

    private fun requestPermissionsFor(mode: String) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (mode == "send" || mode == "receive") permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.POST_NOTIFICATIONS
        } else if (mode == "send" || mode == "receive") {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            if (mode == "receive" && Build.VERSION.SDK_INT <= 28) permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, "FusionDrop needs permission to connect and transfer files.", Toast.LENGTH_LONG).show()
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
private fun FusionDropApp(
    files: List<Uri>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onRequestPermissions: (String) -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var qr by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<TransferProgress?>(null) }
    var error by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(0.0) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var connection by remember { mutableStateOf<WifiDirectConnection?>(null) }
    val linkState by LinkShareState.state.collectAsState()

    val scheme = darkColorScheme(
        primary = Blue,
        secondary = Cyan,
        background = AppBackground,
        surface = Panel,
        surfaceVariant = PanelRaised,
        onPrimary = Color.White,
        onSecondary = Color(0xFF061321),
        onBackground = PrimaryText,
        onSurface = PrimaryText,
        onSurfaceVariant = MutedText,
        outline = Color(0xFF2B3D56)
    )

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = AppBackground) {
            Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(340.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFF10223D), AppBackground))
                )
            )
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                TopBar(page = page, onBack = { page = "home" }, onSettings = { page = "settings" })
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 18 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 28 })
                    },
                    label = "FusionDropPageTransition"
                ) { currentPage ->
                    when (currentPage) {
                        "home" -> HomeScreen(
                            onSend = { onRequestPermissions("send"); page = "select"; onPick("send") },
                            onReceive = { onRequestPermissions("receive"); page = "scan" },
                            onLinkShare = { onRequestPermissions("link"); page = "link" }
                        )
                        "select" -> SelectFilesScreen(
                            files = files,
                            onPick = { onPick("send") },
                            onClear = onClear,
                            onStart = {
                                startSender(
                                    context = context,
                                    files = files,
                                    connection = { connection },
                                    setConnection = { connection = it },
                                    onQr = { qr = it; page = "qr" },
                                    onError = { error = it; page = "error" },
                                    onProgress = { progress = it; speed = it.bytesPerSecond; page = "transfer" }
                                )
                            }
                        )
                        "qr" -> QrReadyScreen(qr = qr, onCancel = { connection?.close(); page = "home" })
                        "scan" -> ScanScreen(
                            onCode = { raw ->
                                try {
                                    val obj = JSONObject(raw)
                                    val host = obj.getString("host")
                                    val port = obj.getInt("port")
                                    val ssid = obj.optString("ssid")
                                    val password = obj.optString("password")
                                    val owner = obj.optString("ownerAddress")
                                    page = "connecting"
                                    startReceiver(
                                        context, host, port, ssid, password, owner,
                                        onProgress = { progress = it; speed = it.bytesPerSecond; page = "transfer" },
                                        onDone = { received -> history = listOf("Received ${received.size} file(s)") + history; page = "done" },
                                        onError = { error = it; page = "error" }
                                    )
                                } catch (_: Exception) {
                                    error = "This QR code is not a valid FusionDrop connection."
                                    page = "error"
                                }
                            }
                        )
                        "connecting" -> CenterMessage("Connecting…", "Keep both phones close and Wi-Fi enabled.")
                        "transfer" -> TransferScreen(progress = progress, speed = speed, onCancel = { connection?.close(); page = "home" })
                        "done" -> CenterMessage("Transfer complete", "Your files are saved in Downloads / FlashDrop.")
                        "link" -> LinkShareScreen(
                            files = files,
                            state = linkState,
                            onPick = { onPick("link") },
                            onStart = { LinkShareService.start(context, files, it) },
                            onStop = { LinkShareService.stop(context) }
                        )
                        "history" -> HistoryScreen(history)
                        "settings" -> SettingsScreen(onHistory = { page = "history" })
                        "error" -> CenterMessage("Could not transfer", error, action = { page = "home" })
                        else -> HomeScreen(
                            onSend = { page = "select" },
                            onReceive = { page = "scan" },
                            onLinkShare = { page = "link" }
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun TopBar(page: String, onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page == "home") {
            BrandMark(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("FusionDrop", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = PrimaryText, letterSpacing = (-0.3).sp)
                Text("PRIVATE FILE TRANSFER", fontSize = 10.sp, letterSpacing = 1.6.sp, color = MutedText, fontWeight = FontWeight.SemiBold)
            }
        } else {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Panel).border(1.dp, Hairline, CircleShape)
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryText, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Text(pageTitle(page), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryText, letterSpacing = (-0.2).sp)
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Panel).border(1.dp, Hairline, CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MutedText, modifier = Modifier.size(20.dp))
        }
    }
}

private fun pageTitle(page: String): String = when (page) {
    "select" -> "Send files"
    "qr" -> "Ready to connect"
    "scan" -> "Receive files"
    "connecting" -> "Connecting"
    "transfer" -> "Transfer in progress"
    "done" -> "Transfer complete"
    "link" -> "Share by link"
    "history" -> "Transfer history"
    "settings" -> "Settings"
    else -> "FusionDrop"
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Blue, Cyan))),
        contentAlignment = Alignment.Center
    ) {
        Text("F", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit, onLinkShare: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.92f).clip(RoundedCornerShape(22.dp)).border(1.dp, Hairline, RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.fusiondrop_logo),
                contentDescription = "FusionDrop artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x33060B14), Color(0xE6060B14)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text("FAST · PRIVATE · ORIGINAL", color = Color(0xFF9CECF5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("Move files. Keep quality.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Your files, your way.", color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text("Choose how you want to share today.", color = MutedText, fontSize = 14.sp)
        }

        Text("TRANSFER MODES", color = MutedText, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        TransferModeCard(
            title = "Send to another phone",
            subtitle = "Direct phone-to-phone transfer",
            icon = Icons.Default.Send,
            accent = Blue,
            onClick = onSend
        )
        TransferModeCard(
            title = "Receive from a phone",
            subtitle = "Scan a FusionDrop QR code",
            icon = Icons.Default.QrCodeScanner,
            accent = Color(0xFF7A68F7),
            onClick = onReceive
        )
        TransferModeCard(
            title = "Share by browser link",
            subtitle = "Friends download from any browser",
            icon = Icons.Default.Link,
            accent = Cyan,
            featured = true,
            onClick = onLinkShare
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF7C93B2), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("No cloud upload · No file compression", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransferModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    featured: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, animationSpec = spring(stiffness = 600f), label = "modeCardScale")
    Surface(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (featured) Color(0xFF0F2438) else Panel,
        border = BorderStroke(1.dp, if (featured) Cyan.copy(alpha = 0.40f) else Hairline)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (featured) {
                        Spacer(Modifier.width(8.dp))
                        Surface(modifier = Modifier.widthIn(min = 48.dp), shape = CircleShape, color = Cyan.copy(alpha = 0.16f)) {
                            Text("NEW", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF7DE8EF), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Text(subtitle, color = MutedText, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (featured) Cyan else MutedText, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SelectFilesScreen(files: List<Uri>, onPick: () -> Unit, onClear: () -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        ScreenIntro("Send files directly", "Choose photos, videos, documents or any other file. Originals stay unchanged.")
        files.forEach { uri -> FileRow(name = queryName(context, uri), size = querySize(context, uri)) }
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) {
            Text(if (files.isEmpty()) "Choose files" else "Add more files")
        }
        if (files.isNotEmpty()) {
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("Clear selection", color = MutedText) }
            GradientButton(text = "Start phone transfer", icon = Icons.Default.Bolt, enabled = files.isNotEmpty(), onClick = onStart)
        }
        InfoPanel("Both phones need to be close. The receiving phone scans the QR code shown by the sender.")
    }
}

@Composable
private fun QrReadyScreen(qr: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenIntro("Ready to connect", "Open FusionDrop on the other phone, tap Receive and scan this code.")
        val bitmap = remember(qr) { qrBitmap(qr) }
        if (bitmap != null) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Image(bitmap.asImageBitmap(), contentDescription = "Connection QR code", modifier = Modifier.padding(18.dp).fillMaxWidth().aspectRatio(1f))
            }
        }
        InfoPanel("Keep this screen open until the other phone connects. Both devices should have Wi-Fi enabled.")
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) { Text("Cancel transfer") }
    }
}

@Composable
private fun ScanScreen(onCode: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenIntro("Scan to connect", "Point your camera at the QR code on the sender's phone.")
        Spacer(Modifier.height(8.dp))
        QrScanner(onCode = onCode)
    }
}

@Composable
private fun TransferScreen(progress: TransferProgress?, speed: Double, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenIntro("Transferring your files", progress?.fileName ?: "Preparing the connection…")
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("TRANSFER SPEED", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("${String.format("%.1f", speed / 1024 / 1024)} MB/s", color = PrimaryText, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                LinearProgressIndicator(
                    progress = { progress?.let { if (it.totalSize > 0) it.totalBytes.toFloat() / it.totalSize else 0f } ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Cyan,
                    trackColor = PanelRaised,
                    drawStopIndicator = {}
                )
                Text("${progress?.totalBytes?.let(::formatBytes) ?: "0 B"} / ${progress?.totalSize?.let(::formatBytes) ?: "—"}", color = MutedText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) { Text("Close transfer screen") }
    }
}

@Composable
private fun LinkShareScreen(
    files: List<Uri>,
    state: LinkShareUiState,
    onPick: () -> Unit,
    onStart: (String) -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var welcomeMessage by remember { mutableStateOf("A few files for you — enjoy!") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenIntro("One link. Any browser.", "Share a private link with a friend on the same Wi-Fi or your phone hotspot. They do not need the app.")

        if (state.isRunning) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2730)), border = BorderStroke(1.dp, Color(0xFF1F6B63)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFF16503F), shape = CircleShape) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Success))
                                Spacer(Modifier.width(6.dp))
                                Text("LIVE", color = Color(0xFFB6FFE0), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${state.fileCount} files", color = Color(0xFFB8D4D3), fontSize = 12.sp)
                    }
                    Text("Your share link is ready", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Surface(color = Color(0xFF0A1A21), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF1B3B44)), modifier = Modifier.fillMaxWidth()) {
                        Text(state.url, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Color(0xFF9FEAF0), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("FusionDrop share link", state.url))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Copy link")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.url)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share FusionDrop link"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ButtonShape,
                            border = OutlineBorder,
                            colors = outlinedColors()
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Share")
                        }
                    }
                    val qr = remember(state.url) { qrBitmap(state.url) }
                    if (qr != null) {
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.align(Alignment.CenterHorizontally).size(196.dp)) {
                            Image(qr.asImageBitmap(), contentDescription = "Link Share QR code", modifier = Modifier.padding(10.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FileDownload, null, tint = Color(0xFF9FEED0), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${state.activeDownloads} active download(s)", color = Color(0xFFD1EBE7), fontSize = 12.sp)
                    }
                    Text("Keep this screen or the notification active while your friend downloads. Anyone with this private link on your network can access the shared files.", color = Color(0xFF9FB8B6), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = BorderStroke(1.dp, Color(0xFF5A3038)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9A9A))) {
                Icon(Icons.Default.StopCircle, null); Spacer(Modifier.width(8.dp)); Text("Stop sharing")
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Hairline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Cyan.copy(alpha = 0.14f)).border(1.dp, Cyan.copy(alpha = 0.28f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderOpen, null, tint = Cyan)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Files to share", color = PrimaryText, fontWeight = FontWeight.Bold)
                            Text("${files.size} selected", color = MutedText, fontSize = 12.sp)
                        }
                    }
                    if (files.isNotEmpty()) {
                        files.take(8).forEach { uri -> FileRow(queryName(context, uri), querySize(context, uri)) }
                        if (files.size > 8) Text("and ${files.size - 8} more file(s)", color = MutedText, fontSize = 12.sp)
                    } else {
                        Text("No files selected yet.", color = MutedText, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) {
                        Text(if (files.isEmpty()) "Choose files" else "Change selected files")
                    }
                }
            }
            OutlinedTextField(
                value = welcomeMessage,
                onValueChange = { welcomeMessage = it.take(180) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Welcome note for your friend") },
                supportingText = { Text("Optional · shown at the top of their browser page") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    cursorColor = Cyan,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = OutlineColor,
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = MutedText,
                    focusedSupportingTextColor = MutedText,
                    unfocusedSupportingTextColor = MutedText
                )
            )
            if (state.isStarting) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Starting your local share…", color = MutedText, fontSize = 13.sp)
                }
            }
            state.error?.let { InfoPanel(it, warning = true) }
            GradientButton(text = "Start sharing", icon = Icons.Default.Link, enabled = files.isNotEmpty() && !state.isStarting, onClick = { onStart(welcomeMessage.trim()) })
            InfoPanel("Both devices must be connected to the same Wi-Fi network or your hotspot. Your phone serves the original files directly; no cloud account, upload or compression is used.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Security, null, tint = Color(0xFFFFD27A), modifier = Modifier.size(16.dp))
                Text("Use a trusted network. Anyone who receives the link can download these files until you stop sharing.", color = MutedText, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenIntro("Recent activity", "Transfers recorded during this app session.")
        if (history.isEmpty()) {
            InfoPanel("No transfers in this session yet.")
        } else {
            history.forEach { entry -> FileRow(entry, "Completed") }
        }
    }
}

@Composable
private fun SettingsScreen(onHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        ScreenIntro("App preferences", "A few details about how FusionDrop handles your files.")
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SHA-256 verification", color = PrimaryText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("PLANNED", color = Color(0xFFFFD27A), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Text("The current phone-transfer protocol does not yet calculate and compare SHA-256 hashes. This switch is intentionally not presented as an active feature.", color = MutedText, fontSize = 12.sp)
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Hairline)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Save location", color = PrimaryText, fontWeight = FontWeight.Bold)
                Text("Downloads / FlashDrop", color = MutedText, fontSize = 13.sp)
                Text("Language follows the phone's system language. More Bengali translations can be added later.", color = MutedText, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) {
            Icon(Icons.Default.History, null); Spacer(Modifier.width(8.dp)); Text("Transfer history")
        }
    }
}

@Composable
private fun ScreenIntro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
        Text(title, color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Text(subtitle, color = MutedText, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun FileRow(name: String, size: String) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Blue.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF9FC6FF), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(size, color = MutedText, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun InfoPanel(text: String, warning: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (warning) Color(0xFF331C25) else Color(0xFF0F1D2F),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (warning) Color(0xFF63404B) else Color(0xFF1F3A55))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = if (warning) Color(0xFFFFA0A0) else Color(0xFF82D9E7), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = if (warning) Color(0xFFFFD3D3) else Color(0xFFB4C8DB), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun GradientButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Blue, Color(0xFF1FA6D9))) else SolidColor(Color(0xFF1B283B)),
                RoundedCornerShape(15.dp)
            ),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color(0xFF6C7F99)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
private fun CenterMessage(title: String, subtitle: String, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(Modifier.size(68.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = (-0.4).sp)
        Text(subtitle, color = MutedText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 20.sp)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = action, shape = ButtonShape, border = OutlineBorder, colors = outlinedColors()) { Text("Back to home") }
        }
    }
}

private fun queryName(c: Context, u: Uri): String {
    return c.contentResolver.query(u, null, null, null, null)?.use { cur ->
        val i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cur.moveToFirst() && i >= 0) cur.getString(i) else "Selected file"
    } ?: "Selected file"
}

private fun formatBytes(v: Long): String = when {
    v < 1024L -> "$v B"
    v < 1024L * 1024 -> String.format("%.1f KB", v / 1024.0)
    v < 1024L * 1024 * 1024 -> String.format("%.1f MB", v / 1024.0 / 1024)
    else -> String.format("%.2f GB", v / 1024.0 / 1024 / 1024)
}

private fun qrBitmap(value: String): android.graphics.Bitmap? = try {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 720, 720)
    android.graphics.Bitmap.createBitmap(720, 720, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 720) for (y in 0 until 720) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
} catch (_: Exception) { null }

private fun startSender(
    context: Context,
    files: List<Uri>,
    connection: () -> WifiDirectConnection?,
    setConnection: (WifiDirectConnection) -> Unit,
    onQr: (String) -> Unit,
    onError: (String) -> Unit,
    onProgress: (TransferProgress) -> Unit
) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        try {
            val conn = WifiDirectConnection(context)
            setConnection(conn)
            val (ssid, password, ownerAddress) = conn.createGroup()
            val server = ServerSocket(8988).apply { soTimeout = 120000; receiveBufferSize = 2 * 1024 * 1024 }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(Intent(context, TransferService::class.java))
            else context.startService(Intent(context, TransferService::class.java))
            val payload = JSONObject().put("v", 1).put("ssid", ssid).put("password", password)
                .put("ownerAddress", ownerAddress).put("host", "192.168.49.1").put("port", 8988).put("protocol", "FDRP")
            withContext(Dispatchers.Main) { onQr(payload.toString()) }
            val socket = server.accept().apply { sendBufferSize = 2 * 1024 * 1024 }
            val (outgoing, staged) = prepareOutgoingFiles(context, files)
            try { TransferProtocol.send(context, socket, outgoing, onProgress) }
            finally { staged.forEach { it.delete() } }
            socket.close(); server.close(); conn.close()
            context.stopService(Intent(context, TransferService::class.java))
            withContext(Dispatchers.Main) { onProgress(TransferProgress(files.size, files.size, "Complete", 1, 1, 1, 1, 0.0)) }
        } catch (e: Exception) {
            context.stopService(Intent(context, TransferService::class.java))
            withContext(Dispatchers.Main) { onError(e.message ?: "Connection failed") }
        }
    }
}

private fun startReceiver(
    context: Context,
    host: String,
    port: Int,
    ssid: String,
    password: String,
    ownerAddress: String,
    onProgress: (TransferProgress) -> Unit,
    onDone: (List<String>) -> Unit,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        try {
            val socket = if (Build.VERSION.SDK_INT >= 29 && ssid.isNotBlank() && password.isNotBlank()) {
                com.fusiondrop.connection.WifiNetworkJoiner(context).connect(ssid, password, host, port).first
            } else if (ownerAddress.isNotBlank()) {
                com.fusiondrop.connection.LegacyWifiP2pJoiner(context).connect(ownerAddress, port)
            } else throw java.io.IOException("The QR code is missing Wi-Fi Direct connection details")
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(Intent(context, TransferService::class.java))
            else context.startService(Intent(context, TransferService::class.java))
            val received = TransferProtocol.receive(context, socket, onProgress)
            socket.close()
            context.stopService(Intent(context, TransferService::class.java))
            withContext(Dispatchers.Main) { onDone(received) }
        } catch (e: Exception) {
            context.stopService(Intent(context, TransferService::class.java))
            withContext(Dispatchers.Main) { onError(e.message ?: "Connection failed") }
        }
    }
}

private fun prepareOutgoingFiles(context: Context, uris: List<Uri>): Pair<List<OutgoingFile>, List<java.io.File>> {
    val staged = mutableListOf<java.io.File>()
    val result = uris.map { uri ->
        val name = queryName(context, uri)
        var size = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { !cursor.isNull(0) && it >= 0 } else null
        }
        if (size == null) size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0 } }
        if (size == null) {
            val temp = java.io.File.createTempFile("fusiondrop-", ".stream", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output, 1024 * 1024) } }
                ?: throw java.io.IOException("Cannot read $name")
            staged += temp
            OutgoingFile(Uri.fromFile(temp), name, temp.length())
        } else OutgoingFile(uri, name, size)
    }
    return result to staged
}

private fun querySize(c: Context, u: Uri): String {
    val size = c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
    return size?.let(::formatBytes) ?: "Size unknown"
}
