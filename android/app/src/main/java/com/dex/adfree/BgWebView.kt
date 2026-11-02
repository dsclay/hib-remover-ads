package com.dex.adfree

import android.content.Context
import android.view.View
import android.webkit.WebView

/** A WebView that keeps reporting "visible" so audio keeps playing when the app is in the background. */
class BgWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
