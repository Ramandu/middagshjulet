# Veiplan for Middagshjulet

Ideer og planer for nettsiden og Light Phone-verktøyet. Endringer samles i `neste` og slippes samlet til `main` (se `CLAUDE.md`).

## Neste versjon

- **Foreslå en rett til alle:** skjema under Rettene som sender lenke, navn, kommentar og avsender til Netlify Forms («forslag»). Lenker som finnes fra før, stoppes i appen. Eieren kuraterer (se `CLAUDE.md`).
- **Fullstendige oppskrifter for retter uten lenke:** 16 retter har fått oppskrift fra Matprat eller Tine, med ingredienser og mengder derfra (regnet om til 5) og skapvarer. Den gamle tabellen `SKAP` beholder bare husstandens vaner. Worcestershiresaus står i standardskapet. Fortsatt uten lenke: Saritas korma (ferdig saus), lammetagine (bok) og kyllingformen (ingen oppskrift funnet).
- **Tøm fryseren:** under Skapet skriver husstanden inn hva som ligger i fryseren (`basis.frys`). «Tøm fryseren» foreslår retter dere har som bruker varene (gjenkjenning på ord og ordstart), og setter dem inn i planen denne eller neste uke (bare den uka, `day.frys`). Varene fra fryseren kommer ikke på handlelista den dagen, og kan tas ut av fryseren.

## Vurderes for neste versjon

Ingenting nå.

## Senere

### Deling av retter mellom husstander
Utvide fanen «Rettene» med en sammenfoldet seksjon, «Fra andre husstander», der man kan legge til retter andre har delt.

- **Samtykke** i Oppsett: «Del egne retter med andre som bruker Middagshjulet», av som standard. Husstanden velger hvilket navn som vises, eller «Anonym».
- **Hva som deles:** bare egne retter med lenke til oppskriften (navn, tagger, tid, porsjoner og varer, ikke oppskriftsteksten). Eventuelt kan man velge per rett.
- **Duplikater:** sjekkes mot nettadressen (renset for `utm_` o.l., `www`, `http`/`https`) og mot navnet. Retter man har fra før, merkes «Har dere». Samme oppskrift delt av flere vises én gang.
- **Lagring:** ett dokument per delende husstand, under en offentlig ID som ikke avslører husstandslenken.
- **Portvakt (valgt løsning):** en liten Netlify-funksjon sjekker husstandslenken og samtykket, og er den eneste som kan skrive. Da kan alle telefonene i husstanden dele og trekke tilbake.
  - Det krever en tjenestenøkkel fra Firebase, lagt inn som hemmelig miljøvariabel i Netlify, og nye Firestore-regler.
  - Alternativet uten server (telefonen som deler, eier delingen) ble vurdert som for skjørt.
- **Moderering:** hver husstand kan skjule en annen husstand for seg selv. Eieren kan fjerne innhold i Firebase-konsollen.

### Husarbeid på Light Phone
Vise dagens oppgaver fra husarbeid i Light Phone-verktøyet. Det krever endringer både i `lp3`-dokumentet og i `tool/`.

### Faktiske mengder i de innebygde rettene
Retter uten lenke er gjort (se «Neste versjon»). Det som gjenstår, er rettene som allerede hadde lenke.
Rundt 100 varer i de innebygde rettene står i pakninger (pose, pakke, potte, beger, glass, bunt). Der oppskriften bruker mindre enn en pakning, skal de få den faktiske mengden, slik at dobling og flere retter gir riktige mengder. Begynn med det som oftest gir rare mengder: spinat, urter i potte, revet ost, rømme og crème fraîche. Butter chicken er rettet (spinat 65 g for 4).

- 32 av 52 innebygde retter har lenke til oppskriften. For å lese dem må skymiljøet ha tilgang til disse nettstedene: `www.matprat.no` (21 retter), `vegetarentusiast.no`, `www.tine.no`, `www.saritas.no`, `www.etkjokken.com`, `gladkokken.no`, `detgladekjokken.no` og `aichasmat.no`. Eieren må godkjenne det fra en PC (det gikk ikke fra telefonen 2026-11-14). Spør igjen når eieren sitter ved PC.
- De 20 rettene uten lenke trenger skjermbilde eller innlimt ingrediensliste.

### Skanner for medlemskort
En skanneknapp der telefonen støtter det (`BarcodeDetector`, bl.a. Chrome på Android), så man slipper å skrive inn eller lime inn innholdet. Safari på iPhone støtter det ikke, så inntasting må finnes uansett.

## Kanskje

- **Del én rett med en lenke eller QR-kode**, uten felles bibliotek. Dekkes trolig av delingen over.

## Sluppet

- **2026-11-24:** nye retter (larb og lammetagine), oppskriften i appen med fremgangsmåte som kan redigeres, endringer i innebygde retter lagrer bare det som er endret, hele pakker i handlelista, butter chicken med spinat i gram, endre planene i siste liten (flytt, utsett og «Bytt» bare denne uka), skapvarer rett fra handlelista (østerssaus og tamarindsaus i standardskapet), og service workeren lagrer ikke feilsider (#9–#12).
- **2026-11-07:** medlemskort under handlelista, og riktigere avdeling for varer i egne retter (#7).
- **2026-11-04:** husarbeid (#4), kompakte oppgavekort (#5), «Sammen» og «Den som kan» (#6).
- **2026-11-04:** hvitløk og løk under Frukt og grønt når de er tomme i skapet (#3).
- **2026-10-31:** flytte varer mellom storhandel og påfyll, og «Nytt i Middagshjulet» (#1). Nyhetsvinduet hakker ikke lenger (#2).
