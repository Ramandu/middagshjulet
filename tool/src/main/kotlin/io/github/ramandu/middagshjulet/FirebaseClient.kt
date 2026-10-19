package io.github.ramandu.middagshjulet

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    private val docsBase =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    private fun docUrl(household: String, doc: String) = "$docsBase/households/$household/docs/$doc"

    /** Kjører et innlogget kall. Får vi 401, logger vi inn på nytt og prøver én gang til. */
    private suspend fun authed(call: suspend (String) -> HttpResponse): HttpResponse {
        val res = call(token())
        if (res.status.value != 401) return res
        idToken = null
        refreshToken = null
        return call(token())
    }

    private fun failure(status: Int, reading: Boolean): FetchException = when (status) {
        403 -> FetchException(
            if (reading) "Fikk ikke lov til å lese planen. Sjekk at reglene i Firebase er oppdatert."
            else "Fikk ikke lov til å lagre. Sjekk at reglene i Firebase er oppdatert.",
        )
        else -> FetchException(
            if (reading) "Fikk ikke hentet planen (feil $status)." else "Fikk ikke lagret (feil $status).",
        )
    }

    /** Henter ett dokument. Null hvis det ikke finnes. */
    private suspend fun getDoc(household: String, doc: String): JsonObject? {
        val res = authed { t ->
            http.get(docUrl(household, doc)) {
                header(HttpHeaders.Authorization, "Bearer $t")
                header(HttpHeaders.Referrer, Config.WEB_URL)
            }
        }
        if (res.status.value == 404) return null
        if (res.status.value != 200) throw failure(res.status.value, reading = true)
        return snapshotJson.parseToJsonElement(res.bodyAsText()).jsonObject
    }

    suspend fun fetchSnapshotJson(household: String): String {
        val doc = getDoc(household, "lp3") ?: throw FetchException(
            "Planen er ikke klar ennå. Åpne Middagshjulet på nett én gang, så lages den.",
        )
        return doc.fields()?.get("json")?.jsonObject
            ?.get("stringValue")?.jsonPrimitive?.contentOrNull
            ?: throw FetchException("Planen hadde et format appen ikke kjenner.")
    }

    /** Avkrysningene i ukedokumentet w[w]. Null hvis dokumentet ikke finnes. */
    suspend fun fetchWeekChecks(household: String, w: Int): WeekChecks? {
        val f = getDoc(household, "w$w")?.fields() ?: return null
        val occ = f["occ"]?.jsonObject?.let { v ->
            v["integerValue"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: v["doubleValue"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
        }
        val checked = f["checked"]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
            ?.mapValues { (_, v) -> v.jsonObject["booleanValue"]?.jsonPrimitive?.contentOrNull == "true" }
            .orEmpty()
        return WeekChecks(occ, checked)
    }

    /**
     * Krysser av eller ut én vare, slik nettsiden gjør.
     * Gjelder ukedokumentet en eldre runde, lages det på nytt for denne runden (som på nettsiden).
     * Når en vare som var tom i skapet krysses av, merkes den som «har» i skapet.
     */
    suspend fun setChecked(household: String, w: Int, occ: Int, key: String, done: Boolean, basisId: String) {
        val current = fetchWeekChecks(household, w)
        val boolField = buildJsonObject {
            put("mapValue", buildJsonObject {
                put("fields", buildJsonObject { put(key, buildJsonObject { put("booleanValue", done) }) })
            })
        }
        if (current == null || current.occ != occ) {
            val body = buildJsonObject {
                put("fields", buildJsonObject {
                    put("occ", buildJsonObject { put("integerValue", occ.toString()) })
                    put("checked", boolField)
                    put("extra", buildJsonObject { put("mapValue", buildJsonObject {}) })
                })
            }
            patch(household, "w$w", body, mask = null)
        } else {
            patch(household, "w$w", buildJsonObject { put("fields", buildJsonObject { put("checked", boolField) }) },
                mask = "checked.${quoteField(key)}")
        }
        if (done && basisId.isNotBlank()) {
            val body = buildJsonObject {
                put("fields", buildJsonObject {
                    put("low", buildJsonObject {
                        put("mapValue", buildJsonObject {
                            put("fields", buildJsonObject { put(basisId, buildJsonObject { put("booleanValue", false) }) })
                        })
                    })
                })
            }
            runCatching { patch(household, "basis", body, mask = "low.${quoteField(basisId)}") }
        }
    }

    private suspend fun patch(household: String, doc: String, body: JsonObject, mask: String?) {
        val res = authed { t ->
            http.patch(docUrl(household, doc)) {
                if (mask != null) url.parameters.append("updateMask.fieldPaths", mask)
                header(HttpHeaders.Authorization, "Bearer $t")
                header(HttpHeaders.Referrer, Config.WEB_URL)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        if (res.status.value != 200) throw failure(res.status.value, reading = false)
    }

    private fun JsonObject.fields(): JsonObject? = this["fields"]?.jsonObject

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

/** Feltnavn i Firestore-stier: i `backticks`, med \ foran ` og \. */
internal fun quoteField(name: String): String =
    "`" + name.replace("\\", "\\\\").replace("`", "\\`") + "`"
