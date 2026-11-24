package com.saraaminpour.lumen.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saraaminpour.lumen.LumenApp
import com.saraaminpour.lumen.R
import com.saraaminpour.lumen.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A device running Lumen that accepts "play this" requests. */
data class Tv(val id: String, val name: String, val host: String, val port: Int) {
    val address get() = (if (host.contains(':')) "[${host.substringBefore('%')}]" else host) + ":" + port
    fun toJson() = JSONObject().put("id", id).put("name", name).put("host", host).put("port", port)
    companion object {
        fun from(o: JSONObject) = Tv(o.optString("id"), o.optString("name"), o.optString("host"), o.optInt("port", Receiver.PORT))
    }
}

/** What the TV reports about playback. */
data class TvStatus(
    val state: String = "idle", val title: String = "", val position: Double = 0.0, val duration: Double = 0.0,
    val index: Int = 0, val count: Int = 0, val error: String = "", val mine: Boolean = false, val buffering: Boolean = false,
) {
    val playing get() = state == "playing"
    val live get() = state == "playing" || state == "paused" || state == "loading" || state == "ended"
}

/** [code] 0 = the TV did not answer. */
class TvReply(val code: Int, val json: JSONObject) {
    val ok get() = code == 200
    val error get() = json.optString("error")
    val message get() = json.optString("message")
}

/** HTTP calls from the phone to a TV. All of them block: call off the main thread. */
class TvClient(private val prefs: Prefs, private val myName: String) {
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()

    init {
        if (prefs.castClientId.isEmpty()) prefs.castClientId = UUID.randomUUID().toString()
        if (prefs.castClientKey.isEmpty()) prefs.castClientKey = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    }

    /** Asks whatever listens at [host]:[port] whether it is Lumen. */
    fun info(host: String, port: Int): Tv? {
        val h = host.trim().removePrefix("http://").trimEnd('/')
        if (h.isEmpty()) return null
        val probe = Tv("", "", h, port)
        val r = call(probe, "GET", "/lumen/info", null)
        if (!r.ok || r.json.optString("app") != "lumen") return null
        return Tv(r.json.optString("id"), r.json.optString("name").ifEmpty { h }, h, port)
    }

    fun call(tv: Tv, method: String, path: String, body: JSONObject?): TvReply {
        return try {
            val b = Request.Builder().url("http://${tv.address}$path")
                .header("X-Lumen-Id", prefs.castClientId).header("X-Lumen-Key", prefs.castClientKey)
                .header("X-Lumen-Name", URLEncoder.encode(myName, "UTF-8"))
            if (method == "POST") b.post((body ?: JSONObject()).toString().toRequestBody(JSON)) else b.get()
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string() ?: ""
                TvReply(r.code, try { JSONObject(text) } catch (_: Exception) { JSONObject() })
            }
        } catch (e: Exception) {
            TvReply(0, JSONObject().put("message", e.message ?: "no answer"))
        }
    }

    fun status(tv: Tv): TvStatus? {
        val r = call(tv, "GET", "/lumen/status", null)
        if (!r.ok) return null
        val o = r.json
        return TvStatus(
            o.optString("state", "idle"), o.optString("title"), o.optDouble("position", 0.0), o.optDouble("duration", 0.0),
            o.optInt("index"), o.optInt("count"), o.optString("error"), o.optBoolean("mine"), o.optBoolean("buffering"),
        )
    }

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}

/** TVs this phone has played on before. */
object TvStore {
    fun list(prefs: Prefs): List<Tv> = try {
        val a = JSONArray(prefs.castTvs)
        (0 until a.length()).map { Tv.from(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    fun save(prefs: Prefs, tv: Tv) {
        val all = listOf(tv) + list(prefs).filter { it.id != tv.id && it.address != tv.address }
        prefs.castTvs = JSONArray().apply { all.take(8).forEach { put(it.toJson()) } }.toString()
        prefs.castLastTv = tv.id
    }

    fun remove(prefs: Prefs, tv: Tv) {
        prefs.castTvs = JSONArray().apply { list(prefs).filter { it.id != tv.id }.forEach { put(it.toJson()) } }.toString()
    }
}

/** Finds TVs on the local network (they announce themselves as `_lumen._tcp`). */
class TvDiscovery(ctx: Context, private val client: TvClient, private val onFound: (Tv) -> Unit) {
    private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) { Log.w(TAG, "discovery failed $code") }
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) { main.post { queue.addLast(i); next() } }
        }
        try { nsd.discoverServices(Receiver.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l); listener = l }
        catch (e: Exception) { Log.w(TAG, "discovery", e) }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd.stopServiceDiscovery(l) } }
        listener = null; queue.clear()
    }

    /** Android resolves one service at a time. */
    private fun next() {
        if (resolving) return
        val i = queue.removeFirstOrNull() ?: return
        resolving = true
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(i, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, code: Int) { main.post { resolving = false; next() } }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val addrs = if (Build.VERSION.SDK_INT >= 34) s.hostAddresses else @Suppress("DEPRECATION") listOfNotNull(s.host)
                    val host = (addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull())?.hostAddress
                    val port = s.port
                    main.post { resolving = false; next() }
                    if (host != null) Thread {
                        client.info(host, port)?.let { tv -> main.post { onFound(tv) } }
                    }.start()
                }
            })
        } catch (e: Exception) { resolving = false; Log.w(TAG, "resolve", e) }
    }

    private companion object { const val TAG = "LumenCast" }
}

/**
 * One "Play on TV" session, owned by the phone: what was sent, to which TV, and what the TV says about it.
 * It lives outside any screen so the remote control comes back when Lumen is reopened.
 */
object CastSession {
    enum class Phase { Idle, Connecting, Approval, Active, Failed }

    var phase by mutableStateOf(Phase.Idle); private set
    var tv by mutableStateOf<Tv?>(null); private set
    var message by mutableStateOf(""); private set
    var status by mutableStateOf(TvStatus()); private set
    var titles by mutableStateOf(emptyList<String>()); private set
    /** The TV stopped answering for a moment (it may still be playing from its buffer). */
    var lost by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var client: TvClient? = null

    val active get() = phase == Phase.Connecting || phase == Phase.Approval || phase == Phase.Active

    fun client(ctx: Context): TvClient {
        val app = ctx.applicationContext as LumenApp
        return client ?: TvClient(app.prefs, deviceName(app)).also { client = it }
    }

    /** Sends files (and/or web links) to [target] and starts playing the first one. */
    fun send(ctx: Context, target: Tv, uris: List<Uri>, links: List<Pair<String, String>> = emptyList(), position: Double = -1.0) {
        val app = ctx.applicationContext as LumenApp
        val c = client(app)
        job?.cancel()
        tv = target; phase = Phase.Connecting; message = "Connecting to ${target.name}…"; status = TvStatus(); lost = false
        CastService.start(app)
        job = scope.launch {
            try {
                // the address of this phone as the TV sees it: the one our connection to the TV leaves from
                val myHost = withContext(Dispatchers.IO) {
                    try {
                        Socket().use { s -> s.connect(InetSocketAddress(target.host.substringBefore('%'), target.port), 4000); s.localAddress.hostAddress }
                    } catch (_: Exception) { null }
                } ?: return@launch fail(app, "${target.name} isn't answering. Is it on, and on the same Wi-Fi?")

                // a TV that has not met this phone asks its owner first
                var waited = 0
                while (true) {
                    val r = withContext(Dispatchers.IO) { c.call(target, "POST", "/lumen/hello", null) }
                    if (r.ok) break
                    when {
                        r.code == 401 && r.error == "approval" -> {
                            phase = Phase.Approval; message = "Press Allow on ${target.name}"
                            if (waited++ > 120) return@launch fail(app, "Nobody pressed Allow on the TV.")
                            delay(1000)
                        }
                        r.code == 403 -> return@launch fail(app, "${target.name} didn't allow this phone.")
                        r.code == 0 -> return@launch fail(app, "${target.name} isn't answering. Is it on, and on the same Wi-Fi?")
                        else -> return@launch fail(app, r.message.ifEmpty { "The TV refused (${r.code})." })
                    }
                }
                phase = Phase.Connecting; message = "Sending to ${target.name}…"
                TvStore.save(app.prefs, target)

                val port = withContext(Dispatchers.IO) { MediaServer.start(app) }
                val served = withContext(Dispatchers.IO) { uris.mapNotNull { MediaServer.add(app, it) } }
                if (served.isEmpty() && links.isEmpty()) return@launch fail(app, "Lumen couldn't open that file.")
                val arr = JSONArray()
                served.forEach { arr.put(JSONObject().put("url", MediaServer.url(it, myHost)).put("title", it.title).put("id", it.id)) }
                links.forEach { (url, title) -> arr.put(JSONObject().put("url", url).put("title", title)) }
                titles = served.map { it.title } + links.map { it.second }
                Log.i("LumenCast", "serving ${served.size} file(s) on $myHost:$port to ${target.address}")

                val body = JSONObject().put("items", arr).put("index", 0)
                if (position > 0) body.put("position", position)
                val r = withContext(Dispatchers.IO) { c.call(target, "POST", "/lumen/play", body) }
                when {
                    r.ok -> message = ""
                    r.code == 409 && r.error == "closed" -> message = "Open Lumen on ${target.name}. It starts playing right away."
                    else -> return@launch fail(app, if (r.code == 0) "${target.name} stopped answering." else r.message.ifEmpty { "The TV refused (${r.code})." })
                }
                phase = Phase.Active
                watch(app, c, target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LumenCast", "send", e)
                fail(app, "Something went wrong: ${e.message}")
            }
        }
    }

    /** Follows the TV until it stops playing what this phone sent. */
    private suspend fun watch(app: LumenApp, c: TvClient, target: Tv) {
        var seenLive = false
        var gone = 0
        var silent = 0
        val started = System.currentTimeMillis()
        while (scope.isActive) {
            val s = withContext(Dispatchers.IO) { c.status(target) }
            if (s == null) {
                silent++
                lost = silent >= 4
                // the TV may only have changed address or be busy; keep serving while it keeps reading
                if (silent > 600 && System.currentTimeMillis() - MediaServer.lastRequestAt > 600_000) break
            } else {
                silent = 0; lost = false
                status = s
                if (s.live && s.mine) { seenLive = true; gone = 0; if (message.isNotEmpty() && s.state != "loading") message = "" }
                else if (seenLive) { if (++gone >= 3) break }
                else if (System.currentTimeMillis() - started > 150_000) break      // never started
                if (s.state == "error" && s.mine) message = s.error
            }
            CastService.refresh(app)
            delay(1000)
        }
        end(app, if (seenLive) "" else "The TV didn't start playing.")
    }

    fun control(ctx: Context, cmd: String, value: Double = 0.0) {
        val t = tv ?: return
        val c = client(ctx)
        // show the change at once; the next status report corrects it
        status = when (cmd) {
            "toggle" -> status.copy(state = if (status.playing) "paused" else "playing")
            "seek" -> status.copy(position = value)
            "seekBy" -> status.copy(position = (status.position + value).coerceIn(0.0, status.duration.coerceAtLeast(0.0)))
            else -> status
        }
        scope.launch(Dispatchers.IO) { c.call(t, "POST", "/lumen/control", JSONObject().put("cmd", cmd).put("value", value)) }
    }

    /** Stops playback on the TV and stops serving. */
    fun stop(ctx: Context) {
        val app = ctx.applicationContext as LumenApp
        val t = tv
        val c = client(app)
        job?.cancel(); job = null
        if (t != null && phase == Phase.Active) scope.launch(Dispatchers.IO) { c.call(t, "POST", "/lumen/control", JSONObject().put("cmd", "stop")) }
        end(app, "")
    }

    fun dismiss() { if (!active) { phase = Phase.Idle; message = "" } }

    private fun fail(app: LumenApp, text: String) {
        phase = Phase.Failed; message = text
        CastService.stop(app); MediaServer.stop()
    }

    private fun end(app: LumenApp, text: String) {
        phase = if (text.isEmpty()) Phase.Idle else Phase.Failed
        message = text; status = TvStatus(); lost = false
        CastService.stop(app); MediaServer.stop()
    }
}

/** Keeps the phone awake and on Wi-Fi while the TV streams from it, with a notification to stop. */
class CastService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Playing on TV", NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTE, note())
        } catch (e: Exception) { Log.w("LumenCast", "sender could not go foreground", e) }
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lumen:send")
            .apply { setReferenceCounted(false); acquire(12 * 3600_000L) }
        @Suppress("DEPRECATION")
        wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lumen:send")
            .apply { setReferenceCounted(false); runCatching { acquire() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> CastSession.stop(this)
            ACTION_TOGGLE -> CastSession.control(this, "toggle")
            ACTION_REFRESH -> runCatching { getSystemService(NotificationManager::class.java).notify(NOTE, note()) }
        }
        if (intent?.action == ACTION_QUIT || !CastSession.active) {
            running = false
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val tv = CastSession.tv?.name ?: "TV"
        val s = CastSession.status
        val title = s.title.ifEmpty { CastSession.titles.firstOrNull() ?: "" }
        val open = PendingIntent.getActivity(this, 0, Intent(this, CastActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, CastService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_cast_small)
            .setContentTitle("Playing on $tv").setContentText(title).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
        if (CastSession.phase == CastSession.Phase.Active) b.addAction(
            Notification.Action.Builder(null, if (s.playing) "Pause" else "Play", action(ACTION_TOGGLE, 1)).build())
        b.addAction(Notification.Action.Builder(null, "Stop", action(ACTION_STOP, 2)).build())
        return b.build()
    }

    override fun onDestroy() {
        runCatching { wake?.release() }; runCatching { wifi?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "sending"
        private const val NOTE = 42
        const val ACTION_STOP = "com.saraaminpour.lumen.cast.STOP"
        const val ACTION_TOGGLE = "com.saraaminpour.lumen.cast.TOGGLE"
        const val ACTION_REFRESH = "com.saraaminpour.lumen.cast.REFRESH"
        private const val ACTION_QUIT = "com.saraaminpour.lumen.cast.QUIT"
        private var shown = ""
        private var running = false

        fun start(ctx: Context) {
            shown = ""
            try { ctx.startForegroundService(Intent(ctx, CastService::class.java)); running = true }
            catch (e: Exception) { Log.w("LumenCast", "sender service", e) }
        }

        /**
         * Asks the service to end itself. (Stopping it from outside right after starting it would crash:
         * Android insists that a service started as "foreground" shows its notification first.)
         */
        fun stop(ctx: Context) {
            if (!running) return
            running = false
            try { ctx.startService(Intent(ctx, CastService::class.java).setAction(ACTION_QUIT)) }
            catch (_: Exception) { runCatching { ctx.stopService(Intent(ctx, CastService::class.java)) } }
        }

        /** Updates the notification when what it shows has changed. */
        fun refresh(ctx: Context) {
            val now = CastSession.status.let { "${it.title}|${it.playing}|${CastSession.phase}" }
            if (now == shown || !CastSession.active) return
            shown = now
            runCatching { ctx.startService(Intent(ctx, CastService::class.java).setAction(ACTION_REFRESH)) }
        }
    }
}
