package io.github.ramandu.middagshjulet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Felles ramme for alle skjermene: Lights toppfelt, en rullbar kolonne og eventuelt bunnfelt.
 * Bare tekst, i telefonens eget svart-hvitt-tema.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    right: LightTopBarButton? = null,
    bottom: List<LightBottomBarItem?>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                center = LightTopBarCenter.Text(title),
                rightButton = right,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1.5f.gridUnitsAsDp(), end = 0.5f.gridUnitsAsDp()),
            ) {
                content()
                Spacer(Modifier.height(1.5f.gridUnitsAsDp()))
            }
            if (bottom != null) LightBottomBar(items = bottom)
        }
    }
}

/** Et trykkbart valg i en liste, som i LightOS sine egne menyer. */
@Composable
fun MenuRow(text: String, detail: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(text = text, variant = LightTextVariant.Copy)
        if (!detail.isNullOrBlank()) {
            LightText(text = detail, variant = LightTextVariant.Detail, lighten = true)
        }
    }
}

@Composable
fun Paragraph(text: String, lighten: Boolean = false, modifier: Modifier = Modifier) {
    LightText(
        text = text,
        variant = LightTextVariant.Paragraph,
        lighten = lighten,
        modifier = modifier.padding(bottom = 1f.gridUnitsAsDp()),
    )
}

@Composable
fun SectionLabel(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = 1.25f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
    )
}

/** Handleliste gruppert etter avdeling. Kjøpte varer er gjennomstreket. */
@Composable
fun ShoppingGroups(groups: List<Group>, showDays: Boolean) {
    groups.forEach { g ->
        SectionLabel(g.s)
        g.it.forEach { item -> ShoppingItem(item, showDays) }
    }
}

@Composable
private fun ShoppingItem(item: Item, showDays: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.4f.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(
                text = item.n,
                variant = LightTextVariant.Copy,
                lighten = item.done,
                strikethrough = item.done,
            )
            val extra = listOfNotNull(
                item.forDays.takeIf { showDays && it.isNotBlank() },
                tagLabel(item.tag),
            ).joinToString(" · ")
            if (extra.isNotBlank()) {
                LightText(text = extra, variant = LightTextVariant.Fine, lighten = true)
            }
        }
        if (item.q.isNotBlank()) {
            LightText(
                text = item.q,
                variant = LightTextVariant.Detail,
                lighten = item.done,
                strikethrough = item.done,
                modifier = Modifier.padding(start = 1f.gridUnitsAsDp()),
            )
        }
    }
}

private fun tagLabel(tag: String): String? = when (tag) {
    "" -> null
    "fast" -> "fast vare"
    "tomt" -> "tomt i skapet"
    "ekstra" -> null
    "påfyll-vare" -> "påfyll"
    else -> tag
}

fun countLine(groups: List<Group>): String {
    val all = groups.flatMap { it.it }
    if (all.isEmpty()) return ""
    return "${all.count { it.done }} av ${all.size} i kurven"
}

fun updatedLine(state: AppState): String {
    if (state.loading) return "Henter planen …"
    state.error?.let { return it }
    if (state.fetchedAt <= 0) return ""
    val t = Instant.ofEpochMilli(state.fetchedAt).atZone(ZoneId.systemDefault())
    val hm = "%02d:%02d".format(t.hour, t.minute)
    return if (t.toLocalDate() == LocalDate.now()) "Oppdatert kl. $hm" else "Oppdatert ${shortDate(t.toLocalDate())} kl. $hm"
}

/** «lørdag 17.10.» */
fun dayAndDate(view: WeekView, dayIndex: Int): String {
    val day = view.week.days.getOrNull(dayIndex)
    return "${day?.l.orEmpty()} ${shortDate(view.dateOf(dayIndex))}".trim()
}
