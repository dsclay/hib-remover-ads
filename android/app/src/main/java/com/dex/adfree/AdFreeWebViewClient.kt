package com.dex.adfree

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

class AdFreeWebViewClient(
    private val blocker: AdBlocker,
    private val assetLoader: WebViewAssetLoader,
    private val blockingEnabled: (String?) -> Boolean,
    private val httpsOnly: () -> Boolean,
    private val rewrite: (String) -> String?,
    private val onBlocked: (String) -> Unit,
    private val onMedia: (String, Map<String, String>) -> Unit,
    private val onStarted: (WebView, String) -> Unit,
    private val onNavigated: (WebView, String) -> Unit,
    private val onRenderGone: () -> Unit,
) : WebViewClient() {

    /** Host of the page this tab is showing; read from the WebView worker threads, so volatile. */
    @Volatile private var pageHost: String? = null

    // Runs on a WebView worker thread for every request.
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        assetLoader.shouldInterceptRequest(request.url)?.let { return it } // built-in home page
        if (request.isForMainFrame) return null
        if (blockingEnabled(pageHost) && blocker.shouldBlock(request.url, pageHost)) {
            onBlocked(request.url.host ?: "")
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        if (Media.isMedia(request)) onMedia(request.url.toString(), HashMap(request.requestHeaders ?: emptyMap()))
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return when (uri.scheme?.lowercase()) {
            "http" -> {
                if (httpsOnly() && request.isForMainFrame) {
                    view?.loadUrl(uri.buildUpon().scheme("https").build().toString())
                    true
                } else {
                    false
                }
            }
            "https" -> {
                val to = if (request.isForMainFrame) rewrite(uri.toString()) else null
                if (to != null) { view?.loadUrl(to); true } else false
            }
            "about", "data", "blob" -> false
            "mailto", "tel", "sms" -> {
                try {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // No app for it; ignore.
                }
                true
            }
            // intent:, market:, vnd.youtube: and similar would hand the page to another app, so ignore them.
            else -> true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (view != null && url != null) {
            pageHost = Uri.parse(url).host
            onStarted(view, url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) onNavigated(view, url)
    }

    // Also fires for in-page (history API) navigation, which is how YouTube moves between videos.
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (view != null && url != null) {
            pageHost = Uri.parse(url).host
            onNavigated(view, url)
        }
    }

    // Google Safe Browsing: never let the user continue to a flagged page.
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
        callback?.backToSafety(true)
        view?.context?.let { Toast.makeText(it, "Blocked a dangerous site", Toast.LENGTH_LONG).show() }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onRenderGone()
        return true
    }
}
