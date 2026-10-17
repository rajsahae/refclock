package com.refclock.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilesTest {
    private val min = 60_000L
    private val ayso10 = Profiles.defaults.single { it.id == "ayso-10u" }
    private val ayso12 = Profiles.defaults.single { it.id == "ayso-12u" }

    @Test
    fun breakLeavesEqualQuartersAroundTheWaterBreak() {
        assertEquals(12 * min, ayso10.breakAtMs)
        assertEquals(14 * min + 30_000, ayso12.breakAtMs)
        assertEquals(15_000L, Profiles.test.breakAtMs)
    }

    @Test
    fun aysoDefaultsHaveFiveMinuteHalftime() {
        assertEquals(listOf(5 * min, 5 * min), Profiles.defaults.map { it.halftimeLengthMs })
    }

    @Test
    fun noBreakMeansOnlyTheHalfEndAlert() {
        val noBreak = ayso10.copy(breakLengthMs = null)
        assertNull(noBreak.breakAtMs)
        assertEquals(listOf(AlertKind.HALF_END), noBreak.alerts.map { it.kind })
        assertEquals(listOf(AlertKind.CHECKPOINT, AlertKind.HALF_END), ayso10.alerts.map { it.kind })
    }

    @Test
    fun jsonRoundTrip() {
        val custom = Profile("abc", "FIFA", 45 * min, 15 * min, breakLengthMs = null)
        val list = Profiles.defaults + custom
        assertEquals(list, Profiles.decodeList(Profiles.encodeList(list)))
        assertEquals(custom, Profiles.decode(Profiles.encode(custom)))
    }

    @Test
    fun unreadableOrEmptyListDecodesToNull() {
        assertNull(Profiles.decodeList("not json"))
        assertNull(Profiles.decodeList("[]"))
        assertNull(Profiles.decode("{}"))
    }

    @Test
    fun savingReplacesByIdOrAppends() {
        val renamed = ayso10.copy(name = "U10")
        assertEquals(listOf(renamed, ayso12), Profiles.withSaved(Profiles.defaults, renamed))
        val custom = Profile("new", "New", 20 * min, 5 * min)
        assertEquals(Profiles.defaults + custom, Profiles.withSaved(Profiles.defaults, custom))
    }

    @Test
    fun theLastProfileCannotBeDeleted() {
        assertEquals(listOf(ayso12), Profiles.withDeleted(Profiles.defaults, ayso10.id))
        assertEquals(listOf(ayso12), Profiles.withDeleted(listOf(ayso12), ayso12.id))
    }
}
