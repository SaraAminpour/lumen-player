package com.saraaminpour.lumen.cast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.lumen
import com.saraaminpour.lumen.ui.Blobs
import com.saraaminpour.lumen.ui.C
import com.saraaminpour.lumen.ui.IconBtn
import com.saraaminpour.lumen.ui.LSlider
import com.saraaminpour.lumen.ui.LocalAccent
import com.saraaminpour.lumen.ui.LumenTheme
import com.saraaminpour.lumen.ui.PillButton
import com.saraaminpour.lumen.ui.extractLink
import com.saraaminpour.lumen.ui.fmtTime
import com.saraaminpour.lumen.ui.focusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** On the TV: "this phone wants to play here" — asked once per phone. Shown by whichever screen is in front. */
@Composable
fun CastPrompt() {
    val p = Receiver.pending ?: return
    val accent = LocalAccent.current
    val allow = remember { FocusRequester() }
    // the dialog's window takes a moment to get the focus, and then focuses its first button by itself
    LaunchedEffect(p.id) { repeat(4) { kotlinx.coroutines.delay(130); runCatching { allow.requestFocus() } } }
    Dialog(onDismissRequest = { Receiver.answer(false) }) {
        Column(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(26.dp)).background(C.glassSolid)
                .border(1.dp, accent.light.copy(alpha = 0.35f), RoundedCornerShape(26.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(accent.brush), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = accent.onAccent, modifier = Modifier.size(30.dp))
            }
            Text("Let ${p.name} play here?", color = C.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "This phone wants to play videos on this TV. Lumen asks only the first time.",
                color = C.textDim, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Not now", height = 46.dp) { Receiver.answer(false) }
                PillButton("Allow", Modifier.focusRequester(allow), Icons.Rounded.CastConnected, primary = true, height = 46.dp) { Receiver.answer(true) }
            }
        }
    }
}

/** What the player hands over when "Play on TV" is pressed while watching. */
class Handoff(val uris: List<Uri>, val links: List<Pair<String, String>>, val position: Double)

/** On the phone: pick videos, pick a TV, then a remote control for what is playing there. */
class CastActivity : ComponentActivity() {
    private val picked = mutableStateOf<List<Uri>>(emptyList())
    private val links = mutableStateOf<List<Pair<String, String>>>(emptyList())
    private val position = mutableStateOf(-1.0)
    private val openPicker = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        setContent {
            LumenTheme(lumen.prefs.accent) { CastScreen(picked, links, position, openPicker) { finish() } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    @Suppress("DEPRECATION")
    private fun read(i: Intent?) {
        handoff?.let { h ->
            handoff = null
            picked.value = h.uris; links.value = h.links; position.value = h.position
            return
        }
        when (i?.action) {
            Intent.ACTION_SEND -> {
                val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
                val link = i.getStringExtra(Intent.EXTRA_TEXT)?.let { extractLink(it) }
                if (stream != null) { picked.value = listOf(stream); links.value = emptyList(); position.value = -1.0 }
                else if (link != null) { links.value = listOf(link to link); picked.value = emptyList(); position.value = -1.0 }
                i.action = Intent.ACTION_MAIN
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list: List<Uri> = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
                if (list.isNotEmpty()) { picked.value = list; links.value = emptyList(); position.value = -1.0 }
                i.action = Intent.ACTION_MAIN
            }
            else -> if (i?.getBooleanExtra(EXTRA_PICK, false) == true) { openPicker.value = true; i.removeExtra(EXTRA_PICK) }
        }
    }

    companion object {
        const val EXTRA_PICK = "pick"
        var handoff: Handoff? = null
    }
}

@Composable
private fun CastScreen(
    picked: MutableState<List<Uri>>, links: MutableState<List<Pair<String, String>>>, position: MutableState<Double>,
    openPicker: MutableState<Boolean>, close: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.lumen
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val client = remember { CastSession.client(ctx) }
    val found = remember { mutableStateListOf<Tv>() }
    var saved by remember { mutableStateOf(TvStore.list(app.prefs)) }
    var addOpen by remember { mutableStateOf(false) }
    var autoTried by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val d = TvDiscovery(ctx, client) { tv -> if (found.none { it.id == tv.id }) found.add(tv) }
        d.start()
        onDispose { d.stop() }
    }

    val names = remember(picked.value, links.value) {
        picked.value.map { Library.title(ctx, it) } + links.value.map { it.second }
    }
    val hasMedia = picked.value.isNotEmpty() || links.value.isNotEmpty()

    fun keep(uris: List<Uri>) {
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        picked.value = uris; links.value = emptyList(); position.value = -1.0
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) keep(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) keep(uris)
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun gallery() = pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    fun send(tv: Tv) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        CastSession.send(ctx, tv, picked.value, links.value, position.value)
        saved = TvStore.list(app.prefs)
    }

    LaunchedEffect(openPicker.value) {
        if (openPicker.value) { openPicker.value = false; if (!CastSession.active) gallery() }
    }
    // videos chosen and a TV used before: send straight away, no second tap
    LaunchedEffect(picked.value, links.value) {
        if (!hasMedia || CastSession.active || autoTried) return@LaunchedEffect
        autoTried = true
        val last = saved.firstOrNull { it.id == app.prefs.castLastTv } ?: return@LaunchedEffect
        checking = true
        val tv = withContext(Dispatchers.IO) { client.info(last.host, last.port) }
        checking = false
        if (tv != null && tv.id == last.id && !CastSession.active) send(tv)
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Blobs()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, desc = "Back", onClick = close)
                Spacer(Modifier.width(6.dp))
                Text("Play on TV", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val phase = CastSession.phase
                if (phase != CastSession.Phase.Idle) {
                    Remote(
                        onPickMore = { autoTried = true; gallery() },
                        onChangeTv = { CastSession.stop(ctx); autoTried = true },
                    )
                    // new videos picked while something is playing go to the same TV
                    LaunchedEffect(picked.value) {
                        val tv = CastSession.tv
                        if (CastSession.phase == CastSession.Phase.Active && tv != null && picked.value.isNotEmpty() &&
                            names != CastSession.titles) send(tv)
                    }
                }
                if (!CastSession.active) {
                    // ---- 1: what to play ----
                    Card {
                        Step(1, "Choose what to play")
                        if (!hasMedia) {
                            PillButton("Choose videos", Modifier.fillMaxWidth(), Icons.Rounded.VideoLibrary, primary = true, height = 50.dp) { gallery() }
                            PillButton("Browse files", Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen, height = 44.dp) {
                                pickFiles.launch(arrayOf("video/*", "audio/*", "application/x-matroska"))
                            }
                        } else {
                            names.take(4).forEachIndexed { i, n ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PickedThumb(picked.value.getOrNull(i))
                                    Spacer(Modifier.width(12.dp))
                                    Text(n, color = C.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (names.size > 4) Text("and ${names.size - 4} more", color = C.textFaint, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("Choose others", icon = Icons.Rounded.VideoLibrary, height = 38.dp) { autoTried = true; gallery() }
                                PillButton("Files", icon = Icons.Rounded.FolderOpen, height = 38.dp) {
                                    autoTried = true; pickFiles.launch(arrayOf("video/*", "audio/*", "application/x-matroska"))
                                }
                            }
                        }
                    }
                    // ---- 2: where ----
                    Card {
                        Step(2, "Choose the TV")
                        val tvs = (found + saved.filter { s -> found.none { it.id == s.id } })
                        if (checking) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = accent.light, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Looking for the TV you used last…", color = C.textDim, fontSize = 13.sp)
                        }
                        tvs.forEach { tv ->
                            val here = found.any { it.id == tv.id }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                                    .border(1.dp, C.glassBorder, RoundedCornerShape(14.dp)).focusRing(RoundedCornerShape(14.dp))
                                    .clickable(enabled = hasMedia) { send(found.firstOrNull { it.id == tv.id } ?: tv) }
                                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Tv, null, tint = if (here) accent.light else C.textFaint)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tv.name, color = C.text.copy(alpha = if (hasMedia) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (here) "Ready  ·  ${tv.host}" else "Used before  ·  ${tv.host}", color = C.textFaint, fontSize = 11.sp)
                                }
                                if (!here) IconBtn(Icons.Rounded.Close, size = 38.dp, tint = C.textFaint, desc = "Forget this TV") {
                                    TvStore.remove(app.prefs, tv); saved = TvStore.list(app.prefs)
                                } else Spacer(Modifier.height(38.dp))
                            }
                        }
                        if (tvs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = accent.light, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Looking for TVs…", color = C.textDim, fontSize = 13.sp)
                        }
                        Text(
                            "Open Lumen on the TV once. The phone and the TV must be on the same Wi-Fi.",
                            color = C.textFaint, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                        PillButton("Add a TV by its address", icon = Icons.Rounded.Add, height = 38.dp) { addOpen = true }
                    }
                }
            }
        }
        if (addOpen) AddTvDialog({ addOpen = false }) { host ->
            scope.launch {
                val parts = host.trim().removePrefix("http://").trimEnd('/').split(':')
                val tv = withContext(Dispatchers.IO) { client.info(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: Receiver.PORT) }
                if (tv != null) {
                    if (found.none { it.id == tv.id }) found.add(tv)
                    addOpen = false
                    if (hasMedia) send(tv)
                }
            }
        }
    }
}

/** A small preview of a picked video (the system makes it; a film icon stands in for links and odd files). */
@Composable
private fun PickedThumb(uri: Uri?) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val bmp by produceState<android.graphics.Bitmap?>(null, uri) {
        value = if (uri == null || Build.VERSION.SDK_INT < 29) null else withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, android.util.Size(320, 180), null) }.getOrNull()
        }
    }
    Box(Modifier.width(76.dp).height(43.dp).clip(RoundedCornerShape(10.dp)).background(C.glassSolid), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Movie, null, tint = accent.light.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card)
            .border(1.dp, C.glassBorder, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Step(n: Int, title: String) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(accent.a.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Text("$n", color = accent.light, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** The phone as a remote control for what it sent to the TV. */
@Composable
private fun Remote(onPickMore: () -> Unit, onChangeTv: () -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val s = CastSession.status
    val phase = CastSession.phase
    val tv = CastSession.tv
    var drag by remember { mutableFloatStateOf(Float.NaN) }
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.glass)
            .border(1.dp, accent.light.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.brush), contentAlignment = Alignment.Center) {
                Icon(if (phase == CastSession.Phase.Active) Icons.Rounded.CastConnected else Icons.Rounded.Cast, null, tint = accent.onAccent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (phase) {
                        CastSession.Phase.Failed -> "Couldn't play on ${tv?.name ?: "the TV"}"
                        CastSession.Phase.Approval -> "Waiting for the TV"
                        CastSession.Phase.Connecting -> "Connecting"
                        else -> "Playing on ${tv?.name ?: "TV"}"
                    },
                    color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val title = s.title.ifEmpty { CastSession.titles.firstOrNull() ?: "" }
                if (title.isNotEmpty()) Text(title, color = C.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val note = when {
            CastSession.message.isNotEmpty() -> CastSession.message
            CastSession.lost -> "The TV isn't answering right now. If it keeps playing, all is well."
            phase == CastSession.Phase.Active && s.state == "loading" -> "Starting on the TV…"
            phase == CastSession.Phase.Active && s.buffering -> "The TV is buffering…"
            else -> ""
        }
        if (note.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            if (phase == CastSession.Phase.Connecting || phase == CastSession.Phase.Approval) {
                CircularProgressIndicator(color = accent.light, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(note, color = if (phase == CastSession.Phase.Failed) C.danger else C.textDim, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (phase == CastSession.Phase.Active) {
            val dur = s.duration.coerceAtLeast(1.0).toFloat()
            LSlider(
                if (drag.isNaN()) s.position.toFloat().coerceIn(0f, dur) else drag, 0f..dur, { drag = it },
                onDone = { if (!drag.isNaN()) CastSession.control(ctx, "seek", drag.toDouble()); drag = Float.NaN },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtTime(if (drag.isNaN()) s.position else drag.toDouble()), color = C.textDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (s.count > 1) Text("${s.index + 1} of ${s.count}", color = C.textFaint, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(s.duration), color = C.textDim, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn(Icons.Rounded.SkipPrevious, size = 48.dp, desc = "Previous") { CastSession.control(ctx, "prev") }
                IconBtn(Icons.Rounded.Replay10, size = 54.dp, desc = "Back 10 seconds") { CastSession.control(ctx, "seekBy", -10.0) }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(accent.brush).focusRing(CircleShape)
                        .clickable { CastSession.control(ctx, "toggle") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.playing) "Pause" else "Play",
                        tint = accent.onAccent, modifier = Modifier.size(40.dp))
                }
                IconBtn(Icons.Rounded.Forward10, size = 54.dp, desc = "Forward 10 seconds") { CastSession.control(ctx, "seekBy", 10.0) }
                IconBtn(Icons.Rounded.SkipNext, size = 48.dp, desc = "Next", enabled = s.index + 1 < s.count) { CastSession.control(ctx, "next") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (CastSession.active) {
                PillButton("Stop", icon = Icons.Rounded.Stop, height = 40.dp) { CastSession.stop(ctx) }
                if (phase == CastSession.Phase.Active) PillButton("Play something else", icon = Icons.Rounded.VideoLibrary, height = 40.dp, onClick = onPickMore)
            } else {
                PillButton("OK", primary = true, height = 40.dp) { CastSession.dismiss() }
            }
        }
        if (phase == CastSession.Phase.Active) {
            Text(
                "Keep Lumen running on this phone while the TV plays. The screen can be off.",
                color = C.textFaint, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
            Text("Use another TV", color = accent.light, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onChangeTv).padding(6.dp))
        }
    }
}

@Composable
private fun AddTvDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val accent = LocalAccent.current
    var text by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.glassSolid)
                .border(1.dp, C.glassBorder, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tv, null, tint = accent.light)
                Spacer(Modifier.width(10.dp))
                Text("Add a TV", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                text, { text = it; tried = false }, Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("192.168.1.20", color = C.textFaint) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { tried = true; onAdd(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.light, unfocusedBorderColor = C.glassBorder,
                    focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = accent.light,
                ),
            )
            Text(
                if (tried) "Looking for Lumen at that address… If nothing happens, check the address and that Lumen is open on the TV."
                else "Lumen on the TV shows its address on the home screen, under Play from your phone.",
                color = C.textFaint, fontSize = 12.sp, lineHeight = 17.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Cancel") { onDismiss() }
                Spacer(Modifier.width(8.dp))
                PillButton("Add", icon = Icons.Rounded.Add, primary = true) { tried = true; onAdd(text) }
            }
        }
    }
}
