package com.dex.adfree

import android.webkit.URLUtil
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Tab(
    val id: Int,
    val isPrivate: Boolean,
    /** WebView profile name for private tabs (isolated cookies, storage, cache); null for normal tabs. */
    val profile: String?,
    val webView: WebView,
) {
    var title by mutableStateOf("New tab")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

/** A download waiting for the user's confirmation. */
class PendingDownload(
    val url: String,
    val userAgent: String,
    contentDisposition: String?,
    val mimeType: String?,
    val length: Long,
    val tab: Tab,
) {
    val fileName: String = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val risky: Boolean = fileName.substringAfterLast('.', "").lowercase() in RISKY_EXTENSIONS

    private companion object {
        val RISKY_EXTENSIONS = setOf(
            "apk", "xapk", "apks", "exe", "msi", "bat", "cmd", "sh", "jar",
            "scr", "dex", "js", "vbs", "ps1", "dmg", "pkg",
        )
    }
}
