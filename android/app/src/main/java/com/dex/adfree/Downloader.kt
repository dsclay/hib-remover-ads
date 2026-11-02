package com.dex.adfree

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class DlStatus { RUNNING, DONE, FAILED, CANCELLED }

class DownloadItem(val id: Int, val name: String, val url: String) {
    var status by mutableStateOf(DlStatus.RUNNING)
    var done by mutableStateOf(0L)
    var total by mutableStateOf(-1L)
    var speed by mutableStateOf(0L)
    var parts by mutableStateOf(1)
    var error by mutableStateOf<String?>(null)
    var uri: String? = null
    var finishedAt = 0L
    @Volatile var abort = false
    @Volatile var userCancelled = false
    var persist = true
}

/**
 * In-app downloader. With "boost" on, a file is fetched in several parallel ranges (when the server
 * supports them), which is often much faster than a single connection. Downloads run while the app is alive.
 */
class Downloader(context: Context, private val settings: Settings) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 0

    val items = mutableStateListOf<DownloadItem>()

    init {
        runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = DownloadItem(nextId++, o.getString("name"), "")
                it.status = DlStatus.DONE
                it.uri = o.optString("uri").ifEmpty { null }
                it.total = o.optLong("size", -1)
                it.done = it.total
                it.finishedAt = o.optLong("time")
                items.add(it)
            }
        }
    }

    fun start(url: String, name: String, headers: Map<String, String>, persist: Boolean) {
        val item = DownloadItem(nextId++, name, url)
        item.persist = persist
        main.post { items.add(0, item) }
        ACTIVE.incrementAndGet()
        pool.execute { run(item, headers) }
    }

    fun cancel(item: DownloadItem) {
        item.userCancelled = true
        item.abort = true
    }

    fun remove(item: DownloadItem, deleteFile: Boolean) {
        if (deleteFile) item.uri?.let { u ->
            runCatching {
                if (u.startsWith("content:")) app.contentResolver.delete(Uri.parse(u), null, null)
                else File(Uri.parse(u).path ?: "").delete()
            }
        }
        items.remove(item)
        save()
    }

    fun open(item: DownloadItem) {
        val u = item.uri
        try {
            if (u != null && u.startsWith("content:")) {
                val mime = mimeOf(item.name)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(u), mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                app.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Exception) {
            runCatching {
                app.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (it in items) {
            if (it.status != DlStatus.DONE || !it.persist) continue
            arr.put(
                JSONObject().put("name", it.name).put("uri", it.uri ?: "").put("size", it.total).put("time", it.finishedAt),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    // ---- worker ----

    private fun run(item: DownloadItem, headers: Map<String, String>) {
        val tmp = File(app.cacheDir, "dl_${item.id}_${System.nanoTime()}.part")
        val counter = AtomicLong()
        var last = 0L
        val ticker = object : Runnable {
            override fun run() {
                val now = counter.get()
                item.speed = (now - last) * 2
                last = now
                item.done = now
                main.postDelayed(this, 500)
            }
        }
        main.post(ticker)
        try {
            var probe = connect(item.url, headers, "bytes=0-0")
            var code = probe.responseCode
            if (code != 200 && code != 206) { // some servers refuse Range requests: try a plain GET
                probe.disconnect()
                probe = connect(item.url, headers, null)
                code = probe.responseCode
            }
            val finalUrl = probe.url.toString()
            var total = -1L
            var ranged = false
            when (code) {
                206 -> {
                    total = probe.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                    ranged = total > 0
                }
                200 -> total = probe.contentLengthLong
                else -> throw IOException("Server said $code")
            }
            item.total = total
            val parts = if (settings.boostDownloads && ranged && total >= MIN_BOOST) PARTS else 1
            item.parts = parts
            if (ranged && parts > 1) {
                probe.disconnect()
                multi(item, finalUrl, headers, tmp, total, parts, counter)
            } else if (code == 200) {
                probe.inputStream.use { copy(item, it, tmp, counter) }
            } else {
                probe.disconnect()
                val c = connect(finalUrl, headers, null)
                if (c.responseCode != 200) throw IOException("Server said ${c.responseCode}")
                c.inputStream.use { copy(item, it, tmp, counter) }
            }
            if (item.abort) throw IOException("cancelled")
            item.done = counter.get()
            if (item.total <= 0) item.total = item.done
            item.uri = publish(item, tmp)
            item.finishedAt = System.currentTimeMillis()
            item.status = DlStatus.DONE
            save()
        } catch (e: Exception) {
            item.abort = true
            if (item.userCancelled) {
                item.status = DlStatus.CANCELLED
            } else {
                item.error = e.message?.take(80) ?: "Failed"
                item.status = DlStatus.FAILED
            }
        } finally {
            main.removeCallbacks(ticker)
            ACTIVE.decrementAndGet()
            item.speed = 0
            tmp.delete()
        }
    }

    private fun copy(item: DownloadItem, input: java.io.InputStream, tmp: File, counter: AtomicLong) {
        FileOutputStream(tmp).use { out ->
            val buf = ByteArray(65536)
            while (true) {
                if (item.abort) throw IOException("cancelled")
                val r = input.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
                counter.addAndGet(r.toLong())
            }
        }
    }

    private fun multi(item: DownloadItem, url: String, headers: Map<String, String>, tmp: File, total: Long, n: Int, counter: AtomicLong) {
        RandomAccessFile(tmp, "rw").use { it.setLength(total) }
        val seg = total / n
        val futures = (0 until n).map { i ->
            val s = i * seg
            val e = if (i == n - 1) total - 1 else s + seg - 1
            pool.submit { segment(item, url, headers, tmp, s, e, counter) }
        }
        try {
            futures.forEach { it.get() }
        } catch (ex: ExecutionException) {
            item.abort = true
            throw (ex.cause as? Exception) ?: ex
        }
    }

    private fun segment(item: DownloadItem, url: String, headers: Map<String, String>, tmp: File, start: Long, end: Long, counter: AtomicLong) {
        var pos = start
        var tries = 0
        RandomAccessFile(tmp, "rw").use { raf ->
            while (pos <= end) {
                if (item.abort) throw IOException("cancelled")
                try {
                    val c = connect(url, headers, "bytes=$pos-$end")
                    if (c.responseCode != 206) throw IOException("Server said ${c.responseCode}")
                    c.inputStream.use { input ->
                        val buf = ByteArray(65536)
                        raf.seek(pos)
                        while (pos <= end) {
                            if (item.abort) throw IOException("cancelled")
                            val r = input.read(buf)
                            if (r < 0) break
                            val n = minOf(r.toLong(), end - pos + 1).toInt()
                            raf.write(buf, 0, n)
                            pos += n
                            counter.addAndGet(n.toLong())
                        }
                    }
                } catch (e: IOException) {
                    if (item.abort || ++tries > 3) throw e
                }
            }
        }
    }

    /** Opens a connection, following redirects by hand so Cookie is only sent to the original host. */
    private fun connect(url: String, headers: Map<String, String>, range: String?): HttpURLConnection {
        var current = url
        val originHost = Uri.parse(url).host
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            val sameHost = Uri.parse(current).host == originHost
            for ((k, v) in headers) if (sameHost || k != "Cookie") c.setRequestProperty(k, v)
            c.setRequestProperty("Accept-Encoding", "identity")
            if (range != null) c.setRequestProperty("Range", range)
            val code = c.responseCode
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                c.disconnect()
                current = URL(URL(current), loc).toString()
                return@repeat
            }
            return c
        }
        throw IOException("Too many redirects")
    }

    private fun publish(item: DownloadItem, tmp: File): String {
        val mime = mimeOf(item.name)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, item.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Can't save file")
            resolver.openOutputStream(uri)!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        var target = File(dir, item.name)
        var n = 1
        while (target.exists()) target = File(dir, "${item.name.substringBeforeLast('.')} ($n).${item.name.substringAfterLast('.', "bin")}".also { n++ })
        tmp.copyTo(target)
        return Uri.fromFile(target).toString()
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    companion object {
        /** Number of downloads currently running (read by the keep-alive service). */
        val ACTIVE = AtomicInteger()
        private const val KEY = "history"
        private const val PARTS = 6
        private const val MIN_BOOST = 2L * 1024 * 1024
    }
}
