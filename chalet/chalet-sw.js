// Mon chalet : met en cache l'enveloppe de l'appli et la bibliothèque 3D. Les produits restent dans le téléphone (localStorage), jamais ici.
const CACHE = 'chalet-app-v1';
const LIBS = 'chalet-libs';
const FILES = ['./chalet.html', './chalet-manifest.webmanifest',
  './chalet-icon-192.png', './chalet-icon-512.png', './chalet-icon-masque-512.png',
  './chalet-apple-touch-icon.png'];
const LIB_URLS = ['https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js'];
const DOSSIER = new URL('./', self.location).pathname;
self.addEventListener('install', e => {
  e.waitUntil((async () => {
    await (await caches.open(CACHE)).addAll(FILES);
    const libs = await caches.open(LIBS);
    await Promise.all(LIB_URLS.map(u => fetch(u, {mode: 'cors'}).then(r => r.ok && libs.put(u, r)).catch(() => {})));
    await self.skipWaiting();
  })());
});
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k.startsWith('chalet-app-') && k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // Bibliothèque 3D : cache d'abord (pour que le chalet s'ouvre sans réseau)
  if (LIB_URLS.includes(req.url)) {
    e.respondWith((async () => {
      const hit = await caches.match(req);
      if (hit) return hit;
      const r = await fetch(req);
      if (r.ok) (await caches.open(LIBS)).put(req, r.clone());
      return r;
    })());
    return;
  }
  // On ne touche qu'aux fichiers de ce dossier : les autres applis passent toujours en direct.
  if (url.origin !== location.origin || !url.pathname.startsWith(DOSSIER)) return;
  if (url.pathname.endsWith('/version.json')) return;
  e.respondWith((async () => {
    try {
      const r = await fetch(req);
      if (r.ok && !url.search) (await caches.open(CACHE)).put(req, r.clone());
      return r;
    } catch (_) {
      return (await caches.match(req, {ignoreSearch: true})) || (await caches.match('./chalet.html'));
    }
  })());
});
