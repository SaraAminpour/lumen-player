package com.saraaminpour.lumen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.saraaminpour.lumen.data.Folder
import com.saraaminpour.lumen.data.HistoryEntry
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.data.naturalOrder
import com.saraaminpour.lumen.player.QueueItem
import com.saraaminpour.lumen.ui.Blobs
import com.saraaminpour.lumen.ui.C
import com.saraaminpour.lumen.ui.IconBtn
import com.saraaminpour.lumen.ui.LinkDialog
import com.saraaminpour.lumen.ui.LocalAccent
import com.saraaminpour.lumen.ui.LumenTheme
import com.saraaminpour.lumen.ui.PillButton
import com.saraaminpour.lumen.ui.SettingsContent
import com.saraaminpour.lumen.ui.UpdateCard
import com.saraaminpour.lumen.ui.extractLink
import com.saraaminpour.lumen.ui.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val app = lumen
            LumenTheme(app.prefs.accent) { Home() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Share → Lumen" from YouTube, a browser or any app that shares a link. */
    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            extractLink(text)?.let { playUri(Uri.parse(it)) }
            i.action = Intent.ACTION_MAIN
        }
    }
}

private enum class Screen { Home, Folder, Settings }

private val MEDIA_TYPES = arrayOf("video/*", "audio/*", "application/x-matroska", "application/octet-stream")

@Composable
private fun Home() {
    val ctx = LocalContext.current
    val app = ctx.lumen
    val accent = LocalAccent.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var folder by remember { mutableStateOf<Folder?>(null) }
    var linkOpen by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(hasVideoPermission(ctx)) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1500)
        if (app.prefs.autoUpdate) app.updater.check(true)
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        if (uris.size == 1) ctx.playUri(uris[0])
        else ctx.playQueue(uris.map { QueueItem(it.toString(), Library.displayName(ctx, it) ?: "Video") })
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val dir = DocumentFile.fromTreeUri(ctx, tree) ?: return@rememberLauncherForActivityResult
        val media = dir.listFiles().filter { f ->
            f.isFile && (f.type?.startsWith("video") == true || f.type?.startsWith("audio") == true ||
                f.name?.substringAfterLast('.', "")?.lowercase() in VIDEO_EXT)
        }.sortedWith(compareBy(naturalOrder) { it.name ?: "" })
        ctx.playQueue(media.map { QueueItem(it.uri.toString(), it.name ?: "Video", dir.name ?: "") })
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = hasVideoPermission(ctx); refresh++
    }

    BackHandler(screen != Screen.Home) { screen = Screen.Home }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Blobs()
        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.Settings -> SubPage("Settings", { screen = Screen.Home }) {
                    item { SettingsContent(app.prefs, app.updater) }
                }
                Screen.Folder -> folder?.let { f ->
                    SubPage(f.name, { screen = Screen.Home }) {
                        itemsIndexed(f.videos) { i, v ->
                            VideoRow(v.uri, v.name, v.durationMs / 1000.0, app.history.get(v.uri.toString())) {
                                ctx.playQueue(f.videos.map { QueueItem(it.uri.toString(), it.name, it.folder) }, i)
                            }
                        }
                    }
                }
                Screen.Home -> LazyColumn(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconBtn(Icons.Rounded.Settings, desc = "Settings") { screen = Screen.Settings }
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.lumen_logo), null, Modifier.size(88.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Lumen", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = C.text)
                            Text("Open a video, a folder or a link.", color = C.textDim, fontSize = 14.sp)
                        }
                    }
                    item {
                        Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Open file", Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen, primary = true, height = 48.dp) {
                                pickFiles.launch(MEDIA_TYPES)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton("Open folder", Modifier.weight(1f), Icons.Rounded.Folder, height = 46.dp) { pickFolder.launch(null) }
                                PillButton("Open link", Modifier.weight(1f), Icons.Rounded.Link, height = 46.dp) { linkOpen = true }
                            }
                        }
                    }
                    item { UpdateCard(app.updater, Modifier.widthIn(max = 520.dp)) }
                    val cont = app.history.continueWatching()
                    if (cont.isNotEmpty()) {
                        item { Heading("Continue watching") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                items(cont, key = { it.key }) { e -> ContinueCard(e, { app.history.remove(e.key) }) { ctx.playUri(Uri.parse(e.key), e.title) } }
                            }
                        }
                    }
                    item { Heading("Your videos") }
                    if (!permission) item {
                        Column(
                            Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)
                                .border(1.dp, C.glassBorder, RoundedCornerShape(18.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("See the videos on this phone, sorted by folder, and let Lumen queue the next episode.", color = C.textDim, fontSize = 13.sp)
                            PillButton("Show my videos", icon = Icons.Rounded.VideoLibrary, primary = true) {
                                askPermission.launch(videoPermissions())
                            }
                        }
                    } else item {
                        val folders by produceState<List<Folder>?>(null, refresh) { value = withContext(Dispatchers.IO) { Library.folders(ctx) } }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 720.dp)) {
                            when {
                                folders == null -> Text("Looking for videos…", color = C.textFaint, fontSize = 13.sp)
                                folders!!.isEmpty() -> Text("No videos found on this phone yet.", color = C.textFaint, fontSize = 13.sp)
                                else -> folders!!.forEach { f ->
                                    FolderRow(f) { folder = f; screen = Screen.Folder }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        if (linkOpen) LinkDialog({ linkOpen = false }) { linkOpen = false; ctx.playUri(Uri.parse(it)) }
    }
}

val VIDEO_EXT = setOf(
    "mkv", "mp4", "m4v", "avi", "mov", "webm", "wmv", "flv", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "3gp",
    "vob", "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "mka",
)

private fun videoPermissions() =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasVideoPermission(ctx: android.content.Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)
}

@Composable
private fun Heading(t: String) = Text(
    t.uppercase(), color = C.textFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
)

@Composable
private fun SubPage(title: String, back: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.ArrowBack, desc = "Back", onClick = back)
            Spacer(Modifier.width(6.dp))
            Text(title, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
        )
    }
}

@Composable
private fun Thumb(uri: Uri?, modifier: Modifier, web: Boolean = false) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = if (uri != null && uri.authority == "media") withContext(Dispatchers.IO) { Library.thumbnail(ctx, uri) } else null
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(C.glassSolid), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(if (web) Icons.Rounded.Language else Icons.Rounded.Movie, null, tint = accent.light.copy(alpha = 0.7f), modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ContinueCard(e: HistoryEntry, remove: () -> Unit, open: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.width(220.dp).clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.glassBorder, RoundedCornerShape(16.dp)).clickable(onClick = open).padding(8.dp),
    ) {
        Box {
            Thumb(if (e.web) null else Uri.parse(e.key), Modifier.fillMaxWidth().aspectRatio(16 / 9f), e.web)
            Box(Modifier.align(Alignment.Center).size(38.dp).clip(RoundedCornerShape(50)).background(accent.brush), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = accent.onAccent)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(RoundedCornerShape(50)).background(C.scrim)
                .clickable(onClick = remove), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Remove", tint = C.text, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(e.title, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${fmtTime(e.dur - e.pos)} left", color = C.textFaint, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.hover)) {
            Box(Modifier.fillMaxWidth((e.pos / e.dur).toFloat().coerceIn(0f, 1f)).height(3.dp).background(accent.brush))
        }
    }
}

@Composable
private fun FolderRow(f: Folder, open: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, C.glassBorder, RoundedCornerShape(16.dp)).clickable(onClick = open).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(f.videos.first().uri, Modifier.width(96.dp).aspectRatio(16 / 9f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (f.videos.size == 1) "1 video" else "${f.videos.size} videos", color = C.textFaint, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.Folder, null, tint = accent.light.copy(alpha = 0.6f))
    }
}

@Composable
private fun VideoRow(uri: Uri, name: String, dur: Double, h: HistoryEntry?, open: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).clickable(onClick = open).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Thumb(uri, Modifier.width(128.dp).aspectRatio(16 / 9f))
            if (h != null && h.dur > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(C.hover)) {
                Box(Modifier.fillMaxWidth((h.pos / h.dur).toFloat().coerceIn(0f, 1f)).height(3.dp).background(accent.brush))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    h != null && h.finished -> "Watched"
                    h != null && h.pos > 5 -> "${fmtTime(h.dur - h.pos)} left"
                    else -> fmtTime(dur)
                },
                color = C.textFaint, fontSize = 12.sp,
            )
        }
    }
}
