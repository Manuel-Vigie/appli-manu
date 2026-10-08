// Replay Rando 3D — service worker (mise en cache pour usage hors connexion)
const VERSION = 'rr3d-v61';
const SHELL = VERSION + '-app';
const LIBS = 'rr3d-libs';
const TILES = 'rr3d-tiles';
const MAX_TILES = 4000;

const SHELL_FILES = ['./rando-3d.html', './manifest.webmanifest',
  './icon-192.png', './icon-512.png', './apple-touch-icon.png', './favicon.png'];
const LIB_URLS = [
  'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js',
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js',
  'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css',
  'https://cdn.jsdelivr.net/npm/maplibre-gl@5.24.0/dist/maplibre-gl.js',
  'https://cdn.jsdelivr.net/npm/maplibre-gl@5.24.0/dist/maplibre-gl.css',
  'https://cdn.jsdelivr.net/npm/mp4-muxer@5.1.3/build/mp4-muxer.js'
];

self.addEventListener('install', e => {
  e.waitUntil((async () => {
    await (await caches.open(SHELL)).addAll(SHELL_FILES);
    const libs = await caches.open(LIBS);
    await Promise.all(LIB_URLS.map(u => fetch(u, {mode: 'cors'}).then(r => r.ok && libs.put(u, r)).catch(() => {})));
    self.skipWaiting();
  })());
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys())
      if (k.startsWith('rr3d-v') && k !== SHELL) await caches.delete(k);
    await self.clients.claim();
  })());
});

const isLib = u => /cdnjs\.cloudflare\.com|cdn\.jsdelivr\.net|fonts\.(googleapis|gstatic)\.com/.test(u.host);
const isTile = u => /data\.geopf\.fr|elevation-tiles-prod|arcgisonline\.com/.test(u.host + u.pathname);

async function trimTiles(cache) {
  const keys = await cache.keys();
  for (let i = 0; i < keys.length - MAX_TILES; i++) await cache.delete(keys[i]);
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // Fichier de version : toujours lu en ligne (sert à afficher « Nouvelle version »)
  if (url.origin === location.origin && url.pathname.endsWith('/version.json')) return;

  // Page de l'app : réseau d'abord (pour recevoir les mises à jour), sinon cache
  if (req.mode === 'navigate' && url.origin === location.origin) {
    e.respondWith((async () => {
      try {
        const r = await fetch(req);
        if (r.ok) (await caches.open(SHELL)).put(req, r.clone());
        return r;
      } catch (_) {
        return (await caches.match(req)) || (await caches.match('./rando-3d.html'));
      }
    })());
    return;
  }
  // Fichiers de l'app et bibliothèques : cache d'abord
  if (url.origin === location.origin || isLib(url)) {
    e.respondWith((async () => {
      const hit = await caches.match(req);
      if (hit) return hit;
      const r = await fetch(req);
      if (r.ok || r.type === 'opaque') (await caches.open(url.origin === location.origin ? SHELL : LIBS)).put(req, r.clone());
      return r;
    })());
    return;
  }
  // Tuiles de carte et relief : cache d'abord, gardées pour revoir une rando hors connexion
  if (isTile(url)) {
    e.respondWith((async () => {
      const cache = await caches.open(TILES);
      const hit = await cache.match(req);
      if (hit) return hit;
      const r = await fetch(req);
      if (r.ok) { cache.put(req, r.clone()).then(() => trimTiles(cache)); }
      return r;
    })());
  }
  // Le reste (météo, itinéraires, sommets…) passe directement par le réseau
});
