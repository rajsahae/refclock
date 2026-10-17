package com.refclock.match

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val halfLengthMs: Long,
    val halftimeLengthMs: Long,
    /** The mid-half water break, or null for none. */
    val breakLengthMs: Long? = null,
    /** Listed only while the watch has Developer options turned on. */
    val devOnly: Boolean = false,
) {
    /**
     * The clock keeps running through the break and both quarters get equal playing time, so the
     * break starts at (half - break) / 2: 12:00 in a 25-minute half with a 1-minute break.
     */
    val breakAtMs: Long?
        get() = breakLengthMs?.let { (halfLengthMs - it) / 2 }

    /** Alerts during a half, in order. */
    val alerts: List<Alert>
        get() = listOfNotNull(
            breakAtMs?.let { Alert(it, "Quarter break", AlertKind.CHECKPOINT) },
            Alert(halfLengthMs, "Half ends", AlertKind.HALF_END),
        )

    val halftimeAlert: Alert
        get() = Alert(halftimeLengthMs, "Halftime over", AlertKind.HALFTIME_END)

    fun nextAlert(halfElapsedMs: Long): Alert? = alerts.firstOrNull { it.atMs > halfElapsedMs }
}

object Profiles {
    private const val MINUTE = 60_000L

    /** Used until the user saves a change, and restored by "Reset to defaults". The IDs predate profiles. */
    val defaults = listOf(
        ayso(id = "ayso-10u", name = "AYSO 10U", halfMinutes = 25),
        ayso(id = "ayso-12u", name = "AYSO 12U", halfMinutes = 30),
    )

    /** For feeling the vibration patterns without waiting out a real half. Built in, never stored. */
    val test = Profile(
        id = "test",
        name = "Test",
        halfLengthMs = 30_000,
        halftimeLengthMs = 15_000,
        breakLengthMs = 0,
        devOnly = true,
    )

    private fun ayso(id: String, name: String, halfMinutes: Int) = Profile(
        id = id,
        name = name,
        halfLengthMs = halfMinutes * MINUTE,
        halftimeLengthMs = 5 * MINUTE,
        breakLengthMs = MINUTE,
    )

    fun withSaved(profiles: List<Profile>, profile: Profile): List<Profile> =
        if (profiles.any { it.id == profile.id }) {
            profiles.map { if (it.id == profile.id) profile else it }
        } else {
            profiles + profile
        }

    /** The last profile can't be deleted: the setup screen always needs one to kick off with. */
    fun withDeleted(profiles: List<Profile>, id: String): List<Profile> =
        if (profiles.size <= 1) profiles else profiles.filterNot { it.id == id }

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeList(profiles: List<Profile>): String = json.encodeToString(profiles)

    /** Null unless [text] is a non-empty profile list, so the caller can fall back to the defaults. */
    fun decodeList(text: String): List<Profile>? =
        runCatching { json.decodeFromString<List<Profile>>(text) }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun encode(profile: Profile): String = json.encodeToString(profile)

    fun decode(text: String): Profile? = runCatching { json.decodeFromString<Profile>(text) }.getOrNull()
}
