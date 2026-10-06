// Bibliothèque Photo — mise en cache (fonctionne sans connexion)
const SHELL = 'biblio-photo-V2';
const FICHIERS = ['./bibliotheque-photo.html','./bibliotheque-manifest.webmanifest','./bibliotheque-icone-192.png','./bibliotheque-icone-512.png','./bibliotheque-icone-masque-512.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(SHELL).then(c => Promise.all(FICHIERS.map(f => c.add(new Request(f,{cache:'reload'})).catch(()=>{})))).then(()=>self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil((async()=>{ for(const k of await caches.keys()) if(k.startsWith('biblio-photo-') && k!==SHELL) await caches.delete(k); await self.clients.claim(); })());
});
self.addEventListener('fetch', e => {
  const r = e.request; if(r.method!=='GET') return;
  const u = new URL(r.url);
  if(u.origin===location.origin){
    e.respondWith(fetch(r).then(resp=>{ const cp=resp.clone(); caches.open(SHELL).then(c=>c.put(r,cp)); return resp; }).catch(()=>caches.match(r).then(m=>m||caches.match('./bibliotheque-photo.html'))));
  }
});
