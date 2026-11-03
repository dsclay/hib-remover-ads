package com.dex.adfree

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(val url: String, val title: String, val time: Long)

/** Bookmarks and browsing history, saved on the device. Private tabs never write history. */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    val bookmarks = mutableStateListOf<LibraryEntry>()
    val history = mutableStateListOf<LibraryEntry>()

    init {
        load(KEY_BOOKMARKS, bookmarks)
        load(KEY_HISTORY, history)
    }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(url: String, title: String) {
        val existing = bookmarks.firstOrNull { it.url == url }
        if (existing != null) bookmarks.remove(existing)
        else bookmarks.add(0, LibraryEntry(url, title.ifBlank { url }, System.currentTimeMillis()))
        save(KEY_BOOKMARKS, bookmarks)
    }

    fun removeBookmark(e: LibraryEntry) {
        bookmarks.remove(e)
        save(KEY_BOOKMARKS, bookmarks)
    }

    fun removeHistory(e: LibraryEntry) {
        history.remove(e)
        save(KEY_HISTORY, history)
    }

    fun clearHistory() {
        history.clear()
        save(KEY_HISTORY, history)
    }

    /** Called when a normal tab shows [url]. Repeats of the same page in a row are merged. */
    fun visit(url: String) {
        if (!url.startsWith("http") || Urls.isHome(url)) return
        if (history.firstOrNull()?.url == url) return
        history.add(0, LibraryEntry(url, url, System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        save(KEY_HISTORY, history)
    }

    /** Fills in the page title once the page reports it. */
    fun titled(url: String, title: String) {
        val top = history.firstOrNull() ?: return
        if (top.url != url || title.isBlank() || top.title == title) return
        history[0] = top.copy(title = title)
        save(KEY_HISTORY, history)
    }

    private fun load(key: String, into: MutableList<LibraryEntry>) {
        runCatching {
            val arr = JSONArray(prefs.getString(key, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                into.add(LibraryEntry(o.getString("url"), o.optString("title"), o.optLong("time")))
            }
        }
    }

    private fun save(key: String, list: List<LibraryEntry>) {
        val arr = JSONArray()
        for (e in list) arr.put(JSONObject().put("url", e.url).put("title", e.title).put("time", e.time))
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private companion object {
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 500
    }
}
