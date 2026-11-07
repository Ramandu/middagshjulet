# Middagshjulet – for Claude

Les dette før du endrer noe. Brukeren skriver norsk, og all tekst i appen, i commit-meldinger og i pull requester skal være på norsk.

## Hva repoet er

- `web/` er nettsiden (middagshjulet.netlify.app). Nesten alt ligger i `web/index.html`. Netlify publiserer mappa slik den er, uten byggesteg (se `netlify.toml`).
- Data lagres i Firebase Firestore under `households/{husstands-ID}/docs/{dokument}`. Husstands-ID-en er den hemmelige lenken. Den må aldri havne et sted andre kan lese den.
- `web/firestore.rules` må limes inn i Firebase-konsollen når de endres. Unngå nye dokumenter hvis det går. Innstillinger kan legges i oppsettdokumentet (`setup`), slik husarbeid (`setup.hus`) og medlemskort (`setup.kort`) gjør. Avkrysninger per uke ligger i ukedokumentene (`w0`–`w3`).
- `tool/` er verktøyet for Light Phone III. Det leser dokumentet `lp3`, som nettsiden skriver.
- Resten (`sdk/`, `plugin/`, `gradle/` osv.) er Light SDK. Ikke endre der.
- Planer og ideer står i `ROADMAP.md`.

## Arbeidsflyt: endringer samles i versjoner

Husstanden bruker appen hver dag, så endringer slippes samlet, ikke flere ganger om dagen.

| Gren | Hva |
|---|---|
| `main` | Det som ligger ute nå. Netlify publiserer den til middagshjulet.netlify.app. |
| `neste` | Neste versjon. Ferdige endringer samles her. Testside: neste--middagshjulet.netlify.app (krever at grenpublisering er skrudd på i Netlify). |

1. **Ny endring:** lag en arbeidsgren fra `neste`, og lag en pull request til `neste`. Flett med «squash» når brukeren sier ja.
2. **Slipp:** når brukeren sier at versjonen skal slippes, lag en pull request fra `neste` til `main`, og flett med vanlig «merge», ikke «squash», så grenene ikke kommer i utakt. Flytt punktene i `ROADMAP.md` fra «Neste versjon» til «Sluppet» med dato.
3. **Hastefeil** (noe som ikke virker i butikken o.l.): rett i en arbeidsgren fra `main`, flett til `main`, og flett deretter `main` inn i `neste`.

Spør alltid brukeren før du fletter noe til `main`, og før du lager en pull request.

## Nyheter i appen

`NEWS` i `web/index.html` viser nyhetene én gang per enhet etter en oppdatering. Øverst står det nyeste.

- Under arbeidet i `neste`: legg punktene i én felles oppføring øverst for neste versjon.
- Ved slipp: sett `id` og `title` til slippdatoen (ÅÅÅÅ-MM-DD). `id` må være større enn forrige oppføring, ellers vises den ikke.
- Nyhetene skal forklare hva brukeren merker, ikke hvordan det er laget.

## Testing

Det finnes ingen testpakke i repoet. Slik har det vært testet:

- Syntaks: hent ut innholdet i `<script>`-blokkene i `web/index.html` og kjør `node --check` på dem.
- I nettleser: Chromium og Playwright er installert. Last siden med Playwright og svar på `firebase-config.js` med `window.FIREBASE_CONFIG=null;`, så kjører den i lokal modus uten Firebase. Bruk en husstandslenke som `#h=AbCdEfGhIjKlMnOpQrStUvWx`. Koden ligger i en IIFE. For å nå interne funksjoner i testen, erstatt `window.mhConnect = connect;` med en linje som også legger dem på `window` (bare i testkopien).
- Test på mobilbredde (390 px), og se på skjermbilder.
- Endringer i gjetting av avdeling: sammenlign med de håndsatte avdelingene i de innebygde rettene (`D`).

## Stil

- Korte, konkrete tekster i appen. Skriv «dere» til husstanden.
- Bruk de klassene og mønstrene som finnes (`.card`, `.pchip`, `.dchip`, vinduer via `renderSheet`), og test både lys og mørk modus.
- Nye biblioteker legges i `web/vendor/` med versjon i filnavnet (se `LESMEG.md`), ikke på en CDN som service workeren ikke lagrer.
