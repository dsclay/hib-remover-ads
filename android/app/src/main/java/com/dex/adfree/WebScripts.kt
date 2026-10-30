package com.dex.adfree

import android.content.Context
import org.json.JSONObject

/** The extension's scripts, loaded from assets/web/ (copied from ../src at build time). */
class WebScripts(context: Context) {

    /** Runs inside YouTube's page before its own scripts; strips ad data from player responses. */
    val ytMain: String = read(context, "web/yt-main.js")

    /** Fallback on YouTube: mutes, fast-forwards and skips any ad that still reaches the player. */
    val ytHush: String = read(context, "web/youtube.js")

    /** Wraps generic.css so it is applied as a <style> element as early as the document allows. */
    val cosmetic: String = cosmeticLoader(read(context, "web/generic.css"))

    private fun read(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun cosmeticLoader(css: String): String = """
        (function () {
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
