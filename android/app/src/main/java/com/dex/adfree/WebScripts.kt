package com.dex.adfree

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Scripts injected into pages. The web folder is copied from ../src at build time; the scripts folder is this app's own. */
class WebScripts(context: Context) {

    /** Runs inside YouTube's page before its own scripts; strips ad data from player responses. */
    val ytMain: String = read(context, "web/yt-main.js")

    /** Fallback on YouTube: mutes, fast-forwards and skips any ad that still reaches the player. */
    val ytHush: String = read(context, "web/youtube.js")

    /** Keeps YouTube playing while the app is in the background. */
    val ytBackground: String = read(context, "scripts/yt-bg.js")

    /** Removes sponsored items from YouTube feeds and Shorts. */
    val ytFeed: String = read(context, "scripts/yt-feed.js")

    /** Lazy-loads off-screen images and frames. */
    val perf: String = read(context, "scripts/perf.js")

    /** Makes pages treat the browser as a desktop one while Desktop site is on. */
    val desktop: String = read(context, "scripts/desktop.js")

    /**
     * Wraps generic.css so it is applied as a <style> element as early as the document allows,
     * except on the sites the extension also exempts (rules/no_generic.txt), e.g. reddit.com and youtube.com.
     */
    val cosmetic: String = cosmeticLoader(read(context, "web/generic.css"), readLines(context, "rules/no_generic.txt"))

    private fun read(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun readLines(context: Context, path: String): List<String> = try {
        context.assets.open(path).bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun cosmeticLoader(css: String, skipHosts: List<String>): String = """
        (function () {
          if (window !== window.top) return; // top frame only: cheaper, ads are blocked at the network level anyway
          var skip = new Set(${JSONArray(skipHosts)});
          for (var p = location.hostname.toLowerCase(); p; ) {
            if (skip.has(p)) return;
            var i = p.indexOf('.');
            p = i < 0 ? '' : p.slice(i + 1);
          }
          var css = ${JSONObject.quote(css)};
          function add() {
            var s = document.createElement('style');
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
          }
          if (document.documentElement) { add(); return; }
          new MutationObserver(function (_, o) {
            if (document.documentElement) { o.disconnect(); add(); }
          }).observe(document, { childList: true });
        })();
    """.trimIndent()
}
