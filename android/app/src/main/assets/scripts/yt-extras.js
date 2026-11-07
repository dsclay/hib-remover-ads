// Hedgehog: SponsorBlock (skip sponsor segments) and remembered playback speed for YouTube.
// window.__hhCfg = { sb: bool, speed: bool } is set by the app before this runs.
(function () {
  if (window !== window.top || window.__hhExtras) return;
  window.__hhExtras = true;
  var cfg = window.__hhCfg || {};
  var CATS = ['sponsor', 'selfpromo', 'interaction'];
  var SPEEDS = [1, 1.25, 1.5, 1.75, 2, 0.75];
  var videoId = '', segs = [], skipped = {}, hooked = null, setting = false, pill = null, toastEl = null;

  function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) {} return null; }
  function currentId() {
    var m = location.pathname.match(/^\/(?:shorts|live|embed)\/([\w-]{11})/);
    if (m) return m[1];
    return location.pathname === '/watch' ? (new URLSearchParams(location.search).get('v') || '') : '';
  }
  function sha256Prefix(s) {
    var enc = new TextEncoder().encode(s);
    return crypto.subtle.digest('SHA-256', enc).then(function (b) {
      return Array.prototype.map.call(new Uint8Array(b), function (x) { return ('0' + x.toString(16)).slice(-2); }).join('').slice(0, 4);
    });
  }
  function loadSegments(id) {
    segs = []; skipped = {};
    if (!cfg.sb || !id) return;
    sha256Prefix(id).then(function (p) {
      return fetch('https://sponsor.ajay.app/api/skipSegments/' + p + '?categories=' + encodeURIComponent(JSON.stringify(CATS)));
    }).then(function (r) { return r.ok ? r.json() : []; }).then(function (all) {
      if (id !== videoId) return;
      for (var i = 0; i < all.length; i++) if (all[i].videoID === id) { segs = all[i].segments || []; break; }
    }).catch(function () {});
  }
  function toast(text) {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.style.cssText = 'position:fixed;left:50%;bottom:90px;transform:translateX(-50%);z-index:2147483647;padding:8px 14px;border-radius:999px;background:rgba(20,20,20,.85);color:#fff;font:600 13px sans-serif;pointer-events:none;transition:opacity .3s;opacity:0';
      document.documentElement.appendChild(toastEl);
    }
    toastEl.textContent = text;
    toastEl.style.opacity = '1';
    clearTimeout(toastEl.__t);
    toastEl.__t = setTimeout(function () { toastEl.style.opacity = '0'; }, 1800);
  }
  function applySpeed(v) {
    var s = parseFloat(store('hh_speed')) || 1;
    if (Math.abs(v.playbackRate - s) > 0.01) { setting = true; v.playbackRate = s; setting = false; }
    if (pill) pill.textContent = s + 'x';
  }
  function hook(v) {
    if (hooked === v) return;
    hooked = v;
    v.addEventListener('ratechange', function () {
      if (setting || !cfg.speed) return;
      store('hh_speed', String(v.playbackRate)); // the user changed it in YouTube's own menu
      if (pill) pill.textContent = v.playbackRate + 'x';
    });
    v.addEventListener('loadedmetadata', function () { if (cfg.speed) applySpeed(v); });
  }
  function ensurePill() {
    if (!cfg.speed) return;
    var show = !!currentId();
    if (!pill) {
      pill = document.createElement('div');
      pill.style.cssText = 'position:fixed;right:10px;top:84px;z-index:2147483646;padding:6px 12px;border-radius:999px;background:rgba(20,20,20,.7);color:#fff;font:700 13px sans-serif;border:1px solid rgba(255,255,255,.25)';
      pill.addEventListener('click', function () {
        var cur = parseFloat(store('hh_speed')) || 1;
        var i = SPEEDS.indexOf(cur);
        var next = SPEEDS[(i + 1) % SPEEDS.length];
        store('hh_speed', String(next));
        var v = document.querySelector('video');
        if (v) applySpeed(v); else pill.textContent = next + 'x';
      });
      pill.textContent = (parseFloat(store('hh_speed')) || 1) + 'x';
      document.documentElement.appendChild(pill);
    }
    pill.style.display = show ? '' : 'none';
  }
  function tick() {
    var id = currentId();
    if (id !== videoId) { videoId = id; loadSegments(id); }
    var v = document.querySelector('video');
    ensurePill();
    if (!v) return;
    hook(v);
    if (cfg.speed) applySpeed(v);
    if (!segs.length || v.paused && v.currentTime < 0.1) return;
    var t = v.currentTime;
    for (var i = 0; i < segs.length; i++) {
      var a = segs[i].segment;
      if (t >= a[0] && t < a[1] - 0.3 && !skipped[i + ':' + Math.floor(a[0])]) {
        skipped[i + ':' + Math.floor(a[0])] = 1;
        v.currentTime = a[1];
        toast('Skipped ' + (segs[i].category === 'sponsor' ? 'sponsor' : segs[i].category === 'selfpromo' ? 'self-promo' : 'reminder'));
        break;
      }
    }
  }
  setInterval(tick, 500);
})();
