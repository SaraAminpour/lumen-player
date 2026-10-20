package com.saraaminpour.lumen.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saraaminpour.lumen.PlayerActivity
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.Updater
import com.saraaminpour.lumen.ui.C
import com.saraaminpour.lumen.ui.Chip
import com.saraaminpour.lumen.ui.IconBtn
import com.saraaminpour.lumen.ui.LSlider
import com.saraaminpour.lumen.ui.LSwitch
import com.saraaminpour.lumen.ui.Label
import com.saraaminpour.lumen.ui.LocalAccent
import com.saraaminpour.lumen.ui.PillButton
import com.saraaminpour.lumen.ui.Section
import com.saraaminpour.lumen.ui.SettingsContent
import com.saraaminpour.lumen.ui.fmtBytes
import com.saraaminpour.lumen.ui.fmtDelay
import com.saraaminpour.lumen.ui.fmtTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Tab(val id: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("subs", "Subtitles", Icons.Rounded.ClosedCaption), Tab("audio", "Audio", Icons.Rounded.MusicNote),
    Tab("video", "Video", Icons.Rounded.Tune), Tab("playlist", "Playlist", Icons.AutoMirrored.Rounded.PlaylistPlay),
    Tab("info", "Info", Icons.Rounded.Info), Tab("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun SidePanel(p: Player, ui: PlayerUiState, prefs: Prefs, updater: Updater, activity: PlayerActivity) {
    val accent = LocalAccent.current
    val id = ui.panel ?: return
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(C.glass)
            .border(1.dp, accent.light.copy(alpha = 0.25f), RoundedCornerShape(24.dp)).clickable(enabled = false) {},
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(TABS.first { it.id == id }.label, color = C.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconBtn(Icons.Rounded.Close, size = 40.dp, desc = "Close panel") { ui.panel = null }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.card).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TABS.forEach { t ->
                val on = t.id == id
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) accent.a.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { ui.panel = t.id }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, tint = if (on) accent.light else C.textDim, modifier = Modifier.size(20.dp))
                    Text(t.label, color = if (on) C.text else C.textFaint, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (id) {
                "subs" -> SubtitlePage(p, ui, prefs)
                "audio" -> AudioPage(p, ui, prefs)
                "video" -> VideoPage(p, ui, prefs)
                "playlist" -> PlaylistPage(p, ui)
                "info" -> InfoPage(p, ui)
                "settings" -> SettingsContent(prefs, updater) {
                    p.set("hwdec", if (prefs.hwdec) "mediacodec-copy" else "no")
                }
            }
        }
    }
}

@Composable
private fun TrackList(p: Player, type: String, selected: String, offText: String = "Off", exclude: String = "", onPick: (Int?) -> Unit) {
    val accent = LocalAccent.current
    val list = p.tracks.filter { it.type == type && it.id.toString() != exclude }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable fun row(label: String, detail: String, on: Boolean, pick: () -> Unit) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (on) accent.a.copy(alpha = 0.2f) else Color.Transparent)
                    .border(1.dp, if (on) accent.light.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(onClick = pick).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, if (on) accent.light else C.textFaint, CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(accent.light))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, color = C.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (detail.isNotEmpty()) Text(detail, color = C.textFaint, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        row(offText, "", selected == "no" || selected.isEmpty()) { onPick(null) }
        list.forEach { t ->
            val detail = buildList {
                add(t.codec.uppercase())
                if (t.channels > 0) add("${t.channels} ch")
                if (t.external) add("External")
                if (t.default) add("Default")
                if (t.forced) add("Forced")
            }.joinToString(" · ")
            row(t.label, detail, selected == t.id.toString()) { onPick(t.id) }
        }
    }
}

@Composable
private fun DelayControl(value: Double, meaning: String, onSet: (Double) -> Unit) {
    val accent = LocalAccent.current
    var drag by remember { mutableFloatStateOf(Float.NaN) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtDelay(value), color = if (abs(value) < 0.0005) C.text else accent.light, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(meaning, color = C.textFaint, fontSize = 11.sp)
            }
            PillButton("Reset", icon = Icons.Rounded.RestartAlt, height = 32.dp, enabled = abs(value) > 0.0005) { onSet(0.0) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(-1.0 to "−1 s", -0.1 to "−100", -0.05 to "−50", 0.05 to "+50", 0.1 to "+100", 1.0 to "+1 s").forEach { (d, t) ->
                PillButton(t, Modifier.weight(1f), height = 34.dp) { onSet(((value + d) * 1000).roundToInt() / 1000.0) }
            }
        }
        LSlider(if (drag.isNaN()) value.toFloat() else drag, -10f..10f, { drag = (it * 20).roundToInt() / 20f },
            onDone = { if (!drag.isNaN()) onSet(drag.toDouble()); drag = Float.NaN })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlePage(p: Player, ui: PlayerUiState, prefs: Prefs) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { p.addSubtitle(it, Library.displayName(ctx, it) ?: "Subtitle"); ui.osd.show("Subtitle loaded", Icons.Rounded.Subtitles) }
    }
    var markShown by remember { mutableDoubleStateOf(-1.0) }
    var markHeard by remember { mutableDoubleStateOf(-1.0) }
    fun applyAssistant() {
        if (markShown < 0 || markHeard < 0) return
        val d = ((markHeard - markShown) * 1000).roundToInt() / 1000.0
        val v = ((p.subDelay + d) * 1000).roundToInt() / 1000.0
        p.changeSubDelay(v)
        ui.osd.show("Subtitles shifted ${fmtDelay(d)}  →  ${fmtDelay(v)}", Icons.Rounded.Sync)
        markShown = -1.0; markHeard = -1.0
    }
    Section("Track", Icons.Rounded.Subtitles) {
        TrackList(p, "sub", p.sid) { p.selectTrack("sub", it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Load subtitle file…", icon = Icons.Rounded.NoteAdd, height = 34.dp) { pick.launch(arrayOf("*/*")) }
            PillButton(if (p.subVisible) "Hide" else "Show", icon = if (p.subVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, height = 34.dp) {
                p.set("sub-visibility", if (p.subVisible) "no" else "yes")
            }
        }
    }
    Section("Timing", Icons.Rounded.Timer) {
        DelayControl(p.subDelay, delayMeaning(p.subDelay)) { p.changeSubDelay(it); ui.osd.show("Subtitle delay ${fmtDelay(it)}", Icons.Rounded.Timer) }
        Label("Keyboard: Z / X nudge by 100 ms, Shift+Z / Shift+X by 1 s.", C.textFaint)
    }
    Section("Snap to a line", Icons.Rounded.Sync) {
        Label("Hearing a line whose subtitle is missing or late? Jump the subtitle timing so a neighbouring line shows right now.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Previous line now", Modifier.weight(1f), Icons.Rounded.ChevronLeft, height = 38.dp) { p.subStep(-1); ui.subToast(p) }
            PillButton("Next line now", Modifier.weight(1f), Icons.Rounded.ChevronRight, height = 38.dp) { p.subStep(1); ui.subToast(p) }
        }
    }
    Section("Sync assistant", Icons.Rounded.AutoFixHigh) {
        Label("Tap Line appeared when a subtitle pops up, and Heard it when that line is spoken (any order). Lumen shifts the subtitles by the difference.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (markShown >= 0) "Appeared ${fmtTime(markShown)}" else "Line appeared", Modifier.weight(1f), Icons.Rounded.Subtitles,
                checked = markShown >= 0, height = 40.dp) { markShown = p.position; applyAssistant() }
            PillButton(if (markHeard >= 0) "Heard ${fmtTime(markHeard)}" else "Heard it", Modifier.weight(1f), Icons.Rounded.Headphones,
                checked = markHeard >= 0, height = 40.dp) { markHeard = p.position; applyAssistant() }
        }
    }
    Section("Appearance", Icons.Rounded.FormatSize) {
        Label("Size  ·  ${(prefs.subScale * 100).roundToInt()}%")
        LSlider(prefs.subScale, 0.5f..2.5f, { prefs.subScale = (it * 20).roundToInt() / 20f; p.set("sub-scale", prefs.subScale.toString()) })
        Label("Position  ·  " + if (prefs.subPos >= 100) "bottom" else "${100 - prefs.subPos}% up")
        LSlider(prefs.subPos.toFloat(), 30f..100f, { prefs.subPos = it.roundToInt(); p.set("sub-pos", prefs.subPos.toString()) })
        Label("Colour")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("#FFFFFF", "#FFE45C", "#7DF9FF", "#B6FF8A", "#FFB3D9").forEach { c ->
                val on = prefs.subColor == c
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(c)))
                        .border(3.dp, if (on) accent.light else Color.Transparent, CircleShape)
                        .clickable { prefs.subColor = c; p.applySubStyle() },
                )
            }
        }
        LSwitch("Background box", prefs.subBox, "Dark box behind the text for readability") { prefs.subBox = it; p.applySubStyle() }
        LSwitch("Override styled (ASS) subtitles", prefs.subOverride, "Apply your size and colour to anime-style subtitles too") {
            prefs.subOverride = it; p.applySubStyle()
        }
    }
    Section("Second subtitle", Icons.Rounded.Translate) {
        Label("Show another language at the top of the screen at the same time — great for learning.")
        TrackList(p, "sub", p.secondarySid, offText = "None", exclude = p.sid) { p.selectSecondary(it) }
    }
}

@Composable
private fun AudioPage(p: Player, ui: PlayerUiState, prefs: Prefs) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { p.addAudio(it, Library.displayName(ctx, it) ?: "Audio"); ui.osd.show("Audio track added", Icons.Rounded.MusicNote) }
    }
    Section("Track", Icons.Rounded.MusicNote) {
        TrackList(p, "audio", p.aid, offText = "No audio") { p.selectTrack("audio", it) }
        PillButton("Load audio file…", icon = Icons.Rounded.NoteAdd, height = 34.dp) { pick.launch(arrayOf("audio/*", "video/*", "*/*")) }
    }
    Section("Audio sync", Icons.Rounded.Timer) {
        DelayControl(p.audioDelay, if (abs(p.audioDelay) < 0.0005) "In sync with the picture" else if (p.audioDelay > 0) "Sound plays later" else "Sound plays earlier") {
            p.changeAudioDelay(it); ui.osd.show("Audio delay ${fmtDelay(it)}", Icons.Rounded.Timer)
        }
    }
    Section("Sound", Icons.Rounded.GraphicEq) {
        Label("Volume  ·  ${p.volume.roundToInt()}%")
        LSlider(p.volume.toFloat(), 0f..if (prefs.volumeBoost) 200f else 100f, { p.set("volume", it.roundToInt().toString()) })
        LSwitch("Volume boost up to 200%", prefs.volumeBoost, "For quiet files. Very high values can distort") {
            prefs.volumeBoost = it; if (!it && p.volume > 100) p.set("volume", "100")
        }
        LSwitch("Normalize loudness", prefs.normalize, "Evens out quiet dialogue and loud explosions") {
            prefs.normalize = it; p.toggleFilter("af", Player.NORM)
        }
        LSwitch("Downmix to stereo", prefs.downmix, "Better dialogue on headphones for 5.1 / 7.1 audio") {
            prefs.downmix = it; p.set("audio-channels", if (it) "stereo" else "auto-safe")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoPage(p: Player, ui: PlayerUiState, prefs: Prefs) {
    val aspect = p.aspectOverride.toDoubleOrNull() ?: -1.0
    Section("Aspect ratio", Icons.Rounded.AspectRatio) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Auto" to -1.0, "16:9" to 16 / 9.0, "4:3" to 4 / 3.0, "21:9" to 21 / 9.0, "2.35:1" to 2.35, "1.85:1" to 1.85, "1:1" to 1.0, "9:16" to 9 / 16.0)
                .forEach { (name, v) ->
                    Chip(name, if (v < 0) aspect <= 0 else abs(aspect - v) < 0.01) {
                        p.set("video-aspect-override", if (v < 0) "no" else String.format(Locale.US, "%.6f", v))
                        ui.osd.show("Aspect $name", Icons.Rounded.AspectRatio)
                    }
                }
        }
        LSwitch("Fill the screen (crop black bars)", p.panscan > 0.5, "Also: double-tap the middle of the video") {
            prefs.fillCrop = it; p.set("panscan", if (it) "1.0" else "0.0")
        }
    }
    Section("Zoom & rotate", Icons.Rounded.ZoomIn) {
        Label("Zoom  ·  ${(Math.pow(2.0, p.zoom) * 100).roundToInt()}%   (or pinch the video)")
        LSlider(p.zoom.toFloat(), -1f..2f, { p.set("video-zoom", String.format(Locale.US, "%.2f", it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("", icon = Icons.Rounded.RotateLeft, height = 36.dp) { p.set("video-rotate", ((p.rotate + 270) % 360).toString()) }
            PillButton("", icon = Icons.Rounded.RotateRight, height = 36.dp) { p.set("video-rotate", ((p.rotate + 90) % 360).toString()) }
            PillButton("Mirror", icon = Icons.Rounded.Flip, height = 36.dp, checked = p.vf.contains("@flip")) { p.toggleFilter("vf", Player.FLIP) }
            PillButton("Reset", icon = Icons.Rounded.RestartAlt, height = 36.dp) {
                p.set("video-zoom", "0"); p.set("video-pan-x", "0"); p.set("video-pan-y", "0"); p.set("video-rotate", "0")
            }
        }
    }
    Section("Picture", Icons.Rounded.Contrast) {
        listOf(Triple("Brightness", "brightness", p.brightness), Triple("Contrast", "contrast", p.contrast),
            Triple("Saturation", "saturation", p.saturation), Triple("Gamma", "gamma", p.gamma)).forEach { (label, prop, v) ->
            Label("$label  ·  $v")
            LSlider(v.toFloat(), -50f..50f, { p.set(prop, it.roundToInt().toString()) })
        }
        PillButton("Reset picture", icon = Icons.Rounded.RestartAlt, height = 34.dp) {
            listOf("brightness", "contrast", "saturation", "gamma").forEach { p.set(it, "0") }
        }
    }
    Section("Quality", Icons.Rounded.AutoFixHigh) {
        LSwitch("Sharpen", p.vf.contains("@sharp"), "Contrast-adaptive sharpening for soft footage") { p.toggleFilter("vf", Player.SHARP) }
        LSwitch("Debanding", p.deband, "Smooths colour banding in dark scenes and skies") { p.set("deband", if (it) "yes" else "no") }
        LSwitch("Deinterlace", p.deinterlace, "For old TV recordings and DVDs with comb lines") { p.set("deinterlace", if (it) "yes" else "no") }
        LSwitch("Hardware decoding", prefs.hwdec, if (p.hwdecCurrent.isNotEmpty() && p.hwdecCurrent != "no") "Active: ${p.hwdecCurrent}" else "Using the CPU") {
            prefs.hwdec = it; p.set("hwdec", if (it) "mediacodec-copy" else "no")
        }
    }
    Section("Screenshot", Icons.Rounded.CameraAlt) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "With subtitles", false to "Video only").forEach { (subs, t) ->
                PillButton(t, Modifier.weight(1f), Icons.Rounded.CameraAlt, height = 36.dp) {
                    p.screenshot(subs) { ok -> ui.osd.show(if (ok) "Screenshot saved to Pictures › Lumen" else "Couldn't save the screenshot", Icons.Rounded.CameraAlt) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPage(p: Player, ui: PlayerUiState) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            p.addToQueue(uris.map { QueueItem(it.toString(), Library.displayName(ctx, it) ?: "Video") })
            ui.osd.show(if (uris.size == 1) "Added to playlist" else "${uris.size} files added", Icons.AutoMirrored.Rounded.PlaylistPlay)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Add files", icon = Icons.Rounded.Add, height = 34.dp) { add.launch(arrayOf("video/*", "audio/*", "*/*")) }
        PillButton("Shuffle", icon = Icons.Rounded.Shuffle, height = 34.dp) { p.shuffleQueue(); ui.osd.show("Playlist shuffled", Icons.Rounded.Shuffle) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Loop all", icon = Icons.Rounded.Repeat, height = 34.dp, checked = p.loopQueue) { p.loopQueue = !p.loopQueue }
        PillButton("Loop file", icon = Icons.Rounded.RepeatOne, height = 34.dp, checked = p.loopFile) { p.set("loop-file", if (p.loopFile) "no" else "inf") }
        PillButton("Clear others", height = 34.dp, enabled = p.queue.size > 1) { p.clearOthers() }
    }
    p.queue.forEachIndexed { i, item ->
        val on = i == p.index
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (on) accent.a.copy(alpha = 0.22f) else C.card)
                .clickable { p.play(i) }.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", color = if (on) accent.light else C.textFaint, fontSize = 12.sp, modifier = Modifier.width(28.dp))
            Text(item.title, color = C.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!on) IconBtn(Icons.Rounded.Close, size = 36.dp, tint = C.textFaint) { p.removeFromQueue(i) } else Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun InfoPage(p: Player, ui: PlayerUiState) {
    val ctx = LocalContext.current
    @Composable fun kv(k: String, v: String) {
        if (v.isEmpty() || v == "—") return
        Row(Modifier.fillMaxWidth()) {
            Text(k, color = C.textFaint, fontSize = 13.sp, modifier = Modifier.width(110.dp))
            Text(v, color = C.text, fontSize = 13.sp)
        }
    }
    Section("File", Icons.Rounded.Movie) {
        kv("Name", p.mediaTitle.ifEmpty { p.current?.title ?: "" })
        kv("Container", p.fileFormat)
        kv("Size", fmtBytes(p.fileSize))
        kv("Duration", fmtTime(p.duration))
        kv("Chapters", if (p.chapters.isEmpty()) "" else p.chapters.size.toString())
        kv("Source", p.current?.key?.let { if (it.startsWith("http")) it else p.current?.folder ?: "" } ?: "")
        p.current?.key?.takeIf { it.startsWith("http") }?.let { url ->
            PillButton("Copy link", icon = Icons.Rounded.ContentCopy, height = 34.dp) {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", url))
                ui.osd.show("Link copied", Icons.Rounded.ContentCopy)
            }
        }
    }
    Section("Video", Icons.Rounded.Tune) {
        kv("Codec", p.videoCodec)
        kv("Resolution", if (p.videoW > 0) "${p.videoW} × ${p.videoH}" else "")
        kv("Frame rate", if (p.fps > 0) String.format(Locale.US, "%.3f fps", p.fps) else "")
        kv("Bitrate", if (p.videoBitrate > 0) "${(p.videoBitrate / 1000).roundToInt()} kb/s" else "")
        kv("Pixel format", p.pixelFormat)
        kv("Colour", p.colorMatrix)
        kv("Decoder", if (p.hwdecCurrent.isNotEmpty() && p.hwdecCurrent != "no") "Hardware (${p.hwdecCurrent})" else "Software")
        kv("Dropped", p.dropped.toString())
    }
    Section("Audio", Icons.Rounded.VolumeUp) {
        kv("Codec", p.audioCodec)
        kv("Channels", if (p.channels > 0) p.channels.toString() else "")
        kv("Sample rate", if (p.sampleRate > 0) "${p.sampleRate} Hz" else "")
        kv("Bitrate", if (p.audioBitrate > 0) "${(p.audioBitrate / 1000).roundToInt()} kb/s" else "")
    }
}
