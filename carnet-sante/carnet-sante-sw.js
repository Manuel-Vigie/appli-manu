// Carnet de santé : met en cache SEULEMENT l'enveloppe de l'appli (aucune donnée perso).
const CACHE = 'carnet-sante-app-v1';
const FILES = ['./carnet-sante.html', './carnet-sante-manifest.webmanifest',
  './carnet-sante-icon-192.png', './carnet-sante-icon-512.png', './carnet-sante-icon-masque-512.png',
  './carnet-sante-apple-touch-icon.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k.startsWith('carnet-sante-app-') && k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  e.respondWith((async () => {
    try {
      const r = await fetch(req);
      if (r.ok) (await caches.open(CACHE)).put(req, r.clone());
      return r;
    } catch (_) {
      return (await caches.match(req, {ignoreSearch: true})) || (await caches.match('./carnet-sante.html'));
    }
  })());
});
