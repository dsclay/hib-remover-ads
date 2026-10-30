package com.dex.adfree

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    private val state = BrowserState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val blockedTotal = AtomicInteger()
    private lateinit var scripts: WebScripts
    private lateinit var webView: WebView
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var documentStartScripts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        scripts = WebScripts(this)
        webView = createWebView(AdBlocker(this))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    state.customView != null -> exitFullscreen()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        setContent {
            AdFreeTheme {
                BrowserScreen(
                    state = state,
                    webView = webView,
                    onGo = { load(it) },
                    onBack = { if (webView.canGoBack()) webView.goBack() },
                    onForward = { if (webView.canGoForward()) webView.goForward() },
                    onReload = { webView.reload() },
                    onHome = { load(Urls.HOME) },
                )
            }
        }

        load(Urls.fromIntent(intent) ?: Urls.HOME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Urls.fromIntent(intent)?.let { load(it) }
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun load(input: String) {
        webView.loadUrl(Urls.normalize(input))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(blocker: AdBlocker): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Present as plain mobile Chrome; the "; wv" marker makes some sites serve app-style pages.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Same injection points as the extension: cosmetic CSS everywhere, YouTube scripts on YouTube,
        // all before the page's own scripts run.
        documentStartScripts = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStartScripts) {
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.cosmetic, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytMain, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytHush, YOUTUBE_ORIGINS)
        }

        wv.webViewClient = AdFreeWebViewClient(
            blocker = blocker,
            onBlocked = {
                val n = blockedTotal.incrementAndGet()
                mainHandler.post { state.blocked = n }
            },
            onStarted = { view, url ->
                if (!documentStartScripts) injectFallback(view, url)
                syncNavigation(view, url)
            },
            onNavigated = { view, url -> syncNavigation(view, url) },
            onRenderGone = { recreate() },
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                state.progress = newProgress
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            // Some devices crash when the page asks for a video poster and gets null.
            override fun getDefaultVideoPoster(): Bitmap? =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        return wv
    }

    private fun syncNavigation(view: WebView, url: String) {
        state.url = url
        state.canGoBack = view.canGoBack()
        state.canGoForward = view.canGoForward()
    }

    /** Older WebView builds without document-start scripts: inject once the load begins. */
    private fun injectFallback(view: WebView, url: String) {
        view.evaluateJavascript(scripts.cosmetic, null)
        val host = Uri.parse(url).host.orEmpty()
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtube-nocookie.com")) {
            view.evaluateJavascript(scripts.ytMain, null)
            view.evaluateJavascript(scripts.ytHush, null)
        }
    }

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = callback
        state.customView = view
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitFullscreen() {
        if (state.customView == null && customViewCallback == null) return
        state.customView = null
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        val YOUTUBE_ORIGINS = setOf(
            "https://youtube.com",
            "https://*.youtube.com",
            "https://*.youtube-nocookie.com",
        )
    }
}
