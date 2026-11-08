# Veiplan for Middagshjulet

Ideer og planer for nettsiden og Light Phone-verktøyet. Endringer samles i `neste` og slippes samlet til `main` (se `CLAUDE.md`).

## Neste versjon

- **Nye retter fra `RETTER.md`:** foreløpig larb. Flere samles før de sendes inn.

## Vurderes for neste versjon

- **Service workeren skal ikke lagre feilsider.** Hvis Netlify svarer med en feil (f.eks. hvis prosjektet settes på pause fordi gratiskredittene er brukt opp), skal appen bruke den lagrede versjonen i stedet for å lagre og vise feilsiden.

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

### Skanner for medlemskort
En skanneknapp der telefonen støtter det (`BarcodeDetector`, bl.a. Chrome på Android), så man slipper å skrive inn eller lime inn innholdet. Safari på iPhone støtter det ikke, så inntasting må finnes uansett.

## Kanskje

- **Del én rett med en lenke eller QR-kode**, uten felles bibliotek. Dekkes trolig av delingen over.

## Sluppet

- **2026-11-07:** medlemskort under handlelista, og riktigere avdeling for varer i egne retter (#7).
- **2026-11-04:** husarbeid (#4), kompakte oppgavekort (#5), «Sammen» og «Den som kan» (#6).
- **2026-11-04:** hvitløk og løk under Frukt og grønt når de er tomme i skapet (#3).
- **2026-10-31:** flytte varer mellom storhandel og påfyll, og «Nytt i Middagshjulet» (#1). Nyhetsvinduet hakker ikke lenger (#2).
