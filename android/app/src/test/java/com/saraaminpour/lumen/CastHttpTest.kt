package com.saraaminpour.lumen

import com.saraaminpour.lumen.cast.HttpResponse
import com.saraaminpour.lumen.cast.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** The small HTTP server behind "Play on TV": byte ranges are what make seeking on the TV work. */
class CastHttpTest {
    @Test fun ranges() {
        assertEquals(0L..99L, HttpServer.parseRange("bytes=0-99", 1000))
        assertEquals(500L..999L, HttpServer.parseRange("bytes=500-", 1000))
        assertEquals(900L..999L, HttpServer.parseRange("bytes=-100", 1000))
        assertEquals(0L..999L, HttpServer.parseRange("bytes=-5000", 1000))       // more than the file: all of it
        assertEquals(990L..999L, HttpServer.parseRange("bytes=990-2000", 1000))  // end past the file: clamp
        assertNull(HttpServer.parseRange("bytes=1000-", 1000))                   // starts after the end
        assertNull(HttpServer.parseRange("bytes=20-10", 1000))
        assertNull(HttpServer.parseRange("bytes=abc", 1000))
    }

    @Test fun servesRangesOverASocket() {
        val data = ByteArray(5000) { (it % 251).toByte() }
        val server = HttpServer("test") { req ->
            val r = HttpServer.parseRange(req.header("Range"), data.size.toLong())
            when {
                req.path != "/m/file name.mp4" -> HttpResponse.text(404, "no")
                req.header("range").isEmpty() -> HttpResponse(200, emptyMap(), data.size.toLong()) { it.write(data) }
                r == null -> HttpResponse(416, mapOf("Content-Range" to "bytes */${data.size}"))
                else -> HttpResponse(206, mapOf("Content-Range" to "bytes ${r.first}-${r.last}/${data.size}"), r.last - r.first + 1) {
                    it.write(data, r.first.toInt(), (r.last - r.first + 1).toInt())
                }
            }
        }
        val port = server.start(0)
        try {
            fun get(path: String, range: String? = null, method: String = "GET"): Pair<Int, ByteArray> {
                val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
                c.requestMethod = method
                range?.let { c.setRequestProperty("Range", it) }
                val code = c.responseCode
                val body = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                return code to body
            }
            val (c1, b1) = get("/m/file%20name.mp4")                      // the path is URL-decoded
            assertEquals(200, c1); assertEquals(5000, b1.size)
            val (c2, b2) = get("/m/file%20name.mp4", "bytes=4990-")
            assertEquals(206, c2); assertEquals(data.copyOfRange(4990, 5000).toList(), b2.toList())
            val (c3, b3) = get("/m/file%20name.mp4", "bytes=10-19")
            assertEquals(206, c3); assertEquals(data.copyOfRange(10, 20).toList(), b3.toList())
            assertEquals(416, get("/m/file%20name.mp4", "bytes=9000-").first)
            assertEquals(404, get("/other").first)
            val (c4, b4) = get("/m/file%20name.mp4", method = "HEAD")     // headers only
            assertEquals(200, c4); assertEquals(0, b4.size)
        } finally {
            server.stop()
        }
    }
}
