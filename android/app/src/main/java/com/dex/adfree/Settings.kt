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

    /** Lazy-load images and frames. Read when a tab is created, so it applies to new tabs. */
    var fasterLoading by mutableStateOf(prefs.getBoolean(KEY_FAST, true))
        private set

    fun updateFasterLoading(value: Boolean) {
        fasterLoading = value
        prefs.edit().putBoolean(KEY_FAST, value).apply()
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
        const val KEY_FAST = "fasterLoading"
    }
}
