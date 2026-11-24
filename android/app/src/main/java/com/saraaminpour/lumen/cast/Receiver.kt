package com.saraaminpour.lumen.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saraaminpour.lumen.BuildConfig
import com.saraaminpour.lumen.LumenApp
import com.saraaminpour.lumen.MainActivity
import com.saraaminpour.lumen.PlayerActivity
import com.saraaminpour.lumen.R
import com.saraaminpour.lumen.player.QueueItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A phone waiting for "Allow" on the TV screen. */
data class PendingClient(val id: String, val name: String, val keyHash: String, val address: String)

/**
 * The receiving end of "Play on TV": a small HTTP API that a phone running Lumen talks to.
 *
 *   GET  /lumen/info      who am I (no auth; used to find and name TVs)
 *   POST /lumen/hello     am I allowed? (a new phone triggers the Allow prompt on the TV)
 *   POST /lumen/play      {items:[{url,title,id}], index, position}
 *   GET  /lumen/status    what is playing, where
 *   POST /lumen/control   {cmd: toggle|play|pause|seek|seekBy|next|prev|stop, value}
 *
 * Only http(s) links are accepted, and every call except /info needs the phone's id and key.
 */
object Receiver {
    const val PORT = 47800
    const val SERVICE_TYPE = "_lumen._tcp"
    private const val TAG = "LumenCast"

    private lateinit var app: LumenApp
    private val main = Handler(Looper.getMainLooper())
    private val server = HttpServer("lumen-recv", ::handle)

    var port by mutableIntStateOf(0); private set
    /** Shown as a prompt by whichever Lumen screen is in front. */
    var pending by mutableStateOf<PendingClient?>(null); private set
    /** Name of the phone that started the current playback (for the TV home screen). */
    var lastSender by mutableStateOf(""); private set
    @Volatile private var sessionClient = ""
    private val denied = HashMap<String, Long>()
    /** A play request that arrived while Lumen could not come to the front; played when it does. */
    @Volatile private var waiting: Waiting? = null
    private class Waiting(val items: List<QueueItem>, val index: Int, val at: Long)

    val running get() = server.running

    fun start(a: LumenApp): Int {
        app = a
        if (a.prefs.castTvId.isEmpty()) a.prefs.castTvId = UUID.randomUUID().toString().take(13)
        port = server.start(PORT)
        return port
    }

    fun stop() { server.stop(); port = 0 }

    fun tvName(ctx: Context): String {
        val custom = (ctx.applicationContext as LumenApp).prefs.castName.trim()
        return custom.ifEmpty { deviceName(ctx) }
    }

    // ---- allow list ----
    private fun allowed(): JSONObject = try { JSONObject(app.prefs.castAllowed) } catch (_: Exception) { JSONObject() }
    val allowedCount get() = allowed().length()
    fun forgetPhones() { app.prefs.castAllowed = "{}" }

    private fun remember(c: PendingClient) {
        val all = allowed()
        all.put(c.id, JSONObject().put("name", c.name).put("hash", c.keyHash).put("time", System.currentTimeMillis()))
        app.prefs.castAllowed = all.toString()
    }

    fun answer(allow: Boolean) {
        val p = pending ?: return
        if (allow) remember(p) else synchronized(denied) { denied[p.id] = System.currentTimeMillis() }
        pending = null
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** null = allowed; otherwise the response to send back. */
    private fun authorize(req: HttpRequest): HttpResponse? {
        val id = req.header("x-lumen-id").take(64)
        val key = req.header("x-lumen-key")
        val name = runCatching { URLDecoder.decode(req.header("x-lumen-name"), "UTF-8") }.getOrDefault("").take(60).ifEmpty { "A phone" }
        if (id.isEmpty() || key.length < 16) return err(401, "identity", "This app is too old to play on this TV.")
        val hash = sha(key)
        val known = allowed().optJSONObject(id)
        if (known != null && known.optString("hash") == hash) return null
        val client = PendingClient(id, name, hash, req.remote.hostAddress ?: "")
        if (!onMain { app.prefs.castAsk }) { onMain { remember(client) }; return null }
        val deniedAt = synchronized(denied) { denied[id] } ?: 0L
        if (System.currentTimeMillis() - deniedAt < 20_000) return err(403, "denied", "The TV said no.")
        onMain {
            if (pending?.id != id) pending = client
            if (!app.inFront) bringToFront(Intent(app, MainActivity::class.java))
        }
        return err(401, "approval", "Allow this phone on the TV screen.")
    }

    private fun err(status: Int, code: String, message: String) =
        HttpResponse.json(status, JSONObject().put("ok", false).put("error", code).put("message", message).toString())

    // ---- requests ----
    private fun handle(req: HttpRequest): HttpResponse {
        if (!req.path.startsWith("/lumen/")) return HttpResponse.text(404, "not found")
        if (req.path == "/lumen/info") return HttpResponse.json(200, info().toString())
        authorize(req)?.let { return it }
        val clientId = req.header("x-lumen-id")
        return when (req.path) {
            "/lumen/hello" -> HttpResponse.json(200, info().put("ok", true).toString())
            "/lumen/status" -> HttpResponse.json(200, status(clientId, req.query["debug"] == "1").toString())
            "/lumen/play" -> play(req, clientId)
            "/lumen/control" -> control(req)
            else -> HttpResponse.text(404, "not found")
        }
    }

    private fun info() = JSONObject().put("app", "lumen").put("proto", 1).put("id", app.prefs.castTvId)
        .put("name", tvName(app)).put("version", BuildConfig.VERSION_NAME).put("tv", app.isTv)

    private fun play(req: HttpRequest, clientId: String): HttpResponse {
        val o = try { JSONObject(req.text) } catch (_: Exception) { return err(400, "json", "Bad request") }
        val arr = o.optJSONArray("items") ?: JSONArray()
        val from = runCatching { URLDecoder.decode(req.header("x-lumen-name"), "UTF-8") }.getOrDefault("").ifEmpty { "Phone" }
        val position = o.optDouble("position", -1.0)
        val index = o.optInt("index", 0)
        val items = (0 until arr.length()).mapNotNull { i ->
            val it = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = it.optString("url")
            // http(s) only: nothing a phone sends may reach files or other protocols on the TV
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
            val id = it.optString("id").filter { c -> c.isLetterOrDigit() }.take(40)
            QueueItem(
                url, it.optString("title").ifEmpty { "Video" }.take(200), "From $from",
                id = if (id.isEmpty()) "" else "cast:$id", startAt = if (i == index) position else -1.0, from = from,
            )
        }
        if (items.isEmpty()) return err(400, "empty", "Nothing to play")
        val start = index.coerceIn(0, items.lastIndex)
        val started = onMain {
            sessionClient = clientId; lastSender = from
            waiting = Waiting(items, start, System.currentTimeMillis())
            launchWaiting()
        }
        if (!started) {
            // Android only lets an app open itself from the background with the "display over other apps" permission
            return err(409, "closed", "Open Lumen on the TV, then it starts playing.")
        }
        return HttpResponse.json(200, JSONObject().put("ok", true).put("count", items.size).toString())
    }

    /** Hands a waiting request to the player. Main thread. Returns false when Lumen may not come to the front. */
    fun launchWaiting(): Boolean {
        val w = waiting ?: return false
        if (System.currentTimeMillis() - w.at > 120_000) { waiting = null; return false }
        if (!app.inFront && !Settings.canDrawOverlays(app)) return false
        waiting = null
        wakeScreen()
        PlayerActivity.pendingQueue = w.items to w.index
        bringToFront(Intent(app, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_CAST, true))
        return true
    }

    private fun bringToFront(i: Intent) {
        try { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        catch (e: Exception) { Log.w(TAG, "could not open Lumen", e) }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() = runCatching {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "lumen:cast",
        ).acquire(5_000)
    }

    private fun status(clientId: String, debug: Boolean = false): JSONObject = onMain {
        val a = PlayerActivity.current
        val p = a?.player
        val o = JSONObject()
        if (p == null || p.queue.isEmpty()) return@onMain o.put("state", if (waiting != null) "loading" else "idle")
        val cur = p.current
        o.put("state", when {
            p.error.isNotEmpty() && p.idle -> "error"
            p.loading -> "loading"
            p.idle -> "idle"
            p.eof && p.index == p.queue.lastIndex -> "ended"
            p.paused -> "paused"
            else -> "playing"
        })
        o.put("buffering", p.buffering)
        o.put("title", p.mediaTitle.ifEmpty { cur?.title ?: "" })
        o.put("id", cur?.id?.removePrefix("cast:") ?: "")
        o.put("position", p.position); o.put("duration", p.duration)
        o.put("index", p.index); o.put("count", p.queue.size)
        o.put("error", p.error)
        o.put("mine", sessionClient == clientId && cur?.from?.isNotEmpty() == true)
        if (debug) o.put("debug", JSONObject().put("video", "${p.videoCodec} ${p.videoW}x${p.videoH}").put("fps", p.fps)
            .put("hwdec", p.hwdecCurrent).put("voDrops", p.dropped).put("cache", p.cacheTime - p.position).put("audio", p.audioCodec)
            .put("decDrops", p.mpv?.getPropertyString("decoder-frame-drop-count") ?: "")
            .put("avsync", p.mpv?.getPropertyString("avsync") ?: "").put("vo", p.mpv?.getPropertyString("current-vo") ?: "")
            .put("speed", p.mpv?.getPropertyString("cache-speed") ?: ""))
        o
    }

    private fun control(req: HttpRequest): HttpResponse {
        val o = try { JSONObject(req.text) } catch (_: Exception) { return err(400, "json", "Bad request") }
        val cmd = o.optString("cmd"); val v = o.optDouble("value", 0.0)
        val ok = onMain {
            val a = PlayerActivity.current ?: return@onMain false
            val p = a.player
            when (cmd) {
                "toggle" -> a.ui.togglePlay(p)
                "play" -> if (p.paused) a.ui.togglePlay(p)
                "pause" -> if (!p.paused) a.ui.togglePlay(p)
                "seek" -> p.seekTo(v.coerceIn(0.0, p.duration.coerceAtLeast(0.0)))
                "seekBy" -> a.ui.seek(p, v)
                "next" -> p.next()
                "prev" -> p.previous()
                "stop" -> a.finish()
                else -> return@onMain false
            }
            true
        }
        return if (ok) HttpResponse.json(200, "{\"ok\":true}") else err(409, "idle", "Nothing is playing")
    }

    /** Runs [f] on the main thread and waits for it (the player and Compose state live there). */
    private fun <T> onMain(f: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return f()
        var out: Result<T>? = null
        val latch = CountDownLatch(1)
        main.post { out = runCatching(f); latch.countDown() }
        if (!latch.await(5, TimeUnit.SECONDS)) throw IllegalStateException("main thread busy")
        return out!!.getOrThrow()
    }
}

fun deviceName(ctx: Context): String {
    val r = ctx.contentResolver
    return runCatching { Settings.Global.getString(r, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: runCatching { Settings.Secure.getString(r, "bluetooth_name") }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: Build.MODEL ?: "Android"
}

/** Keeps the receiver reachable while Lumen is not on screen, and announces it on the local network. */
class ReceiverService : Service() {
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as LumenApp
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Play from phone", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_cast_small)
            .setContentTitle("Lumen is ready to play from your phone").setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTE, n)
        } catch (e: Exception) {
            Log.w("LumenCast", "receiver could not go foreground", e)
        }
        val port = Receiver.start(app)
        register(port)
    }

    private fun register(port: Int) {
        val app = application as LumenApp
        val info = NsdServiceInfo().apply {
            serviceName = Receiver.tvName(this@ReceiverService).take(40)
            serviceType = Receiver.SERVICE_TYPE
            setPort(port)
            setAttribute("id", app.prefs.castTvId)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i("LumenCast", "announced as ${i.serviceName} on $port") }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w("LumenCast", "announce failed $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        try {
            nsd = (getSystemService(Context.NSD_SERVICE) as NsdManager).also { it.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
            registration = l
        } catch (e: Exception) { Log.w("LumenCast", "announce", e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        Receiver.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "receiver"
        private const val NOTE = 41

        /** Starts or stops the receiver to match the setting. Safe to call from anywhere, any number of times. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext as LumenApp
            val i = Intent(app, ReceiverService::class.java)
            try {
                if (app.prefs.castReceive) app.startForegroundService(i) else app.stopService(i)
            } catch (e: Exception) {
                // Android refuses a foreground service started from the background; the next launch starts it
                Log.i("LumenCast", "receiver not started: ${e.message}")
            }
        }
    }
}

/** Starts the receiver after the TV boots or Lumen is updated, so a phone can play without opening Lumen first. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { ReceiverService.sync(ctx) }
}
