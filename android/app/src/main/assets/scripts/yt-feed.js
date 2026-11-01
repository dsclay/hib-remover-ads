// Removes sponsored items from YouTube's feeds and Shorts before the page renders them.
// Complements yt-main.js, which only strips in-player ad data.
(() => {
  const AD_KEYS = [
    'adSlotRenderer', 'promotedSparklesWebRenderer', 'promotedSparklesTextSearchRenderer',
    'compactPromotedVideoRenderer', 'promotedVideoRenderer', 'displayAdRenderer',
    'searchPyvRenderer', 'inFeedAdLayoutRenderer',
  ];

  const hasAdKey = (o) => {
    for (const k of AD_KEYS) if (k in o) return true;
    return false;
  };

  const isAd = (item) => {
    if (!item || typeof item !== 'object') return false;
    if (hasAdKey(item)) return true;
    const content = item.richItemRenderer && item.richItemRenderer.content;
    if (content && typeof content === 'object' && hasAdKey(content)) return true;
    // Sponsored Shorts
    const ep = item.command && item.command.reelWatchEndpoint;
    if (ep && ep.adClientParams && ep.adClientParams.isAd) return true;
    return false;
  };

  const walk = (node, depth) => {
    if (!node || typeof node !== 'object' || depth > 16) return;
    if (Array.isArray(node)) {
      for (let i = node.length - 1; i >= 0; i--) {
        if (isAd(node[i])) node.splice(i, 1);
        else walk(node[i], depth + 1);
      }
      return;
    }
    for (const k in node) {
      const v = node[k];
      if (v && typeof v === 'object') walk(v, depth + 1);
    }
  };

  const prune = (data) => {
    try {
      walk(data, 0);
    } catch {
      // Frozen or exotic objects: leave them alone.
    }
    return data;
  };

  JSON.parse = new Proxy(JSON.parse, {
    apply: (target, self, args) => prune(Reflect.apply(target, self, args)),
  });

  Response.prototype.json = new Proxy(Response.prototype.json, {
    apply: (target, self, args) => Reflect.apply(target, self, args).then(prune),
  });

  // The first page of a feed is embedded in the HTML as a global assignment.
  let initial;
  try {
    Object.defineProperty(window, 'ytInitialData', {
      configurable: true,
      get: () => initial,
      set: (v) => { initial = prune(v); },
    });
  } catch {
    // Already defined by the page; the JSON hooks still apply.
  }

  // Fallback for anything that still renders.
  const css = 'ytm-ad-slot-renderer,ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,' +
    'ytm-companion-ad-renderer,ad-slot-renderer{display:none!important}';
  const addStyle = () => {
    const s = document.createElement('style');
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  };
  if (document.documentElement) addStyle();
  else new MutationObserver((_, o) => {
    if (document.documentElement) { o.disconnect(); addStyle(); }
  }).observe(document, { childList: true });
})();
