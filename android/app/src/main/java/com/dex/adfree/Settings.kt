package com.dex.adfree

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User settings, persisted in SharedPreferences and observable from Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var blockAds by mutableStateOf(prefs.getBoolean(KEY_BLOCK, true))
        private set

    /** Always on: http links are opened as https. */
    val httpsOnly = true

    /** Parallel-connection downloads. */
    var boostDownloads by mutableStateOf(prefs.getBoolean(KEY_BOOST, true))
        private set

    fun updateBoostDownloads(value: Boolean) {
        boostDownloads = value
        prefs.edit().putBoolean(KEY_BOOST, value).apply()
    }

    /** Cosmetic ad-box hiding: always on. */
    val hideAdBoxes = true

    /** Skips sponsor segments in YouTube videos (SponsorBlock data). */
    var sponsorBlock by mutableStateOf(prefs.getBoolean(KEY_SB, true))
        private set

    fun updateSponsorBlock(value: Boolean) {
        sponsorBlock = value
        prefs.edit().putBoolean(KEY_SB, value).apply()
    }

    /** Remembers the YouTube playback speed and shows a speed button on videos. */
    var keepSpeed by mutableStateOf(prefs.getBoolean(KEY_SPEED, true))
        private set

    fun updateKeepSpeed(value: Boolean) {
        keepSpeed = value
        prefs.edit().putBoolean(KEY_SPEED, value).apply()
    }

    /** Slides the bottom bar away a few seconds after the last touch. */
    var autoHideBar by mutableStateOf(prefs.getBoolean(KEY_HIDEBAR, true))
        private set

    fun updateAutoHideBar(value: Boolean) {
        autoHideBar = value
        prefs.edit().putBoolean(KEY_HIDEBAR, value).apply()
    }

    /** Search engine for text typed in the address bar. */
    var searchEngine by mutableStateOf(prefs.getString(KEY_ENGINE, "DuckDuckGo") ?: "DuckDuckGo")
        private set

    fun updateSearchEngine(value: String) {
        searchEngine = value
        prefs.edit().putString(KEY_ENGINE, value).apply()
    }

    /** Data saver: do not load images (new tabs). */
    var blockImages by mutableStateOf(prefs.getBoolean(KEY_NOIMG, false))
        private set

    fun updateBlockImages(value: Boolean) {
        blockImages = value
        prefs.edit().putBoolean(KEY_NOIMG, value).apply()
    }

    /** Data saver: do not load web fonts. */
    var blockFonts by mutableStateOf(prefs.getBoolean(KEY_NOFONT, false))
        private set

    fun updateBlockFonts(value: Boolean) {
        blockFonts = value
        prefs.edit().putBoolean(KEY_NOFONT, value).apply()
    }

    /** Highest YouTube quality to use ("Auto" = YouTube decides). */
    var ytQuality by mutableStateOf(prefs.getString(KEY_YTQ, "Auto") ?: "Auto")
        private set

    fun updateYtQuality(value: String) {
        ytQuality = value
        prefs.edit().putString(KEY_YTQ, value).apply()
    }

    /** The old.reddit.com option was removed: Reddit now works normally. */
    val lightReddit = false

    /** How many parallel connections a boosted download uses. */
    var connections by mutableStateOf(prefs.getInt(KEY_CONN, 6))
        private set

    fun updateConnections(value: Int) {
        connections = value
        prefs.edit().putInt(KEY_CONN, value).apply()
    }

    /** Lazy-load images and frames. Read when a tab is created, so it applies to new tabs. */
    var fasterLoading by mutableStateOf(prefs.getBoolean(KEY_FAST, true))
        private set

    fun updateFasterLoading(value: Boolean) {
        fasterLoading = value
        prefs.edit().putBoolean(KEY_FAST, value).apply()
    }

    /** Hosts where blocking is paused (a site that breaks with it on). */
    private var paused: Set<String> by mutableStateOf(prefs.getStringSet(KEY_PAUSED, emptySet())?.toSet() ?: emptySet())

    fun isPaused(host: String?): Boolean = host != null && paused.any { host == it || host.endsWith(".$it") }

    fun setPaused(host: String, value: Boolean) {
        paused = if (value) paused + host else paused - host
        prefs.edit().putStringSet(KEY_PAUSED, paused.toSet()).apply()
    }

    fun updateBlockAds(value: Boolean) {
        blockAds = value
        prefs.edit().putBoolean(KEY_BLOCK, value).apply()
    }

    private companion object {
        const val KEY_BLOCK = "blockAds"
        const val KEY_HTTPS = "httpsOnly"
        const val KEY_BOOST = "boostDownloads"
        const val KEY_BOXES = "hideAdBoxes"
        const val KEY_SB = "sponsorBlock"
        const val KEY_CONN = "connections"
        const val KEY_NOIMG = "blockImages"
        const val KEY_NOFONT = "blockFonts"
        const val KEY_YTQ = "ytQuality"
        const val KEY_ENGINE = "searchEngine"
        const val KEY_HIDEBAR = "autoHideBar"
        const val KEY_SPEED = "keepSpeed"
        const val KEY_REDDIT = "lightReddit"
        const val KEY_FAST = "fasterLoading"
        const val KEY_PAUSED = "pausedHosts"
    }
}
