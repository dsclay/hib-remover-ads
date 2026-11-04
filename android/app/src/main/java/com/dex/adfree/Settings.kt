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

    /** Off by default: upgrading every http link breaks sites that have no https version. */
    var httpsOnly by mutableStateOf(prefs.getBoolean(KEY_HTTPS, false))
        private set

    /** Parallel-connection downloads. */
    var boostDownloads by mutableStateOf(prefs.getBoolean(KEY_BOOST, true))
        private set

    fun updateBoostDownloads(value: Boolean) {
        boostDownloads = value
        prefs.edit().putBoolean(KEY_BOOST, value).apply()
    }

    /** Cosmetic ad-box hiding (a large style sheet on every page; can slow big pages). Applies to new tabs. */
    var hideAdBoxes by mutableStateOf(prefs.getBoolean(KEY_BOXES, true))
        private set

    fun updateHideAdBoxes(value: Boolean) {
        hideAdBoxes = value
        prefs.edit().putBoolean(KEY_BOXES, value).apply()
    }

    /** Opens reddit.com pages as old.reddit.com, which is much lighter. */
    var lightReddit by mutableStateOf(prefs.getBoolean(KEY_REDDIT, false))
        private set

    fun updateLightReddit(value: Boolean) {
        lightReddit = value
        prefs.edit().putBoolean(KEY_REDDIT, value).apply()
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

    fun updateHttpsOnly(value: Boolean) {
        httpsOnly = value
        prefs.edit().putBoolean(KEY_HTTPS, value).apply()
    }

    private companion object {
        const val KEY_BLOCK = "blockAds"
        const val KEY_HTTPS = "httpsOnly"
        const val KEY_BOOST = "boostDownloads"
        const val KEY_BOXES = "hideAdBoxes"
        const val KEY_REDDIT = "lightReddit"
        const val KEY_FAST = "fasterLoading"
        const val KEY_PAUSED = "pausedHosts"
    }
}
