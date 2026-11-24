package com.saraaminpour.lumen.cast

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.FileInputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The sending end of "Play on TV": serves the files picked on this phone over HTTP so the TV can stream them.
 * Byte ranges are supported, which is what makes seeking (and files with their index at the end, like
 * every phone recording) work. Links carry a random token; nothing else on the phone is reachable.
 */
object MediaServer {
    /**
     * [channel] is the file, opened once when it was picked and kept open: reading through it needs no
     * permission later, so playback survives the picker's access grant ending (closing the screen that
     * picked the file, for example). Null for the rare provider whose files cannot be read at a position.
     */
    class Item(
        val id: String, val uri: Uri, val name: String, val size: Long, val mime: String,
        /** What to show on the TV; [name] is the file name as the provider reports it. */
        val title: String,
        internal val afd: AssetFileDescriptor?, internal val channel: FileChannel?,
    )

    const val PORT = 47801
    private const val TAG = "LumenCast"
    private val items = ConcurrentHashMap<String, Item>()
    private val server = HttpServer("lumen-media", ::handle)
    private var token = ""
    private lateinit var app: Context
    val bytesSent = AtomicLong()
    @Volatile var lastRequestAt = 0L; private set

    val running get() = server.running

    fun start(ctx: Context): Int {
        app = ctx.applicationContext
        if (token.isEmpty()) token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        return server.start(PORT)
    }

    fun stop() {
        server.stop()
        items.values.forEach { close(it) }
        items.clear()
    }

    private fun close(i: Item) { runCatching { i.channel?.close() }; runCatching { i.afd?.close() } }

    /** Makes [uri] available to the TV. Null when the file cannot be opened. */
    fun add(ctx: Context, uri: Uri): Item? {
        val r = ctx.contentResolver
        var name = ""; var size = -1L
        try {
            if (uri.scheme == "file") name = java.io.File(uri.path ?: return null).name
            else r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: ""
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (e: Exception) { Log.w(TAG, "query $uri", e) }
        var afd: AssetFileDescriptor? = null
        var channel: FileChannel? = null
        try {
            afd = r.openAssetFileDescriptor(uri, "r") ?: return null
            // the provider's size column is sometimes missing or wrong; the open file is the truth
            val real = if (afd.length >= 0) afd.length else Os.fstat(afd.fileDescriptor).st_size - afd.startOffset
            if (real > 0) size = real
            val ch = FileInputStream(afd.fileDescriptor).channel
            channel = try { ch.read(ByteBuffer.allocate(1), afd.startOffset); ch } catch (_: Exception) { null }
            if (channel == null) { afd.close(); afd = null }
        } catch (e: Exception) {
            Log.w(TAG, "open $uri", e)
            runCatching { afd?.close() }
            return null
        }
        if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "video"
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = r.getType(uri)?.takeIf { it.contains('/') && it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        // the same file gets the same id every time, so the TV can resume it where it stopped
        val id = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray()).take(10).joinToString("") { "%02x".format(it) }
        val item = Item(id, uri, name, size, mime, com.saraaminpour.lumen.data.Library.title(ctx, uri), afd, channel)
        items.put(id, item)?.let { close(it) }
        return item
    }

    fun url(item: Item, host: String): String {
        val h = if (host.contains(':')) "[${host.substringBefore('%')}]" else host
        return "http://$h:${server.port}/m/$token/${item.id}/" + URLEncoder.encode(item.name, "UTF-8").replace("+", "%20")
    }

    private fun handle(req: HttpRequest): HttpResponse {
        if (req.method != "GET" && req.method != "HEAD") return HttpResponse.text(405, "method not allowed")
        val parts = req.path.split('/')          // "", "m", token, id, name
        if (parts.size < 4 || parts[1] != "m" || parts[2] != token) return HttpResponse.text(404, "not found")
        val item = items[parts[3]] ?: return HttpResponse.text(404, "not found")
        lastRequestAt = System.currentTimeMillis()
        val base = mapOf("Content-Type" to item.mime, "Accept-Ranges" to "bytes", "Cache-Control" to "no-store")
        if (item.size < 0) return HttpResponse(200, base, -1) { out -> copy(item, 0, Long.MAX_VALUE, out) }
        val rangeHeader = req.header("range")
        if (rangeHeader.isEmpty()) return HttpResponse(200, base, item.size) { out -> copy(item, 0, item.size, out) }
        val range = HttpServer.parseRange(rangeHeader, item.size)
            ?: return HttpResponse(416, base + ("Content-Range" to "bytes */${item.size}"))
        val count = range.last - range.first + 1
        return HttpResponse(206, base + ("Content-Range" to "bytes ${range.first}-${range.last}/${item.size}"), count) { out ->
            copy(item, range.first, count, out)
        }
    }

    private fun copy(item: Item, start: Long, count: Long, out: OutputStream) {
        val ch = item.channel
        val held = item.afd
        if (ch != null && held != null) {
            // positional reads: several connections (a seek opens a new one) share the one open file safely
            val buf = ByteBuffer.allocate(1 shl 17)
            var pos = held.startOffset + start
            var left = count
            while (left > 0) {
                buf.clear()
                if (left < buf.capacity()) buf.limit(left.toInt())
                val n = ch.read(buf, pos)
                if (n <= 0) break
                out.write(buf.array(), 0, n)
                pos += n; left -= n
                bytesSent.addAndGet(n.toLong())
                lastRequestAt = System.currentTimeMillis()
            }
            return
        }
        // a provider that hands out a pipe: open it again and read up to the start
        val afd = app.contentResolver.openAssetFileDescriptor(item.uri, "r") ?: throw java.io.FileNotFoundException(item.name)
        afd.use {
            FileInputStream(afd.fileDescriptor).use { input ->
                val buf = ByteArray(1 shl 17)
                var skip = start
                while (skip > 0) {
                    val n = input.read(buf, 0, minOf(skip, buf.size.toLong()).toInt())
                    if (n < 0) return
                    skip -= n
                }
                var left = count
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    left -= n
                    bytesSent.addAndGet(n.toLong())
                    lastRequestAt = System.currentTimeMillis()
                }
            }
        }
    }
}
