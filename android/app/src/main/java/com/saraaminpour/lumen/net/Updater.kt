package com.saraaminpour.lumen.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.saraaminpour.lumen.BuildConfig
import com.saraaminpour.lumen.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer Lumen and downloads its APK.
 * Android always asks the user to confirm the install; we hand the verified APK to the system installer.
 */
class Updater(private val app: Context, private val prefs: Prefs) {
    enum class State { Idle, Checking, UpToDate, Available, Downloading, Ready, Error }

    var state by mutableStateOf(State.Idle)
    var latest by mutableStateOf("")
    var notes by mutableStateOf("")
    var message by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var automatic by mutableStateOf(false)
    val current: String = BuildConfig.VERSION_NAME

    private var asset: JSONObject? = null
    private var apk: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun check(auto: Boolean) {
        if (state == State.Checking || state == State.Downloading) return
        if (auto && System.currentTimeMillis() - prefs.lastUpdateCheck < 20 * 3600_000L) return
        automatic = auto
        state = State.Checking; message = "Checking for updates…"
        scope.launch {
            try {
                val rel = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Lumen-Android-Updater").build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        JSONObject(r.body!!.string())
                    }
                }
                prefs.lastUpdateCheck = System.currentTimeMillis()
                latest = rel.optString("tag_name").trimStart('v', 'V')
                notes = shortNotes(rel.optString("body"))
                val assets = rel.optJSONArray("assets")
                asset = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                    .firstOrNull { APK_RE.matches(it.optString("name")) }
                when {
                    !isNewer(latest, current) -> { state = State.UpToDate; message = "Lumen $current is the latest version." }
                    asset == null -> { state = State.UpToDate; message = "Lumen $current is the latest Android version." }
                    else -> {
                        state = State.Available
                        message = "Lumen $latest is available (${(asset!!.optLong("size") / 1_000_000)} MB)."
                    }
                }
            } catch (e: Exception) {
                state = State.Error
                message = if (e is java.io.IOException) "Couldn't reach GitHub. Check your connection." else "Update check failed: ${e.message}"
            }
        }
    }

    fun download() {
        val a = asset ?: return
        if (state == State.Downloading) return
        state = State.Downloading; progress = 0f; message = "Downloading…"
        scope.launch {
            try {
                val out = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val f = File(dir, a.getString("name"))
                    val total = a.optLong("size")
                    val md = MessageDigest.getInstance("SHA-256")
                    http.newCall(Request.Builder().url(a.getString("browser_download_url")).build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        r.body!!.byteStream().use { input ->
                            f.outputStream().use { o ->
                                val buf = ByteArray(1 shl 16); var done = 0L; var last = 0L
                                while (true) {
                                    val n = input.read(buf); if (n < 0) break
                                    o.write(buf, 0, n); md.update(buf, 0, n); done += n
                                    if (total > 0 && System.currentTimeMillis() - last > 150) {
                                        last = System.currentTimeMillis()
                                        withContext(Dispatchers.Main) {
                                            progress = done.toFloat() / total
                                            message = "Downloading…  ${done / 1_000_000} / ${total / 1_000_000} MB"
                                        }
                                    }
                                }
                                if (total > 0 && done != total) throw IllegalStateException("The download was incomplete. Please try again.")
                            }
                        }
                    }
                    val digest = a.optString("digest").lowercase()
                    val hex = md.digest().joinToString("") { "%02x".format(it) }
                    if (digest.startsWith("sha256:") && digest.removePrefix("sha256:") != hex)
                        throw IllegalStateException("The download was damaged (checksum mismatch). Please try again.")
                    f
                }
                apk = out; progress = 1f
                state = State.Ready; message = "Lumen $latest is downloaded."
                install()
            } catch (e: Exception) {
                state = State.Error
                message = if (e is IllegalStateException) e.message ?: "Download failed" else "The download failed. Check your connection and try again."
            }
        }
    }

    /** Opens the system installer; if Lumen may not install apps yet, opens that setting first. */
    fun install(): Boolean {
        val f = apk ?: return false
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            message = "Allow Lumen to install updates, then come back and press Install."
            return false
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", f)
        app.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    companion object {
        const val REPO = "SaraAminpour/lumen-player"
        const val API = "https://api.github.com/repos/$REPO/releases/latest"
        const val RELEASES = "https://github.com/$REPO/releases/latest"
        private val APK_RE = Regex("^Lumen-Android-[\\d.]+\\.apk$", RegexOption.IGNORE_CASE)

        fun parse(v: String) = Regex("\\d+").findAll(v).map { it.value.toInt() }.take(3).toList()
        fun isNewer(a: String, b: String): Boolean {
            val x = parse(a); val y = parse(b)
            for (i in 0 until 3) {
                val c = (x.getOrNull(i) ?: 0).compareTo(y.getOrNull(i) ?: 0)
                if (c != 0) return c > 0
            }
            return false
        }

        fun shortNotes(body: String, limit: Int = 6) = body.lines().map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .take(limit)
            .joinToString("\n") {
                "•  " + it.drop(2).replace(Regex("\\*+|`"), "").replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            }
    }
}
