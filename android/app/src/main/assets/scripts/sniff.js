(function () {
  if (window.__hhSniff) return;
  window.__hhSniff = true;
  var seen = {};
  function report(u) {
    try {
      u = new URL(u, location.href).href;
      if (seen[u]) return;
      seen[u] = 1;
      var m = { hhm: 1, u: u, r: location.href };
      if (window === window.top) add(m); else window.top.postMessage(m, '*');
    } catch (e) {}
  }
  function add(m) {
    var a = (window.__hhM = window.__hhM || []);
    for (var i = 0; i < a.length; i++) if (a[i].u === m.u) return;
    if (a.length < 40) a.push({ u: m.u, r: m.r });
  }
  if (window === window.top) {
    window.addEventListener('message', function (e) {
      var d = e.data;
      if (d && d.hhm === 1 && typeof d.u === 'string') add(d);
    });
  }
  function isList(t) { return typeof t === 'string' && t.slice(0, 64).indexOf('#EXTM3U') >= 0; }
  var of = window.fetch;
  if (of) {
    window.fetch = function (input) {
      var p = of.apply(this, arguments);
      try {
        p.then(function (res) {
          try {
            var ct = (res.headers.get('content-type') || '').toLowerCase();
            var len = +(res.headers.get('content-length') || 0);
            if (ct.indexOf('video/') === 0 || ct.indexOf('image/') === 0 || ct.indexOf('font') >= 0 || ct.indexOf('javascript') >= 0 || ct.indexOf('css') >= 0 || len > 2000000) return;
            res.clone().text().then(function (t) { if (isList(t)) report(res.url || (input && input.url) || input); }).catch(function () {});
          } catch (e) {}
        }).catch(function () {});
      } catch (e) {}
      return p;
    };
  }
  var oo = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (m, u) {
    var self = this, url = u;
    try {
      this.addEventListener('load', function () {
        try {
          if (self.responseType === '' || self.responseType === 'text') {
            var t = self.responseText;
            if (t && t.length < 2000000 && isList(t)) report(self.responseURL || url);
          }
        } catch (e) {}
      });
    } catch (e) {}
    return oo.apply(this, arguments);
  };
})();
