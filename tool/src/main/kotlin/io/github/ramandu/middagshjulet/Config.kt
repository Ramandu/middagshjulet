package io.github.ramandu.middagshjulet

/**
 * Firebase-prosjektet som nettsiden bruker (samme verdier som i web/firebase-config.js).
 * Verdiene er ikke hemmelige. Det er reglene i web/firestore.rules som bestemmer hva som er lov,
 * og husstandskoden (som bare finnes i delingslenken) bestemmer hvilken plan appen leser.
 */
object Config {
    const val FIREBASE_API_KEY = "AIzaSyCoJHPceEnLvnOSFaFTnh87JI69CG_QCIk"
    const val FIREBASE_PROJECT_ID = "middagshjulet-30f12"

    /** Nettsiden. Sendes som Referer i tilfelle API-nøkkelen er begrenset til den. */
    const val WEB_URL = "https://middagshjulet.netlify.app/"
}
