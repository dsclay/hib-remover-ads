package com.dex.adfree

import android.content.Intent
import android.net.Uri

object Urls {
    const val HOME = "https://appassets.androidplatform.net/assets/home/home.html"

    fun isHome(url: String) = url.startsWith("https://appassets.androidplatform.net/")

    /** http:// to https:// (used by HTTPS-only mode). */
    fun upgrade(url: String) = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    private val HAS_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val URL_IN_TEXT = Regex("https?://\\S+")

    /** Address-bar input to a loadable URL: full URL as is, bare domain gets https, anything else is a search. */
    val ENGINES = listOf("DuckDuckGo" to "https://duckduckgo.com/?q=", "Google" to "https://www.google.com/search?q=",
        "Brave" to "https://search.brave.com/search?q=", "Bing" to "https://www.bing.com/search?q=")

    fun normalize(input: String, engine: String = "DuckDuckGo"): String {
        val t = input.trim()
        return when {
            t.isEmpty() -> HOME
            HAS_SCHEME.containsMatchIn(t) -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> (ENGINES.firstOrNull { it.first == engine } ?: ENGINES[0]).second + Uri.encode(t)
        }
    }

    fun firstUrl(text: String?): String? = text?.let { URL_IN_TEXT.find(it)?.value }

    /** URL carried by a "view link" or "share text" intent, if any. */
    fun fromIntent(intent: Intent?): String? {
        val i = intent ?: return null
        return when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> firstUrl(i.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        }
    }
}
