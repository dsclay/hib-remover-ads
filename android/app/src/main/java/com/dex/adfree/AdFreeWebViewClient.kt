package com.dex.adfree

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class AdFreeWebViewClient(
    private val blocker: AdBlocker,
    private val onBlocked: () -> Unit,
    private val onStarted: (WebView, String) -> Unit,
    private val onNavigated: (WebView, String) -> Unit,
    private val onRenderGone: () -> Unit,
) : WebViewClient() {

    // Runs on a WebView worker thread for every request.
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request == null || request.isForMainFrame) return null
        if (!blocker.shouldBlock(request.url)) return null
        onBlocked()
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return when (uri.scheme?.lowercase()) {
            "http", "https", "about", "data", "blob" -> false
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
        if (view != null && url != null) onStarted(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) onNavigated(view, url)
    }

    // Also fires for in-page (history API) navigation, which is how YouTube moves between videos.
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (view != null && url != null) onNavigated(view, url)
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onRenderGone()
        return true
    }
}
