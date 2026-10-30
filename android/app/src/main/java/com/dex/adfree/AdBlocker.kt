package com.dex.adfree

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.util.Locale

/**
 * Small request blocker driven by the extension's own src/rules/ads.json (Chrome
 * declarativeNetRequest format). Supported:
 *  - "block" rules with requestDomains (matches the domain and all its subdomains)
 *  - "block" rules with a urlFilter of the form "||host/path" or a plain substring
 * Redirect rules and any filter using wildcards are skipped.
 */
class AdBlocker(context: Context) {

    private val domains = HashSet<String>()
    private val hostPaths = ArrayList<Pair<String, String>>() // host, path prefix
    private val substrings = ArrayList<String>()

    init {
        val text = context.assets.open("web/ads.json").bufferedReader().use { it.readText() }
        val rules = JSONArray(text)
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.getJSONObject("action").optString("type") != "block") continue
            val condition = rule.getJSONObject("condition")
            val requestDomains = condition.optJSONArray("requestDomains")
            val urlFilter = condition.optString("urlFilter").lowercase(Locale.ROOT)

            when {
                requestDomains != null && urlFilter.isEmpty() ->
                    for (j in 0 until requestDomains.length()) {
                        domains += requestDomains.getString(j).lowercase(Locale.ROOT)
                    }
                requestDomains == null && urlFilter.isNotEmpty() -> addUrlFilter(urlFilter)
                // Rules combining both conditions are skipped rather than over-blocking.
            }
        }
    }

    private fun addUrlFilter(filter: String) {
        val anchored = filter.startsWith("||")
        val body = filter.removePrefix("||")
        if (body.any { it == '*' || it == '^' || it == '|' }) return // unsupported syntax
        if (anchored) {
            val slash = body.indexOf('/')
            if (slash < 0) hostPaths += body to "" else hostPaths += body.substring(0, slash) to body.substring(slash)
        } else {
            substrings += body
        }
    }

    /** True when a sub-resource request to [url] should be dropped. Main-frame loads are never blocked. */
    fun shouldBlock(url: Uri): Boolean {
        val host = url.host?.lowercase(Locale.ROOT) ?: return false

        // 1. Ad-network domain list, walking up through parent domains.
        var h = host
        while (true) {
            if (h in NEVER_BLOCK_DOMAINS) break
            if (h in domains) return true
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }

        // 2. "||host/path" rules, e.g. youtube.com/pagead/.
        if (hostPaths.isNotEmpty()) {
            val pathAndQuery = ((url.encodedPath ?: "/") + (url.encodedQuery?.let { "?$it" } ?: "")).lowercase(Locale.ROOT)
            for ((hostRule, pathPrefix) in hostPaths) {
                if ((host == hostRule || host.endsWith(".$hostRule")) && pathAndQuery.startsWith(pathPrefix)) return true
            }
        }

        // 3. Plain substring rules, e.g. /prebid.js.
        if (substrings.isNotEmpty()) {
            val full = url.toString().lowercase(Locale.ROOT)
            for (s in substrings) if (full.contains(s)) return true
        }
        return false
    }

    private companion object {
        // Video and site infrastructure that the domain list must never touch.
        val NEVER_BLOCK_DOMAINS = setOf(
            "youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com",
            "youtube-nocookie.com", "gstatic.com",
        )
    }
}
