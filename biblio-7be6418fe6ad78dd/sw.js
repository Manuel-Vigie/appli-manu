const C='lecteur-rando-v1';
self.addEventListener('install',e=>{self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil(self.clients.claim());});
self.addEventListener('fetch',e=>{
 const q=e.request; if(q.method!=='GET') return;
 const u=new URL(q.url); if(u.origin!==location.origin) return;
 if(u.pathname.indexOf('/medias/')>=0){
  e.respondWith(caches.open(C).then(async c=>{const h=await c.match(q); if(h) return h; const r=await fetch(q); if(r.ok) c.put(q,r.clone()); return r;}));
  return;}
 e.respondWith((async()=>{const c=await caches.open(C); try{const r=await fetch(q,{cache:'no-cache'}); if(r.ok) c.put(q,r.clone()); return r;}catch(_){return (await c.match(q,{ignoreSearch:true}))||(await c.match('./'))||(await c.match('index.html'));}})());
});
