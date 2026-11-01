(function () {
  // Active only when the browser's Desktop site mode is on (it swaps in a desktop user agent).
  if (!/X11; Linux x86_64/.test(navigator.userAgent) || window !== window.top) return;
  function def(o, k, v) { try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {} }
  def(navigator, 'maxTouchPoints', 0);
  def(navigator, 'platform', 'Linux x86_64');
  def(navigator, 'userAgentData', {
    mobile: false,
    platform: 'Linux',
    brands: [{ brand: 'Chromium', version: '130' }, { brand: 'Google Chrome', version: '130' }],
    getHighEntropyValues: function () { return Promise.resolve({ mobile: false, platform: 'Linux' }); }
  });
  var W = 'width=1100';
  function fix() {
    var head = document.head || document.documentElement;
    if (!head) return;
    var m = document.querySelector('meta[name=viewport]');
    if (!m) { m = document.createElement('meta'); m.name = 'viewport'; head.appendChild(m); }
    if (m.getAttribute('content') !== W) m.setAttribute('content', W);
  }
  fix();
  new MutationObserver(fix).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
})();
