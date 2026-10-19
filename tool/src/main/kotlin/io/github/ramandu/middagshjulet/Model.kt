package io.github.ramandu.middagshjulet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Dokumentet «lp3» som nettsiden skriver. Nettsiden regner ut alt, appen bare viser det.
 * Feltnavnene er korte fordi de lagres i Firestore.
 */

@Serializable
data class Snapshot(
    val v: Int = 1,
    val name: String = "",
    /** Handledagen, f.eks. «lørdag». Uka starter på handledagen. */
    val shop: String = "",
    /** Påfyllsdagen, eller tom. */
    val pad: String = "",
    val weeks: List<Week> = emptyList(),
)

@Serializable
data class Week(
    val w: Int,
    /** Hvilken runde uka gjelder (nettsidens absFor). Avkrysninger fra en annen runde gjelder ikke. */
    val occ: Int? = null,
    /** Første dag i uka, ISO-dato (yyyy-mm-dd). */
    val start: String,
    val days: List<Day> = emptyList(),
    val stor: List<Group> = emptyList(),
    val paafyll: List<Group> = emptyList(),
)

@Serializable
data class Day(
    val date: String,
    /** Kort dagnavn, f.eks. «Lør». */
    val s: String = "",
    /** Langt dagnavn, f.eks. «lørdag». */
    val l: String = "",
    /** Rettens navn. */
    val n: String = "",
    /** Tid, f.eks. «30 min». */
    val t: String = "",
    val note: String = "",
    val rest: Boolean = false,
    /** Varer til denne middagen alene. */
    val shop: List<Group> = emptyList(),
)

@Serializable
data class Group(
    /** Avdeling i butikken. */
    val s: String,
    val it: List<Item> = emptyList(),
)

@Serializable
data class Item(
    /** Nøkkelen avkrysningen lagres under i ukedokumentet, f.eks. «stor|i|Løk|stk». */
    val k: String = "",
    /** Id i skapet, hvis varen står på lista fordi den er tom der. */
    val b: String = "",
    val n: String,
    val q: String = "",
    val done: Boolean = false,
    val tag: String = "",
    @SerialName("for") val forDays: String = "",
    val seg: String = "stor",
)

val snapshotJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseSnapshot(text: String): Snapshot = snapshotJson.decodeFromString(Snapshot.serializer(), text)

fun encodeSnapshot(s: Snapshot): String = snapshotJson.encodeToString(Snapshot.serializer(), s)

/** Avkrysningene i et ukedokument (w0–w3) slik de ligger i Firestore. */
data class WeekChecks(val occ: Int?, val checked: Map<String, Boolean>)

private fun Group.mapItems(f: (Item) -> Item) = copy(it = it.map(f))

private fun Week.mapItems(f: (Item) -> Item) = copy(
    stor = stor.map { it.mapItems(f) },
    paafyll = paafyll.map { it.mapItems(f) },
    days = days.map { d -> d.copy(shop = d.shop.map { it.mapItems(f) }) },
)

/**
 * Legger de ferske avkrysningene fra ukedokumentene over planen.
 * Da stemmer lista selv om ingen har åpnet nettsiden siden noen krysset av på telefonen.
 * Uker uten dokument i [checks] beholdes som de er.
 */
fun Snapshot.withChecks(checks: Map<Int, WeekChecks?>): Snapshot = copy(
    weeks = weeks.map { week ->
        if (!checks.containsKey(week.w) || week.occ == null) return@map week
        val c = checks[week.w]
        val valid = c != null && c.occ == week.occ
        week.mapItems { item ->
            if (item.k.isBlank()) item else item.copy(done = valid && c!!.checked[item.k] == true)
        }
    },
)

/** Krysser av (eller ut) varen med nøkkel [key] overalt i uke [w]. */
fun Snapshot.withItemDone(w: Int, key: String, done: Boolean): Snapshot = copy(
    weeks = weeks.map { week ->
        if (week.w != w) week else week.mapItems { if (it.k == key) it.copy(done = done) else it }
    },
)

/**
 * En uke slik den skal vises i dag.
 *
 * Planen går i ring på fire uker. Dokumentet beskriver én runde. Har det gått mer enn fire uker
 * siden nettsiden sist skrev det, flytter vi datoene fram med hele runder, og da gjelder ikke
 * avkrysningene lenger ([checksValid] = false).
 */
data class WeekView(
    val week: Week,
    val start: LocalDate,
    /** 0 = denne uka, 1 = neste uke osv. */
    val offset: Int,
    val checksValid: Boolean,
) {
    /** Om man kan krysse av i denne uka fra telefonen. */
    val canCheck: Boolean get() = checksValid && week.occ != null

    fun dateOf(dayIndex: Int): LocalDate = start.plusDays(dayIndex.toLong())

    fun groups(paafyll: Boolean): List<Group> = visible(if (paafyll) week.paafyll else week.stor)

    fun dayGroups(dayIndex: Int): List<Group> = visible(week.days.getOrNull(dayIndex)?.shop.orEmpty())

    private fun visible(groups: List<Group>): List<Group> =
        if (checksValid) groups
        else groups.map { g -> g.copy(it = g.it.map { it.copy(done = false) }) }
}

/**
 * Lager visningene «denne uka» og de tre neste ut fra [today].
 * Returnerer tom liste hvis dokumentet ikke har fire uker.
 */
fun weekViews(snapshot: Snapshot, today: LocalDate): List<WeekView> {
    val weeks = snapshot.weeks.mapNotNull { w -> runCatching { w to LocalDate.parse(w.start) }.getOrNull() }
    if (weeks.size != 4) return emptyList()
    val cycleStart = weeks.minOf { it.second }
    val diff = ChronoUnit.DAYS.between(cycleStart, today)
    val rounds = Math.floorDiv(diff, 28L)
    val shifted = weeks.map { (w, s) -> Triple(w, s, s.plusDays(28L * rounds)) }
    // Uka som inneholder i dag (etter flytting finnes det alltid nøyaktig én).
    val current = shifted.firstOrNull { (_, _, s) -> !today.isBefore(s) && today.isBefore(s.plusDays(7)) }
        ?: return emptyList()
    val currentStart = current.third
    return (0 until 4).map { offset ->
        val start = currentStart.plusDays(7L * offset)
        // Finn uka i runden som faller på denne datoen (modulo fire uker).
        val match = shifted.first { (_, original, _) ->
            Math.floorMod(ChronoUnit.DAYS.between(original, start), 28L) == 0L
        }
        WeekView(
            week = match.first,
            start = start,
            offset = offset,
            checksValid = start == match.second,
        )
    }
}

private val DATE_RX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/** «17.10.» som på nettsiden. */
fun shortDate(d: LocalDate): String = "${d.dayOfMonth}.${d.monthValue}."

fun shortDate(iso: String): String {
    val m = DATE_RX.matchEntire(iso) ?: return iso
    return "${m.groupValues[3].toInt()}.${m.groupValues[2].toInt()}."
}

fun weekTitle(offset: Int): String = when (offset) {
    0 -> "Denne uka"
    1 -> "Neste uke"
    else -> "Om $offset uker"
}

/** Henter husstandskoden fra en delingslenke (…#h=KODE) eller fra koden alene. */
fun parseHousehold(text: String): String? {
    val t = text.trim()
    Regex("""h=([A-Za-z0-9]{20,40})""").find(t)?.let { return it.groupValues[1] }
    return if (Regex("""^[A-Za-z0-9]{20,40}$""").matches(t)) t else null
}
