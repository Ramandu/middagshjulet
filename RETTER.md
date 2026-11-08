# Forslag til nye retter i hovedbiblioteket

Retter som foreslås til de innebygde rettene i appen (`D` i `web/index.html`), som alle husstander har. Lista er kuratert av eieren av repoet. Andre husstander legger inn egne retter i appen.

## Slik gjør vi det

1. **Forslag:** send lenken til oppskriften, så føres den opp under «Forslag» med dato. Kan ikke siden leses fra skymiljøet, sender du et skjermbilde eller limer inn ingredienslisten.
2. **Inn i appen:** retten bygges inn i grenen `neste` og slippes med neste versjon (se `CLAUDE.md`).
   - Navnet er norsk og beskrivende, slik som de andre rettene.
   - Mengdene regnes om til 5 porsjoner. Rettene er lagret for 5, og appen skalerer til husstanden. Står det ikke antall porsjoner i oppskriften, skrives det i merknaden hvordan det er regnet.
   - Varer som må kjøpes, legges under `i` med avdeling. Ferskvarer som ikke holder hele uka, merkes som ferske. Det man vanligvis har hjemme (krydder, olje, ris, sauser på flaske osv.), legges under `k`.
   - Hvis det passer, får retten kjøkken (`c`, f.eks. «Thai»), tagger (`veg`, `fisk`, `helg`, `tex`, `ny`), tid (`t`) og lenke (`url`).
   - Bare navn, mengder og varer tas med, ikke fremgangsmåten. Lenken viser til oppskriften.
   - Kommer oppskriften fra en bok, står boka og oppskriftens tittel i merknaden i stedet for en lenke.
   - ID-en (nøkkelen i `D`) må være ny. Sjekk at den ikke finnes fra før, f.eks. finnes `tagine` allerede. En ID som er brukt to ganger, overskriver den andre retten uten feilmelding.
3. **Sluppet:** retten flyttes ned til «I appen» med versjonsdato.

Flere retter samles før de sendes inn til `neste` i én pull request.

## Forslag

| Dato | Rett | Lenke | Merknad |
|---|---|---|---|

## Bygget inn, kommer med neste versjon

| Dato | Rett i appen | Lenke | Merknad |
|---|---|---|---|
| 2026-11-08 | Larb (laotisk kjøttsalat med svin), `larb` | https://detgladekjokken.no/oppskrift/thai-larb/ | Regnet om fra 4 til 5 porsjoner. Ingrediensene er hentet fra et skjermbilde av oppskriften. |
| 2026-11-08 | Lammetagine med rosiner og mandler, `lammetagine` | Kokebok: «Lamb Tagine with Raisins and Almonds, Tiznit-Style» | For 5–6 personer, mengdene står som i boka. Fra et bilde av boksiden. Bokas tittel er ikke kjent. |

## I appen

Ingen ennå.
