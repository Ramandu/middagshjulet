# Middagshjulet – slik setter du det opp

Middagshjulet er en vanlig nettside (én HTML-fil og noen småfiler) som du legger ut på Netlify.
Den felles lista lagres i **Firebase**, en gratis database fra Google. Brukerne trenger ingen konto.

## Hva Firebase gjør her

Tenk på det i tre deler:

1. **Nettsiden** (Netlify) er selve appen. Den er lik for alle og inneholder ingen data.
2. **Databasen** (Firebase Firestore) er et felles «notatark» i skyen. Hver husstand får sin egen mappe,
   `households/<husstands-ID>`, med planen, handlelista, skapet osv. Når én telefon skriver noe der,
   sender Firestore endringen til alle andre som ser på samme mappe, i løpet av et sekund eller to.
3. **Lenken** er nøkkelen. Husstands-ID-en er en tilfeldig kode på 24 tegn som står i lenken
   (`…/#h=AbC123…`). Den som har lenken, finner mappa. Uten lenken er det i praksis umulig å gjette den,
   og reglene i databasen (filen `firestore.rules`) forbyr å liste opp mapper.

**Anonym innlogging:** Når siden åpnes, logger den seg stille inn som en anonym bruker hos Firebase.
Brukeren merker ingenting. Det gjør at databasen bare tar imot trafikk fra appen, ikke fra hvem som helst
som sender forespørsler. Selve tilgangen til en husstand styres av lenken.

**Konfigurasjonen** (`firebase-config.js`) forteller nettsiden hvilket Firebase-prosjekt den skal snakke med.
Verdiene i den er ikke hemmelige. Det er reglene som bestemmer hva som er lov.

## Steg 1: Lag Firebase-prosjektet (ca. 10 minutter)

Navnene på knappene i Firebase-konsollen kan avvike litt fra det som står her.

1. Gå til <https://console.firebase.google.com> og logg inn med en Google-konto.
2. **Opprett prosjekt.** Gi det et navn, for eksempel «middagshjulet». Google Analytics trengs ikke.
   Gratisplanen (Spark) holder. Du trenger ikke legge inn betalingskort.
3. **Legg til en web-app:** Trykk på `</>`-ikonet på prosjektforsiden og gi appen et navn.
   Du trenger ikke Firebase Hosting. Kopier `firebaseConfig`-objektet du får opp.
4. Åpne `firebase-config.js` og lim inn verdiene (apiKey, authDomain, projectId osv.) i stedet for plassholderne.
5. **Skru på anonym innlogging:** Authentication → Sign-in method → Anonymous → Enable.
6. **Opprett databasen:** Firestore Database → Create database.
   - Velg **production mode**.
   - Velg plassering **europe-north1 (Finland)** eller **europe-north2 (Stockholm)**.
     Plasseringen kan ikke endres etterpå.
7. **Lim inn reglene:** Firestore Database → Rules. Erstatt alt med innholdet i `firestore.rules`, og trykk **Publish**.

## Steg 2: Legg siden ut på Netlify

1. Legg alle filene i denne mappa i én mappe på maskinen (`index.html`, `firebase-config.js`, `sw.js`,
   `manifest.webmanifest`, ikonene og `_headers`). `LESMEG.md` og `firestore.rules` kan være med, de skader ikke.
2. Dra mappa inn på <https://app.netlify.com/drop>, eller inn i et eksisterende Netlify-prosjekt under «Deploys».
3. Åpne adressen du får, for eksempel `https://middagshjulet.netlify.app`.

## Steg 3: Bruk

1. Trykk **Start en ny husstand** og gå gjennom veiviseren.
2. Gå til **Oppsett → Del med husstanden**. Send lenken til partneren, eller vis QR-koden.
3. Begge åpner lenken og legger siden til på hjemskjermen
   (iPhone: Del → «Legg til på Hjem-skjerm». Android: ⋮ → «Legg til på startsiden»).
   Gjør det **etter** at lenken er åpnet, så husker snarveien husstanden.

Hver ny familie gjør det samme: åpner adressen, trykker «Start en ny husstand» og deler sin egen lenke.

## Godt å vite

- **Gratiskvote:** Spark-planen gir 50 000 lesinger og 20 000 skrivinger i databasen per døgn og 1 GiB lagring
  (Firebase sin prisside, oktober 2026). Det er mye for noen familier. Følg med under Usage i konsollen de første ukene.
- **Mistet lenken?** Den står under Oppsett på alle telefoner som er med i husstanden.
- **Offline:** Appen virker uten nett i butikken. Avkrysninger lagres på telefonen og sendes når nettet er tilbake.
- **Oppdatere appen:** Last opp en ny `index.html` til Netlify. Telefonene henter den neste gang de åpner appen med nett.
- **Personvern:** Dataene er middagsplaner og handlelister, ingen navn utover det dere selv skriver inn.
  De ligger i Googles datasenter i Norden. Si gjerne til kollegaene at dette er et privat hobbyprosjekt.
- **«Foreslå varer»** fra Claude-versjonen er ikke med, fordi det krever en betalt API-nøkkel på en server.

## Automatisk publisering fra GitHub

Nettsiden ligger i mappa `web/` i repoet `Ramandu/middagshjulet`. I stedet for å dra mappa inn i Netlify hver gang, kan Netlify hente den selv:

1. Netlify → nettstedet → **Site configuration → Build & deploy → Link repository**, og velg GitHub-repoet.
2. **Base directory:** `web`. **Publish directory:** `web`. **Build command:** la stå tom.

Da publiseres nettsiden på nytt hver gang noe endres i `web/` på GitHub.

## Light Phone

Nettsiden skriver ukeplanen og handlelista ferdig utregnet til dokumentet `lp3`, som Light Phone-verktøyet leser. Det krever at `firestore.rules` tillater `lp3`. Lim inn reglene på nytt i Firebase-konsollen (Firestore Database → Rules → Publish) etter at de er endret.
