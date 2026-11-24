package com.saraaminpour.lumen.cast

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,   // names in lower case
    val body: ByteArray,
    val remote: InetAddress,
) {
    fun header(name: String) = headers[name.lowercase()] ?: ""
    val text get() = String(body, Charsets.UTF_8)
}

/** [write] streams the body; it is skipped for HEAD requests. [length] < 0 means "until the connection closes". */
class HttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val length: Long = 0,
    val write: ((OutputStream) -> Unit)? = null,
) {
    companion object {
        fun json(status: Int, body: String): HttpResponse {
            val b = body.toByteArray(Charsets.UTF_8)
            return HttpResponse(status, mapOf("Content-Type" to "application/json; charset=utf-8"), b.size.toLong()) { it.write(b) }
        }
        fun text(status: Int, body: String) = body.toByteArray(Charsets.UTF_8).let { b ->
            HttpResponse(status, mapOf("Content-Type" to "text/plain; charset=utf-8"), b.size.toLong()) { it.write(b) }
        }
    }
}

/**
 * A very small HTTP/1.1 server: one request per connection, one thread per connection.
 * It carries the two jobs Lumen needs on a home network: the phone streams files to the TV (with byte
 * ranges, so seeking works) and the TV listens for "play this" requests.
 */
class HttpServer(private val name: String, private val handler: (HttpRequest) -> HttpResponse) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val n = AtomicInteger()
    @Volatile var port = 0; private set
    val running get() = server?.isClosed == false

    /** Binds [preferred], or any free port when that one is taken. Returns the port in use. */
    @Synchronized fun start(preferred: Int): Int {
        if (running) return port
        val s = ServerSocket()
        s.reuseAddress = true
        try { s.bind(InetSocketAddress(preferred)) } catch (_: Exception) { s.bind(InetSocketAddress(0)) }
        server = s; port = s.localPort
        val p = Executors.newCachedThreadPool { r -> Thread(r, "$name-${n.incrementAndGet()}").apply { isDaemon = true } }
        pool = p
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Exception) { break }
                try { p.execute { serve(c) } } catch (_: Exception) { runCatching { c.close() } }
            }
        }, "$name-accept").apply { isDaemon = true }.start()
        return port
    }

    @Synchronized fun stop() {
        runCatching { server?.close() }
        pool?.shutdownNow()
        server = null; pool = null; port = 0
    }

    private fun serve(c: Socket) {
        try {
            c.tcpNoDelay = true
            c.keepAlive = true
            c.soTimeout = 20_000                    // for reading the request; writes may block as long as the player pauses
            val input = BufferedInputStream(c.getInputStream(), 8192)
            val req = readRequest(input, c.inetAddress) ?: return
            val res = try { handler(req) } catch (e: Exception) {
                Log.w(TAG, "$name handler", e)
                HttpResponse.text(500, "error")
            }
            val out = c.getOutputStream()
            val head = StringBuilder("HTTP/1.1 ${res.status} ${reason(res.status)}\r\n")
            res.headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
            if (res.length >= 0) head.append("Content-Length: ").append(res.length).append("\r\n")
            head.append("Connection: close\r\n\r\n")
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            if (req.method != "HEAD") res.write?.invoke(out)
            out.flush()
        } catch (_: SocketException) {
            // the other side closed the connection (a seek on the TV does this all the time)
        } catch (e: Exception) {
            Log.d(TAG, "$name connection: $e")
        } finally {
            runCatching { c.close() }
        }
    }

    private fun readRequest(input: InputStream, remote: InetAddress): HttpRequest? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: return null
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            if (headers.size > 100) return null
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len < 0 || len > MAX_BODY) return null
        val body = ByteArray(len)
        var got = 0
        while (got < len) {
            val r = input.read(body, got, len - got)
            if (r < 0) return null
            got += r
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val rawPath = if (q >= 0) target.substring(0, q) else target
        val query = HashMap<String, String>()
        if (q >= 0) target.substring(q + 1).split('&').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) query[decode(kv.substring(0, i))] = decode(kv.substring(i + 1))
        }
        return HttpRequest(parts[0].uppercase(), decode(rawPath), query, headers, body, remote)
    }

    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream(128)
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
            if (b.size() > 16384) return null
        }
        return b.toString("ISO-8859-1")
    }

    private fun decode(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    companion object {
        const val TAG = "LumenCast"
        private const val MAX_BODY = 1 shl 20

        fun reason(code: Int) = when (code) {
            200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; 409 -> "Conflict"; 416 -> "Range Not Satisfiable"
            else -> if (code >= 500) "Server Error" else "OK"
        }

        /** Parses a single `Range: bytes=a-b` header against a resource of [size] bytes. Null = not satisfiable. */
        fun parseRange(header: String, size: Long): LongRange? {
            val spec = header.trim().removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null
            val a = spec.substring(0, dash).trim(); val b = spec.substring(dash + 1).trim()
            return when {
                a.isEmpty() -> {                       // suffix: the last N bytes
                    val n = b.toLongOrNull() ?: return null
                    if (n <= 0 || size <= 0) null else maxOf(0, size - n)..(size - 1)
                }
                else -> {
                    val start = a.toLongOrNull() ?: return null
                    val end = if (b.isEmpty()) size - 1 else minOf(b.toLongOrNull() ?: return null, size - 1)
                    if (start < 0 || start >= size || end < start) null else start..end
                }
            }
        }
    }
}
