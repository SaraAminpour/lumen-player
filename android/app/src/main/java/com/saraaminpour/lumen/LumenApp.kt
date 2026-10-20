package com.saraaminpour.lumen

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.saraaminpour.lumen.data.History
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.Updater
import com.saraaminpour.lumen.player.QueueItem

class LumenApp : Application() {
    lateinit var prefs: Prefs
    lateinit var history: History
    lateinit var updater: Updater

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        history = History(this)
        updater = Updater(this, prefs)
    }
}

val Context.lumen get() = applicationContext as LumenApp

/** Starts the player on a queue. */
fun Context.playQueue(items: List<QueueItem>, start: Int = 0) {
    if (items.isEmpty()) return
    PlayerActivity.pendingQueue = items to start
    startActivity(Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
}

/** Opens one file (and, if allowed, the rest of its folder after it) or a link. */
fun Context.playUri(uri: Uri, title: String? = null) {
    val key = uri.toString()
    if (uri.scheme?.startsWith("http") == true || uri.scheme in setOf("rtmp", "rtsp", "udp", "mms", "ftp")) {
        playQueue(listOf(QueueItem(key, title ?: key)))
        return
    }
    val name = title ?: Library.displayName(this, uri) ?: "Video"
    if (lumen.prefs.autoQueue) {
        val sib = Library.siblings(this, uri)
        val i = sib.indexOfFirst { it.uri == uri || it.name == name }
        if (sib.size > 1 && i >= 0) {
            playQueue(sib.map { QueueItem(it.uri.toString(), it.name, it.folder) }, i)
            return
        }
    }
    playQueue(listOf(QueueItem(key, name)))
}
