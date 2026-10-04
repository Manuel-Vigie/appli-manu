// Carnet de rando V10 — mise en cache pour usage hors connexion
// Pour forcer une mise à jour chez tout le monde : changer ce nom (V9-b, V10…)
const SHELL = 'carnet-app-V18';
const LIBS = 'carnet-libs';
const TILES = 'carnet-tuiles';
const MAX_TILES = 3000;
const SHELL_FILES = ['./carnet-randonnee.html', './carnet-manifest.webmanifest',
  './carnet-icone-192.png', './carnet-icone-512.png', './carnet-icone-masque-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(SHELL).then(c => Promise.all(SHELL_FILES.map(f => c.add(new Request(f, {cache: 'reload'})).catch(() => {})))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys())
      if (k.startsWith('carnet-app-') && k !== SHELL) await caches.delete(k); // ne touche qu'à ses propres caches
    await self.clients.claim();
  })());
});

const isLib = u => /fonts\.(googleapis|gstatic)\.com/.test(u.host);
const isTile = u => /tile\.openstreetmap\.org/.test(u.host);

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (req.mode === 'navigate' && url.origin === location.origin) {
    e.respondWith((async () => {
      try {
        const r = await fetch(new Request(req, {cache: 'no-cache'}));
        if (r.ok) (await caches.open(SHELL)).put(req, r.clone());
        return r;
      } catch (_) {
        return (await caches.match(req, {ignoreSearch: true})) || (await caches.match('./carnet-randonnee.html'));
      }
    })());
    return;
  }
  if (url.origin === location.origin || isLib(url)) {
    e.respondWith((async () => {
      const same = url.origin === location.origin;
      if (same) {
        try {
          const r = await fetch(new Request(req, {cache: 'no-cache'}));
          if (r.ok) (await caches.open(SHELL)).put(req, r.clone());
          return r;
        } catch (_) {
          const hit = await caches.match(req);
          if (hit) return hit;
          throw _;
        }
      }
      const hit = await caches.match(req);
      if (hit) return hit;
      const r = await fetch(req);
      if (r.ok || r.type === 'opaque') (await caches.open(LIBS)).put(req, r.clone());
      return r;
    })());
    return;
  }
  if (isTile(url)) {
    e.respondWith((async () => {
      const cache = await caches.open(TILES);
      const hit = await cache.match(req);
      if (hit) return hit;
      const r = await fetch(req);
      if (r.ok) cache.put(req, r.clone()).then(async () => {
        const keys = await cache.keys();
        for (let i = 0; i < keys.length - MAX_TILES; i++) await cache.delete(keys[i]);
      });
      return r;
    })());
  }
});
