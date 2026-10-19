package io.github.ramandu.middagshjulet

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Feil som kan vises direkte til brukeren. */
class FetchException(message: String) : Exception(message)

/**
 * Leser husstandens «lp3»-dokument fra Firestore over REST.
 *
 * Firestore-reglene krever en innlogget bruker, så appen logger inn anonymt,
 * på samme måte som nettsiden gjør. Innloggingen huskes med en refresh-token.
 */
class FirebaseClient(
    private val apiKey: String = Config.FIREBASE_API_KEY,
    private val projectId: String = Config.FIREBASE_PROJECT_ID,
) {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private var idToken: String? = null
    private var idTokenExpiresAt: Long = 0

    /** Lagret refresh-token. Sett før første kall, les etter, så den kan lagres mellom oppstarter. */
    var refreshToken: String? = null

    suspend fun fetchSnapshotJson(household: String): String {
        val token = token()
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/" +
            "households/$household/docs/lp3"
        var res = http.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Referrer, Config.WEB_URL)
        }
        if (res.status.value == 401) {
            // Utløpt eller ugyldig innlogging: logg inn på nytt og prøv én gang til.
            idToken = null
            refreshToken = null
            val fresh = token()
            res = http.get(url) {
                header(HttpHeaders.Authorization, "Bearer $fresh")
                header(HttpHeaders.Referrer, Config.WEB_URL)
            }
        }
        when (res.status.value) {
            200 -> Unit
            404 -> throw FetchException(
                "Planen er ikke klar ennå. Åpne Middagshjulet på nett én gang, så lages den.",
            )
            403 -> throw FetchException(
                "Fikk ikke lov til å lese planen. Sjekk at reglene i Firebase er oppdatert.",
            )
            else -> throw FetchException("Fikk ikke hentet planen (feil ${res.status.value}).")
        }
        val doc = snapshotJson.parseToJsonElement(res.bodyAsText()).jsonObject
        return doc["fields"]?.jsonObject
            ?.get("json")?.jsonObject
            ?.get("stringValue")?.jsonPrimitive?.contentOrNull
            ?: throw FetchException("Planen hadde et format appen ikke kjenner.")
    }

    private suspend fun token(): String {
        val now = System.currentTimeMillis()
        idToken?.let { if (now < idTokenExpiresAt - 60_000) return it }
        refreshToken?.let { rt ->
            runCatching { refresh(rt) }.getOrNull()?.let { return it }
        }
        return signUpAnonymously()
    }

    private suspend fun signUpAnonymously(): String {
        val res = http.post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey") {
            header(HttpHeaders.Referrer, Config.WEB_URL)
            contentType(ContentType.Application.Json)
            setBody("""{"returnSecureToken":true}""")
        }
        val body = ok(res, "logge inn")
        val id = body.str("idToken") ?: throw FetchException("Innloggingen ga ikke noe svar.")
        refreshToken = body.str("refreshToken")
        idToken = id
        idTokenExpiresAt = System.currentTimeMillis() + (body.str("expiresIn")?.toLongOrNull() ?: 3600L) * 1000
        return id
    }

    private suspend fun refresh(rt: String): String? {
        val res = http.submitForm(
            url = "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", rt)
            },
        ) {
            header(HttpHeaders.Referrer, Config.WEB_URL)
        }
        if (res.status.value != 200) return null
        val body = snapshotJson.parseToJsonElement(res.bodyAsText()).jsonObject
        val id = body.str("id_token") ?: return null
        body.str("refresh_token")?.let { refreshToken = it }
        idToken = id
        idTokenExpiresAt = System.currentTimeMillis() + (body.str("expires_in")?.toLongOrNull() ?: 3600L) * 1000
        return id
    }

    private suspend fun ok(res: HttpResponse, what: String): JsonObject {
        val text = res.bodyAsText()
        if (res.status.value != 200) {
            throw FetchException("Klarte ikke å $what (feil ${res.status.value}).")
        }
        return snapshotJson.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    fun close() = http.close()
}
