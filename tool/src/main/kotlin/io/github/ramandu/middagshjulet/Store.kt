package io.github.ramandu.middagshjulet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class AppState(
    val loaded: Boolean = false,
    val household: String? = null,
    val snapshot: Snapshot? = null,
    /** Når planen sist ble hentet, millisekunder. 0 = aldri. */
    val fetchedAt: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun views(today: LocalDate = LocalDate.now()): List<WeekView> =
        snapshot?.let { weekViews(it, today) }.orEmpty()
}

/**
 * Felles tilstand for alle skjermene: husstandskoden, siste plan og innloggingen.
 * Planen lagres på telefonen, så appen virker uten nett med det den sist hentet.
 */
object Store {
    private val HOUSEHOLD = stringPreferencesKey("household")
    private val SNAPSHOT = stringPreferencesKey("snapshot")
    private val FETCHED_AT = longPreferencesKey("fetchedAt")
    private val REFRESH_TOKEN = stringPreferencesKey("refreshToken")

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var dataStore: DataStore<Preferences>? = null
    private val client = FirebaseClient()
    private val mutex = Mutex()

    suspend fun init(ds: DataStore<Preferences>) = mutex.withLock {
        if (dataStore != null) return@withLock
        dataStore = ds
        val prefs = ds.data.first()
        client.refreshToken = prefs[REFRESH_TOKEN]
        val snap = prefs[SNAPSHOT]?.let { runCatching { parseSnapshot(it) }.getOrNull() }
        _state.value = AppState(
            loaded = true,
            household = prefs[HOUSEHOLD],
            snapshot = snap,
            fetchedAt = prefs[FETCHED_AT] ?: 0,
        )
    }

    /** Henter planen på nytt. Gjør ingenting hvis det ble gjort for under [minAgeMs] siden. */
    suspend fun refresh(minAgeMs: Long = 0) {
        val ds = dataStore ?: return
        val household = _state.value.household ?: return
        if (_state.value.loading) return
        if (minAgeMs > 0 && System.currentTimeMillis() - _state.value.fetchedAt < minAgeMs) return
        _state.update { it.copy(loading = true, error = null) }
        try {
            val (base, checks) = coroutineScope {
                val snapJob = async { parseSnapshot(client.fetchSnapshotJson(household)) }
                // Avkrysningene leses rett fra ukedokumentene, så de er ferske selv om
                // ingen har hatt nettsiden åpen siden noen krysset av på telefonen.
                val weekJobs = (0 until 4).map { w -> w to async { runCatching { client.fetchWeekChecks(household, w) } } }
                val snap = snapJob.await()
                val checks: Map<Int, WeekChecks?> = weekJobs.mapNotNull { (w, job) -> job.await().fold({ w to it }, { null }) }.toMap()
                snap to checks
            }
            val snap = base.withChecks(checks)
            val json = encodeSnapshot(snap)
            val now = System.currentTimeMillis()
            ds.edit {
                it[SNAPSHOT] = json
                it[FETCHED_AT] = now
                client.refreshToken?.let { rt -> it[REFRESH_TOKEN] = rt }
            }
            _state.update { it.copy(snapshot = snap, fetchedAt = now, loading = false, error = null) }
        } catch (e: FetchException) {
            saveToken(ds)
            _state.update { it.copy(loading = false, error = e.message) }
        } catch (e: Exception) {
            saveToken(ds)
            _state.update {
                it.copy(
                    loading = false,
                    error = if (it.snapshot != null) "Uten nett. Viser planen fra sist." else
                        "Fikk ikke kontakt. Sjekk nettet og prøv igjen.",
                )
            }
        }
    }

    private suspend fun saveToken(ds: DataStore<Preferences>) {
        val rt = client.refreshToken ?: return
        runCatching { ds.edit { it[REFRESH_TOKEN] = rt } }
    }

    /**
     * Krysser av eller ut en vare. Vises med en gang, og lagres så på nett.
     * Går lagringen ikke, settes varen tilbake og det vises en melding.
     */
    suspend fun toggle(view: WeekView, item: Item) {
        val ds = dataStore ?: return
        val household = _state.value.household ?: return
        val occ = view.week.occ ?: return
        if (!view.checksValid || item.k.isBlank()) return
        val w = view.week.w
        val done = !item.done
        setLocal(ds) { it.withItemDone(w, item.k, done) }
        try {
            client.setChecked(household, w, occ, item.k, done, item.b)
            saveToken(ds)
        } catch (e: Exception) {
            setLocal(ds) { it.withItemDone(w, item.k, !done) }
            _state.update {
                it.copy(error = (e as? FetchException)?.message ?: "Fikk ikke lagret. Sjekk nettet og prøv igjen.")
            }
        }
    }

    private suspend fun setLocal(ds: DataStore<Preferences>, change: (Snapshot) -> Snapshot) {
        val snap = mutex.withLock {
            val current = _state.value.snapshot ?: return
            val next = change(current)
            _state.update { it.copy(snapshot = next, error = null) }
            next
        }
        runCatching { ds.edit { it[SNAPSHOT] = encodeSnapshot(snap) } }
    }

    suspend fun setHousehold(code: String) {
        val ds = dataStore ?: return
        ds.edit {
            it[HOUSEHOLD] = code
            it.remove(SNAPSHOT)
            it.remove(FETCHED_AT)
        }
        _state.update { it.copy(household = code, snapshot = null, fetchedAt = 0, error = null) }
        refresh()
    }

    suspend fun forgetHousehold() {
        val ds = dataStore ?: return
        ds.edit {
            it.remove(HOUSEHOLD)
            it.remove(SNAPSHOT)
            it.remove(FETCHED_AT)
        }
        _state.update { AppState(loaded = true) }
    }
}
