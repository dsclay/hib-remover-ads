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
import java.util.concurrent.Future
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
    @Volatile var pauseFlag = false
    var paused by mutableStateOf(false)
    @Volatile var userCancelled = false
    var persist = true
    var resumable by mutableStateOf(false)
    @Volatile var segProgress: java.util.concurrent.atomic.AtomicLongArray? = null
    @Volatile var segSizes: LongArray = LongArray(0)
    var segSnap by mutableStateOf<List<Long>>(emptyList())
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
        // Temporary files from downloads that were cut short by the app being killed; nothing is running yet.
        runCatching { app.cacheDir.listFiles { f -> f.name.startsWith("dl_") && f.name.endsWith(".part") }?.forEach { it.delete() } }
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

    fun setPaused(item: DownloadItem, value: Boolean) {
        item.pauseFlag = value
        item.paused = value
    }

    private fun waitWhilePaused(item: DownloadItem) {
        while (item.pauseFlag && !item.abort) Thread.sleep(200)
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
            // Files that can run code or install apps are never opened straight from the list; show the Downloads folder instead.
            val risky = item.name.substringAfterLast('.', "").lowercase() in RISKY_EXT
            if (u != null && u.startsWith("content:") && !risky) {
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
                item.segProgress?.let { p -> item.segSnap = List(p.length()) { p.get(it) } }
                main.postDelayed(this, 500)
            }
        }
        main.post(ticker)
        try {
            if (item.url.lowercase().let { it.contains(".m3u8") || it.contains("/m3u8") || it.endsWith("#hls") }) {
                hls(item, headers, tmp, counter)
            } else {
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
            val parts = if (settings.boostDownloads && ranged && total >= MIN_BOOST) settings.connections.coerceIn(2, 16) else 1
            item.parts = parts
            item.resumable = ranged
            if (ranged) {
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
            }
            if (item.abort) throw IOException("cancelled")
            item.done = counter.get()
            item.segProgress?.let { p -> item.segSnap = List(p.length()) { p.get(it) } }
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

    // ---- HLS (.m3u8) ----

    private class Seg(val url: String, val keyUrl: String?, val iv: ByteArray?, val seq: Long)

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray {
        var last: Exception? = null
        repeat(3) {
            try {
                val c = connect(url, headers, null)
                if (c.responseCode != 200) throw IOException("Server said ${c.responseCode}")
                return c.inputStream.use { it.readBytes() }
            } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("Failed")
    }

    private fun resolve(base: String, ref: String): String = URL(URL(base), ref).toString()

    /** Downloads every segment of an HLS playlist (highest quality, AES-128 supported) and joins them into one file. */
    private fun hls(item: DownloadItem, headers: Map<String, String>, tmp: File, counter: AtomicLong) {
        var playlistUrl = item.url
        var text = String(fetchBytes(playlistUrl, headers))
        repeat(2) {
            if (!text.contains("#EXT-X-STREAM-INF")) return@repeat
            val lines = text.lines().map { it.trim() }
            var best = -1L
            var bestUrl: String? = null
            for (i in lines.indices) {
                if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val u = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
                if (bw > best) { best = bw; bestUrl = u }
            }
            playlistUrl = resolve(playlistUrl, bestUrl ?: throw IOException("Empty playlist"))
            text = String(fetchBytes(playlistUrl, headers))
        }
        if (!text.contains("#EXTINF")) throw IOException("No video in playlist")
        if (!text.contains("#EXT-X-ENDLIST")) throw IOException("Live stream can't be saved")
        var seq = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        var keyUrl: String? = null
        var keyIv: ByteArray? = null
        var mapUrl: String? = null
        val segs = ArrayList<Seg>()
        var expectSeg = false
        for (raw in text.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXT-X-KEY") -> {
                    val method = Regex("METHOD=([A-Z0-9-]+)").find(l)?.groupValues?.get(1)
                    when (method) {
                        "NONE" -> { keyUrl = null; keyIv = null }
                        "AES-128" -> {
                            keyUrl = resolve(playlistUrl, Regex("URI=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: throw IOException("Bad key"))
                            keyIv = Regex("IV=0[xX]([0-9a-fA-F]+)").find(l)?.groupValues?.get(1)?.let { hex ->
                                val h = hex.padStart(32, '0')
                                ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                            }
                        }
                        else -> throw IOException("Protected stream")
                    }
                }
                l.startsWith("#EXT-X-MAP") ->
                    mapUrl = Regex("URI=\"([^\"]+)\"").find(l)?.groupValues?.get(1)?.let { resolve(playlistUrl, it) }
                l.startsWith("#EXTINF") -> expectSeg = true
                l.isNotEmpty() && !l.startsWith("#") && expectSeg -> {
                    segs.add(Seg(resolve(playlistUrl, l), keyUrl, keyIv, seq))
                    seq++
                    expectSeg = false
                }
            }
        }
        if (segs.isEmpty()) throw IOException("No video in playlist")
        val keys = ConcurrentHashMap<String, ByteArray>()
        fun load(s: Seg): ByteArray {
            var data = fetchBytes(s.url, headers)
            val k = s.keyUrl ?: return data
            val key = keys.getOrPut(k) { fetchBytes(k, headers) }
            val iv = s.iv ?: ByteArray(16).also { b -> var v = s.seq; for (i in 15 downTo 8) { b[i] = (v and 0xff).toByte(); v = v shr 8 } }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            data = cipher.doFinal(data)
            return data
        }
        val n = if (settings.boostDownloads) settings.connections.coerceIn(2, 16) else 1
        item.parts = n
        item.resumable = false
        val pool = Executors.newFixedThreadPool(n)
        try {
            val queue = java.util.ArrayDeque<Future<ByteArray>>()
            var next = 0
            var written = 0
            FileOutputStream(tmp).use { out ->
                mapUrl?.let { u -> val d = fetchBytes(u, headers); out.write(d); counter.addAndGet(d.size.toLong()) }
                while (written < segs.size) {
                    waitWhilePaused(item)
                    if (item.abort) throw IOException("cancelled")
                    while (next < segs.size && queue.size < n * 2) { val s = segs[next++]; queue.add(pool.submit<ByteArray> { load(s) }) }
                    val data = try { queue.removeFirst().get() } catch (e: ExecutionException) { throw IOException(e.cause?.message ?: "Segment failed") }
                    out.write(data)
                    written++
                    val done = counter.addAndGet(data.size.toLong())
                    item.total = done * segs.size / written
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun copy(item: DownloadItem, input: java.io.InputStream, tmp: File, counter: AtomicLong) {
        FileOutputStream(tmp).use { out ->
            val buf = ByteArray(65536)
            while (true) {
                waitWhilePaused(item)
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
        item.segSizes = LongArray(n) { i -> if (i == n - 1) total - i * seg else seg }
        item.segProgress = java.util.concurrent.atomic.AtomicLongArray(n)
        val futures = (0 until n).map { i ->
            val s = i * seg
            val e = if (i == n - 1) total - 1 else s + seg - 1
            pool.submit { segment(item, url, headers, tmp, s, e, counter, i) }
        }
        try {
            futures.forEach { it.get() }
        } catch (ex: ExecutionException) {
            item.abort = true
            throw (ex.cause as? Exception) ?: ex
        }
    }

    private fun segment(item: DownloadItem, url: String, headers: Map<String, String>, tmp: File, start: Long, end: Long, counter: AtomicLong, idx: Int) {
        var pos = start
        var tries = 0
        RandomAccessFile(tmp, "rw").use { raf ->
            while (pos <= end) {
                waitWhilePaused(item)
                if (item.abort) throw IOException("cancelled")
                try {
                    val c = connect(url, headers, "bytes=$pos-$end")
                    if (c.responseCode != 206) throw IOException("Server said ${c.responseCode}")
                    c.inputStream.use { input ->
                        val buf = ByteArray(65536)
                        raf.seek(pos)
                        while (pos <= end) {
                            if (item.abort) throw IOException("cancelled")
                            if (item.pauseFlag) break // reconnects from this position on resume
                            val r = input.read(buf)
                            if (r < 0) break
                            val n = minOf(r.toLong(), end - pos + 1).toInt()
                            raf.write(buf, 0, n)
                            pos += n
                            counter.addAndGet(n.toLong())
                            item.segProgress?.addAndGet(idx, n.toLong())
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
        private val RISKY_EXT = setOf("apk", "apks", "xapk", "html", "htm", "xhtml", "svg", "js", "sh", "bat", "jar")
        private const val PARTS = 6
        private const val MIN_BOOST = 2L * 1024 * 1024
    }
}
