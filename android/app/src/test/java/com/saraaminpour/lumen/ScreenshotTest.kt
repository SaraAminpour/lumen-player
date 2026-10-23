package com.saraaminpour.lumen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.saraaminpour.lumen.data.HistoryEntry
import com.saraaminpour.lumen.net.Updater
import com.saraaminpour.lumen.player.QueueItem
import com.saraaminpour.lumen.player.ShotHooks
import com.saraaminpour.lumen.player.Track
import com.saraaminpour.lumen.player.Chapter
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the real screens on the JVM (Robolectric native graphics) into PNGs for the README.
 * Output: build/shots (or -Dshots.dir). The video frame behind the player is a still image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = LumenApp::class)
class ScreenshotTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val still = System.getProperty("shots.still") ?: "/tmp/lumenimg/still2.png"

    private fun idle(ms: Long = 2500) {
        // Compose's global snapshot observer is process-wide; push test-thread writes explicitly
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun save(activity: android.app.Activity, name: String, video: Boolean) {
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (video && File(still).exists()) {
            val frame = BitmapFactory.decodeFile(still)
            // letterbox the 16:9 frame like mpv would
            val scale = minOf(root.width / frame.width.toFloat(), root.height / frame.height.toFloat())
            val w = (frame.width * scale).toInt(); val h = (frame.height * scale).toInt()
            c.drawColor(android.graphics.Color.BLACK)
            c.drawBitmap(frame, null, Rect((root.width - w) / 2, (root.height - h) / 2, (root.width + w) / 2, (root.height + h) / 2), null)
        }
        root.draw(c)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() {
        ShotHooks.still = true
        val app = ApplicationProvider.getApplicationContext<LumenApp>()
        app.prefs.autoUpdate = false
        val now = System.currentTimeMillis()
        app.history.put(HistoryEntry("https://www.youtube.com/watch?v=aqz-KE-bpKQ", "Big Buck Bunny 60fps 4K", 245.0, 635.0, now - 3_600_000))
        app.history.put(HistoryEntry("content://media/external/video/media/2", "Neon Rain.mp4", 51.0, 150.0, now - 60_000))
        app.history.put(HistoryEntry("content://media/external/video/media/1", "The Last Summit.mp4", 1500.0, 7920.0, now))
        app.updater.latest = "1.3.0"
        app.updater.notes = "•  Faster YouTube start-up\n•  Subtitle sync assistant remembers your last shift\n•  Smoother seeking on long files"
        app.updater.state = Updater.State.Available
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun home() {
        seed()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle(); save(a, "android-home", video = false)
    }

    private fun player(panel: String?, name: String, qualifiers: Boolean = true, keys: ((PlayerActivity) -> Unit)? = null) {
        seed()
        ShotHooks.transparent = true
        PlayerActivity.pendingQueue = listOf(
            QueueItem("content://media/external/video/media/2", "Neon Rain.mp4", "Movies"),
            QueueItem("content://media/external/video/media/3", "City Lights.mp4", "Movies"),
        ) to 0
        val ctl = Robolectric.buildActivity(PlayerActivity::class.java).create()
        val a = ctl.get()
        a.window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val p = a.player
        p.idle = false; p.paused = true; p.loading = false
        p.mediaTitle = "Neon Rain"; p.duration = 150.0; p.position = 51.0; p.cacheTime = 40.0
        p.videoW = 1920; p.videoH = 1080; p.videoCodec = "h264"; p.audioCodec = "aac"; p.hwdecCurrent = "mediacodec"
        p.fps = 24.0; p.channels = 2; p.sampleRate = 48000; p.fileFormat = "mp4"; p.fileSize = 18_273_256
        p.pixelFormat = "yuv420p"; p.colorMatrix = "bt.709"; p.videoBitrate = 950_000.0; p.audioBitrate = 128_000.0
        p.chapters = listOf(Chapter(0.0, "Opening"), Chapter(62.0, "Station"), Chapter(118.0, "Home"))
        p.tracks = listOf(
            Track(1, "video", "", "", "h264", false, true, true, false, 1920, 1080),
            Track(1, "audio", "Stereo", "eng", "aac", false, true, true, false, channels = 2),
            Track(1, "sub", "Neon Rain.srt", "", "subrip", true, true, false, false),
            Track(2, "sub", "Persian", "fas", "subrip", true, false, false, false),
        )
        p.sid = "1"; p.aid = "1"; p.subDelay = 0.25
        a.ui.controls = true
        ctl.start().resume().visible()   // first composition happens on attach, after the state is set
        idle(800)
        a.ui.panel = panel               // opened after the first frame, like a tap would
        if (keys != null) { keys(a); repeat(4) { idle(25) } } else idle()
        save(a, name, video = true)
        ShotHooks.transparent = false
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun playerControls() = player(null, "android-player")
    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun playerSubtitles() = player("subs", "android-subtitles")
    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun playerVideo() = player("video", "android-video")
    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun keyboardSeek() = player(null, "android-keys") { a ->
        fun press(code: Int) = a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        a.ui.controls = false
        assertTrue(press(KeyEvent.KEYCODE_DPAD_RIGHT)); assertTrue(press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(1, a.ui.rippleSide); assertEquals(10, a.ui.rippleAmount); assertEquals(5, a.ui.rippleStep)
        assertTrue(press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(-1, a.ui.rippleSide); assertEquals(5, a.ui.rippleAmount)
        val flashes = a.ui.flashSerial
        assertTrue(press(KeyEvent.KEYCODE_SPACE))
        assertEquals(flashes + 1, a.ui.flashSerial)
        // a held Space key must not flicker between play and pause
        assertTrue(a.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 3)))
        assertEquals(flashes + 1, a.ui.flashSerial)
        idle(300)
        assertTrue(press(KeyEvent.KEYCODE_DPAD_RIGHT))
    }
    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun playerSettings() = player("settings", "android-settings")
}
