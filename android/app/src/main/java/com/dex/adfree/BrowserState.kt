package com.dex.adfree

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI state shared between the activity (which owns the WebViews) and the Compose screen. */
class BrowserState(val settings: Settings) {
    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableIntStateOf(-1)
    var showTabs by mutableStateOf(false)
    var showSettings by mutableStateOf(false)

    /** What has been blocked so far; null while the popup is closed. */
    var blockedReport by mutableStateOf<BlockedReport?>(null)
    var showDownloads by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)

    /** Non-null while a page video is in fullscreen. */
    var customView by mutableStateOf<View?>(null)

    var pendingDownload by mutableStateOf<PendingDownload?>(null)

    /** Videos found on the current page; null while the picker is closed. */
    var mediaChoices by mutableStateOf<List<MediaChoice>?>(null)

    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }
}

class BlockedReport(val categories: List<Pair<String, Int>>, val hosts: List<Pair<String, Int>>)
