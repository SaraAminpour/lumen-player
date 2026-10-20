package com.saraaminpour.lumen.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.saraaminpour.lumen.BuildConfig
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.Updater
import kotlin.math.cos
import kotlin.math.sin

/** Two slowly drifting colour blobs behind the home screen (Blob.qml). */
@Composable
fun Blobs(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val t = if (com.saraaminpour.lumen.player.ShotHooks.still) 0.8f else rememberInfiniteTransition(label = "blobs").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "p",
    ).value
    Canvas(modifier.fillMaxSize()) {
        val r1 = size.minDimension * 0.75f
        val c1 = Offset(size.width * 0.12f + 60 * sin(t), size.height * 0.05f + 40 * cos(t))
        drawCircle(Brush.radialGradient(listOf(accent.a.copy(alpha = 0.45f), Color.Transparent), c1, r1), r1, c1)
        val r2 = size.minDimension * 0.7f
        val c2 = Offset(size.width * 0.9f + 50 * cos(t * 1.3f), size.height * 0.95f + 40 * sin(t))
        drawCircle(Brush.radialGradient(listOf(accent.b.copy(alpha = 0.32f), Color.Transparent), c2, r2), r2, c2)
    }
}

/** "Lumen x.y is ready" card with what's new, Install / Later (UpdateCard.qml). */
@Composable
fun UpdateCard(updater: Updater, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var dismissed by remember { mutableStateOf(false) }
    val st = updater.state
    val shown = !dismissed && (st == Updater.State.Available || st == Updater.State.Downloading ||
        st == Updater.State.Ready || (st == Updater.State.Error && updater.latest.isNotEmpty() && updater.notes.isNotEmpty()))
    AnimatedVisibility(shown, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.glass)
                .border(1.dp, C.glassBorder, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accent.brush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CloudDownload, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (st == Updater.State.Error) "Update didn't finish" else "Lumen ${updater.latest} is ready",
                        color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (st == Updater.State.Available) "You have ${updater.current}" else updater.message,
                        color = if (st == Updater.State.Error) C.danger else C.textDim, fontSize = 12.sp,
                    )
                }
            }
            if (st == Updater.State.Available && updater.notes.isNotEmpty())
                Text(updater.notes, color = C.textDim, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 6)
            if (st == Updater.State.Downloading) ProgressLine(updater.progress)
            if (st != Updater.State.Downloading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Later", height = 36.dp) { dismissed = true }
                Spacer(Modifier.width(8.dp))
                PillButton(
                    when (st) { Updater.State.Ready -> "Install"; Updater.State.Error -> "Try again"; else -> "Download & install" },
                    icon = Icons.Rounded.CloudDownload, primary = true, height = 36.dp,
                ) { if (st == Updater.State.Ready) updater.install() else updater.download() }
            }
        }
    }
}

private val LINK = Regex("^(https?|rtmp|rtsp|udp|mms|ftp)://\\S+$", RegexOption.IGNORE_CASE)

fun clipboardLink(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: ""
    return if (LINK.matches(t)) t else ""
}

/** Pulls the first link out of shared text ("Check this out https://youtu.be/…"). */
fun extractLink(text: String): String? = Regex("(https?|rtmp|rtsp)://\\S+", RegexOption.IGNORE_CASE).find(text)?.value

/** Open a link: YouTube, PeerTube, SoundCloud, direct files, HLS/DASH streams. */
@Composable
fun LinkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    var text by remember { mutableStateOf(clipboardLink(ctx)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun go() { extractLink(text.trim())?.let { onOpen(it) } ?: run { if (text.isNotBlank()) onOpen(text.trim()) } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.glassSolid)
                .border(1.dp, C.glassBorder, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null, tint = accent.light)
                Spacer(Modifier.width(10.dp))
                Text("Open a link", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Paste a YouTube or video link", color = C.textFaint) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.light, unfocusedBorderColor = C.glassBorder,
                    focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = accent.light,
                ),
            )
            Text("YouTube, SoundCloud, PeerTube, direct video files and HLS/DASH streams.", color = C.textFaint, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Cancel") { onDismiss() }
                Spacer(Modifier.width(8.dp))
                PillButton("Play", icon = Icons.Rounded.PlayCircleOutline, primary = true) { go() }
            }
        }
    }
}

fun qualityName(h: Int) = when (h) { 0 -> "Best"; 2160 -> "4K"; else -> "${h}p" }

/** Settings page, shared by the home screen and the player's Settings panel (SettingsPage.qml). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(prefs: Prefs, updater: Updater, onPlayerPrefs: () -> Unit = {}) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Look", Icons.Rounded.Palette) {
            Label("Accent  ·  ${accent.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEachIndexed { i, a ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .border(2.dp, if (prefs.accent == i) Color.White else Color.Transparent, CircleShape)
                            .clickable { prefs.accent = i }.padding(5.dp).clip(CircleShape).background(a.brush),
                    )
                }
            }
            LSwitch("Seek-bar previews", prefs.thumbnails, "Thumbnail of the frame under your finger") { prefs.thumbnails = it }
            LSwitch("Show remaining time", prefs.showRemaining) { prefs.showRemaining = it }
        }
        Section("Playback", Icons.Rounded.PlayCircleOutline) {
            LSwitch("Resume where I left off", prefs.resume, "Remembers position, tracks and delays per file") { prefs.resume = it }
            LSwitch("Queue the rest of the folder", prefs.autoQueue, "Opening an episode adds the next ones automatically") { prefs.autoQueue = it }
            LSwitch("Swipe gestures", prefs.gestures, "Left side: brightness · right side: volume · sideways: seek") { prefs.gestures = it }
            LSwitch("Rotate to fit the video", prefs.autoRotate, "Landscape for wide videos, portrait for tall ones") { prefs.autoRotate = it }
            LSwitch("Keep playing in the background", prefs.backgroundAudio, "Audio continues when you leave the app") { prefs.backgroundAudio = it }
            LSwitch("Hardware decoding", prefs.hwdec, "Uses the phone's video decoder. Turn off if a file shows glitches") {
                prefs.hwdec = it; onPlayerPrefs()
            }
            Label("Streaming quality  ·  ${qualityName(prefs.streamQuality)}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(480, 720, 1080, 1440, 2160, 0).forEach { h -> Chip(qualityName(h), prefs.streamQuality == h) { prefs.streamQuality = h } }
            }
            Label("For YouTube and other web videos. Lower it on mobile data.", C.textFaint)
        }
        Section("Updates", Icons.Rounded.Update) {
            Text(
                when (updater.state) {
                    Updater.State.Idle -> "You have Lumen ${updater.current}."
                    Updater.State.Available -> "Lumen ${updater.latest} is available. You have ${updater.current}."
                    else -> updater.message
                },
                color = when (updater.state) { Updater.State.Error -> C.danger; Updater.State.Available -> C.text; else -> C.textDim },
                fontSize = 13.sp,
            )
            if (updater.state == Updater.State.Available && updater.notes.isNotEmpty())
                Text(updater.notes, color = C.textDim, fontSize = 12.sp, lineHeight = 17.sp)
            if (updater.state == Updater.State.Downloading) ProgressLine(updater.progress)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updater.state == Updater.State.Available || updater.state == Updater.State.Ready)
                    PillButton(if (updater.state == Updater.State.Ready) "Install ${updater.latest}" else "Download ${updater.latest}",
                        icon = Icons.Rounded.CloudDownload, primary = true, height = 36.dp) {
                        if (updater.state == Updater.State.Ready) updater.install() else updater.download()
                    }
                if (updater.state != Updater.State.Downloading && updater.state != Updater.State.Checking)
                    PillButton("Check for updates", icon = Icons.Rounded.Refresh, height = 36.dp) { updater.check(false) }
                PillButton("Release notes", icon = Icons.Rounded.OpenInNew, height = 36.dp) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASES)))
                }
            }
            LSwitch("Check for updates automatically", prefs.autoUpdate, "Once a day. Nothing installs without your OK") { prefs.autoUpdate = it }
        }
        Section("About", Icons.Rounded.Tune) {
            Text("Lumen ${BuildConfig.VERSION_NAME} for Android  ·  powered by mpv and FFmpeg", color = C.textFaint, fontSize = 12.sp)
            Text("github.com/SaraAminpour/lumen-player", color = accent.light, fontSize = 12.sp,
                modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SaraAminpour/lumen-player"))) })
        }
        Spacer(Modifier.height(8.dp))
    }
}
