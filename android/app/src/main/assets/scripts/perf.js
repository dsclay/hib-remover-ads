// Faster page loading: lets the browser defer off-screen images and frames, and decode images off the main thread.
(() => {
  const mark = (el) => {
    if (el.tagName === 'IMG') {
      if (!el.hasAttribute('loading') && el.getAttribute('fetchpriority') !== 'high') el.loading = 'lazy';
      if (!el.hasAttribute('decoding')) el.decoding = 'async';
    } else if (el.tagName === 'IFRAME') {
      if (!el.hasAttribute('loading')) el.loading = 'lazy';
    }
  };

  const scan = (node) => {
    if (node.nodeType !== 1) return;
    mark(node);
    if (node.querySelectorAll) node.querySelectorAll('img,iframe').forEach(mark);
  };

  new MutationObserver((records) => {
    for (const r of records) for (const n of r.addedNodes) scan(n);
  }).observe(document, { childList: true, subtree: true });
})();
