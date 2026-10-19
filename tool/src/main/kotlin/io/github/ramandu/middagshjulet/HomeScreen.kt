package io.github.ramandu.middagshjulet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch(Dispatchers.IO) {
            Store.init(dataStore)
            // Hent på nytt når appen åpnes, men ikke hver gang man går tilbake hit.
            Store.refresh(minAgeMs = 60_000)
        }
    }

    fun move(delta: Int) {
        _offset.value = (_offset.value + delta).coerceIn(0, 3)
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.IO) { Store.refresh() }
    }

    fun setHousehold(text: String?) {
        val code = text?.let { parseHousehold(it) } ?: return
        _offset.value = 0
        viewModelScope.launch(Dispatchers.IO) { Store.setHousehold(code) }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val state by Store.state.collectAsState()
        val offset by viewModel.offset.collectAsState()

        when {
            !state.loaded -> Page(title = "Middagshjulet", onBack = null) {}
            state.household == null -> SetupContent()
            else -> WeekContent(state, offset)
        }
    }

    @Composable
    private fun SetupContent() {
        Page(title = "Middagshjulet", onBack = null) {
            Paragraph("Her ser du ukeplanen og handlelista fra Middagshjulet.")
            Paragraph(
                "Koble til husstanden: Åpne Middagshjulet på nett, gå til Oppsett og trykk «Vis QR-kode». Skann den her.",
                lighten = true,
            )
            MenuRow("Skann lenke") { scan() }
            MenuRow("Skriv inn koden", "Koden står etter h= i lenken") { typeCode() }
        }
    }

    @Composable
    private fun WeekContent(state: AppState, offset: Int) {
        val today = LocalDate.now()
        val views = state.views(today)
        val view = views.getOrNull(offset)

        val bottom = if (view == null) null else listOf(
            // Pil – tekst – pil: da får «Handleliste» hele bredden i midten.
            if (offset > 0) LightBarButton.LightIcon(icon = LightIcons.BACK, contentDescription = "Forrige uke", onClick = { viewModel.move(-1) }) else null,
            LightBarButton.Text(text = "Handleliste", onClick = { openShop(offset) }),
            if (offset < views.size - 1) LightBarButton.LightIcon(icon = LightIcons.ARROW_RIGHT, contentDescription = "Neste uke", onClick = { viewModel.move(1) }) else null,
        )

        Page(
            title = weekTitle(offset),
            onBack = null,
            right = LightBarButton.LightIcon(icon = LightIcons.SETTINGS, onClick = { openSettings() }),
            bottom = bottom,
        ) {
            if (view == null) {
                Paragraph(
                    when {
                        state.loading -> "Henter planen …"
                        state.error != null -> state.error
                        else -> "Fant ingen plan ennå."
                    },
                )
                MenuRow("Prøv igjen") { viewModel.refreshNow() }
                return@Page
            }

            LightText(
                text = "${shortDate(view.start)} – ${shortDate(view.dateOf(6))}",
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )

            view.week.days.forEachIndexed { i, day ->
                val date = view.dateOf(i)
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable { openDay(offset, i) }
                        .padding(vertical = 0.6f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = (if (isToday) "I dag · " else "") + "${day.s} ${shortDate(date)}" +
                            (if (day.note.isNotBlank()) " · ${day.note}" else ""),
                        variant = LightTextVariant.Detail,
                        lighten = !isToday,
                    )
                    LightText(
                        text = day.n.ifBlank { "–" },
                        variant = LightTextVariant.Copy,
                        underline = isToday,
                    )
                }
            }

            val status = updatedLine(state)
            if (status.isNotBlank()) {
                LightText(
                    text = status,
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier
                        .padding(top = 1f.gridUnitsAsDp())
                        .lightClickable { viewModel.refreshNow() },
                )
            }
        }
    }

    private fun scan() {
        navigateTo(screenFactory = { ScanScreen(it) }, resultCallback = { viewModel.setHousehold(it) })
    }

    private fun typeCode() {
        navigateTo(
            screenFactory = { EditorScreen(it, "Husstandskode") },
            resultCallback = { viewModel.setHousehold(it) },
        )
    }

    private fun openShop(offset: Int) {
        navigateTo<Unit>(screenFactory = { ShopScreen(it, offset) })
    }

    private fun openDay(offset: Int, dayIndex: Int) {
        navigateTo<Unit>(screenFactory = { DayScreen(it, offset, dayIndex) })
    }

    private fun openSettings() {
        navigateTo<Unit>(screenFactory = { SettingsScreen(it) })
    }
}
