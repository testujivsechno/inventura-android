// Service worker: offline provoz aplikace.
// Při každé změně souborů zvyš číslo verze V, staré kopie se pak samy smažou.
const V='inv-v32';
// Soubory aplikace (chybějící soubor se tiše přeskočí)
const APP=['./inventura.html','./inventura.json','./icon.svg','./icon-192.png','./icon-512.png','./icon-maskable-512.png','./apple-touch-icon.png'];
// Knihovny: [lokální soubor vedle aplikace, záložní adresa na CDN]. CDN se uloží jen když lokální soubor chybí.
const LIBS=[
 ['./xlsx.full.min.js','https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js'],
 ['./zxing.min.js','https://cdn.jsdelivr.net/npm/@zxing/library@0.21.3/umd/index.min.js']
];
const fresh=u=>new Request(u,{cache:'reload'});
self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(V).then(async c=>{
 await Promise.allSettled(APP.map(u=>c.add(fresh(u))));
 for(const [loc,cdn] of LIBS){try{await c.add(fresh(loc))}catch(_){try{await c.add(cdn)}catch(__){}}}
}))});
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(n=>n!==V).map(n=>caches.delete(n)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
 const r=e.request;if(r.method!=='GET')return;
 if(r.mode==='navigate'){
  // Nejdřív síť (max. 4 s), při selhání uložená kopie aplikace
  e.respondWith(Promise.race([fetch(r),new Promise((_,no)=>setTimeout(no,4000))]).then(res=>{
   if(res&&res.ok){const cp=res.clone();caches.open(V).then(c=>c.put(r,cp))}return res
  }).catch(()=>caches.match(r,{ignoreSearch:true}).then(m=>m||caches.match('./inventura.html'))));
  return}
 // Ostatní soubory: z cache, na pozadí se obnoví
 e.respondWith(caches.match(r).then(m=>{
  const net=fetch(r).then(res=>{if(res&&(res.ok||res.type==='opaque')){const cp=res.clone();caches.open(V).then(c=>c.put(r,cp))}return res}).catch(()=>m);
  return m||net}))});
