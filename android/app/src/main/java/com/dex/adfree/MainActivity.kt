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
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    private lateinit var state: BrowserState
    private lateinit var assetLoader: WebViewAssetLoader
    private val mainHandler = Handler(Looper.getMainLooper())
    private val blockedTotal = AtomicInteger()
    private val blockedHosts = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val counterPending = AtomicBoolean(false)
    private lateinit var blocker: AdBlocker
    private lateinit var scripts: WebScripts
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var documentStartScripts = false
    private var privateSupported = false
    private var nextTabId = 0
    private lateinit var downloader: Downloader
    private lateinit var library: Library

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
        downloader = Downloader(this, state.settings)
        library = Library(this)
        PlaybackBridge.evaluate = { code, callback ->
            runOnUiThread {
                val tab = state.tabs.firstOrNull { Uri.parse(it.url).host?.contains("youtube.com") == true } ?: state.current
                tab?.webView?.evaluateJavascript(code) { callback(it ?: "null") }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    state.customView != null -> exitFullscreen()
                    state.showSettings -> state.showSettings = false
                    state.showLibrary -> state.showLibrary = false
                    state.showDownloads -> state.showDownloads = false
                    state.showTabs -> state.showTabs = false
                    state.current?.webView?.canGoBack() == true -> state.current?.webView?.goBack()
                    else -> finish()
                }
            }
        })

        val launchUrl = Urls.fromIntent(intent)
        val restored = restoreTabs()
        if (launchUrl != null) addTab(false, launchUrl) else if (!restored) addTab(false, Urls.HOME)

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
                    onDownloads = { state.showDownloads = true },
                    downloader = downloader,
                    library = library,
                    onClearData = { clearData() },
                    onFindVideos = { findVideos() },
                    onShowBlocked = { showBlocked() },
                    onResetBlocked = { resetBlocked() },
                    onPickMedia = { pickMedia(it) },
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

    override fun onPause() {
        super.onPause()
        saveTabs()
        state.tabs.forEach { runCatching { it.webView.evaluateJavascript("window.__adfreeBg=true", null) } }
        val music = (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).isMusicActive
        if (music || Downloader.ACTIVE.get() > 0) startKeepAlive(music)
    }

    override fun onStop() {
        super.onStop()
        state.tabs.forEach { runCatching { it.webView.resumeTimers() } } // keep page timers (and the player) running
    }

    override fun onResume() {
        super.onResume()
        state.tabs.forEach { runCatching { it.webView.evaluateJavascript("window.__adfreeBg=false", null) } }
    }

    private fun startKeepAlive(music: Boolean) {
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, PlaybackService::class.java).putExtra("music", music),
            )
        }
    }

    // ---- Blocked-requests popup ----

    private fun showBlocked() {
        val hosts = blockedHosts.entries.map { it.key to it.value.get() }.filter { it.first.isNotEmpty() }
        val cats = hosts.groupBy({ categoryOf(it.first) }, { it.second }).map { (k, v) -> k to v.sum() }.sortedByDescending { it.second }
        state.blockedReport = BlockedReport(cats, hosts.sortedByDescending { it.second }.take(12))
    }

    private fun resetBlocked() {
        blockedHosts.clear()
        blockedTotal.set(0)
        state.blocked = 0
        state.blockedReport = null
    }

    override fun onDestroy() {
        PlaybackBridge.evaluate = null
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
        val wv = BgWebView(this)
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
        if (old != null && old !== tab && Uri.parse(old.url).host?.contains("youtube.com") != true) {
            // Pause the tab we leave (YouTube tabs keep playing so music can continue).
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
        // Do not announce this app to websites with an X-Requested-With header (Google refuses sign-in when it sees it).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
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
            if (state.settings.hideAdBoxes) WebViewCompat.addDocumentStartJavaScript(wv, scripts.cosmetic, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytMain, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytHush, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytFeed, YOUTUBE_ORIGINS)
            WebViewCompat.addDocumentStartJavaScript(wv, scripts.ytBackground, YOUTUBE_ORIGINS)
            if (state.settings.fasterLoading) WebViewCompat.addDocumentStartJavaScript(wv, scripts.perf, setOf("*"))
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            state.pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType, contentLength, tab)
        }

        wv.webViewClient = AdFreeWebViewClient(
            blocker = blocker,
            assetLoader = assetLoader,
            blockingEnabled = { host -> state.settings.blockAds && !state.settings.isPaused(host) },
            httpsOnly = { state.settings.httpsOnly },
            rewrite = { u -> redditLite(u).takeIf { it != u } },
            onBlocked = { blockedHost ->
                blockedHosts.getOrPut(blockedHost) { AtomicInteger() }.incrementAndGet()
                // Pages block hundreds of requests; update the counter at most every 300 ms.
                blockedTotal.incrementAndGet()
                if (counterPending.compareAndSet(false, true)) {
                    mainHandler.postDelayed({
                        counterPending.set(false)
                        state.blocked = blockedTotal.get()
                    }, 300)
                }
            },
            onMedia = { url, hdrs ->
                if (tab.media.size < 40 && url !in tab.media) {
                    tab.media.add(url)
                    tab.mediaHeaders[url] = hdrs
                }
            },
            onStarted = { view, url ->
                tab.media.clear()
                tab.mediaHeaders.clear()
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
                if (!tab.isPrivate) view?.url?.let { library.titled(it, title.orEmpty()) }
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
        if (!tab.isPrivate) library.visit(url)
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

    /** reddit.com to old.reddit.com when "Lighter Reddit" is on; any other URL is unchanged. */
    private fun redditLite(url: String): String {
        if (!state.settings.lightReddit) return url
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: return url
        if (host != "reddit.com" && host != "www.reddit.com" && host != "m.reddit.com" && host != "new.reddit.com") return url
        return uri.buildUpon().authority("old.reddit.com").build().toString()
    }

    /** Address-bar input to a loadable URL, applying HTTPS-only mode when it is on. */
    private fun prepare(input: String): String {
        val url = redditLite(Urls.normalize(input))
        return if (state.settings.httpsOnly) Urls.upgrade(url) else url
    }

    // ---- Keep open tabs between launches (private tabs are never saved) ----

    private fun saveTabs() {
        val urls = org.json.JSONArray()
        var current = 0
        state.tabs.filter { !it.isPrivate && it.url.isNotBlank() }.forEachIndexed { i, t ->
            urls.put(t.url)
            if (t.id == state.currentId) current = i
        }
        getSharedPreferences("session", MODE_PRIVATE).edit().putString("tabs", urls.toString()).putInt("current", current).apply()
    }

    private fun restoreTabs(): Boolean {
        val p = getSharedPreferences("session", MODE_PRIVATE)
        val arr = runCatching { org.json.JSONArray(p.getString("tabs", "[]")) }.getOrNull() ?: return false
        if (arr.length() == 0) return false
        val current = p.getInt("current", 0)
        var selected: Tab? = null
        for (i in 0 until minOf(arr.length(), MAX_RESTORED)) {
            val t = addTab(false, arr.getString(i))
            if (i == current) selected = t
        }
        selected?.let { select(it) }
        return state.tabs.isNotEmpty()
    }

    private fun clearData() {
        library.clearHistory()
        getSharedPreferences("session", MODE_PRIVATE).edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        state.tabs.filter { !it.isPrivate }.forEach { it.webView.clearCache(true) }
        Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
    }

    // ---- Video capture ----

    /** Lists videos the page has loaded (seen on the network) plus any in the page itself. */
    private fun findVideos() {
        val tab = state.current ?: return
        tab.webView.evaluateJavascript(FIND_MEDIA_JS) { raw ->
            val urls = LinkedHashSet<String>(tab.media)
            runCatching {
                val inner = JSONTokener(raw).nextValue() as? String
                if (inner != null) {
                    val arr = JSONArray(inner)
                    for (i in 0 until arr.length()) urls.add(arr.getString(i))
                }
            }
            state.mediaChoices = urls.take(20).map { MediaChoice(it, Media.fileName(tab.title, it)) }
        }
    }

    private fun pickMedia(choice: MediaChoice) {
        val tab = state.current ?: return
        state.mediaChoices = null
        state.pendingDownload = PendingDownload(
            url = choice.url,
            userAgent = tab.webView.settings.userAgentString,
            contentDisposition = null,
            mimeType = null,
            length = -1,
            tab = tab,
            suggestedName = choice.label,
            referer = tab.url,
            headers = tab.mediaHeaders[choice.url] ?: emptyMap(),
        )
    }

    // ---- Downloads ----

    private fun startDownload(d: PendingDownload) {
        val scheme = Uri.parse(d.url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(this, "Can't download this file", Toast.LENGTH_LONG).show()
            return
        }
        val headers = HashMap<String, String>()
        headers["User-Agent"] = d.userAgent
        cookieManagerFor(d.tab).getCookie(d.url)?.let { headers["Cookie"] = it }
        d.referer?.let { headers["Referer"] = it }
        // Replay what the page itself sent for this file (the real Referer/Origin often differ from the page URL).
        for ((k, v) in d.headers) if (k.lowercase() in REPLAY_HEADERS) headers[k.lowercase().replaceFirstChar { c -> c.uppercase() }] = v
        downloader.start(d.url, d.fileName, headers, persist = !d.tab.isPrivate)
        startKeepAlive(false)
        Toast.makeText(this, "Downloading ${d.fileName}", Toast.LENGTH_SHORT).show()
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
        val REPLAY_HEADERS = setOf("referer", "origin", "user-agent", "accept-language")
        const val FIND_MEDIA_JS =
            "(function(){var o=[];document.querySelectorAll('video,video source,audio,audio source').forEach(function(e){" +
                "var s=e.currentSrc||e.src;if(s&&/^https?:/.test(s))o.push(s)});return JSON.stringify(o)})()"
        private val POPUPS = Regex("popunder|popads|propeller|clickadu|exoclick|juicyads|trafficjunky|adcash|onclick|clickaine|redirect")
        private val ADS = Regex("(^|[.\\-])ads?([.\\-0-9]|$)|adserv|adsystem|doubleclick|adnxs|adform|pagead|banner|syndication|sponsor|promo|taboola|outbrain|criteo|pubmatic|rubicon|openx|smartad|adx|advert|monetiz|bidder|prebid")
        private val TRACKERS = Regex("analytic|metric|stat(s|istic)|telemetry|pixel|beacon|track|segment|mixpanel|amplitude|hotjar|clarity|sentry|crashlytics|collect|(^|[.\\-])logs?[.\\-]|insight")

        /** Rough label for a blocked host, from its name. */
        fun categoryOf(host: String): String {
            val h = host.lowercase()
            return when {
                POPUPS.containsMatchIn(h) -> "Popups & redirects"
                TRACKERS.containsMatchIn(h) -> "Trackers & analytics"
                ADS.containsMatchIn(h) -> "Advertising"
                else -> "Other ad & tracker requests"
            }
        }

        const val MAX_RESTORED = 10
        const val PAUSE_MEDIA =
            "document.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}})"
        val YOUTUBE_ORIGINS = setOf(
            "https://youtube.com",
            "https://*.youtube.com",
            "https://*.youtube-nocookie.com",
        )
    }
}
