package com.saraaminpour.lumen.player

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.saraaminpour.lumen.PlayerActivity
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.Updater
import com.saraaminpour.lumen.ui.C
import com.saraaminpour.lumen.ui.Chip
import com.saraaminpour.lumen.ui.IconBtn
import com.saraaminpour.lumen.ui.LSlider
import com.saraaminpour.lumen.ui.LinkDialog
import com.saraaminpour.lumen.ui.LocalAccent
import com.saraaminpour.lumen.ui.Osd
import com.saraaminpour.lumen.ui.OsdState
import com.saraaminpour.lumen.ui.UpdateCard
import com.saraaminpour.lumen.ui.fmtDelay
import com.saraaminpour.lumen.ui.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** UI state that outlives recompositions and is shared with key handling in the activity. */
class PlayerUiState {
    var controls by mutableStateOf(true)
    var panel by mutableStateOf<String?>(null)
    var locked by mutableStateOf(false)
    var speedOpen by mutableStateOf(false)
    var typing by mutableStateOf(false)
    var poke by mutableIntStateOf(0)
    val osd = OsdState()

    fun show() { controls = true; poke++ }

    fun nudgeSub(p: Player, d: Double) {
        val v = ((p.subDelay + d) * 1000).roundToInt() / 1000.0
        p.changeSubDelay(v); osd.show("Subtitle delay ${fmtDelay(v)}", Icons.Rounded.Timer)
    }

    fun nudgeAudio(p: Player, d: Double) {
        val v = ((p.audioDelay + d) * 1000).roundToInt() / 1000.0
        p.changeAudioDelay(v); osd.show("Audio delay ${fmtDelay(v)}", Icons.Rounded.Timer)
    }

    fun subToast(p: Player) {
        // sub-step changes sub-delay asynchronously; report after mpv answers
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { osd.show("Subtitle delay ${fmtDelay(p.subDelay)}", Icons.Rounded.Sync) }, 150,
        )
    }

    fun abLoop(p: Player) {
        when (p.abLoop()) {
            "a" -> osd.show("Loop start set at ${fmtTime(p.position)} — press again for the end", Icons.Rounded.Repeat)
            "b" -> osd.show("Looping ${fmtTime(p.abA.toDoubleOrNull() ?: 0.0)} → ${fmtTime(p.position)}", Icons.Rounded.Repeat)
            else -> osd.show("A-B loop cleared", Icons.Rounded.Repeat)
        }
    }
}

private enum class Drag { None, Seek, Brightness, Volume, Zoom }

/** Test-only: lets JVM screenshot tests paint a video frame behind the controls. */
object ShotHooks { @JvmField var transparent = false; @JvmField var still = false }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(activity: PlayerActivity, p: Player, ui: PlayerUiState, prefs: Prefs, updater: Updater, pip: Boolean) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var linkOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // gesture feedback
    var dragMode by remember { mutableStateOf(Drag.None) }
    var dragLevel by remember { mutableFloatStateOf(0f) }
    var seekPreview by remember { mutableStateOf(0.0) }
    var seekDelta by remember { mutableStateOf(0.0) }
    var rippleSide by remember { mutableIntStateOf(0) }
    var rippleAmount by remember { mutableIntStateOf(0) }
    var rippleSerial by remember { mutableIntStateOf(0) }
    var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
    var flashSerial by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val panelOpen = ui.panel != null
    // controls hide 3 s after the last touch while playing (desktop: mouse movement)
    LaunchedEffect(ui.controls, ui.poke, p.paused, panelOpen, ui.speedOpen, menuOpen) {
        if (ui.controls && !p.paused && !panelOpen && !ui.speedOpen && !menuOpen) { delay(3500); ui.controls = false }
    }
    LaunchedEffect(p.paused, p.idle) { activity.updatePip() }

    BackHandler(panelOpen || ui.speedOpen) { ui.panel = null; ui.speedOpen = false }

    fun flash(icon: ImageVector) { flashIcon = icon; flashSerial++ }
    fun togglePlay() { flash(if (p.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause); p.togglePause(); ui.show() }

    Box(
        Modifier.fillMaxSize().background(if (ShotHooks.transparent) Color.Transparent else Color.Black).onSizeChanged { size = it },
    ) {
        // ---- video ----
        AndroidView(
            factory = { c ->
                SurfaceView(c).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) { p.attachSurface(h.surface) }
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { p.surfaceSize(w, hh) }
                        override fun surfaceDestroyed(h: SurfaceHolder) { p.detachSurface() }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- gestures: tap = controls, double-tap sides = ±10 s, swipes, pinch ----
        Box(
            Modifier.fillMaxSize().pointerInput(prefs.gestures, ui.locked) {
                var lastTapAt = 0L
                var lastTapX = 0f
                var seekRun = 0L                // taps keep seeking while a double-tap seek run is going
                var singleJob: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = this.size.width.toFloat(); val h = this.size.height.toFloat()
                    val slop = viewConfiguration.touchSlop
                    var mode = Drag.None
                    var total = Offset.Zero
                    var zoomStart = p.zoom; var zoomAcc = 1f
                    val startPos = p.position
                    val startBright = activity.window.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                    while (true) {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (ui.locked) continue
                        if (pressed.size >= 2 && prefs.gestures) {
                            if (mode != Drag.Zoom) { mode = Drag.Zoom; zoomStart = p.zoom; zoomAcc = 1f }
                            zoomAcc *= ev.calculateZoom()
                            val z = (zoomStart + ln(zoomAcc.toDouble()) / ln(2.0)).coerceIn(-1.0, 2.0)
                            p.set("video-zoom", String.format(Locale.US, "%.3f", z))
                            dragLevel = (Math.pow(2.0, z) * 100).toFloat()
                            ev.changes.forEach { it.consume() }
                            continue
                        }
                        val ch = ev.changes.first()
                        total += ch.positionChange()
                        if (mode == Drag.None && prefs.gestures && !p.idle && total.getDistance() > slop * 1.5f) {
                            mode = when {
                                abs(total.x) > abs(total.y) * 1.2f -> Drag.Seek
                                down.position.x < w / 2 -> Drag.Brightness
                                else -> Drag.Volume
                            }
                        }
                        when (mode) {
                            Drag.Seek -> {
                                val span = minOf(p.duration.coerceAtLeast(1.0), 180.0)
                                seekDelta = total.x / w * span
                                seekPreview = (startPos + seekDelta).coerceIn(0.0, p.duration)
                                dragMode = Drag.Seek
                                ch.consume()
                            }
                            Drag.Brightness -> {
                                val v = (startBright - total.y / (h * 0.7f)).coerceIn(0.01f, 1f)
                                activity.window.attributes = activity.window.attributes.apply { screenBrightness = v }
                                dragLevel = v; dragMode = Drag.Brightness
                                ch.consume()
                            }
                            Drag.Volume -> {
                                val v = (startVol - total.y / (h * 0.7f)).coerceIn(0f, 1f)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                                dragLevel = v; dragMode = Drag.Volume
                                ch.consume()
                            }
                            else -> {}
                        }
                    }
                    if (mode == Drag.Seek) p.seekTo(seekPreview)
                    if (mode == Drag.Zoom) ui.osd.show("Zoom ${dragLevel.roundToInt()}%", Icons.Rounded.ZoomOutMap)
                    dragMode = Drag.None
                    if (mode != Drag.None) return@awaitEachGesture
                    // ---- taps ----
                    val now = System.currentTimeMillis()
                    val x = down.position.x
                    val side = when { x < w * 0.35f -> -1; x > w * 0.65f -> 1; else -> 0 }
                    if (ui.locked) { ui.show(); return@awaitEachGesture }
                    val isDouble = now - lastTapAt < 300 && abs(x - lastTapX) < w * 0.2f
                    val inSeekRun = side != 0 && now - seekRun < 700 && rippleSide == side
                    if ((isDouble || inSeekRun) && !p.idle) {
                        singleJob?.cancel()
                        if (side != 0) {
                            p.seekBy(10.0 * side)
                            rippleAmount = if (inSeekRun) rippleAmount + 10 else 10
                            rippleSide = side; rippleSerial++
                            seekRun = now
                        } else {
                            // centre double-tap: fit ↔ fill (crop the black bars)
                            val fill = p.panscan < 0.5
                            p.set("panscan", if (fill) "1.0" else "0.0"); prefs.fillCrop = fill
                            ui.osd.show(if (fill) "Zoomed to fill" else "Fit to screen", Icons.Rounded.AspectRatio)
                        }
                        lastTapAt = 0L
                    } else {
                        lastTapAt = now; lastTapX = x
                        singleJob = scope.launch {
                            delay(230)
                            // single tap shows / hides the controls; it never pauses (Space or the play button does)
                            if (ui.speedOpen) ui.speedOpen = false
                            else if (ui.panel != null) ui.panel = null
                            else if (ui.controls) ui.controls = false else ui.show()
                        }
                    }
                }
            },
        )

        // ---- double-tap seek ripples ----
        SeekRipple(rippleSide, rippleAmount, rippleSerial, Modifier.fillMaxSize())

        // ---- centre flash (play/pause) ----
        CenterFlash(flashIcon, flashSerial, Modifier.align(Alignment.Center))

        // ---- buffering / opening ----
        if ((p.loading || p.buffering) && !pip) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = accent.light, strokeWidth = 4.dp, modifier = Modifier.size(52.dp))
            if (p.loading && p.current?.key?.startsWith("http") == true) {
                Spacer(Modifier.height(12.dp))
                Text("Opening link…", color = C.textDim, fontSize = 13.sp)
            }
        }

        // ---- gesture indicators ----
        GestureIndicator(dragMode, dragLevel, seekPreview, seekDelta, p.duration, Modifier.align(Alignment.Center))

        if (!pip) {
            // ---- controls ----
            AnimatedVisibility(ui.controls && !ui.locked, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        0f to Color(0xB3000000), 0.22f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color(0xCC000000),
                    )))
                    val sideways = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp)
                        .padding(end = if (panelOpen && sideways) 410.dp else 0.dp)) {
                        TopBar(p, ui, activity, { menuOpen = it }, menuOpen, { linkOpen = true })
                        Spacer(Modifier.weight(1f))
                        CenterControls(p, ::togglePlay)
                        Spacer(Modifier.weight(1f))
                        BottomBar(p, ui, prefs, activity)
                    }
                }
            }
            // ---- lock button ----
            AnimatedVisibility(ui.locked && ui.controls, Modifier.align(Alignment.CenterStart).padding(24.dp),
                enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(C.glass).clickable { ui.locked = false; ui.show(); ui.osd.show("Unlocked", Icons.Rounded.LockOpen) },
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, "Unlock", tint = accent.light) }
            }
            // ---- speed popup ----
            AnimatedVisibility(ui.speedOpen, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                SpeedPopup(p, prefs)
            }
            // ---- side panel ----
            val wide = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
            AnimatedVisibility(
                panelOpen, Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter),
                enter = if (wide) slideInHorizontally { it } + fadeIn() else slideInVertically { it } + fadeIn(),
                exit = if (wide) slideOutHorizontally { it } + fadeOut() else slideOutVertically { it } + fadeOut(),
            ) {
                Box(
                    (if (wide) Modifier.fillMaxHeight().width(400.dp) else Modifier.fillMaxWidth().fillMaxHeight(0.62f))
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp),
                ) {
                    SidePanel(p, ui, prefs, updater, activity)
                }
            }
            // ---- update card (only alongside the controls) ----
            UpdateCard(updater, Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp).width(340.dp)
                .then(if (ui.controls && !panelOpen) Modifier else Modifier.size(0.dp)))
            Osd(ui.osd, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
            p.error.takeIf { it.isNotEmpty() && !p.loading && p.idle }?.let {
                Text(it, color = C.danger, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
        }
        if (linkOpen) LinkDialog({ linkOpen = false; ui.typing = false }) {
            linkOpen = false; ui.typing = false
            p.open(p.queue + QueueItem(it, it), p.queue.size)
        }
        LaunchedEffect(linkOpen) { ui.typing = linkOpen }
    }
}

@Composable
private fun TopBar(
    p: Player, ui: PlayerUiState, activity: PlayerActivity,
    setMenu: (Boolean) -> Unit, menuOpen: Boolean, openLink: () -> Unit,
) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, desc = "Back") { (ctx as Activity).finish() }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(p.mediaTitle.ifEmpty { p.current?.title ?: "" }, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (p.queue.size > 1) add("${p.index + 1} of ${p.queue.size}")
                p.current?.folder?.takeIf { it.isNotEmpty() }?.let { add(it) }
                p.webQuality.takeIf { it.isNotEmpty() }?.let { add(it) }
            }.joinToString("  ·  ")
            if (sub.isNotEmpty()) Text(sub, color = C.textDim, fontSize = 12.sp, maxLines = 1)
        }
        IconBtn(Icons.Rounded.PictureInPictureAlt, desc = "Picture in picture") { activity.enterPip() }
        IconBtn(Icons.Rounded.Lock, desc = "Lock screen") { ui.locked = true; ui.panel = null; ui.osd.show("Screen locked — tap the lock to unlock", Icons.Rounded.Lock) }
        Box {
            IconBtn(Icons.Rounded.MoreVert, desc = "More") { setMenu(true) }
            DropdownMenu(menuOpen, { setMenu(false) }, containerColor = C.glassSolid) {
                @Composable fun item(t: String, icon: ImageVector, f: () -> Unit) = DropdownMenuItem(
                    text = { Text(t, color = C.text) }, leadingIcon = { Icon(icon, null, tint = C.textDim) },
                    onClick = { setMenu(false); f() },
                )
                item("Open link…", Icons.Rounded.Link) { openLink() }
                item("Take screenshot", Icons.Rounded.CameraAlt) {
                    p.screenshot(true) { ok -> ui.osd.show(if (ok) "Screenshot saved to Pictures › Lumen" else "Couldn't save the screenshot", Icons.Rounded.CameraAlt) }
                }
                item(if (p.loopFile) "Stop looping this file" else "Loop this file", Icons.Rounded.RepeatOne) {
                    p.set("loop-file", if (p.loopFile) "no" else "inf")
                    ui.osd.show(if (p.loopFile) "Loop off" else "Looping this file", Icons.Rounded.RepeatOne)
                }
                item(when { p.abA == "no" -> "A-B loop: set start"; p.abB == "no" -> "A-B loop: set end"; else -> "Clear A-B loop" }, Icons.Rounded.Repeat) { ui.abLoop(p) }
                item("Frame back", Icons.Rounded.SkipPrevious) { p.frameStep(false) }
                item("Frame forward", Icons.Rounded.SkipNext) { p.frameStep(true) }
                item("Rotate screen", Icons.Rounded.ScreenRotation) { activity.toggleRotation() }
            }
        }
    }
}

@Composable
private fun CenterControls(p: Player, togglePlay: () -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Rounded.SkipPrevious, size = 52.dp, desc = "Previous", enabled = p.queue.size > 1 || p.position > 5) { p.previous() }
        Spacer(Modifier.width(18.dp))
        IconBtn(Icons.Rounded.Replay10, size = 56.dp, desc = "Back 10 seconds") { p.seekBy(-10.0) }
        Spacer(Modifier.width(18.dp))
        val s by animateFloatAsState(if (p.paused) 1f else 0.94f, label = "play")
        Box(
            Modifier.size((76 * s).dp).clip(CircleShape).background(accent.brush).clickable(onClick = togglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (p.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (p.paused) "Play" else "Pause",
                tint = accent.onAccent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.width(18.dp))
        IconBtn(Icons.Rounded.Forward10, size = 56.dp, desc = "Forward 10 seconds") { p.seekBy(10.0) }
        Spacer(Modifier.width(18.dp))
        IconBtn(Icons.Rounded.SkipNext, size = 52.dp, desc = "Next", enabled = p.index + 1 < p.queue.size || p.loopQueue) { p.next() }
    }
}

@Composable
private fun BottomBar(p: Player, ui: PlayerUiState, prefs: Prefs, activity: PlayerActivity) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SeekBar(p, prefs) { ui.show() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtTime(p.position) + "  /  " + if (prefs.showRemaining) "−" + fmtTime(p.duration - p.position) else fmtTime(p.duration),
                color = C.text, fontSize = 13.sp, modifier = Modifier.clickable { prefs.showRemaining = !prefs.showRemaining }.padding(8.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (p.speed != 1.0) accent.a.copy(alpha = 0.3f) else C.card)
                    .clickable { ui.speedOpen = !ui.speedOpen; ui.show() }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(speedLabel(p.speed), color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(4.dp))
            fun tab(id: String) { ui.panel = if (ui.panel == id) null else id; ui.speedOpen = false; ui.show() }
            IconBtn(Icons.Rounded.ClosedCaption, active = ui.panel == "subs", desc = "Subtitles") { tab("subs") }
            IconBtn(Icons.Rounded.Audiotrack, active = ui.panel == "audio", desc = "Audio") { tab("audio") }
            IconBtn(Icons.Rounded.Tune, active = ui.panel == "video", desc = "Video") { tab("video") }
            IconBtn(Icons.AutoMirrored.Rounded.PlaylistPlay, active = ui.panel == "playlist", desc = "Playlist") { tab("playlist") }
            IconBtn(Icons.Rounded.Info, active = ui.panel == "info", desc = "Media info") { tab("info") }
            IconBtn(Icons.Rounded.Settings, active = ui.panel == "settings", desc = "Settings") { tab("settings") }
        }
    }
}

fun speedLabel(s: Double): String {
    val r = (s * 100).roundToInt() / 100.0
    return (if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()) + "×"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedPopup(p: Player, prefs: Prefs) {
    Column(
        Modifier.width(340.dp).clip(RoundedCornerShape(22.dp)).background(C.glass).border(1.dp, C.glassBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, null, tint = LocalAccent.current.light, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Speed  ·  ${speedLabel(p.speed)}", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0).forEach { v ->
                Chip(speedLabel(v), abs(p.speed - v) < 0.01) { p.changeSpeed(v) }
            }
        }
        LSlider(p.speed.toFloat(), 0.25f..4f, { p.changeSpeed((it * 20).roundToInt() / 20.0) })
        com.saraaminpour.lumen.ui.LSwitch("Keep voices natural", prefs.pitchCorrection, "Avoids chipmunk voices at higher speeds") {
            prefs.pitchCorrection = it; p.set("audio-pitch-correction", if (it) "yes" else "no")
        }
    }
}

/** Seek bar with buffered range, chapter marks and a thumbnail preview while dragging. */
@Composable
private fun SeekBar(p: Player, prefs: Prefs, onTouch: () -> Unit) {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragT by remember { mutableStateOf(0.0) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val thumbs = remember(p.current?.key) { Thumbnailer(ctx, p.current?.key) }
    DisposableEffect(thumbs) { onDispose { thumbs.release() } }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(dragging, (dragT / 2).toInt()) {
        if (dragging && prefs.thumbnails) preview = withContext(Dispatchers.IO) { thumbs.frameAt(dragT) }
    }
    val dur = p.duration.coerceAtLeast(0.001)
    val shown = if (dragging) dragT else p.position
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        if (dragging) {
            val x = (shown / dur * widthPx).toFloat()
            Column(
                Modifier.offset { IntOffset((x - with(density) { 80.dp.toPx() }).roundToInt().coerceIn(0, (widthPx - with(density) { 160.dp.toPx() }).roundToInt().coerceAtLeast(0)), -with(density) { 118.dp.toPx() }.roundToInt()) }
                    .width(160.dp).clip(RoundedCornerShape(12.dp)).background(C.glass).border(1.dp, C.glassBorder, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(90.dp), contentScale = ContentScale.Crop) }
                Text(fmtTime(shown), color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp))
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(p.duration) {
                    awaitEachGesture {
                        val d = awaitFirstDown()
                        dragging = true; onTouch()
                        dragT = (d.position.x / size.width * dur).coerceIn(0.0, dur)
                        var lastSeek = 0L
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.first()
                            if (!c.pressed) break
                            dragT = (c.position.x / size.width * dur).coerceIn(0.0, dur)
                            c.consume(); onTouch()
                            if (System.currentTimeMillis() - lastSeek > 120) { lastSeek = System.currentTimeMillis(); p.seekTo(dragT, fast = true) }
                        }
                        p.seekTo(dragT)
                        dragging = false
                    }
                },
        ) {
            val cy = size.height / 2; val trackH = if (dragging) 8.dp.toPx() else 5.dp.toPx()
            val r = CornerRadius(trackH / 2)
            drawRoundRect(Color(0x33FFFFFF), Offset(0f, cy - trackH / 2), Size(size.width, trackH), r)
            val cached = ((p.position + p.cacheTime.coerceAtLeast(0.0) - p.position).coerceAtMost(dur) / dur).toFloat()
            if (p.cacheTime > p.position) drawRoundRect(Color(0x55FFFFFF), Offset(0f, cy - trackH / 2),
                Size(size.width * ((p.cacheTime / dur).toFloat().coerceIn(0f, 1f)), trackH), r)
            val f = (shown / dur).toFloat().coerceIn(0f, 1f)
            drawRoundRect(Brush.horizontalGradient(listOf(accent.a, accent.b)), Offset(0f, cy - trackH / 2), Size(size.width * f, trackH), r)
            p.chapters.forEach { ch ->
                val x = (ch.time / dur).toFloat() * size.width
                drawRect(Color(0xAA000000), Offset(x - 1.dp.toPx(), cy - trackH / 2), Size(2.dp.toPx(), trackH))
            }
            if (p.abA != "no") (p.abA.toDoubleOrNull())?.let { a ->
                val xa = (a / dur).toFloat() * size.width
                val xb = (p.abB.toDoubleOrNull() ?: p.position).let { (it / dur).toFloat() * size.width }
                drawRect(Color(0x66F59E0B), Offset(xa, cy - trackH / 2), Size((xb - xa).coerceAtLeast(2f), trackH))
            }
            drawCircle(Color.White, if (dragging) 10.dp.toPx() else 7.dp.toPx(), Offset(size.width * f, cy))
        }
    }
}

/** Frames for seek-bar previews of local files (web streams skip previews). */
private class Thumbnailer(private val ctx: Context, key: String?) {
    private var mmr: MediaMetadataRetriever? = null
    private var tried = false
    private val uri = key?.takeIf { it.startsWith("content://") || it.startsWith("file://") }?.let { Uri.parse(it) }

    @Synchronized fun frameAt(t: Double): Bitmap? {
        val u = uri ?: return null
        if (!tried) {
            tried = true
            mmr = runCatching { MediaMetadataRetriever().apply { setDataSource(ctx, u) } }.getOrNull()
        }
        return runCatching {
            mmr?.getScaledFrameAtTime((t * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
        }.getOrNull()
    }

    @Synchronized fun release() { runCatching { mmr?.release() }; mmr = null }
}

@Composable
private fun GestureIndicator(mode: Drag, level: Float, preview: Double, delta: Double, duration: Double, modifier: Modifier) {
    val accent = LocalAccent.current
    AnimatedVisibility(mode != Drag.None && mode != Drag.Zoom, modifier, enter = fadeIn(tween(100)), exit = fadeOut(tween(250))) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(C.glass).padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (mode) {
                Drag.Seek -> {
                    Text((if (delta >= 0) "+" else "−") + fmtTime(abs(delta)), color = accent.light, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${fmtTime(preview)} / ${fmtTime(duration)}", color = C.textDim, fontSize = 14.sp)
                }
                else -> {
                    Icon(
                        if (mode == Drag.Brightness) Icons.Rounded.BrightnessMedium
                        else if (level <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        null, tint = accent.light, modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.hover)) {
                        Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).height(6.dp).background(accent.brush))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${(level * 100).roundToInt()}%", color = C.text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SeekRipple(side: Int, amount: Int, serial: Int, modifier: Modifier) {
    val accent = LocalAccent.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        a.snapTo(1f); a.animateTo(0f, tween(700, easing = LinearEasing))
    }
    if (a.value <= 0f || side == 0) return
    BoxWithConstraints(modifier) {
        Box(
            Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(maxWidth * 0.38f)
                .clip(if (side < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                .background(accent.a.copy(alpha = 0.18f * a.value)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (side < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White.copy(alpha = a.value), modifier = Modifier.size(40.dp))
                Text((if (side < 0) "−" else "+") + "$amount s", color = Color.White.copy(alpha = a.value), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CenterFlash(icon: ImageVector?, serial: Int, modifier: Modifier) {
    val accent = LocalAccent.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        a.snapTo(1f); a.animateTo(0f, tween(600))
    }
    if (icon == null || a.value <= 0f) return
    Box(
        modifier.size((84 + 30 * (1 - a.value)).dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f * a.value)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = accent.light.copy(alpha = a.value), modifier = Modifier.size(44.dp)) }
}
