(function () {
  // Lets YouTube keep playing when the app goes to the background: the page is told it is always visible.
  function def(o, k, v) { try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {} }
  def(document, 'hidden', false);
  def(document, 'webkitHidden', false);
  def(document, 'visibilityState', 'visible');
  def(document, 'webkitVisibilityState', 'visible');
  function stop(e) { e.stopImmediatePropagation(); }
  ['visibilitychange', 'webkitvisibilitychange'].forEach(function (t) {
    document.addEventListener(t, stop, true);
    window.addEventListener(t, stop, true);
  });
  // While the app is in the background (the app sets window.__adfreeBg), resume playback if the system pauses it.
  document.addEventListener('pause', function (e) {
    var v = e.target;
    if (!window.__adfreeBg || !v || v.tagName !== 'VIDEO') return;
    setTimeout(function () {
      var done = v.ended || (v.duration && v.currentTime >= v.duration - 0.5);
      if (window.__adfreeBg && v.paused && !done) { var p = v.play(); if (p && p.catch) p.catch(function () {}); }
    }, 200);
  }, true);
})();
