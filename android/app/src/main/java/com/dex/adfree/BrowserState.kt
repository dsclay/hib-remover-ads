package com.dex.adfree

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI state shared between the activity (which owns the WebView) and the Compose screen. */
class BrowserState {
    var url by mutableStateOf(Urls.HOME)
    var progress by mutableIntStateOf(0)
    var blocked by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** Non-null while a page video is in fullscreen. */
    var customView by mutableStateOf<View?>(null)
}
