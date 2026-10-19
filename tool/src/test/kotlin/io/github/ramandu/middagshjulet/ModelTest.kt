package io.github.ramandu.middagshjulet

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    // Slik nettsiden skriver det: uka som går nå (Uke 4) først i tid, de andre etter.
    private val json = """
        {"v":1,"name":"","shop":"lørdag","pad":"mandag","weeks":[
         {"w":0,"start":"2026-10-24","days":[{"date":"2026-10-24","s":"Lør","l":"lørdag","n":"Pizza","t":"1 t","note":"","rest":false,"shop":[]}],
          "stor":[{"s":"Frukt og grønt","it":[{"n":"Paprika","q":"1 stk","done":true,"tag":"","for":"Lør","seg":"stor"}]}],"paafyll":[]},
         {"w":1,"start":"2026-10-31","days":[],"stor":[],"paafyll":[]},
         {"w":2,"start":"2026-11-07","days":[],"stor":[],"paafyll":[]},
         {"w":3,"start":"2026-10-17","days":[],"stor":[],"paafyll":[],"ekstra":"ukjent felt"}
        ]}
    """.trimIndent()

    @Test
    fun parsesSnapshotAndIgnoresUnknownFields() {
        val s = parseSnapshot(json)
        assertEquals(4, s.weeks.size)
        assertEquals("Paprika", s.weeks[0].stor[0].it[0].n)
        assertEquals("Lør", s.weeks[0].stor[0].it[0].forDays)
    }

    @Test
    fun currentWeekIsTheOneContainingToday() {
        val views = weekViews(parseSnapshot(json), LocalDate.of(2026, 10, 19))
        assertEquals(4, views.size)
        assertEquals(3, views[0].week.w)
        assertEquals(LocalDate.of(2026, 10, 17), views[0].start)
        assertEquals(0, views[1].week.w)
        assertEquals(LocalDate.of(2026, 10, 24), views[1].start)
        assertTrue(views.all { it.checksValid })
        assertTrue(views[1].groups(false)[0].it[0].done)
    }

    @Test
    fun lastWeekOfRoundWrapsToFirstWeekOfNextRound() {
        val views = weekViews(parseSnapshot(json), LocalDate.of(2026, 11, 10))
        assertEquals(2, views[0].week.w)
        assertEquals(3, views[1].week.w)
        assertEquals(LocalDate.of(2026, 11, 14), views[1].start)
        assertFalse(views[1].checksValid)
    }

    @Test
    fun oldSnapshotIsShiftedByWholeRoundsAndHidesChecks() {
        val views = weekViews(parseSnapshot(json), LocalDate.of(2026, 11, 24))
        assertEquals(0, views[0].week.w)
        assertEquals(LocalDate.of(2026, 11, 21), views[0].start)
        assertFalse(views[0].checksValid)
        assertFalse(views[0].groups(false)[0].it[0].done)
    }

    @Test
    fun parsesHouseholdFromLinkOrCode() {
        assertEquals("AbCdEfGhIjKlMnOpQrStUvWx", parseHousehold("https://middagshjulet.netlify.app/#h=AbCdEfGhIjKlMnOpQrStUvWx"))
        assertEquals("AbCdEfGhIjKlMnOpQrStUvWx", parseHousehold("  AbCdEfGhIjKlMnOpQrStUvWx "))
        assertNull(parseHousehold("https://example.com"))
        assertNull(parseHousehold("kort"))
    }

    @Test
    fun formatsDatesLikeTheWebsite() {
        assertEquals("17.10.", shortDate("2026-10-17"))
        assertEquals("1.11.", shortDate(LocalDate.of(2026, 11, 1)))
    }
}
