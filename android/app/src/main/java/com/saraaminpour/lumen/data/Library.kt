package com.saraaminpour.lumen.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size

data class Video(
    val uri: Uri,
    val name: String,
    val folderId: String,
    val folder: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
)

data class Folder(val id: String, val name: String, val videos: List<Video>)

/** Natural order, so "Episode 2" comes before "Episode 10" (like the desktop natural_key). */
val naturalOrder = Comparator<String> { a, b ->
    val ra = Regex("(\\d+)|(\\D+)").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("(\\d+)|(\\D+)").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]; val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val n = x.trimStart('0').length.compareTo(y.trimStart('0').length)
            if (n != 0) n else x.trimStart('0').compareTo(y.trimStart('0'))
        } else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ra.size.compareTo(rb.size)
}

object Library {
    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun videos(ctx: Context): List<Video> {
        val out = ArrayList<Video>()
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
        )
        try {
            ctx.contentResolver.query(collection, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Video(
                        ContentUris.withAppendedId(collection, id), c.getString(1) ?: "video",
                        c.getString(2) ?: "", c.getString(3) ?: "Videos", c.getLong(4), c.getLong(5), c.getLong(6),
                    )
                }
            }
        } catch (_: SecurityException) { }
        return out
    }

    fun folders(ctx: Context): List<Folder> =
        videos(ctx).groupBy { it.folderId }
            .map { (id, vs) -> Folder(id, vs.first().folder, vs.sortedWith(compareBy(naturalOrder) { it.name })) }
            .sortedByDescending { f -> f.videos.maxOf { it.dateAdded } }

    /** The other videos in the same folder as [uri], in natural order (for "queue the rest of the folder"). */
    fun siblings(ctx: Context, uri: Uri): List<Video> {
        val all = videos(ctx)
        val me = all.firstOrNull { it.uri == uri } ?: findByName(ctx, uri, all) ?: return emptyList()
        return all.filter { it.folderId == me.folderId }.sortedWith(compareBy(naturalOrder) { it.name })
    }

    private fun findByName(ctx: Context, uri: Uri, all: List<Video>): Video? {
        val name = displayName(ctx, uri) ?: return null
        val size = fileSize(ctx, uri)
        return all.firstOrNull { it.name == name && (size <= 0 || it.size == size) }
    }

    fun displayName(ctx: Context, uri: Uri): String? = try {
        if (uri.scheme == "file") uri.lastPathSegment
        else ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { uri.lastPathSegment }

    fun fileSize(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    } catch (_: Exception) { -1L }

    private val thumbs = LruCache<String, Bitmap>(60)

    fun thumbnail(ctx: Context, uri: Uri): Bitmap? {
        thumbs.get(uri.toString())?.let { return it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(384, 216), null)
            else @Suppress("DEPRECATION") MediaStore.Video.Thumbnails.getThumbnail(
                ctx.contentResolver, ContentUris.parseId(uri), MediaStore.Video.Thumbnails.MINI_KIND, null,
            )
        } catch (_: Exception) { null }
        bmp?.let { thumbs.put(uri.toString(), it) }
        return bmp
    }
}
