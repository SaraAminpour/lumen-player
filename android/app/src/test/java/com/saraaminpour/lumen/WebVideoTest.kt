package com.saraaminpour.lumen

import com.saraaminpour.lumen.net.WebVideo
import org.junit.Assert.assertTrue
import org.junit.Test

/** Network test: resolves a real YouTube video (Big Buck Bunny). */
class WebVideoTest {
    @Test
    fun youtube() {
        val url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        assertTrue(WebVideo.isWebPage(url))
        val r = WebVideo.resolve(url, 1080)
        println("title=${r.title} quality=${r.quality} audio=${r.audio != null} subs=${r.subtitles.size}")
        println("video=${r.video.take(120)}")
        java.io.File("/tmp/yt_urls.txt").writeText(r.video + "\n" + (r.audio ?: ""))
        assertTrue(r.video.startsWith("http"))
        assertTrue(!WebVideo.isWebPage("https://example.com/movie.mp4"))
    }
}
