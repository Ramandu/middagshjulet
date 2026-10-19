package io.github.ramandu.middagshjulet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------- Handleliste for en uke ----------

class ShopViewModel : LightViewModel<Unit>() {
    private val _paafyll = MutableStateFlow(false)
    val paafyll: StateFlow<Boolean> = _paafyll.asStateFlow()
    fun show(paafyll: Boolean) { _paafyll.value = paafyll }
}

class ShopScreen(sealedActivity: SealedLightActivity, private val offset: Int) :
    LightScreen<Unit, ShopViewModel>(sealedActivity) {

    override val viewModelClass: Class<ShopViewModel> get() = ShopViewModel::class.java
    override fun createViewModel() = ShopViewModel()

    @Composable
    override fun Content() {
        val state by Store.state.collectAsState()
        val paafyll by viewModel.paafyll.collectAsState()
        val view = state.views().getOrNull(offset)
        val snap = state.snapshot
        val hasPad = view != null && snap != null && snap.pad.isNotBlank() && view.week.paafyll.isNotEmpty()
        val showPad = paafyll && hasPad

        // Valgt fane vises i [klammer], som i LightOS.
        val bottom = if (!hasPad) null else listOf(
            LightBarButton.Text(text = if (!showPad) "[Storhandel]" else "Storhandel", onClick = { viewModel.show(false) }),
            LightBarButton.Text(text = if (showPad) "[Påfyll]" else "Påfyll", onClick = { viewModel.show(true) }),
        )

        Page(title = "Handleliste", onBack = { goBack() }, bottom = bottom) {
            if (view == null || snap == null) {
                Paragraph("Fant ingen plan.")
                return@Page
            }
            val groups = view.groups(showPad)
            val padIndex = view.week.days.indexOfFirst { it.l == snap.pad }
            val heading = if (showPad && padIndex >= 0) "Påfyll ${dayAndDate(view, padIndex)}"
            else "Storhandel ${dayAndDate(view, 0)}"
            LightText(text = heading, variant = LightTextVariant.Copy)
            val count = countLine(groups)
            if (count.isNotBlank()) {
                LightText(text = count, variant = LightTextVariant.Detail, lighten = true)
            }
            if (!view.checksValid) {
                LightText(
                    text = "Avkrysninger vises når uka er i gang på nettsiden.",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                )
            }
            if (groups.isEmpty()) {
                Paragraph("Lista er tom.", lighten = true, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
            } else {
                ShoppingGroups(groups, showDays = true)
            }
        }
    }
}

// ---------- Én dag: middagen og det den trenger ----------

class DayScreen(sealedActivity: SealedLightActivity, private val offset: Int, private val dayIndex: Int) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val state by Store.state.collectAsState()
        val view = state.views().getOrNull(offset)
        val day = view?.week?.days?.getOrNull(dayIndex)

        Page(title = view?.let { dayAndDate(it, dayIndex) } ?: "Middag", onBack = { goBack() }) {
            if (view == null || day == null) {
                Paragraph("Fant ingen plan.")
                return@Page
            }
            LightText(text = day.n.ifBlank { "–" }, variant = LightTextVariant.Heading)
            val meta = listOf(day.t, day.note).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                LightText(
                    text = meta,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                )
            }
            val groups = view.dayGroups(dayIndex)
            if (day.rest) {
                Paragraph(
                    "Ingen handling til denne dagen.",
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            } else if (groups.isEmpty()) {
                Paragraph(
                    "Alt til denne middagen står i skapet.",
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            } else {
                SectionLabel("Handle til denne middagen")
                ShoppingGroups(groups, showDays = false)
            }
        }
    }
}

// ---------- Innstillinger ----------

class SettingsViewModel : LightViewModel<Unit>() {
    fun refresh() { viewModelScope.launch(Dispatchers.IO) { Store.refresh() } }
    fun setHousehold(text: String?) {
        val code = text?.let { parseHousehold(it) } ?: return
        viewModelScope.launch(Dispatchers.IO) { Store.setHousehold(code) }
    }
    fun forget() { viewModelScope.launch(Dispatchers.IO) { Store.forgetHousehold() } }
}

class SettingsScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel> get() = SettingsViewModel::class.java
    override fun createViewModel() = SettingsViewModel()

    @Composable
    override fun Content() {
        val state by Store.state.collectAsState()
        var confirmForget by remember { mutableStateOf(false) }

        Page(title = "Innstillinger", onBack = { goBack() }) {
            val hh = state.household
            LightText(
                text = if (hh != null) "Husstand ${hh.take(4)}…${hh.takeLast(4)}" else "Ingen husstand",
                variant = LightTextVariant.Copy,
            )
            val status = updatedLine(state)
            if (status.isNotBlank()) LightText(text = status, variant = LightTextVariant.Detail, lighten = true)
            SectionLabel(" ")
            MenuRow("Oppdater nå") { viewModel.refresh() }
            MenuRow("Skann ny lenke") {
                navigateTo(screenFactory = { ScanScreen(it) }, resultCallback = { viewModel.setHousehold(it) })
            }
            MenuRow("Skriv inn koden") {
                navigateTo(
                    screenFactory = { EditorScreen(it, "Husstandskode") },
                    resultCallback = { viewModel.setHousehold(it) },
                )
            }
            if (hh != null) {
                if (!confirmForget) {
                    MenuRow("Glem husstanden", "Fjerner koden og planen fra telefonen") { confirmForget = true }
                } else {
                    MenuRow("Ja, glem husstanden", "Planen blir liggende på nett") {
                        confirmForget = false
                        viewModel.forget()
                        goBack()
                    }
                    MenuRow("Avbryt") { confirmForget = false }
                }
            }
            SectionLabel(" ")
            Paragraph(
                "Planen endrer du på middagshjulet.netlify.app. Appen henter den nye planen når du åpner den.",
                lighten = true,
            )
        }
    }
}

// ---------- Skanne QR-kode med delingslenken ----------

class ScanScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<String>(sealedActivity) {
    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pending by remember { mutableStateOf<String?>(null) }
        LightTheme(colors = themeColors) {
            LightQrCodeScanner(
                title = "Skann lenken",
                onScanned = { pending = it },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
        LaunchedEffect(pending) {
            val value = pending ?: return@LaunchedEffect
            pending = null
            goBack(value)
        }
    }
}

// ---------- Skrive inn tekst med Lights tastatur ----------

class EditorScreen(sealedActivity: SealedLightActivity, private val title: String) :
    SimpleLightScreen<String>(sealedActivity) {
    @Composable
    override fun Content() {
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val textState = rememberTextFieldState("")
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "OK",
                singleLine = true,
            )
        }
    }
}
