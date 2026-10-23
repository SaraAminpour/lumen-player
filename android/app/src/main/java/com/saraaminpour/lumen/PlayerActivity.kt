package com.saraaminpour.lumen

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.player.Player
import com.saraaminpour.lumen.player.PlayerScreen
import com.saraaminpour.lumen.player.PlayerUiState
import com.saraaminpour.lumen.player.QueueItem
import com.saraaminpour.lumen.ui.LumenTheme
import com.saraaminpour.lumen.ui.extractLink

class PlayerActivity : ComponentActivity() {
    lateinit var player: Player
    val ui = PlayerUiState()
    val inPip = mutableStateOf(false)
    private var userOrientation: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immersive()
        player = Player(this, lumen.prefs, lumen.history)
        player.onEvent = { e ->
            when (e) {
                "video-params" -> autoRotate()
                "end" -> ui.osd.show("End of playlist")
                "error" -> ui.osd.show(player.error)
            }
        }
        openFromIntent(intent)
        setContent {
            LumenTheme(lumen.prefs.accent) {
                PlayerScreen(this, player, ui, lumen.prefs, lumen.updater, inPip.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    private fun openFromIntent(i: Intent?) {
        pendingQueue?.let { (items, start) ->
            pendingQueue = null
            player.open(items, start); return
        }
        val data: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { extractLink(it) }?.let { Uri.parse(it) }
                ?: (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM))
            else -> null
        }
        if (data == null) { if (player.queue.isEmpty()) finish(); return }
        val key = data.toString()
        if (data.scheme?.startsWith("http") == true || data.scheme in setOf("rtmp", "rtsp", "udp", "mms", "ftp")) {
            player.open(listOf(QueueItem(key, key)), 0); return
        }
        val name = Library.displayName(this, data) ?: data.lastPathSegment ?: "Video"
        if (lumen.prefs.autoQueue) {
            val sib = Library.siblings(this, data)
            val idx = sib.indexOfFirst { it.uri == data || it.name == name }
            if (sib.size > 1 && idx >= 0) {
                player.open(sib.map { QueueItem(it.uri.toString(), it.name, it.folder) }, idx); return
            }
        }
        player.open(listOf(QueueItem(key, name)), 0)
    }

    fun immersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Landscape for wide videos, portrait for tall ones, unless the user rotated by hand. */
    private fun autoRotate() {
        if (!lumen.prefs.autoRotate || userOrientation != null || player.videoW == 0) return
        requestedOrientation = if (player.videoH > player.videoW) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        updatePip()
    }

    fun toggleRotation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        userOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        requestedOrientation = userOrientation!!
    }

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        if (player.videoW > 0 && player.videoH > 0) {
            val r = player.videoW.toDouble() / player.videoH
            if (r in 0.42..2.39) b.setAspectRatio(Rational(player.videoW, player.videoH))
        }
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(!player.paused && !player.idle && player.videoW > 0)
        return b.build()
    }

    fun updatePip() { if (Build.VERSION.SDK_INT >= 26) runCatching { setPictureInPictureParams(pipParams()) } }

    fun enterPip() {
        if (player.videoW == 0) return
        ui.controls = false; ui.panel = null
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && !player.paused && !player.idle && player.videoW > 0 && !lumen.prefs.backgroundAudio) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) { ui.controls = false; ui.panel = null }
    }

    override fun onStop() {
        super.onStop()
        player.saveState()
        if (!inPip.value && !lumen.prefs.backgroundAudio) player.pause()
    }

    override fun onResume() {
        super.onResume()
        immersive()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.destroy()
    }

    /** Keyboard shortcuts (tablets, Chromebooks, TV remotes) — the same keys as on Windows. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || ui.typing) return super.dispatchKeyEvent(event)
        val shift = event.isShiftPressed
        val first = event.repeatCount == 0
        // ← → always seek on a real keyboard; a TV remote's D-pad moves between buttons while they're shown
        val keyboard = event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        val arrowsSeek = keyboard || !ui.controls
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (first) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_DPAD_CENTER -> { if (ui.controls) false else { if (first) ui.togglePlay(player); true } }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (player.paused) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (!player.paused) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (arrowsSeek) { ui.seek(player, if (shift) -1.0 else -5.0); true } else false }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (arrowsSeek) { ui.seek(player, if (shift) 1.0 else 5.0); true } else false }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_MEDIA_REWIND -> { ui.seek(player, -10.0); true }
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { ui.seek(player, 10.0); true }
            KeyEvent.KEYCODE_Z -> { ui.nudgeSub(player, if (shift) -1.0 else -0.1); true }
            KeyEvent.KEYCODE_X -> { ui.nudgeSub(player, if (shift) 1.0 else 0.1); true }
            KeyEvent.KEYCODE_G -> { player.subStep(-1); ui.subToast(player); true }
            KeyEvent.KEYCODE_H -> { player.subStep(1); ui.subToast(player); true }
            KeyEvent.KEYCODE_V -> { player.set("sub-visibility", if (player.subVisible) "no" else "yes"); true }
            KeyEvent.KEYCODE_M -> { player.set("mute", if (player.muted) "no" else "yes"); true }
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_MEDIA_NEXT -> { player.next(); true }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { player.previous(); true }
            KeyEvent.KEYCODE_COMMA -> { player.frameStep(false); true }
            KeyEvent.KEYCODE_PERIOD -> { player.frameStep(true); true }
            KeyEvent.KEYCODE_LEFT_BRACKET -> { player.changeSpeed((player.speed - 0.1).coerceAtLeast(0.25)); true }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> { player.changeSpeed((player.speed + 0.1).coerceAtMost(4.0)); true }
            KeyEvent.KEYCODE_DEL -> { player.changeSpeed(1.0); true }
            KeyEvent.KEYCODE_B -> { ui.abLoop(player); true }
            KeyEvent.KEYCODE_I -> { ui.panel = if (ui.panel == "info") null else "info"; true }
            KeyEvent.KEYCODE_ESCAPE -> { if (ui.panel != null) { ui.panel = null; true } else false }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                player.seekTo(player.duration * (event.keyCode - KeyEvent.KEYCODE_0) / 10.0); true
            }
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    companion object {
        /** Queue handed over by the home screen (avoids pushing large lists through an Intent). */
        var pendingQueue: Pair<List<QueueItem>, Int>? = null
    }
}
