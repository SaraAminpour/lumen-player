package com.saraaminpour.lumen.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saraaminpour.lumen.data.History
import com.saraaminpour.lumen.data.HistoryEntry
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.WebVideo
import dev.jdtech.mpv.MPVLib
import dev.jdtech.mpv.MPVLib.MpvEvent
import dev.jdtech.mpv.MPVLib.MpvFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class Track(
    val id: Int, val type: String, val title: String, val lang: String, val codec: String,
    val external: Boolean, val selected: Boolean, val default: Boolean, val forced: Boolean,
    val w: Int = 0, val h: Int = 0, val channels: Int = 0, val sampleRate: Int = 0,
) {
    val label: String get() {
        val base = title.ifEmpty { if (type == "sub") "Subtitle $id" else if (type == "audio") "Track $id" else "Video $id" }
        return if (lang.isNotEmpty() && !base.contains(lang, true)) "$base ($lang)" else base
    }
}

data class Chapter(val time: Double, val title: String)
data class QueueItem(val key: String, val title: String, val folder: String = "")

/** libmpv wrapper: owns the mpv instance and exposes its state to Compose. */
class Player(private val ctx: Context, private val prefs: Prefs, private val history: History) : MPVLib.EventObserver {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** null only where libmpv can't load (JVM screenshot tests); every call is null-safe. */
    val mpv: MPVLib? = try { MPVLib.create(ctx) } catch (t: Throwable) { Log.e(TAG, "mpv unavailable", t); null }

    // ---- state (main thread) ----
    var position by mutableDoubleStateOf(0.0)
    var duration by mutableDoubleStateOf(0.0)
    var paused by mutableStateOf(true)
    var idle by mutableStateOf(true)
    var buffering by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var cacheTime by mutableDoubleStateOf(0.0)
    var speed by mutableDoubleStateOf(1.0)
    var volume by mutableDoubleStateOf(100.0)
    var muted by mutableStateOf(false)
    var subDelay by mutableDoubleStateOf(0.0)
    var audioDelay by mutableDoubleStateOf(0.0)
    var subScale by mutableDoubleStateOf(1.0)
    var subVisible by mutableStateOf(true)
    var sid by mutableStateOf("no")
    var secondarySid by mutableStateOf("no")
    var aid by mutableStateOf("no")
    var tracks by mutableStateOf(emptyList<Track>())
    var chapters by mutableStateOf(emptyList<Chapter>())
    var chapter by mutableIntStateOf(-1)
    var mediaTitle by mutableStateOf("")
    var videoW by mutableIntStateOf(0)
    var videoH by mutableIntStateOf(0)
    var videoCodec by mutableStateOf("")
    var audioCodec by mutableStateOf("")
    var hwdecCurrent by mutableStateOf("")
    var loopFile by mutableStateOf(false)
    var abA by mutableStateOf("no")
    var abB by mutableStateOf("no")
    var zoom by mutableDoubleStateOf(0.0)
    var rotate by mutableIntStateOf(0)
    var panscan by mutableDoubleStateOf(0.0)
    var aspectOverride by mutableStateOf("no")
    var brightness by mutableIntStateOf(0)
    var contrast by mutableIntStateOf(0)
    var saturation by mutableIntStateOf(0)
    var gamma by mutableIntStateOf(0)
    var deband by mutableStateOf(false)
    var deinterlace by mutableStateOf(false)
    var vf by mutableStateOf("")
    var af by mutableStateOf("")
    var fps by mutableDoubleStateOf(0.0)
    var videoBitrate by mutableDoubleStateOf(0.0)
    var audioBitrate by mutableDoubleStateOf(0.0)
    var dropped by mutableIntStateOf(0)
    var fileFormat by mutableStateOf("")
    var fileSize by mutableStateOf(0L)
    var pixelFormat by mutableStateOf("")
    var colorMatrix by mutableStateOf("")
    var sampleRate by mutableIntStateOf(0)
    var channels by mutableIntStateOf(0)
    var eof by mutableStateOf(false)
    var error by mutableStateOf("")
    var webQuality by mutableStateOf("")

    // queue (managed here so content:// files can be opened one at a time)
    var queue by mutableStateOf(emptyList<QueueItem>())
    var index by mutableIntStateOf(-1)
    var loopQueue by mutableStateOf(false)
    val current get() = queue.getOrNull(index)

    private var surfaceReady = false
    private var pending: Int? = null
    private var restoreFor: String? = null
    private var pendingWebSubs: List<Pair<String, String>> = emptyList()
    var onEvent: (String) -> Unit = {}

    init {
        val config = File(ctx.filesDir, "mpv").apply { mkdirs() }
        val fonts = File(ctx.filesDir, "fonts").apply { mkdirs() }
        // libass has no system font access on Android: ship a default subtitle font (+ Persian/Arabic)
        listOf("subfont.ttf" to config, "Vazirmatn-Bold.ttf" to fonts, "subfont.ttf" to fonts).forEach { (name, dir) ->
            val out = File(dir, if (dir == fonts && name == "subfont.ttf") "NotoSans-Bold.ttf" else name)
            if (!out.exists()) ctx.assets.open("fonts/$name").use { i -> FileOutputStream(out).use { i.copyTo(it) } }
        }
        mpv?.setOptionString("config", "yes")
        mpv?.setOptionString("config-dir", config.path)
        mpv?.setOptionString("gpu-shader-cache-dir", ctx.cacheDir.path)
        mpv?.setOptionString("icc-cache-dir", ctx.cacheDir.path)
        mpv?.setOptionString("vo", "gpu")
        mpv?.setOptionString("gpu-context", "android")
        mpv?.setOptionString("opengl-es", "yes")
        mpv?.setOptionString("hwdec", if (prefs.hwdec) "mediacodec-copy" else "no")
        mpv?.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv?.setOptionString("ao", "audiotrack,opensles")
        mpv?.setOptionString("input-default-bindings", "no")
        mpv?.setOptionString("osc", "no")
        mpv?.setOptionString("ytdl", "no")
        mpv?.setOptionString("keep-open", "yes")
        mpv?.setOptionString("idle", "yes")
        mpv?.setOptionString("save-position-on-quit", "no")
        mpv?.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        mpv?.setOptionString("demuxer-max-back-bytes", "${32 * 1024 * 1024}")
        mpv?.setOptionString("volume-max", "200")
        mpv?.setOptionString("sub-fonts-dir", fonts.path)
        mpv?.setOptionString("sub-font", "Noto Sans")
        mpv?.setOptionString("sub-auto", "no")
        mpv?.setOptionString("screenshot-directory", File(ctx.cacheDir, "shots").apply { mkdirs() }.path)
        mpv?.setOptionString("screenshot-format", "png")
        mpv?.setOptionString(
            "reset-on-next-file",
            "sub-delay,audio-delay,ab-loop-a,ab-loop-b,video-zoom,video-pan-x,video-pan-y,video-rotate,secondary-sid",
        )
        mpv?.init()
        mpv?.setOptionString("force-window", "no")
        mpv?.addObserver(this)
        OBSERVED.forEach { (name, fmt) -> mpv?.observeProperty(name, fmt) }
        applyPrefs()
        // keep Continue watching current even if Android later kills the app mid-film
        scope.launch { while (true) { kotlinx.coroutines.delay(10_000); if (!paused && !idle) saveState() } }
    }

    fun applyPrefs() {
        set("audio-pitch-correction", if (prefs.pitchCorrection) "yes" else "no")
        set("audio-channels", if (prefs.downmix) "stereo" else "auto-safe")
        set("panscan", if (prefs.fillCrop) "1.0" else "0.0")
        set("sub-scale", prefs.subScale.toString())
        set("sub-pos", prefs.subPos.toString())
        applySubStyle()
        if (prefs.normalize != af.contains("@norm")) toggleFilter("af", NORM)
    }

    fun applySubStyle() {
        set("sub-color", prefs.subColor)
        set("sub-ass-override", if (prefs.subOverride) "force" else "scale")
        set("sub-border-style", if (prefs.subBox) "opaque-box" else "outline-and-shadow")
        set("sub-back-color", if (prefs.subBox) "#B3000000" else "#00000000")
    }

    // ---- surface ----
    fun attachSurface(s: Surface) {
        mpv?.attachSurface(s)
        mpv?.setOptionString("force-window", "yes")
        surfaceReady = true
        val p = pending
        if (p != null) { pending = null; play(p) } else set("vo", "gpu")
    }

    fun surfaceSize(w: Int, h: Int) = set("android-surface-size", "${w}x$h")

    fun detachSurface() {
        surfaceReady = false
        set("vo", "null")
        mpv?.setOptionString("force-window", "no")
        mpv?.detachSurface()
    }

    // ---- opening ----
    fun open(items: List<QueueItem>, start: Int) {
        queue = items
        play(start)
    }

    fun play(i: Int) {
        if (i !in queue.indices) return
        saveState()
        index = i
        if (!surfaceReady) { pending = i; return }
        val item = queue[i]
        error = ""; eof = false; loading = true; webQuality = ""
        val saved = if (prefs.resume) history.get(item.key) else null
        val start = saved?.takeIf { it.pos > 5 && !it.finished }?.pos
        restoreFor = item.key
        scope.launch {
            try {
                val target = resolve(item)
                val opts = ArrayList<String>()
                opts += "force-media-title=" + q(target.title.ifEmpty { item.title })
                if (start != null) opts += "start=$start"
                target.audio?.let { opts += "audio-file=" + q(it) }
                pendingWebSubs = target.subs
                webQuality = target.quality
                mpv?.command(arrayOf("loadfile", target.url, "replace", "-1", opts.joinToString(",")))
            } catch (e: Exception) {
                Log.w(TAG, "open failed", e)
                loading = false
                error = when {
                    item.key.startsWith("http") -> "Couldn't open this link" + (e.message?.let { ": ${it.take(90)}" } ?: "")
                    else -> "Couldn't open this file"
                }
                onEvent("error")
            }
        }
    }

    private class Target(val url: String, val title: String, val audio: String? = null,
                         val subs: List<Pair<String, String>> = emptyList(), val quality: String = "")

    private suspend fun resolve(item: QueueItem): Target = withContext(Dispatchers.IO) {
        val k = item.key
        when {
            k.startsWith("content://") || k.startsWith("file://") -> {
                val pfd = ctx.contentResolver.openFileDescriptor(Uri.parse(k), "r")
                    ?: throw IllegalStateException("no file descriptor")
                Target("fdclose://${pfd.detachFd()}", item.title)
            }
            k.startsWith("http", true) && WebVideo.isWebPage(k) -> {
                val r = WebVideo.resolve(k, prefs.streamQuality)
                Target(r.video, r.title, r.audio, r.subtitles, r.quality)
            }
            else -> Target(k, item.title)
        }
    }

    /** mpv's %length% quoting keeps commas in URLs from splitting the option list. */
    private fun q(v: String) = "%${v.toByteArray().size}%$v"

    fun next() = when {
        index + 1 < queue.size -> play(index + 1)
        loopQueue && queue.isNotEmpty() -> play(0)
        else -> onEvent("end")
    }

    fun previous() = if (position > 5 || index == 0) seekTo(0.0) else play(index - 1)

    fun removeFromQueue(i: Int) {
        if (i == index || i !in queue.indices) return
        queue = queue.filterIndexed { j, _ -> j != i }
        if (i < index) index--
    }

    fun shuffleQueue() {
        val cur = current ?: return
        queue = listOf(cur) + (queue - cur).shuffled()
        index = 0
    }

    fun clearOthers() {
        val cur = current ?: return
        queue = listOf(cur); index = 0
    }

    fun addToQueue(items: List<QueueItem>) { queue = queue + items }

    // ---- commands ----
    fun set(name: String, value: String) { try { mpv?.setPropertyString(name, value) } catch (e: Exception) { Log.w(TAG, "set $name", e) } }
    fun cmd(vararg args: String) { try { mpv?.command(arrayOf(*args)) } catch (e: Exception) { Log.w(TAG, "cmd ${args.toList()}", e) } }

    fun togglePause() { if (!idle) set("pause", if (paused) "no" else "yes") }
    fun pause() { if (!idle && !paused) set("pause", "yes") }
    fun seekBy(s: Double) { if (!idle) cmd("seek", s.toString(), "relative") }
    fun seekTo(t: Double, fast: Boolean = false) {
        if (!idle) cmd("seek", t.toString(), if (fast) "absolute+keyframes" else "absolute")
    }
    fun frameStep(forward: Boolean) = cmd(if (forward) "frame-step" else "frame-back-step")
    fun changeSpeed(v: Double) = set("speed", String.format(java.util.Locale.US, "%.2f", v))
    fun changeSubDelay(v: Double) = set("sub-delay", String.format(java.util.Locale.US, "%.3f", v))
    fun changeAudioDelay(v: Double) = set("audio-delay", String.format(java.util.Locale.US, "%.3f", v))
    fun subStep(n: Int) = cmd("sub-step", n.toString())
    fun selectTrack(type: String, id: Int?) = set(if (type == "sub") "sid" else if (type == "audio") "aid" else "vid", id?.toString() ?: "no")
    fun selectSecondary(id: Int?) = set("secondary-sid", id?.toString() ?: "no")

    fun toggleFilter(kind: String, spec: String) = cmd(kind, "toggle", spec)

    fun addSubtitle(uri: Uri, name: String) = scope.launch {
        val fd = withContext(Dispatchers.IO) { ctx.contentResolver.openFileDescriptor(uri, "r")?.detachFd() } ?: return@launch
        cmd("sub-add", "fdclose://$fd", "select", name)
    }

    fun addAudio(uri: Uri, name: String) = scope.launch {
        val fd = withContext(Dispatchers.IO) { ctx.contentResolver.openFileDescriptor(uri, "r")?.detachFd() } ?: return@launch
        cmd("audio-add", "fdclose://$fd", "select", name)
    }

    fun abLoop(): String {
        return when {
            abA == "no" -> { set("ab-loop-a", position.toString()); "a" }
            abB == "no" -> { set("ab-loop-b", position.toString()); "b" }
            else -> { set("ab-loop-a", "no"); set("ab-loop-b", "no"); "clear" }
        }
    }

    /** Takes a screenshot and files it under Pictures/Lumen. Returns false on failure. */
    fun screenshot(withSubs: Boolean, done: (Boolean) -> Unit) = scope.launch {
        val tmp = File(ctx.cacheDir, "shots/shot-${System.currentTimeMillis()}.png")
        cmd("screenshot-to-file", tmp.path, if (withSubs) "subtitles" else "video")
        val ok = withContext(Dispatchers.IO) {
            var waited = 0
            while (!tmp.exists() && waited < 3000) { Thread.sleep(50); waited += 50 }
            if (!tmp.exists()) return@withContext false
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Lumen ${mediaTitle.take(60)} ${fmtStamp(position)}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumen")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                ctx.contentResolver.openOutputStream(uri)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                tmp.delete(); true
            } catch (e: Exception) { Log.w(TAG, "screenshot", e); false }
        }
        done(ok)
    }

    private fun fmtStamp(t: Double): String {
        val s = t.toLong(); return String.format(java.util.Locale.US, "%02d.%02d.%02d", s / 3600, s / 60 % 60, s % 60)
    }

    /** Remember position, tracks and delays for the current item (Continue watching + resume). */
    fun saveState() {
        val item = current ?: return
        if (duration <= 0 || idle) return
        val nearEnd = duration - position < 30
        history.put(
            HistoryEntry(
                key = item.key, title = mediaTitle.ifEmpty { item.title }, pos = if (nearEnd) 0.0 else position,
                dur = duration, time = System.currentTimeMillis(), folder = item.folder, sid = sid, aid = aid,
                secondarySid = secondarySid, subDelay = subDelay, audioDelay = audioDelay,
            )
        )
    }

    private fun restoreState() {
        val key = restoreFor ?: return
        restoreFor = null
        if (!prefs.resume) return
        val e = history.get(key) ?: return
        if (e.sid.isNotEmpty()) set("sid", e.sid)
        if (e.aid.isNotEmpty() && e.aid != "no") set("aid", e.aid)
        if (e.secondarySid.isNotEmpty() && e.secondarySid != "no") set("secondary-sid", e.secondarySid)
        if (e.subDelay != 0.0) changeSubDelay(e.subDelay)
        if (e.audioDelay != 0.0) changeAudioDelay(e.audioDelay)
    }

    fun destroy() {
        saveState()
        scope.cancel()
        mpv?.removeObserver(this)
        mpv?.destroy()
    }

    // ---- mpv events (native thread) ----
    override fun eventProperty(property: String) { main.post { onNoneProperty(property) } }
    override fun eventProperty(property: String, value: Long) { main.post { onLong(property, value) } }
    override fun eventProperty(property: String, value: Double) { main.post { onDouble(property, value) } }
    override fun eventProperty(property: String, value: Boolean) { main.post { onFlag(property, value) } }
    override fun eventProperty(property: String, value: String) { main.post { onString(property, value) } }
    override fun event(eventId: Int) { main.post { onMpvEvent(eventId) } }

    private fun onMpvEvent(id: Int) {
        when (id) {
            MpvEvent.MPV_EVENT_FILE_LOADED -> {
                loading = false
                restoreState()
                pendingWebSubs.forEach { (url, lang) -> cmd("sub-add", url, "auto", lang, lang) }
                pendingWebSubs = emptyList()
                onEvent("loaded")
            }
            MpvEvent.MPV_EVENT_END_FILE -> loading = false
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> loading = false
        }
    }

    private fun onNoneProperty(p: String) {
        when (p) {
            "track-list" -> readTracks()
            "chapter-list" -> readChapters()
            "video-params" -> {
                videoW = mpv?.getPropertyInt("video-params/w") ?: 0
                videoH = mpv?.getPropertyInt("video-params/h") ?: 0
                pixelFormat = mpv?.getPropertyString("video-params/pixelformat") ?: ""
                colorMatrix = mpv?.getPropertyString("video-params/colormatrix") ?: ""
                onEvent("video-params")
            }
            "audio-params" -> {
                sampleRate = mpv?.getPropertyInt("audio-params/samplerate") ?: 0
                channels = mpv?.getPropertyInt("audio-params/channel-count") ?: 0
            }
        }
    }

    private fun onLong(p: String, v: Long) {
        when (p) {
            "chapter" -> chapter = v.toInt()
            "video-rotate" -> rotate = v.toInt()
            "brightness" -> brightness = v.toInt()
            "contrast" -> contrast = v.toInt()
            "saturation" -> saturation = v.toInt()
            "gamma" -> gamma = v.toInt()
            "frame-drop-count" -> dropped = v.toInt()
            "file-size" -> fileSize = v
        }
    }

    private fun onDouble(p: String, v: Double) {
        when (p) {
            "time-pos" -> position = v
            "duration" -> duration = v
            "demuxer-cache-time" -> cacheTime = v
            "speed" -> speed = v
            "volume" -> volume = v
            "sub-delay" -> subDelay = v
            "audio-delay" -> audioDelay = v
            "sub-scale" -> subScale = v
            "video-zoom" -> zoom = v
            "panscan" -> panscan = v
            "estimated-vf-fps" -> fps = v
            "video-bitrate" -> videoBitrate = v
            "audio-bitrate" -> audioBitrate = v
        }
    }

    private fun onFlag(p: String, v: Boolean) {
        when (p) {
            "pause" -> { paused = v; if (v) saveState() }
            "idle-active" -> idle = v
            "paused-for-cache" -> buffering = v
            "mute" -> muted = v
            "sub-visibility" -> subVisible = v
            "deband" -> deband = v
            "eof-reached" -> {
                eof = v
                if (v && !loopFile) { saveState(); next() }
            }
        }
    }

    private fun onString(p: String, v: String) {
        when (p) {
            "sid" -> sid = v
            "secondary-sid" -> secondarySid = v
            "aid" -> aid = v
            "media-title" -> mediaTitle = v
            "video-codec" -> videoCodec = v
            "audio-codec-name" -> audioCodec = v
            "hwdec-current" -> hwdecCurrent = v
            "loop-file" -> loopFile = v != "no"
            "ab-loop-a" -> abA = v
            "ab-loop-b" -> abB = v
            "video-aspect-override" -> aspectOverride = v
            "deinterlace" -> deinterlace = v == "yes"
            "vf" -> vf = v
            "af" -> af = v
            "file-format" -> fileFormat = v
        }
    }

    private fun readTracks() {
        val n = mpv?.getPropertyInt("track-list/count") ?: 0
        tracks = (0 until n).mapNotNull { i ->
            val b = "track-list/$i/"
            val type = mpv?.getPropertyString(b + "type") ?: return@mapNotNull null
            Track(
                id = mpv?.getPropertyInt(b + "id") ?: return@mapNotNull null, type = type,
                title = mpv?.getPropertyString(b + "title") ?: "", lang = mpv?.getPropertyString(b + "lang") ?: "",
                codec = mpv?.getPropertyString(b + "codec") ?: "", external = mpv?.getPropertyBoolean(b + "external") ?: false,
                selected = mpv?.getPropertyBoolean(b + "selected") ?: false, default = mpv?.getPropertyBoolean(b + "default") ?: false,
                forced = mpv?.getPropertyBoolean(b + "forced") ?: false,
                w = mpv?.getPropertyInt(b + "demux-w") ?: 0, h = mpv?.getPropertyInt(b + "demux-h") ?: 0,
                channels = mpv?.getPropertyInt(b + "demux-channel-count") ?: 0,
                sampleRate = mpv?.getPropertyInt(b + "demux-samplerate") ?: 0,
            )
        }
    }

    private fun readChapters() {
        val n = mpv?.getPropertyInt("chapter-list/count") ?: 0
        chapters = (0 until n).map { i ->
            Chapter(mpv?.getPropertyDouble("chapter-list/$i/time") ?: 0.0, mpv?.getPropertyString("chapter-list/$i/title") ?: "")
        }
    }

    companion object {
        const val TAG = "Lumen"
        const val NORM = "@norm:lavfi=[dynaudnorm=f=250:g=31:p=0.5]"
        const val SHARP = "@sharp:lavfi=[cas=0.6]"
        const val FLIP = "@flip:hflip"
        private val OBSERVED = listOf(
            "time-pos" to MpvFormat.MPV_FORMAT_DOUBLE, "duration" to MpvFormat.MPV_FORMAT_DOUBLE,
            "pause" to MpvFormat.MPV_FORMAT_FLAG, "idle-active" to MpvFormat.MPV_FORMAT_FLAG,
            "paused-for-cache" to MpvFormat.MPV_FORMAT_FLAG, "eof-reached" to MpvFormat.MPV_FORMAT_FLAG,
            "demuxer-cache-time" to MpvFormat.MPV_FORMAT_DOUBLE, "speed" to MpvFormat.MPV_FORMAT_DOUBLE,
            "volume" to MpvFormat.MPV_FORMAT_DOUBLE, "mute" to MpvFormat.MPV_FORMAT_FLAG,
            "sub-delay" to MpvFormat.MPV_FORMAT_DOUBLE, "audio-delay" to MpvFormat.MPV_FORMAT_DOUBLE,
            "sub-scale" to MpvFormat.MPV_FORMAT_DOUBLE, "sub-visibility" to MpvFormat.MPV_FORMAT_FLAG,
            "sid" to MpvFormat.MPV_FORMAT_STRING, "secondary-sid" to MpvFormat.MPV_FORMAT_STRING,
            "aid" to MpvFormat.MPV_FORMAT_STRING, "track-list" to MpvFormat.MPV_FORMAT_NONE,
            "chapter-list" to MpvFormat.MPV_FORMAT_NONE, "chapter" to MpvFormat.MPV_FORMAT_INT64,
            "media-title" to MpvFormat.MPV_FORMAT_STRING, "video-params" to MpvFormat.MPV_FORMAT_NONE,
            "audio-params" to MpvFormat.MPV_FORMAT_NONE, "video-codec" to MpvFormat.MPV_FORMAT_STRING,
            "audio-codec-name" to MpvFormat.MPV_FORMAT_STRING, "hwdec-current" to MpvFormat.MPV_FORMAT_STRING,
            "loop-file" to MpvFormat.MPV_FORMAT_STRING, "ab-loop-a" to MpvFormat.MPV_FORMAT_STRING,
            "ab-loop-b" to MpvFormat.MPV_FORMAT_STRING, "video-zoom" to MpvFormat.MPV_FORMAT_DOUBLE,
            "video-rotate" to MpvFormat.MPV_FORMAT_INT64, "panscan" to MpvFormat.MPV_FORMAT_DOUBLE,
            "video-aspect-override" to MpvFormat.MPV_FORMAT_STRING, "brightness" to MpvFormat.MPV_FORMAT_INT64,
            "contrast" to MpvFormat.MPV_FORMAT_INT64, "saturation" to MpvFormat.MPV_FORMAT_INT64,
            "gamma" to MpvFormat.MPV_FORMAT_INT64, "deband" to MpvFormat.MPV_FORMAT_FLAG,
            "deinterlace" to MpvFormat.MPV_FORMAT_STRING, "vf" to MpvFormat.MPV_FORMAT_STRING,
            "af" to MpvFormat.MPV_FORMAT_STRING, "estimated-vf-fps" to MpvFormat.MPV_FORMAT_DOUBLE,
            "video-bitrate" to MpvFormat.MPV_FORMAT_DOUBLE, "audio-bitrate" to MpvFormat.MPV_FORMAT_DOUBLE,
            "frame-drop-count" to MpvFormat.MPV_FORMAT_INT64, "file-format" to MpvFormat.MPV_FORMAT_STRING,
            "file-size" to MpvFormat.MPV_FORMAT_INT64,
        )
    }
}

/** Roughly how the subtitle delay reads to a person. */
fun delayMeaning(v: Double): String = when {
    (v * 1000).roundToInt() == 0 -> "In sync with the file's original timing"
    v > 0 -> "Subtitles appear later than in the file"
    else -> "Subtitles appear earlier than in the file"
}
