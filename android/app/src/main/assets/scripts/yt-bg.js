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
})();
