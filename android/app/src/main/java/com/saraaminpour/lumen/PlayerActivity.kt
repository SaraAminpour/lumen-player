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
    private val tv get() = lumen.isTv
    private var lastHeldSeek = 0L
    private var swallowUp = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wakeForCast(intent)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immersive()
        player = Player(this, lumen.prefs, lumen.history)
        player.onEvent = { e ->
            when (e) {
                "video-params" -> autoRotate()
                "end" -> {
                    ui.osd.show("End of playlist")
                    // something a phone sent has finished: give the TV back instead of holding the last frame
                    if (player.current?.from?.isNotEmpty() == true) main.postDelayed({
                        if (!isFinishing && player.eof && player.index == player.queue.lastIndex) finish()
                    }, 2500)
                }
                "loaded" -> if (tv) ui.show()
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
        wakeForCast(intent)
        openFromIntent(intent)
    }

    /** A phone started this: turn the screen on if the TV was asleep. */
    private fun wakeForCast(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_CAST, false) != true) return
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
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
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(!tv && !player.paused && !player.idle && player.videoW > 0)
        return b.build()
    }

    /** "Play on TV" from the player: hand what is playing (and the rest of the queue) to a TV, at this position. */
    fun sendToTv() {
        if (player.queue.isEmpty()) return
        val rest = player.queue.drop(player.index.coerceAtLeast(0))
        val files = rest.filter { it.key.startsWith("content://") || it.key.startsWith("file://") }
        val links = rest.filter { it.key.startsWith("http", true) }
        // a TV plays one kind of queue: the files on this phone, or web links it opens itself
        val first = rest.first()
        val sendFiles = first.key.startsWith("content://") || first.key.startsWith("file://")
        com.saraaminpour.lumen.cast.CastActivity.handoff = com.saraaminpour.lumen.cast.Handoff(
            if (sendFiles) files.map { Uri.parse(it.key) } else emptyList(),
            if (sendFiles) emptyList() else links.map { it.key to it.title },
            player.position,
        )
        player.pause()
        startActivity(Intent(this, com.saraaminpour.lumen.cast.CastActivity::class.java))
    }

    fun updatePip() { if (Build.VERSION.SDK_INT >= 26) runCatching { setPictureInPictureParams(pipParams()) } }

    fun enterPip() {
        if (player.videoW == 0 || tv) return
        ui.controls = false; ui.panel = null
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && !tv && !player.paused && !player.idle && player.videoW > 0 && !lumen.prefs.backgroundAudio) enterPip()
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
        if (current === this) current = null
        main.removeCallbacksAndMessages(null)
        player.destroy()
    }

    /**
     * Keyboard shortcuts (tablets, Chromebooks), the same keys as on Windows, and the TV remote.
     *
     * With a remote, while the controls are hidden: OK plays or pauses and brings the controls up, left and
     * right skip 10 seconds, up and down bring the controls up. While they are shown the D-pad moves between
     * the buttons and Back hides them again.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ui.typing) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        // arrow keys always seek on a real keyboard; a remote's D-pad moves between buttons while they are shown
        val keyboard = event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && !tv
        val overlay = ui.controls || ui.panel != null || ui.speedOpen
        if (event.action != KeyEvent.ACTION_DOWN) {
            // the press that brought the controls up must not also click the button that just got focus
            if (event.action == KeyEvent.ACTION_UP && swallowUp == code) { swallowUp = 0; return true }
            return super.dispatchKeyEvent(event)
        }
        val shift = event.isShiftPressed
        val first = event.repeatCount == 0
        if (swallowUp == code && !first) return true
        if (overlay) ui.poke++
        val arrowsSeek = keyboard || !overlay
        val step = if (tv) 10.0 else 5.0
        fun heldSeek(s: Double): Boolean {
            // a held key repeats about 20 times a second, more seeks than a stream can follow
            val now = System.currentTimeMillis()
            if (first || now - lastHeldSeek > 220) { lastHeldSeek = now; ui.seek(player, s) }
            return true
        }
        val handled = when (code) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (first) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (overlay) false else {
                    if (first) { ui.togglePlay(player); if (tv) { ui.show(); swallowUp = code } }
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (!overlay && !keyboard) { ui.show(); true } else false
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (player.paused) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (!player.paused) ui.togglePlay(player); true }
            KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (arrowsSeek) heldSeek(if (shift) -1.0 else -step) else false
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (arrowsSeek) heldSeek(if (shift) 1.0 else step) else false
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_MEDIA_REWIND -> heldSeek(-10.0)
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> heldSeek(10.0)
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
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_INFO -> { ui.panel = if (ui.panel == "info") null else "info"; ui.show(); true }
            KeyEvent.KEYCODE_CAPTIONS -> { ui.panel = if (ui.panel == "subs") null else "subs"; ui.show(); true }
            KeyEvent.KEYCODE_MENU -> { if (overlay && ui.panel == null) ui.controls = false else ui.show(); true }
            KeyEvent.KEYCODE_ESCAPE -> { if (ui.panel != null) { ui.panel = null; true } else false }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                player.seekTo(player.duration * (code - KeyEvent.KEYCODE_0) / 10.0); true
            }
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    companion object {
        /** Queue handed over by the home screen (avoids pushing large lists through an Intent). */
        var pendingQueue: Pair<List<QueueItem>, Int>? = null
        /** The player on screen, for requests that arrive from a phone. */
        var current: PlayerActivity? = null; private set
        const val EXTRA_CAST = "cast"
    }
}
