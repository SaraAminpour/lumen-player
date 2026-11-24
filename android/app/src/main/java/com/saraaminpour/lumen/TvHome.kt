package com.saraaminpour.lumen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saraaminpour.lumen.cast.Receiver
import com.saraaminpour.lumen.data.Folder
import com.saraaminpour.lumen.data.HistoryEntry
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.ui.C
import com.saraaminpour.lumen.ui.LocalAccent
import com.saraaminpour.lumen.ui.UpdateCard
import com.saraaminpour.lumen.ui.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The home screen on Android TV: a top bar, a wide "Play from your phone" panel, then rows of things to watch.
 * Everything is reachable with the D-pad and the focused item is always obvious.
 */
@Composable
fun TvHome(
    hasPermission: Boolean, refresh: Int,
    askPermission: () -> Unit, openFolder: (Folder) -> Unit, openLink: () -> Unit, openSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.lumen
    val first = remember { FocusRequester() }
    val cont = app.history.continueWatching()
    val folders by produceState<List<Folder>?>(null, refresh, hasPermission) {
        value = if (hasPermission) withContext(Dispatchers.IO) { Library.folders(ctx) } else emptyList()
    }
    // start on the first thing to watch; with nothing to watch yet, on "Open a link"
    LaunchedEffect(cont.isNotEmpty()) { kotlinx.coroutines.delay(80); runCatching { first.requestFocus() } }
    val side = 48.dp

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.lumen_logo), null, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text("Lumen", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = C.text)
            Spacer(Modifier.weight(1f))
            TvAction("Open a link", Icons.Rounded.Link, if (cont.isEmpty()) Modifier.focusRequester(first) else Modifier, wide = false, onClick = openLink)
            Spacer(Modifier.width(10.dp))
            TvAction("Settings", Icons.Rounded.Settings, wide = false, onClick = openSettings)
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Box(Modifier.padding(horizontal = side)) { PhonePanel() } }
            item { UpdateCard(app.updater, Modifier.padding(horizontal = side).width(430.dp)) }
            if (cont.isNotEmpty()) {
                item { TvHeading("Continue watching", side) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = side, vertical = 8.dp)) {
                        itemsIndexed(cont, key = { _, e -> e.key }) { i, e ->
                            TvContinueCard(e, if (i == 0) Modifier.focusRequester(first) else Modifier) { ctx.playUri(Uri.parse(e.key), e.title) }
                        }
                    }
                }
            }
            item { TvHeading("On this TV", side) }
            val list = folders
            when {
                !hasPermission -> item {
                    Box(Modifier.padding(horizontal = side)) {
                        TvAction("Show videos on this TV and on USB drives", Icons.Rounded.Usb, wide = false, onClick = askPermission)
                    }
                }
                list == null -> item { Text("Looking for videos…", color = C.textFaint, fontSize = 14.sp, modifier = Modifier.padding(horizontal = side)) }
                list.isEmpty() -> item {
                    Text(
                        "No videos are stored on this TV. Plug in a USB drive, or send one from your phone.",
                        color = C.textFaint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = side),
                    )
                }
                else -> item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = side, vertical = 8.dp)) {
                        items(list, key = { it.id }) { f -> TvFolderCard(f) { openFolder(f) } }
                    }
                }
            }
        }
    }
}

/** "Play from your phone": whether this TV is listening, under which name, and the three steps. */
@Composable
private fun PhonePanel() {
    val ctx = LocalContext.current
    val app = ctx.lumen
    val accent = LocalAccent.current
    val on = app.prefs.castReceive && Receiver.port > 0
    // the "open over other apps" permission can change while Lumen is in the background
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) overlay = Settings.canDrawOverlays(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val address = remember(Receiver.port) { localAddress() }
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.a.copy(alpha = 0.30f), C.glass, accent.b.copy(alpha = 0.16f))))
            .border(1.dp, accent.light.copy(alpha = 0.30f), shape).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(accent.brush), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = accent.onAccent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Play from your phone", color = C.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (on) "Send any video on your phone to this TV. It streams over Wi-Fi; nothing is copied."
                    else "Turn on \"Let phones play on this TV\" in Settings.",
                    color = C.textDim, fontSize = 14.sp,
                )
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.scrim).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (on) C.good else C.warn))
                Spacer(Modifier.width(8.dp))
                Text(if (on) "Ready" else "Off", color = if (on) C.good else C.warn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("Open Lumen on your phone", "Tap Play on TV", "Choose a video").forEachIndexed { i, t ->
                    if (i > 0) Icon(Icons.Rounded.ChevronRight, null, tint = C.textFaint, modifier = Modifier.padding(horizontal = 6.dp).size(20.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(C.card).border(1.dp, C.glassBorder, RoundedCornerShape(50))
                            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(accent.a.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(t, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CastConnected, null, tint = C.textFaint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildString {
                        append("This TV appears as "); append(Receiver.tvName(ctx))
                        if (address.isNotEmpty()) append("   ·   ").append(address) else append("   ·   not connected to a network")
                        if (!overlay) append("   ·   keep Lumen open, or turn on \"Start when Lumen is closed\" in Settings")
                    },
                    color = C.textFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** This TV's address on the home network (not a VPN's). */
fun localAddress(): String = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") && !it.name.startsWith("ppp") }
        .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: ""
} catch (_: Exception) { "" }

/** A wide button for the remote: it fills with the accent colour when focused. */
@Composable
fun TvAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, wide: Boolean = true, onClick: () -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.03f else 1f, spring(dampingRatio = 0.7f), label = "tv")
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.then(if (wide) Modifier.fillMaxWidth() else Modifier).scale(s).height(if (wide) 50.dp else 44.dp).clip(shape)
            .then(if (focused) Modifier.background(accent.brush) else Modifier.background(C.card).border(1.dp, C.glassBorder, shape))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (focused) accent.onAccent else accent.light, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = if (focused) accent.onAccent else C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvHeading(t: String, side: androidx.compose.ui.unit.Dp) = Text(
    t.uppercase(), color = C.textFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
    modifier = Modifier.padding(start = side, top = 8.dp),
)

@Composable
fun TvThumb(uri: Uri?, modifier: Modifier, web: Boolean = false) {
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = if (uri != null && uri.authority == "media") withContext(Dispatchers.IO) { Library.thumbnail(ctx, uri) } else null
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(C.glassSolid), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(if (web) Icons.Rounded.Language else Icons.Rounded.Movie, null, tint = accent.light.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun TvContinueCard(e: HistoryEntry, modifier: Modifier = Modifier, open: () -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f), label = "card")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.width(232.dp).scale(s).clip(shape).background(if (focused) C.glassSolid else C.card)
            .border(if (focused) 2.5.dp else 1.dp, if (focused) Color.White else C.glassBorder, shape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = open).padding(9.dp),
    ) {
        Box {
            TvThumb(if (e.web) null else Uri.parse(e.key), Modifier.fillMaxWidth().aspectRatio(16 / 9f), e.web)
            if (focused) Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(accent.brush), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = accent.onAccent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(e.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${fmtTime(e.dur - e.pos)} left", color = C.textFaint, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.hover)) {
            Box(Modifier.fillMaxWidth((e.pos / e.dur).toFloat().coerceIn(0f, 1f)).height(3.dp).background(accent.brush))
        }
    }
}

@Composable
private fun TvFolderCard(f: Folder, open: () -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f), label = "folder")
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(232.dp).scale(s).clip(shape).background(if (focused) C.glassSolid else C.card)
            .border(if (focused) 2.5.dp else 1.dp, if (focused) Color.White else C.glassBorder, shape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = open).padding(9.dp),
    ) {
        TvThumb(f.videos.first().uri, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, tint = accent.light.copy(alpha = if (focused) 1f else 0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(f.name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (f.videos.size == 1) "1 video" else "${f.videos.size} videos", color = C.textFaint, fontSize = 12.sp)
            }
        }
    }
}

/** Opens Android's "display over other apps" page for Lumen, which is what lets a phone start a video while Lumen is closed. */
fun openOverlaySetting(ctx: Context): Boolean {
    val pkg = Uri.parse("package:${ctx.packageName}")
    for (i in listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
    )) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) { }
    }
    return false
}
