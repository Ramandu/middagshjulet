# Middagshjulet

Fire uker med middager som går i ring, og handlelister som lager seg selv.

Repoet har to deler:

| Mappe | Hva |
|---|---|
| `web/` | Nettsiden (middagshjulet.netlify.app). Her planlegger og endrer dere. Firebase holder lista felles for husstanden. |
| `tool/` | Verktøyet for **Light Phone III**. Viser ukeplanen og handlelista, uten nettleser. Bare lesing. |

Resten (`sdk/`, `plugin/`, `gradle/` osv.) er [Light SDK](https://github.com/lightphone/light-sdk) (MIT), som verktøyet bygges med. Se `README-light-sdk.md`.

## Light Phone III

### Installere

1. Installer [Obtainium](https://github.com/ImranR98/Obtainium) på telefonen.
2. Legg til appen med adressen `https://github.com/Ramandu/middagshjulet`.
3. Åpne **Middagshjulet** på telefonen og velg **Skann lenke**.
4. På nettsiden: **Oppsett → Del med husstanden → Vis QR-kode**. Skann den.

LightOS sier at verktøyet ikke er signert av Light. Det er forventet: det er bygget her, ikke hos Light.

### Slik virker det

LP3-verktøyet regner ikke ut noe selv. Nettsiden skriver et ferdig utregnet dokument (`lp3`) til Firebase hver gang planen eller lista endres. Verktøyet logger inn anonymt, leser dokumentet og lagrer det på telefonen, så det virker uten nett med det det sist hentet.

Planen går i ring på fire uker. Har ingen åpnet nettsiden på lenge, flytter verktøyet datoene fram selv. Avkrysningene vises bare for uker nettsiden har skrevet.

### Ny versjon

Øk `versionCode` og `versionName` i `tool/lighttool.toml`. GitHub Actions bygger APK-en og lager en release, som Obtainium henter.

## Nettsiden

Se `web/LESMEG.md`. Firestore-reglene ligger i `web/firestore.rules` og må limes inn i Firebase-konsollen når de endres.
