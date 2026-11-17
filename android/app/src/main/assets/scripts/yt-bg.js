(function () {
  'use strict';

  // Prevent double-install (e.g. if injected on every navigation).
  if (window.__adfreeInstalled) return;
  window.__adfreeInstalled = true;

  var RESUME_DELAY_MS = 200;   // wait before attempting resume after a pause
  var ENDED_EPSILON_S = 0.5;   // treat near-end as "ended" and don't resume

  // ---------------------------------------------------------------------------
  // 1) Force the page to always look visible.
  //    Defines an own accessor on the instance (shadowing the prototype getter).
  //    The no-op setter prevents strict-mode throws if page code assigns to it.
  // ---------------------------------------------------------------------------
  function def(obj, key, value) {
    try {
      Object.defineProperty(obj, key, {
        get: function () { return value; },
        set: function () { /* no-op: swallow assignments */ },
        configurable: true
      });
      return true;
    } catch (e) {
      return false;
    }
  }

  def(document, 'hidden', false);
  def(document, 'webkitHidden', false);
  def(document, 'visibilityState', 'visible');
  def(document, 'webkitVisibilityState', 'visible');
  def(document, 'mozVisibilityState', 'visible');
  def(document, 'msVisibilityState', 'visible');

  // Some engines also read from Document.prototype; try there too (best-effort).
  try {
    var proto = Object.getPrototypeOf(document);
    if (proto && proto !== Object.prototype) {
      def(proto, 'hidden', false);
      def(proto, 'visibilityState', 'visible');
    }
  } catch (e) {}

  // ---------------------------------------------------------------------------
  // 2) Suppress visibilitychange from reaching page listeners.
  //    Only active while window.__adfreeBg is truthy, so the app can still
  //    receive genuine visibility changes when it wants them.
  // ---------------------------------------------------------------------------
  function stopIfBackground(e) {
    if (!window.__adfreeBg) return;
    e.stopImmediatePropagation();
  }

  // Capture phase on window is enough to intercept before document/target
  // listeners in most flows; keep document too for defensiveness.
  ['visibilitychange', 'webkitvisibilitychange'].forEach(function (type) {
    window.addEventListener(type, stopIfBackground, true);
    document.addEventListener(type, stopIfBackground, true);
  });

  // ---------------------------------------------------------------------------
  // 3) Resume video playback while the app is in the background.
  //    Media events don't bubble, so we rely on the capture phase from window
  //    to observe 'pause' on <video> elements, plus observe dynamically added
  //    videos directly for reliability.
  // ---------------------------------------------------------------------------
  function isNearEnd(v) {
    if (!v) return true;
    if (v.ended) return true;
    if (typeof v.duration !== 'number' || !isFinite(v.duration) || v.duration <= 0) {
      return false; // unknown/live duration: assume not ended
    }
    return v.currentTime >= v.duration - ENDED_EPSILON_S;
  }

  function tryResume(v) {
    if (!window.__adfreeBg || window.__hhUserPause || !v || v.tagName !== 'VIDEO') return; // __hhUserPause: paused from the notification
    if (v.__resumePending) return; // de-dup stacked retries
    v.__resumePending = true;

    setTimeout(function () {
      v.__resumePending = false;
      if (!window.__adfreeBg || !v.isConnected) return;
      if (!v.paused || isNearEnd(v)) return;
      var p = v.play();
      if (p && typeof p.catch === 'function') {
        p.catch(function () { /* blocked by policy, give up silently */ });
      }
    }, RESUME_DELAY_MS);
  }

  // Any play (by the user or the notification) clears the "paused on purpose" flag.
  window.addEventListener('play', function () { window.__hhUserPause = false; }, true);

  // Capture-phase listener catches non-bubbling media events from descendants.
  window.addEventListener('pause', function (e) {
    tryResume(e.target);
  }, true);

  // Belt-and-suspenders: bind directly to videos as they appear.
  function bindVideo(v) {
    if (!v || v.tagName !== 'VIDEO' || v.__adfreeBound) return;
    v.__adfreeBound = true;
    v.addEventListener('pause', function () {
      if (window.__adfreeBg) tryResume(v);
    }, false);
  }

  function scanForVideos(root) {
    if (!root || !root.querySelectorAll) return;
    var vids = root.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) bindVideo(vids[i]);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { scanForVideos(document); }, { once: true });
  } else {
    scanForVideos(document);
  }

  try {
    var mo = new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var added = records[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var n = added[j];
          if (n.nodeType === 1) {
            if (n.tagName === 'VIDEO') bindVideo(n);
            else scanForVideos(n);
          }
        }
      }
    });
    mo.observe(document.documentElement || document, { childList: true, subtree: true });
  } catch (e) {}
})();
