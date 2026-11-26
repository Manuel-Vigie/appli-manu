// Congés : met en cache SEULEMENT l'enveloppe de l'appli. Les congés restent dans le téléphone (localStorage), jamais ici.
const CACHE = 'conges-app-v9';
const FILES = ['./conges.html', './conges-manifest.webmanifest',
  './conges-icon-192.png', './conges-icon-512.png', './conges-icon-masque-512.png',
  './conges-apple-touch-icon.png'];
const DOSSIER = new URL('./', self.location).pathname;
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k.startsWith('conges-app-') && k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // On ne touche qu'aux fichiers de ce dossier : les autres applis passent toujours en direct.
  if (url.origin !== location.origin || !url.pathname.startsWith(DOSSIER)) return;
  if (url.pathname.endsWith('/version.json')) return;
  e.respondWith((async () => {
    try {
      const r = await fetch(req);
      if (r.ok && !url.search) (await caches.open(CACHE)).put(req, r.clone());
      return r;
    } catch (_) {
      return (await caches.match(req, {ignoreSearch: true})) || (await caches.match('./conges.html'));
    }
  })());
});
