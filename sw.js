// Caras en GIF — service worker: recibe archivos desde "Compartir" y funciona sin conexión
const CACHE='caras-v1';
const ASSETS=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).catch(()=>{}));self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil((async()=>{
  for(const k of await caches.keys())if(k!==CACHE&&k!=='caras-share')await caches.delete(k);
  await self.clients.claim();})());});
self.addEventListener('fetch',e=>{
  const url=new URL(e.request.url);
  if(e.request.method==='POST'&&url.pathname.endsWith('/share-target')){
    e.respondWith((async()=>{
      try{
        const fd=await e.request.formData();
        const f=fd.getAll('media').find(x=>x&&typeof x==='object'&&x.size>0);
        if(!f)return Response.redirect('./?shared=error',303);
        const c=await caches.open('caras-share');
        await c.put('shared-file',new Response(f,{headers:{'Content-Type':f.type||'application/octet-stream','X-Name':encodeURIComponent(f.name||'compartido')}}));
        return Response.redirect('./?shared=1',303);
      }catch(err){return Response.redirect('./?shared=error',303);}
    })());
    return;
  }
  if(e.request.method!=='GET'||url.origin!==self.location.origin)return;
  // Primero la red (para recibir actualizaciones); si no hay conexión, la copia guardada
  e.respondWith(fetch(e.request).then(r=>{if(r.ok){const cp=r.clone();caches.open(CACHE).then(c=>c.put(e.request,cp));}return r;})
    .catch(()=>caches.match(e.request,{ignoreSearch:true}).then(r=>r||caches.match('./index.html'))));
});
