package com.dex.adfree

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    private lateinit var state: BrowserState
    private lateinit var assetLoader: WebViewAssetLoader
    private val mainHandler = Handler(Looper.getMainLooper())
    private val blockedTotal = AtomicInteger()
    private lateinit var blocker: AdBlocker
    private lateinit var scripts: WebScripts
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var documentStartScripts = false
    private var privateSupported = false
    private var nextTabId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = BrowserState(Settings(this))
        // Serves the built-in home page from assets over https, never file://.
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        blocker = AdBlocker(this)
        scripts = WebScripts(this)
        documentStartScripts = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        privateSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        deleteLeftoverPrivateProfiles()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    state.customView != null -> exitFullscreen()
                    state.showSettings -> state.showSettings = false
                    state.showTabs -> state.showTabs = false
                    state.current?.webView?.canGoBack() == true -> state.current?.webView?.goBack()
                    else -> finish()
                }
            }
        })

        addTab(false, Urls.fromIntent(intent) ?: Urls.HOME)

        setContent {
            AdFreeTheme {
                BrowserScreen(
                    state = state,
                    onGo = { load(it) },
                    onBack = { state.current?.webView?.takeIf { it.canGoBack() }?.goBack() },
                    onForward = { state.current?.webView?.takeIf { it.canGoForward() }?.goForward() },
                    onReload = { state.current?.webView?.reload() },
                    onSelectTab = {
                        select(it)
                        state.showTabs = false
                    },
                    onCloseTab = { closeTab(it) },
                    onNewTab = { addTab(it) },
                    onDownloads = { openDownloads() },
                    onClearData = { clearData() },
                    onConfirmDownload = { startDownload(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Urls.fromIntent(intent)?.let { addTab(false, it) }
    }

    override fun onDestroy() {
        state.tabs.toList().forEach { destroyTab(it) }
        super.onDestroy()
    }

    // ---- Tabs ----

    private fun load(input: String) {
        state.current?.webView?.loadUrl(prepare(input))
    }

    private fun addTab(isPrivate: Boolean, input: String = Urls.HOME): Tab? {
        if (isPrivate && !privateSupported) {
            Toast.makeText(this, "Private tabs need a newer Android System WebView", Toast.LENGTH_LONG).show()
            return null
        }
        val tab = createTab(isPrivate)
        state.tabs.add(tab)
        select(tab)
        state.showTabs = false
        tab.webView.loadUrl(prepare(input))
        return tab
    }

    private fun createTab(isPrivate: Boolean): Tab {
        val id = nextTabId++
        val wv = WebView(this)
        // Private tabs get their own WebView profile: separate cookies, storage and cache.
        // Must be set before the WebView is used for anything else.
        val profile = if (isPrivate) "$PRIVATE_PREFIX$id" else null
        if (profile != null) WebViewCompat.setProfile(wv, profile)
        val tab = Tab(id, isPrivate, profile, wv)
        configure(wv, tab)
        return tab
    }

    private fun select(tab: Tab) {
        val old = state.current
        if (old != null && old !== tab) {
            // No background audio: silence and pause the tab we leave.
            runCatching {
                old.webView.evaluateJavascript(PAUSE_MEDIA, null)
                old.webView.onPause()
            }
        }
        state.currentId = tab.id
        tab.webView.onResume()
    }

    private fun closeTab(tab: Tab) {
        val index = state.tabs.indexOf(tab)
        if (index < 0) return
        state.tabs.removeAt(index)
        if (state.tabs.isEmpty()) {
            addTab(false)
        } else if (state.currentId == tab.id) {
            select(state.tabs[minOf(index, state.tabs.lastIndex)])
        }
        // Give Compose time to detach the WebView before it is destroyed.
        mainHandler.postDelayed({ destroyTab(tab) }, 400)
    }

    /** Used when a tab's renderer process dies: same page in a fresh WebView. */
    private fun replaceTab(tab: Tab) {
        val wasCurrent = state.currentId == tab.id
        val fresh = createTab(tab.isPrivate)
        state.tabs.add(state.tabs.indexOf(tab) + 1, fresh)
        if (wasCurrent) select(fresh)
        fresh.webView.loadUrl(tab.url.ifBlank { Urls.HOME })
        closeTab(tab)
    }

    private fun destroyTab(tab: Tab) {
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.destroy()
        tab.profile?.let { name -> runCatching { ProfileStore.getInstance().deleteProfile(name) } }
    }

    /** Private profiles left behind by a crash or kill are wiped at startup. */
    private fun deleteLeftoverPrivateProfiles() {
        if (!privateSupported) return
        runCatching {
            val store = ProfileStore.getInstance()
            store.allProfileNames.filter { it.startsWith(PRIVATE_PREFIX) }.forEach { store.deleteProfile(it) }
        }
    }

    // ---- WebView setup ----

    private fun cookieManagerFor(tab: Tab): CookieManager =
        tab.profile?.let { ProfileStore.getInstance().getProfile(it)?.cookieManager } ?: CookieManager.getInstance()

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(wv: WebView, tab: Tab) {
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
            // Strict baseline: pages cannot read local files or content providers; no mixed content.
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Present as plain mobile Chrome; the "; wv" marker makes some sites serve app-style pages.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        cookieManagerFor(tab).setAcceptThirdPartyCookies(wv, true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(wv.settings, true)
        }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Same injection points as the extension: cosmetic CSS everywhere, YouTube scripts on YouTube,
        // all before the page's own scripts run.
        if (documentStartScripts) {
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.cosmetic, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytMain, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytHush, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytFeed, YOUTUBE_ORIGINS)
            if (state.settings.fasterLoading) WebViewCompat.addDocumentStartJavaScript(wv, scripts.perf, setOf("*"))
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            state.pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType, contentLength, tab)
        }

        wv.webViewClient = AdFreeWebViewClient(
            blocker = blocker,
            assetLoader = assetLoader,
            blockingEnabled = { state.settings.blockAds },
            httpsOnly = { state.settings.httpsOnly },
            onBlocked = {
                val n = blockedTotal.incrementAndGet()
                mainHandler.post { state.blocked = n }
            },
            onStarted = { view, url ->
                if (!documentStartScripts) injectFallback(view, url)
                sync(tab, url)
            },
            onNavigated = { _, url -> sync(tab, url) },
            onRenderGone = { replaceTab(tab) },
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title.orEmpty()
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
    }

    private fun sync(tab: Tab, url: String) {
        tab.url = url
        tab.canGoBack = tab.webView.canGoBack()
        tab.canGoForward = tab.webView.canGoForward()
    }

    /** Older WebView builds without document-start scripts: inject once the load begins. */
    private fun injectFallback(view: WebView, url: String) {
        view.evaluateJavascript(scripts.cosmetic, null)
        val host = Uri.parse(url).host.orEmpty()
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtube-nocookie.com")) {
            view.evaluateJavascript(scripts.ytMain, null)
            view.evaluateJavascript(scripts.ytHush, null)
            view.evaluateJavascript(scripts.ytFeed, null)
        }
    }

    /** Address-bar input to a loadable URL, applying HTTPS-only mode when it is on. */
    private fun prepare(input: String): String {
        val url = Urls.normalize(input)
        return if (state.settings.httpsOnly) Urls.upgrade(url) else url
    }

    private fun clearData() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        state.tabs.filter { !it.isPrivate }.forEach { it.webView.clearCache(true) }
        Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
    }

    // ---- Downloads ----

    private fun startDownload(d: PendingDownload) {
        try {
            val request = DownloadManager.Request(Uri.parse(d.url))
                .setTitle(d.fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, d.fileName)
                .addRequestHeader("User-Agent", d.userAgent)
            d.mimeType?.let { request.setMimeType(it) }
            cookieManagerFor(d.tab).getCookie(d.url)?.let { request.addRequestHeader("Cookie", it) }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Downloading ${d.fileName}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // blob:/data: links and missing storage permission (Android 8-9) end up here.
            Toast.makeText(this, "Can't download this file", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDownloads() {
        runCatching {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- Fullscreen video ----

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
        const val PRIVATE_PREFIX = "private_"
        const val PAUSE_MEDIA =
            "document.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}})"
        val YOUTUBE_ORIGINS = setOf(
            "https://youtube.com",
            "https://*.youtube.com",
            "https://*.youtube-nocookie.com",
        )
    }
}
