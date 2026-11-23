package com.fusiondrop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fusiondrop.connection.WifiDirectConnection
import com.fusiondrop.transfer.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

private val Blue=Color(0xFF2563EB); private val Cyan=Color(0xFF22D3EE)
class MainActivity : ComponentActivity() {
 private val picker=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if(uris.isNotEmpty()) selectedUris=uris }
 private var selectedUris by mutableStateOf<List<Uri>>(emptyList())
 private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if(result.values.any{!it}) Toast.makeText(this,"Allow the requested permission in Settings to continue.",Toast.LENGTH_LONG).show() }
 override fun onCreate(savedInstanceState:Bundle?) { installSplashScreen(); super.onCreate(savedInstanceState); setContent { FusionDropApp(selectedUris, onPick={ picker.launch(arrayOf("*/*")) }, onClear={selectedUris=emptyList()}, onRequestPermissions={ requestPermissionsFor(it) }) } }
 private fun requestPermissionsFor(mode:String) { val permissions= mutableListOf<String>(); if(Build.VERSION.SDK_INT>=33) { permissions+=Manifest.permission.NEARBY_WIFI_DEVICES; permissions+=Manifest.permission.POST_NOTIFICATIONS } else { permissions+=Manifest.permission.ACCESS_FINE_LOCATION; if(mode=="receive" && Build.VERSION.SDK_INT<=28) permissions+=Manifest.permission.WRITE_EXTERNAL_STORAGE }; val missing=permissions.filter { ContextCompat.checkSelfPermission(this,it)!=android.content.pm.PackageManager.PERMISSION_GRANTED }; if(missing.isNotEmpty()) { Toast.makeText(this,"FusionDrop needs permission to connect and transfer files.",Toast.LENGTH_LONG).show(); permissionLauncher.launch(missing.toTypedArray()) } }
}

@Composable private fun FusionDropApp(files:List<Uri>,onPick:()->Unit,onClear:()->Unit,onRequestPermissions:(String)->Unit) {
 val context= LocalContext.current; var page by remember { mutableStateOf("home") }; var qr by remember { mutableStateOf("") }; var progress by remember { mutableStateOf<TransferProgress?>(null) }; var status by remember { mutableStateOf("Ready") }; var error by remember { mutableStateOf("") }; var speed by remember { mutableStateOf(0.0) }; var sha by remember { mutableStateOf(false) }; var history by remember { mutableStateOf(listOf<String>()) }; var connection by remember { mutableStateOf<WifiDirectConnection?>(null) }
 val dark= isSystemInDarkTheme(); val scheme= if(dark) darkColorScheme(primary=Blue,secondary=Cyan,error=Color(0xFFEF4444),background=Color(0xFF0B1220),surface=Color(0xFF131C2E),onSurface=Color.White) else lightColorScheme(primary=Blue,secondary=Cyan,error=Color(0xFFEF4444),background=Color(0xFFF6F8FC),surface=Color.White,onSurface=Color(0xFF172033))
 MaterialTheme(colorScheme=scheme) { Surface(Modifier.fillMaxSize(),color=scheme.background) { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=22.dp)) {
  Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) { if(page!="home") IconButton(onClick={page="home"}) { Icon(Icons.Default.ArrowBack,"Back") }; Spacer(Modifier.weight(1f)); Text("FusionDrop",fontWeight=FontWeight.Bold,fontSize=18.sp,color=scheme.onSurface); Spacer(Modifier.weight(1f)); IconButton(onClick={page="settings"}) { Icon(Icons.Default.Settings,"Settings") } }
  when(page) {
   "home" -> { Spacer(Modifier.weight(.3f)); Image(painterResource(R.drawable.fusiondrop_logo),null,Modifier.fillMaxWidth().heightIn(max=270.dp).clip(RoundedCornerShape(24.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Fit); Text("FusionDrop",Modifier.fillMaxWidth().padding(top=20.dp),fontSize=32.sp,fontWeight=FontWeight.ExtraBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center); Text("Share files. Keep every byte.",Modifier.fillMaxWidth().padding(top=4.dp,bottom=26.dp),color=Color.Gray,textAlign=androidx.compose.ui.text.style.TextAlign.Center); ActionButton("Send",Icons.Default.Send){ onRequestPermissions("send"); onPick(); page="select" }; Spacer(Modifier.height(14.dp)); ActionButton("Receive",Icons.Default.QrCodeScanner){ onRequestPermissions("receive"); page="scan" }; Spacer(Modifier.weight(.4f)) }
   "select" -> { Text("Choose files",fontSize=26.sp,fontWeight=FontWeight.Bold); Text("Original files are sent without conversion.",color=Color.Gray,modifier=Modifier.padding(top=6.dp,bottom=18.dp)); if(files.isEmpty()) { Text("No files selected",color=Color.Gray); Button(onClick=onPick) { Text("Choose files") } } else { LazyColumn(Modifier.weight(1f)) { items(files) { uri -> val name=remember(uri){ queryName(context,uri) }; Row(Modifier.fillMaxWidth().padding(vertical=8.dp).background(scheme.surface,RoundedCornerShape(14.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.InsertDriveFile,null,tint=Blue); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name,maxLines=2); Text(querySize(context,uri),fontSize=12.sp,color=Color.Gray) }; IconButton(onClick={}) { Icon(Icons.Default.CheckCircle,contentDescription=null,tint=Color(0xFF22C55E)) } } } }; Button(onClick=onPick,Modifier.fillMaxWidth()) { Text("Add more files") }; Spacer(Modifier.height(10.dp)); ActionButton("Start transfer",Icons.Default.Bolt) { startSender(context,files,connection={connection},setConnection={connection=it},onQr={qr=it;page="qr"},onError={error=it;page="error"},onProgress={progress=it;page="transfer";speed=it.bytesPerSecond}) }; Spacer(Modifier.height(18.dp)) } }
   "qr" -> { Text("Ready to connect",fontSize=26.sp,fontWeight=FontWeight.Bold); Text("Ask the other phone to tap Receive and scan this",color=Color.Gray,modifier=Modifier.padding(vertical=14.dp)); val bitmap=remember(qr){ qrBitmap(qr) }; if(bitmap!=null) Image(bitmap.asImageBitmap(),null,Modifier.fillMaxWidth().weight(1f).padding(12.dp).background(Color.White,RoundedCornerShape(20.dp)).padding(18.dp)); Text("Keep this screen open while the other phone connects.",color=Color.Gray); Spacer(Modifier.height(20.dp)); Button(onClick={page="home"},Modifier.fillMaxWidth()) { Text("Cancel") }; Spacer(Modifier.height(18.dp)) }
   "scan" -> { Text("Scan to connect",fontSize=26.sp,fontWeight=FontWeight.Bold); Text("Point your camera at the sender's QR code.",color=Color.Gray,modifier=Modifier.padding(vertical=12.dp)); QrScanner(onCode={ raw -> try { val obj=JSONObject(raw); val host=obj.getString("host"); val port=obj.getInt("port"); val ssid=obj.optString("ssid"); val password=obj.optString("password"); val owner=obj.optString("ownerAddress"); page="connecting"; startReceiver(context,host,port,ssid,password,owner,onProgress={progress=it;speed=it.bytesPerSecond;page="transfer"},onDone={history=listOf("Received ${it.size} file(s)")+history;status="Received successfully";page="done"},onError={error=it;page="error"}) } catch(e:Exception){error="This QR code is not a valid FusionDrop connection.";page="error"} }); Spacer(Modifier.height(16.dp)) }
   "connecting" -> CenterMessage("Connecting…","Keep both phones close and Wi-Fi enabled.")
   "transfer" -> { Text("Transferring",fontSize=26.sp,fontWeight=FontWeight.Bold); Text(progress?.fileName ?: "Preparing files",color=Color.Gray,modifier=Modifier.padding(vertical=12.dp)); LinearProgressIndicator(progress = { progress?.let { if(it.totalSize>0) it.totalBytes.toFloat()/it.totalSize else 0f } ?: 0f }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp))); Text("${String.format("%.1f",speed/1024/1024)} MB/s",fontSize=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=28.dp)); Text("${progress?.totalBytes?.let(::formatBytes) ?: "0 B"} / ${progress?.totalSize?.let(::formatBytes) ?: "—"}",color=Color.Gray); Spacer(Modifier.weight(1f)); OutlinedButton(onClick={connection?.close();page="home"},Modifier.fillMaxWidth()) { Text("Cancel transfer") }; Spacer(Modifier.height(18.dp)) }
   "done" -> CenterMessage("Transfer complete","Your files are saved in Downloads / FlashDrop.") { Button(onClick={ try { val intent=Intent(Intent.ACTION_VIEW).apply { setDataAndType(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,"resource/folder"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; context.startActivity(intent) } catch(_:Exception){} }) { Text("Open folder") } }
   "history" -> { Text("Transfer history",fontSize=26.sp,fontWeight=FontWeight.Bold); if(history.isEmpty()) Text("No transfers yet",color=Color.Gray,modifier=Modifier.padding(top=20.dp)) else history.forEach { Text(it,Modifier.padding(12.dp)) } }
   "settings" -> { Text("Settings",fontSize=26.sp,fontWeight=FontWeight.Bold); Row(Modifier.fillMaxWidth().padding(top=24.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SHA-256 verification",fontWeight=FontWeight.SemiBold); Text("Extra integrity check; slower for large files.",fontSize=12.sp,color=Color.Gray) }; Switch(sha,{sha=it}) }; Text("Save location",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=20.dp)); Text("Downloads / FlashDrop",color=Color.Gray); Text("Language follows your phone's system language.",color=Color.Gray,modifier=Modifier.padding(top=20.dp)); TextButton(onClick={page="history"}) { Text("Transfer history") } }
   "error" -> CenterMessage("Could not transfer",error) { Button(onClick={page="home"}) { Text("Back to home") } }
  }
 } } }
}

@Composable private fun ActionButton(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) { Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Blue,Cyan))).clickable(onClick=onClick),contentAlignment=Alignment.Center) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,tint=Color.White); Spacer(Modifier.width(12.dp)); Text(label,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold) } } }
@Composable private fun CenterMessage(title:String,subtitle:String,extra:@Composable ()->Unit={}) { Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold); Text(subtitle,color=Color.Gray,modifier=Modifier.padding(12.dp)); extra() } }
private fun queryName(c:Context,u:Uri):String { return c.contentResolver.query(u,null,null,null,null)?.use { cur -> val i=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if(cur.moveToFirst()&&i>=0) cur.getString(i) else "Selected file" } ?: "Selected file" }
private fun formatBytes(v:Long):String = if(v>=1024L*1024) String.format("%.1f MB",v/1024.0/1024) else String.format("%.0f KB",v/1024.0)
private fun qrBitmap(value:String):android.graphics.Bitmap? = try { val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,720,720); android.graphics.Bitmap.createBitmap(720,720,android.graphics.Bitmap.Config.ARGB_8888).also { b -> for(x in 0 until 720) for(y in 0 until 720) b.setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) } } catch(_:Exception){null}

private fun startSender(context:Context,files:List<Uri>,connection:()->WifiDirectConnection?,setConnection:(WifiDirectConnection)->Unit,onQr:(String)->Unit,onError:(String)->Unit,onProgress:(TransferProgress)->Unit) {
 val scope=CoroutineScope(Dispatchers.IO+SupervisorJob()); scope.launch { try { val conn=WifiDirectConnection(context); setConnection(conn); val (ssid,password,ownerAddress)=conn.createGroup(); val server=ServerSocket(8988).apply { soTimeout=120000; receiveBufferSize=2*1024*1024 }; if(Build.VERSION.SDK_INT>=26) context.startForegroundService(Intent(context,TransferService::class.java)) else context.startService(Intent(context,TransferService::class.java)); val obj=JSONObject().put("v",1).put("ssid",ssid).put("password",password).put("ownerAddress",ownerAddress).put("host","192.168.49.1").put("port",8988).put("protocol","FDRP"); withContext(Dispatchers.Main){onQr(obj.toString())}; val socket=server.accept().apply { sendBufferSize=2*1024*1024 }; val (outgoing, staged)=prepareOutgoingFiles(context,files); try { TransferProtocol.send(context,socket,outgoing,onProgress) } finally { staged.forEach { it.delete() } }; socket.close();server.close();conn.close(); context.stopService(Intent(context,TransferService::class.java)); withContext(Dispatchers.Main){onProgress(TransferProgress(files.size,files.size,"Complete",1,1,1,1,0.0))} } catch(e:Exception){ context.stopService(Intent(context,TransferService::class.java)); withContext(Dispatchers.Main){onError(e.message?:"Connection failed")}} }
}
private fun startReceiver(context:Context,host:String,port:Int,ssid:String,password:String,ownerAddress:String,onProgress:(TransferProgress)->Unit,onDone:(List<String>)->Unit,onError:(String)->Unit) { CoroutineScope(Dispatchers.IO+SupervisorJob()).launch { try { val socket=if(Build.VERSION.SDK_INT>=29 && ssid.isNotBlank() && password.isNotBlank()) com.fusiondrop.connection.WifiNetworkJoiner(context).connect(ssid,password,host,port).first else if(ownerAddress.isNotBlank()) com.fusiondrop.connection.LegacyWifiP2pJoiner(context).connect(ownerAddress,port) else throw java.io.IOException("The QR code is missing Wi-Fi Direct connection details"); if(Build.VERSION.SDK_INT>=26) context.startForegroundService(Intent(context,TransferService::class.java)) else context.startService(Intent(context,TransferService::class.java)); val files=TransferProtocol.receive(context,socket,onProgress); socket.close(); context.stopService(Intent(context,TransferService::class.java)); withContext(Dispatchers.Main){onDone(files)} } catch(e:Exception){context.stopService(Intent(context,TransferService::class.java)); withContext(Dispatchers.Main){onError(e.message?:"Connection failed")}} } }

private fun prepareOutgoingFiles(context:Context, uris:List<Uri>):Pair<List<OutgoingFile>,List<java.io.File>> {
 val staged=mutableListOf<java.io.File>(); val result=uris.map { uri ->
  val name=queryName(context,uri); var size=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use { c -> if(c.moveToFirst()) c.getLong(0).takeIf { !c.isNull(0) && it>=0 } else null }
  if(size==null) size=context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length.takeIf { n -> n>=0 } }
  if(size==null) { val temp=java.io.File.createTempFile("fusiondrop-", ".stream", context.cacheDir); context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output,1024*1024) } } ?: throw java.io.IOException("Cannot read $name"); staged+=temp; OutgoingFile(Uri.fromFile(temp),name,temp.length()) } else OutgoingFile(uri,name,size)
 }; return result to staged
}

private fun querySize(c:Context,u:Uri):String { val size=c.contentResolver.query(u,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use { cur -> if(cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else null }; return size?.let(::formatBytes) ?: "Size unknown" }
