package com.fusiondrop.transfer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.*
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class OutgoingFile(val uri: Uri, val name: String, val size: Long)
data class TransferProgress(val fileIndex: Int, val fileCount: Int, val fileName: String, val fileBytes: Long, val fileSize: Long, val totalBytes: Long, val totalSize: Long, val bytesPerSecond: Double)

/** Length-prefixed metadata followed by exact original bytes. Payload is never decoded or transformed. */
object TransferProtocol {
 private const val BUFFER = 1024 * 1024
 fun send(context: Context, socket: Socket, files: List<OutgoingFile>, onProgress: (TransferProgress)->Unit, sha256: Boolean = false) {
  val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER)); val input = context.contentResolver
  out.writeInt(0x46445250); out.writeInt(files.size); val total = files.sumOf { it.size }; out.writeLong(total); var sentTotal = 0L; val started = System.nanoTime()
  files.forEachIndexed { index, file ->
   val name = safeName(file.name).toByteArray(Charsets.UTF_8); out.writeInt(name.size); out.write(name); out.writeLong(file.size); out.flush()
   val queue = ArrayBlockingQueue<ByteArray>(4); val failure = AtomicReference<Throwable?>(null); var sentFile = 0L
   val producer = thread(name="FusionDrop-reader") { try { input.openInputStream(file.uri)?.use { stream -> var remaining=file.size; while (remaining>0) { val b=ByteArray(minOf(BUFFER.toLong(),remaining).toInt()); var n=0; while(n<b.size) { val r=stream.read(b,n,b.size-n); if(r<0) throw EOFException("Source file ended early"); n+=r }; queue.put(b); remaining-=n } } ?: throw IOException("Cannot open ${file.name}") } catch(t:Throwable) { failure.set(t) } finally { queue.put(ByteArray(0)) } }
   while (true) { val chunk=queue.take(); if(chunk.isEmpty()) break; out.write(chunk); sentFile+=chunk.size; sentTotal+=chunk.size; val elapsed=(System.nanoTime()-started)/1e9; onProgress(TransferProgress(index,files.size,file.name,sentFile,file.size,sentTotal,total,if(elapsed>0) sentTotal/elapsed else 0.0)) }
   producer.join(); failure.get()?.let { throw IOException("Could not read ${file.name}",it) }; out.flush()
  }; out.flush()
 }
 fun receive(context: Context, socket: Socket, onProgress: (TransferProgress)->Unit, sha256: Boolean = false): List<String> {
  val input=DataInputStream(BufferedInputStream(socket.getInputStream(),BUFFER)); val output=context.contentResolver
  if(input.readInt()!=0x46445250) throw IOException("Not a FusionDrop transfer")
  val count=input.readInt(); if(count !in 1..500) throw IOException("Invalid file count")
  val total=input.readLong(); if(total<0) throw IOException("Invalid total size"); var totalDone=0L; val started=System.nanoTime(); val completed=ArrayList<String>()
  repeat(count) { index ->
   val len=input.readInt(); if(len !in 1..4096) throw IOException("Invalid file name")
   val nameBytes=ByteArray(len); input.readFully(nameBytes); val name=safeName(nameBytes.toString(Charsets.UTF_8))
   val size=input.readLong(); if(size<0) throw IOException("Invalid file size")
   val uri = if (Build.VERSION.SDK_INT >= 29) {
    val values=ContentValues().apply {
     put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName(context,name))
     put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
     put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/FlashDrop")
    }
    output.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: throw IOException("Cannot create destination file")
   } else {
    val folder=java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"FlashDrop")
    if (!folder.exists() && !folder.mkdirs()) throw IOException("Cannot create Downloads/FlashDrop")
    Uri.fromFile(java.io.File(folder, uniqueLegacyName(folder,name)))
   }
   var done=0L
   try {
    (if (uri.scheme == "file") java.io.FileOutputStream(java.io.File(uri.path!!)) else output.openOutputStream(uri,"w"))?.use { stream ->
     val buf=ByteArray(BUFFER)
     while(done<size) {
      val wanted=minOf(buf.size.toLong(),size-done).toInt()
      input.readFully(buf,0,wanted); stream.write(buf,0,wanted)
      done+=wanted; totalDone+=wanted
      val elapsed=(System.nanoTime()-started)/1e9
      onProgress(TransferProgress(index,count,name,done,size,totalDone,total,if(elapsed>0) totalDone/elapsed else 0.0))
     }
    } ?: throw IOException("Cannot write $name")
    completed+=uri.toString()
   } catch(t:Throwable) { if (uri.scheme == "file") java.io.File(uri.path!!).delete() else output.delete(uri,null,null); throw t }
  }
  return completed
 }
 private fun safeName(raw:String):String { val n=raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\x00-\\x1F<>:\"/\\|?*]"),"_").trim().take(180); return n.ifBlank { "received_file" } }
 private fun uniqueName(context:Context,name:String):String { val resolver=context.contentResolver; val base=name.substringBeforeLast('.',name); val ext=name.substringAfterLast('.',"").let { if(it==name) "" else ".$it" }; var candidate=name; var i=1; while(true) { val c=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Downloads._ID),"${MediaStore.Downloads.DISPLAY_NAME}=?",arrayOf(candidate),null); val exists=c?.use { it.moveToFirst() } ?: false; if(!exists) return candidate; candidate="$base ($i)$ext"; i++ } }
 private fun uniqueLegacyName(folder:java.io.File,name:String):String { val base=name.substringBeforeLast('.',name); val ext=name.substringAfterLast('.',"").let { if(it==name) "" else ".$it" }; var candidate=name; var i=1; while(java.io.File(folder,candidate).exists()) { candidate="$base ($i)$ext"; i++ }; return candidate }

}
