const CACHE = 'filmrec-v3';
const POSTER_CACHE = 'filmrec-posters-v1';
const ASSETS = [
  './',
  './index.html',
  './demo_db.json',
  './posters.json',
  './manifest.json',
  './icon-192.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  const keep = new Set([CACHE, POSTER_CACHE]);
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => !keep.has(k)).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);

  if (url.hostname === 'image.tmdb.org') {
    e.respondWith(staleWhileRevalidate(e.request, POSTER_CACHE));
    return;
  }

  if (url.origin === location.origin) {
    e.respondWith(staleWhileRevalidate(e.request, CACHE));
  }
});

function staleWhileRevalidate(request, cacheName) {
  return caches.open(cacheName).then(cache =>
    cache.match(request).then(cached => {
      const fetchPromise = fetch(request).then(resp => {
        if (resp.ok) cache.put(request, resp.clone());
        return resp;
      }).catch(() => cached);

      return cached || fetchPromise;
    })
  );
}
