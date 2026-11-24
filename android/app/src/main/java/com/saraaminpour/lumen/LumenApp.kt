package com.saraaminpour.lumen

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.saraaminpour.lumen.cast.Receiver
import com.saraaminpour.lumen.cast.ReceiverService
import com.saraaminpour.lumen.data.History
import com.saraaminpour.lumen.data.Library
import com.saraaminpour.lumen.data.Prefs
import com.saraaminpour.lumen.net.Updater
import com.saraaminpour.lumen.player.QueueItem

class LumenApp : Application() {
    lateinit var prefs: Prefs
    lateinit var history: History
    lateinit var updater: Updater

    /** Android TV (or another device driven by a remote instead of a touch screen). */
    val isTv by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    /** A Lumen screen is visible. Android only lets a visible app open another of its screens. */
    var inFront = false; private set
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        history = History(this)
        updater = Updater(this, prefs)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                started++; inFront = true
                // a phone asked to play while Lumen was closed: now that it is open, play
                if (a is MainActivity) Receiver.launchWaiting()
            }
            override fun onActivityStopped(a: Activity) { started = maxOf(0, started - 1); inFront = started > 0 }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        ReceiverService.sync(this)
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
