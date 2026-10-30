package com.dex.adfree

import android.content.Intent
import android.net.Uri

object Urls {
    const val HOME = "https://m.youtube.com"

    private val HAS_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val URL_IN_TEXT = Regex("https?://\\S+")

    /** Address-bar input to a loadable URL: full URL as is, bare domain gets https, anything else is a search. */
    fun normalize(input: String): String {
        val t = input.trim()
        return when {
            t.isEmpty() -> HOME
            HAS_SCHEME.containsMatchIn(t) -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(t)
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
