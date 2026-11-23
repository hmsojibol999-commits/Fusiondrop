package com.fusiondrop.linkshare

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small, token-protected HTTP server for direct browser downloads on the same LAN.
 * It streams the original content URI bytes and never transcodes or compresses files.
 */
internal class LinkShareHttpServer(
    private val context: Context,
    private val files: List<LinkShareFile>,
    private val welcomeMessage: String,
    private val onActiveDownloadsChanged: (Int) -> Unit
) : Closeable {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { byte -> "%02x".format(byte) }
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val activeDownloads = AtomicInteger(0)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null

    @Throws(IOException::class)
    fun start(): String {
        if (files.isEmpty()) throw IOException("Choose at least one file before sharing.")
        val address = findWifiIpv4Address()
            ?: throw IOException("No Wi-Fi or hotspot address was found. Connect to a Wi-Fi network or start your hotspot first.")
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", 0), 16)
        }
        serverSocket = socket
        running = true
        Thread({ acceptLoop(socket) }, "FusionDrop-LinkShare-Accept").apply {
            isDaemon = true
            start()
        }
        return "http://$address:${socket.localPort}/s/$token"
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            try {
                val client = server.accept()
                try {
                    client.tcpNoDelay = true
                    client.soTimeout = 20_000
                    activeSockets.add(client)
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    activeSockets.remove(client)
                    try { client.close() } catch (_: Exception) {}
                }
            } catch (_: SocketException) {
                if (running) close()
                break
            } catch (_: IOException) {
                if (!running) break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                if (requestLine.length > 4096) return
                val parts = requestLine.split(' ', limit = 3)
                if (parts.size < 2) {
                    sendError(client, 400, "Bad Request", "The request could not be read.")
                    return
                }
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                if (method != "GET" && method != "HEAD") {
                    sendError(client, 405, "Method Not Allowed", "Only file downloads are supported.")
                    return
                }
                var headerCount = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.length > 8192 || ++headerCount > 64) return
                }
                val headOnly = method == "HEAD"
                when {
                    path == "/s/$token" || path == "/s/$token/" -> sendHtml(client, headOnly)
                    path == "/api/$token/files" -> sendFileList(client, headOnly)
                    path.startsWith("/download/$token/") -> {
                        val index = path.substringAfterLast('/').toIntOrNull()
                        val file = index?.let(files::getOrNull)
                        if (file == null) sendError(client, 404, "Not Found", "This file is no longer available.")
                        else streamFile(client, file, inline = false, headOnly = headOnly, countDownload = true)
                    }
                    path.startsWith("/preview/$token/") -> {
                        val index = path.substringAfterLast('/').toIntOrNull()
                        val file = index?.let(files::getOrNull)
                        if (file == null || !file.mimeType.startsWith("image/")) {
                            sendError(client, 404, "Not Found", "Preview is not available for this file.")
                        } else {
                            streamFile(client, file, inline = true, headOnly = headOnly, countDownload = false)
                        }
                    }
                    else -> sendError(client, 404, "Not Found", "This FusionDrop share link is invalid or has expired.")
                }
            } catch (_: Exception) {
                // A receiver can close a browser tab during a large download. That is not a server failure.
            } finally {
                activeSockets.remove(client)
            }
        }
    }

    private fun sendHtml(socket: Socket, headOnly: Boolean) {
        val body = welcomePage().toByteArray(StandardCharsets.UTF_8)
        val output = BufferedOutputStream(socket.getOutputStream())
        writeHeaders(output, 200, "OK", "text/html; charset=utf-8", body.size.toLong(),
            "Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'\r\n")
        if (!headOnly) output.write(body)
        output.flush()
    }

    private fun sendFileList(socket: Socket, headOnly: Boolean) {
        val array = JSONArray()
        files.forEachIndexed { index, file ->
            array.put(JSONObject()
                .put("index", index)
                .put("name", file.name)
                .put("size", if (file.size >= 0L) file.size else JSONObject.NULL)
                .put("mimeType", file.mimeType)
                .put("isImage", file.mimeType.startsWith("image/")))
        }
        val body = JSONObject()
            .put("message", welcomeMessage)
            .put("files", array)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val output = BufferedOutputStream(socket.getOutputStream())
        writeHeaders(output, 200, "OK", "application/json; charset=utf-8", body.size.toLong())
        if (!headOnly) output.write(body)
        output.flush()
    }

    private fun streamFile(
        socket: Socket,
        file: LinkShareFile,
        inline: Boolean,
        headOnly: Boolean,
        countDownload: Boolean
    ) {
        val input = if (headOnly) null else context.contentResolver.openInputStream(file.uri)
        if (!headOnly && input == null) {
            sendError(socket, 410, "Gone", "Android could not open this original file. Select it again in FusionDrop.")
            return
        }
        val disposition = if (inline) "inline" else "attachment"
        val safeName = file.name.replace(Regex("[\\r\\n\\\"\\\\]"), "_")
            .filter { it >= ' ' && it != '\u007f' }
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val extra = "Content-Disposition: $disposition; filename=\"$safeName\"; filename*=UTF-8''$encodedName\r\n"
        val output = BufferedOutputStream(socket.getOutputStream())
        writeHeaders(output, 200, "OK", file.mimeType, file.size.takeIf { it >= 0L }, extra)
        if (!headOnly) {
            if (countDownload) onActiveDownloadsChanged(activeDownloads.incrementAndGet())
            try {
                input!!.use { source ->
                    val buffer = ByteArray(128 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        val remaining = if (file.size >= 0L) file.size - copied else read.toLong()
                        if (remaining <= 0L) break
                        val toWrite = minOf(read.toLong(), remaining).toInt()
                        output.write(buffer, 0, toWrite)
                        copied += toWrite
                    }
                    if (file.size >= 0L && copied != file.size) {
                        throw EOFException("The source returned fewer bytes than its reported file size.")
                    }
                }
                output.flush()
            } finally {
                if (countDownload) {
                    val remaining = activeDownloads.updateAndGet { (it - 1).coerceAtLeast(0) }
                    onActiveDownloadsChanged(remaining)
                }
            }
        } else {
            output.flush()
        }
    }

    private fun sendError(socket: Socket, code: Int, reason: String, detail: String) {
        val body = "<!doctype html><html><meta name=viewport content='width=device-width,initial-scale=1'><title>$code $reason</title><body style='font:16px system-ui;background:#0b1220;color:white;padding:32px'><h2>$reason</h2><p>${escapeHtml(detail)}</p></body></html>"
            .toByteArray(StandardCharsets.UTF_8)
        val output = BufferedOutputStream(socket.getOutputStream())
        writeHeaders(output, code, reason, "text/html; charset=utf-8", body.size.toLong())
        output.write(body)
        output.flush()
    }

    private fun writeHeaders(
        output: BufferedOutputStream,
        code: Int,
        reason: String,
        contentType: String,
        contentLength: Long?,
        extraHeaders: String = ""
    ) {
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            if (contentLength != null) append("Content-Length: $contentLength\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append(extraHeaders)
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    private fun welcomePage(): String = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#080e1a"><title>FusionDrop · Private file share</title>
<style>
:root{color-scheme:dark;--bg:#080e1a;--panel:#111c2d;--panel2:#152238;--line:#26364e;--text:#f3f7ff;--muted:#91a3bc;--blue:#3978f6;--cyan:#24cde0;--green:#35d49a}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(ellipse at 85% 0%,#173a5b 0,transparent 34%),radial-gradient(ellipse at 0% 35%,#112d52 0,transparent 30%),var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
main{width:min(760px,100%);margin:0 auto;padding:28px 18px 52px}.brand{display:flex;align-items:center;gap:11px;font-weight:750;letter-spacing:-.02em}.mark{width:42px;height:42px;border-radius:14px;display:grid;place-items:center;background:linear-gradient(135deg,var(--blue),var(--cyan));box-shadow:0 8px 28px #167ed455;font-size:22px;font-weight:900}.brand small{display:block;color:var(--muted);font-size:11px;font-weight:650;letter-spacing:.13em;text-transform:uppercase}
.hero{padding:38px 0 25px}.eyebrow{display:inline-flex;align-items:center;gap:9px;color:#9ddbe8;font-weight:750;font-size:11px;letter-spacing:.14em;text-transform:uppercase}.dot{width:8px;height:8px;border-radius:100%;background:var(--green);box-shadow:0 0 0 5px #35d49a22}h1{font-size:clamp(34px,8vw,54px);line-height:1.02;letter-spacing:-.055em;margin:16px 0 14px}h1 span{background:linear-gradient(90deg,#83aaff,var(--cyan));color:transparent;background-clip:text}.intro{color:var(--muted);max-width:590px;margin:0;font-size:16px}.note{margin-top:22px;padding:15px 17px;border:1px solid #2c5570;border-radius:17px;background:#122a3a99;color:#d9edf5}.note b{display:block;color:#8de5ed;font-size:12px;letter-spacing:.08em;text-transform:uppercase;margin-bottom:5px}
.summary{display:flex;justify-content:space-between;align-items:center;margin:28px 2px 13px;color:var(--muted);font-size:12px;font-weight:750;letter-spacing:.1em;text-transform:uppercase}.count{color:var(--text);letter-spacing:0}.files{display:grid;gap:11px}.file{display:flex;align-items:center;gap:13px;border:1px solid var(--line);background:linear-gradient(145deg,#142239,#0e1828);border-radius:18px;padding:13px;min-width:0}.thumb{width:58px;height:58px;border-radius:12px;object-fit:cover;background:#263851;flex:none}.fileinfo{min-width:0;flex:1}.filename{font-weight:700;overflow-wrap:anywhere}.meta{color:var(--muted);font-size:12px;margin-top:3px}.download{display:inline-flex;align-items:center;justify-content:center;min-width:43px;height:43px;padding:0 13px;border-radius:13px;text-decoration:none;color:white;font-weight:750;background:linear-gradient(110deg,var(--blue),#18abc9);box-shadow:0 7px 18px #0876ba33}.download:active{transform:scale(.97)}.empty{padding:25px;border:1px dashed var(--line);border-radius:18px;color:var(--muted);text-align:center}.footer{margin-top:25px;padding-top:18px;border-top:1px solid var(--line);color:var(--muted);font-size:12px;display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}.footer strong{color:#d4e5f4}.safe{display:inline-flex;gap:6px;align-items:center;color:#a6efd8}.error{color:#ff9b9b;padding:20px;border:1px solid #854b57;border-radius:16px}
@media(min-width:620px){main{padding-top:42px}.hero{padding-top:54px}}
</style></head>
<body><main>
<header class="brand"><div class="mark">F</div><div>FusionDrop<small>Private file transfer</small></div></header>
<section class="hero"><div class="eyebrow"><span class="dot"></span> Direct local share</div><h1>Your files are ready.<br><span>Welcome to FusionDrop.</span></h1><p class="intro">A friend has shared these files with you. Choose what you need and download the originals directly from their nearby device.</p>
<div class="note"><b>A note from your friend</b><span id="welcome">Loading your welcome message…</span></div></section>
<div class="summary"><span>Shared files</span><span class="count" id="count">Loading…</span></div><section class="files" id="files"><div class="empty">Preparing your file list…</div></section>
<footer class="footer"><span class="safe">● Direct connection · No cloud upload</span><span><strong>Original files.</strong> No compression or conversion.</span></footer>
</main><script>
const shareToken='$token';
function sizeLabel(n){if(n===null||n===undefined)return 'Size not provided';if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(1)+' KB';if(n<1073741824)return (n/1048576).toFixed(1)+' MB';return (n/1073741824).toFixed(2)+' GB'}
function makeFile(item){const card=document.createElement('article');card.className='file';if(item.isImage){const image=document.createElement('img');image.className='thumb';image.loading='lazy';image.alt='';image.src='/preview/'+shareToken+'/'+item.index;card.appendChild(image)}else{const icon=document.createElement('div');icon.className='thumb';icon.style.display='grid';icon.style.placeItems='center';icon.style.color='#9fc9ff';icon.style.fontSize='23px';icon.textContent='▤';card.appendChild(icon)}const info=document.createElement('div');info.className='fileinfo';const name=document.createElement('div');name.className='filename';name.textContent=item.name;const meta=document.createElement('div');meta.className='meta';meta.textContent=sizeLabel(item.size)+' · '+item.mimeType;info.appendChild(name);info.appendChild(meta);card.appendChild(info);const link=document.createElement('a');link.className='download';link.href='/download/'+shareToken+'/'+item.index;link.textContent='↓';link.setAttribute('aria-label','Download '+item.name);link.setAttribute('download','');card.appendChild(link);return card}
fetch('/api/'+shareToken+'/files',{cache:'no-store'}).then(response=>{if(!response.ok)throw new Error('Share link expired');return response.json()}).then(data=>{document.getElementById('welcome').textContent=data.message||'Your friend has shared these files with you.';document.getElementById('count').textContent=data.files.length+' item'+(data.files.length===1?'':'s');const root=document.getElementById('files');root.textContent='';if(!data.files.length){root.innerHTML='<div class="empty">No files are available.</div>';return}data.files.forEach(item=>root.appendChild(makeFile(item)))}).catch(error=>{document.getElementById('count').textContent='Unavailable';document.getElementById('files').innerHTML='<div class="error">This share is no longer available. Ask your friend to create a new link.</div>'});
</script></body></html>""".trimIndent()

    private fun findWifiIpv4Address(): String? {
        val allInterfaces = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        } catch (_: Exception) {
            emptyList()
        }
        val candidates = allInterfaces.asSequence()
            .filter { iface ->
                try { iface.isUp && !iface.isLoopback && !iface.isVirtual } catch (_: Exception) { false }
            }
            .flatMap { iface ->
                Collections.list(iface.inetAddresses).asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { address ->
                        !address.isLoopbackAddress && !address.isLinkLocalAddress && address.isSiteLocalAddress
                    }
                    .map { address -> iface.name to address.hostAddress }
            }
            .sortedBy { (name, _) ->
                when {
                    name.startsWith("wlan", ignoreCase = true) -> 0
                    name.startsWith("ap", ignoreCase = true) -> 1
                    name.contains("wifi", ignoreCase = true) || name.contains("swlan", ignoreCase = true) -> 2
                    else -> 5
                }
            }
            .toList()
        return candidates.firstOrNull()?.second
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    override fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        activeSockets.forEach { client -> try { client.close() } catch (_: Exception) {} }
        activeSockets.clear()
        executor.shutdownNow()
        activeDownloads.set(0)
        onActiveDownloadsChanged(0)
    }
}
