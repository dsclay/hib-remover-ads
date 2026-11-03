package com.dex.adfree

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.util.Locale

/**
 * Request blocker. Two layers:
 *  1. ads.json (small, loaded immediately): ad-network domains and a few URL rules.
 *  2. rules.txt (large, loaded on a background thread): the compiled block.json / allow.json,
 *     see android/tools/compile_rules.py. Until it finishes loading, only layer 1 is active.
 */
class AdBlocker(context: Context) {

    private val domains = HashSet<String>()
    private val hostPaths = ArrayList<Pair<String, String>>() // host, path prefix
    private val substrings = ArrayList<String>()
    @Volatile private var extended: RuleSet? = null

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
            }
        }
        val appContext = context.applicationContext
        Thread({
            extended = try {
                RuleSet.load(appContext)
            } catch (_: Exception) {
                null // Missing or broken rules.txt: keep running with layer 1 only.
            }
        }, "rules-loader").start()
    }

    private fun addUrlFilter(filter: String) {
        val anchored = filter.startsWith("||")
        val body = filter.removePrefix("||")
        if (body.any { it == '*' || it == '^' || it == '|' }) return
        if (anchored) {
            val slash = body.indexOf('/')
            if (slash < 0) hostPaths += body to "" else hostPaths += body.substring(0, slash) to body.substring(slash)
        } else {
            substrings += body
        }
    }

    /**
     * True when a sub-resource request to [url] should be dropped. [pageHost] is the host of the page
     * that made the request (used for first/third-party and per-site rules). Main-frame loads are never blocked.
     */
    fun shouldBlock(url: Uri, pageHost: String?): Boolean {
        val host = url.host?.lowercase(Locale.ROOT) ?: return false
        val page = pageHost?.lowercase(Locale.ROOT)

        // Layer 1: ads.json
        var h = host
        while (true) {
            if (h in NEVER_BLOCK_DOMAINS) break
            if (h in domains) return true
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        if (hostPaths.isNotEmpty()) {
            val pathAndQuery = ((url.encodedPath ?: "/") + (url.encodedQuery?.let { "?$it" } ?: "")).lowercase(Locale.ROOT)
            for ((hostRule, pathPrefix) in hostPaths) {
                if ((host == hostRule || host.endsWith(".$hostRule")) && pathAndQuery.startsWith(pathPrefix)) return true
            }
        }
        if (substrings.isNotEmpty()) {
            val full = url.toString().lowercase(Locale.ROOT)
            for (s in substrings) if (full.contains(s)) return true
        }

        // Layer 2: the big compiled rule set, once loaded. A site's own hosts (github.com and githubassets.com,
        // reddit.com and redditstatic.com) are exempt so broad patterns cannot break the site's own scripts.
        // Sites that break with broad rules (their ads are served from their own pages anyway).
        if (page != null && COMPAT_SITES.any { page == it || page.endsWith(".$it") }) return false
        val brand = page?.let { siteOf(it).substringBefore('.') }
        if (brand != null && brand.length >= 4 && host.contains(brand)) return false
        val ext = extended ?: return false
        val third = page == null || siteOf(page) != siteOf(host)
        return ext.blocks(url.toString().lowercase(Locale.ROOT).take(2000), host, page, third)
    }

    private class PatternRule(
        val pattern: String,
        val flags: Char,
        val inits: List<String>,
        val excl: List<String>,
    ) {
        private val regex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) { compile(pattern) }

        fun matches(url: String, page: String?, third: Boolean): Boolean {
            if (flags == 't' && !third) return false
            if (flags == 'f' && third) return false
            if (inits.isNotEmpty() && (page == null || inits.none { page == it || page.endsWith(".$it") })) return false
            if (excl.isNotEmpty() && page != null && excl.any { page == it || page.endsWith(".$it") }) return false
            return regex.containsMatchIn(url)
        }

        private fun compile(p: String): Regex {
            var s = p
            val sb = StringBuilder()
            if (s.startsWith("||")) {
                sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?")
                s = s.substring(2)
            } else if (s.startsWith("|")) {
                sb.append('^')
                s = s.substring(1)
            }
            val end = s.endsWith("|")
            if (end) s = s.dropLast(1)
            for (c in s) {
                when {
                    c == '*' -> sb.append(".*")
                    c == '^' -> sb.append("(?:[^a-z0-9_.%-]|\$)")
                    c.isLetterOrDigit() -> sb.append(c)
                    else -> sb.append('\\').append(c)
                }
            }
            if (end) sb.append('$')
            return Regex(sb.toString())
        }
    }

    private class RuleSet(
        val domains: HashSet<String>,
        val thirdDomains: HashSet<String>,
        val block: Map<String, List<PatternRule>>,
        val blockGeneric: List<PatternRule>,
        val allow: Map<String, List<PatternRule>>,
        val allowGeneric: List<PatternRule>,
    ) {
        fun blocks(url: String, host: String, page: String?, third: Boolean): Boolean {
            val tokens = url.split(TOKEN_SPLIT).filter { it.length >= 3 }.distinct()

            var hit = false
            var h = host
            while (true) {
                if (h in NEVER_BLOCK_DOMAINS) break
                if (h in domains || (third && h in thirdDomains)) { hit = true; break }
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
            if (!hit) hit = anyMatch(block, blockGeneric, tokens, url, page, third)
            if (!hit) return false
            // Exception rules win over block rules.
            return !anyMatch(allow, allowGeneric, tokens, url, page, third)
        }

        private fun anyMatch(
            index: Map<String, List<PatternRule>>,
            generic: List<PatternRule>,
            tokens: List<String>,
            url: String,
            page: String?,
            third: Boolean,
        ): Boolean {
            for (t in tokens) {
                val bucket = index[t] ?: continue
                for (r in bucket) if (r.matches(url, page, third)) return true
            }
            for (r in generic) if (r.matches(url, page, third)) return true
            return false
        }

        companion object {
            private val TOKEN_SPLIT = Regex("[^a-z0-9]+")
            private val NO_STRINGS = emptyList<String>()

            fun load(context: Context): RuleSet {
                val domains = HashSet<String>()
                val thirdDomains = HashSet<String>()
                val block = HashMap<String, MutableList<PatternRule>>()
                val blockGeneric = ArrayList<PatternRule>()
                val allow = HashMap<String, MutableList<PatternRule>>()
                val allowGeneric = ArrayList<PatternRule>()

                context.assets.open("rules/rules.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.length < 3) continue
                        when (line[0]) {
                            'D' -> domains += line.substring(2)
                            'T' -> thirdDomains += line.substring(2)
                            'B', 'A' -> {
                                // kind, token, flags, inits, excl, pattern
                                val f = line.split('\t')
                                if (f.size < 6 || f[2].isEmpty()) continue
                                val rule = PatternRule(
                                    pattern = f[5],
                                    flags = f[2][0],
                                    inits = if (f[3].isEmpty()) NO_STRINGS else f[3].split(','),
                                    excl = if (f[4].isEmpty()) NO_STRINGS else f[4].split(','),
                                )
                                val index = if (line[0] == 'B') block else allow
                                val generic = if (line[0] == 'B') blockGeneric else allowGeneric
                                if (f[1].isEmpty()) generic.add(rule)
                                else index.getOrPut(f[1]) { ArrayList() }.add(rule)
                            }
                        }
                    }
                }
                return RuleSet(domains, thirdDomains, block, blockGeneric, allow, allowGeneric)
            }
        }
    }

    private companion object {
        val COMPAT_SITES = setOf("reddit.com", "redd.it", "github.com")

        // Video and site infrastructure that domain rules must never touch.
        val NEVER_BLOCK_DOMAINS = setOf(
            "youtube.com", "googlevideo.com", "ytimg.com", "ggpht.com",
            "youtube-nocookie.com", "gstatic.com",
        )

        // Second-level suffixes where the "site" is three labels long (example.co.uk).
        val TWO_PART_SUFFIXES = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz",
            "co.jp", "or.jp", "ne.jp", "co.kr", "co.in", "net.in", "co.id", "or.id", "web.id",
            "com.my", "net.my", "org.my", "gov.my", "edu.my", "com.sg", "com.hk", "com.tw",
            "com.cn", "net.cn", "com.br", "com.mx", "com.ar", "com.tr", "com.ph", "com.vn", "co.th", "co.za",
        )

        /** Approximate registrable domain, used only for first/third-party decisions. */
        fun siteOf(host: String): String {
            val parts = host.split('.')
            if (parts.size <= 2) return host
            val lastTwo = parts.takeLast(2).joinToString(".")
            return if (lastTwo in TWO_PART_SUFFIXES && parts.size >= 3) parts.takeLast(3).joinToString(".") else lastTwo
        }
    }
}
