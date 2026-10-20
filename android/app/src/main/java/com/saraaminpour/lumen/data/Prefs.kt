package com.saraaminpour.lumen.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings as Compose state, persisted to SharedPreferences (mirrors the desktop `prefs`). */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    private inner class P<T>(private val key: String, default: T) : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        private val state: MutableState<T> = mutableStateOf(
            when (default) {
                is Boolean -> sp.getBoolean(key, default) as T
                is Int -> sp.getInt(key, default) as T
                is Long -> sp.getLong(key, default) as T
                is Float -> sp.getFloat(key, default) as T
                is String -> (sp.getString(key, default) ?: default) as T
                else -> default
            }
        )
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }
    }

    var accent by P("accent", 0)
    var resume by P("resume", true)
    var autoQueue by P("autoQueue", true)
    var showRemaining by P("showRemaining", false)
    var thumbnails by P("thumbnails", true)
    var streamQuality by P("streamQuality", 1080)
    var autoUpdate by P("autoUpdate", true)
    var lastUpdateCheck by P("lastUpdateCheck", 0L)
    var hwdec by P("hwdec", true)
    var pitchCorrection by P("pitchCorrection", true)
    var volumeBoost by P("volumeBoost", false)
    var normalize by P("normalize", false)
    var downmix by P("downmix", false)
    var fillCrop by P("fillCrop", false)
    var subScale by P("subScale", 1.0f)
    var subPos by P("subPos", 100)
    var subColor by P("subColor", "#FFFFFF")
    var subBox by P("subBox", false)
    var subOverride by P("subOverride", false)
    var gestures by P("gestures", true)
    var backgroundAudio by P("backgroundAudio", false)
    var autoRotate by P("autoRotate", true)
}

/** One remembered file or link: position for Continue watching plus its tracks and delays. */
data class HistoryEntry(
    val key: String,            // content:// uri, file path or web URL
    val title: String,
    val pos: Double,
    val dur: Double,
    val time: Long,
    val folder: String = "",
    val sid: String = "",
    val aid: String = "",
    val secondarySid: String = "",
    val subDelay: Double = 0.0,
    val audioDelay: Double = 0.0,
) {
    val finished get() = dur > 0 && dur - pos < 30
    val web get() = key.startsWith("http", ignoreCase = true)

    fun toJson() = JSONObject().apply {
        put("key", key); put("title", title); put("pos", pos); put("dur", dur); put("time", time)
        put("folder", folder); put("sid", sid); put("aid", aid); put("ssid", secondarySid)
        put("subDelay", subDelay); put("audioDelay", audioDelay)
    }

    companion object {
        fun from(o: JSONObject) = HistoryEntry(
            o.getString("key"), o.optString("title"), o.optDouble("pos", 0.0), o.optDouble("dur", 0.0),
            o.optLong("time"), o.optString("folder"), o.optString("sid"), o.optString("aid"), o.optString("ssid"),
            o.optDouble("subDelay", 0.0), o.optDouble("audioDelay", 0.0),
        )
    }
}

class History(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lumen-history", Context.MODE_PRIVATE)
    val entries = mutableStateOf(load())

    private fun load(): List<HistoryEntry> = try {
        val a = JSONArray(sp.getString("entries", "[]"))
        (0 until a.length()).map { HistoryEntry.from(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    fun get(key: String) = entries.value.firstOrNull { it.key == key }

    fun put(e: HistoryEntry) {
        entries.value = (listOf(e) + entries.value.filter { it.key != e.key }).take(60)
        save()
    }

    fun remove(key: String) {
        entries.value = entries.value.filter { it.key != key }
        save()
    }

    /** Continue watching: started, not finished, newest first. */
    fun continueWatching() = entries.value.filter { it.pos > 5 && !it.finished }.take(12)

    private fun save() {
        val a = JSONArray(); entries.value.forEach { a.put(it.toJson()) }
        sp.edit { putString("entries", a.toString()) }
    }
}
