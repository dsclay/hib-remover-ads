package com.dex.adfree

import android.net.Uri
import android.webkit.WebResourceRequest
import java.util.Locale

data class MediaChoice(val url: String, val label: String)

/** Spots downloadable media files among a page's requests. Streaming playlists, YouTube and DRM are not handled. */
object Media {
    private val MEDIA_EXT = Regex("\\.(mp4|webm|m4v|mov|mkv|3gp|ogv|mp3|m4a|ogg|wav|flac)$")
    private val NOT_MEDIA_EXT = Regex("\\.(js|css|json|wasm|woff2?|ts|m4s|aac|pdf|png|jpe?g|gif|webp|svg|ico|html?)$")
    private val SKIP_HOSTS = listOf("googlevideo.com", "youtube.com", "ytimg.com", "ggpht.com")

    /** Runs on WebView worker threads for every sub-resource request. */
    fun isMedia(request: WebResourceRequest): Boolean {
        val url = request.url
        val host = url.host?.lowercase(Locale.ROOT) ?: return false
        if (SKIP_HOSTS.any { host == it || host.endsWith(".$it") }) return false
        val path = url.path?.lowercase(Locale.ROOT) ?: return false
        if (MEDIA_EXT.containsMatchIn(path)) return true
        if (NOT_MEDIA_EXT.containsMatchIn(path)) return false
        // Video and audio elements fetch with a Range header, even when the URL has no file extension.
        return request.requestHeaders?.get("Range")?.startsWith("bytes=") == true
    }

    /** File name for a captured video: the page title plus the URL's extension (mp4 when unknown). */
    fun fileName(title: String, url: String): String {
        val ext = MEDIA_EXT.find(Uri.parse(url).path.orEmpty().lowercase(Locale.ROOT))?.groupValues?.get(1) ?: "mp4"
        val base = title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(60).ifBlank { "video" }
        return "$base.$ext"
    }
}
