package io.github.ramandu.middagshjulet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
            val json = client.fetchSnapshotJson(household)
            val snap = parseSnapshot(json)
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
