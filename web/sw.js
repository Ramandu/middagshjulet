// Enkel offline-støtte: appens egne filer hentes fra nettet først, og fra lager hvis nettet er borte.
// Biblioteker og fonter (gstatic, Google Fonts, cdnjs) lagres første gang de brukes.
// Firebase-trafikken (firestore.googleapis.com osv.) berøres ikke.
// Bare svar som er i orden, lagres. Svarer serveren med en feil eller en side som ikke er appen (f.eks. hvis
// Netlify-prosjektet er satt på pause), brukes versjonen som er lagret på telefonen, så appen virker videre.
const CACHE = 'middagshjulet-v1';
const SHELL = ['./', './index.html', './firebase-config.js', './manifest.webmanifest', './icon-192.png', './icon-512.png', './apple-touch-icon.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
const LIBS = /^https:\/\/(www\.gstatic\.com\/firebasejs\/|fonts\.googleapis\.com\/|fonts\.gstatic\.com\/|cdnjs\.cloudflare\.com\/)/;
// Kjennetegn på at en side faktisk er appen (vinduet som ligger over siden finnes bare i index.html).
const APP_MARK = 'id="sheet"';
const fromCache = req => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match('./index.html'));
self.addEventListener('fetch', e => {
  const req = e.request; if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin === self.location.origin) {
    const isPage = req.mode === 'navigate' || url.pathname.endsWith('/') || url.pathname.endsWith('/index.html');
    e.respondWith(fetch(req).then(res => {
      const keep = res.ok
        ? (isPage ? res.clone().text().then(t => t.indexOf(APP_MARK) >= 0) : Promise.resolve(true))
        : Promise.resolve(false);
      return keep.then(ok => {
        if (ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res; }
        // Feil eller fremmed side: bruk den lagrede versjonen hvis vi har en, ellers svaret som kom.
        return caches.match(req, { ignoreSearch: true }).then(r => r || (isPage ? caches.match('./index.html') : null)).then(r => r || res);
      });
    }).catch(() => fromCache(req)));
    return;
  }
  if (LIBS.test(req.url)) {
    // Skript og stilark fra andre domener kan komme som «opaque» svar (status 0). De lagres også.
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => { if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); } return res; })));
  }
});
