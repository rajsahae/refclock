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
    /** Listed only in debug builds, never in a store release. */
    val debugOnly: Boolean = false,
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
            breakAtMs?.let { Alert(it, AlertKind.CHECKPOINT) },
            Alert(halfLengthMs, AlertKind.HALF_END),
        )

    val halftimeAlert: Alert
        get() = Alert(halftimeLengthMs, AlertKind.HALFTIME_END)

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
        debugOnly = true,
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

    /** Brings back deleted defaults and reverts edits to them; custom profiles are kept, after them. */
    fun withDefaultsRestored(profiles: List<Profile>): List<Profile> {
        val defaultIds = defaults.map { it.id }.toSet()
        return defaults + profiles.filterNot { it.id in defaultIds }
    }

    /** The last profile can't be deleted: the setup screen always needs one to kick off with. */
    fun withDeleted(profiles: List<Profile>, id: String): List<Profile> =
        if (profiles.size <= 1) profiles else profiles.filterNot { it.id == id }

    // Editor choices. Half and halftime lengths are whole minutes; the break goes in 30-second steps.
    val halfLengthOptions = (1..60).map { it * MINUTE }
    val halftimeLengthOptions = (1..20).map { it * MINUTE }

    /** The break must be shorter than the half, or there'd be no playing time before it. */
    fun breakLengthOptions(halfLengthMs: Long): List<Long> = (1..10).map { it * 30_000L }.filter { it < halfLengthMs }

    fun newProfile(id: String) = Profile(
        id = id,
        name = "",
        halfLengthMs = 25 * MINUTE,
        halftimeLengthMs = 5 * MINUTE,
        breakLengthMs = MINUTE,
    )

    /** Shortening the half may also shorten the break, to the longest length still allowed. */
    fun withHalfLength(profile: Profile, halfLengthMs: Long): Profile = profile.copy(
        halfLengthMs = halfLengthMs,
        breakLengthMs = profile.breakLengthMs?.let { current ->
            breakLengthOptions(halfLengthMs).lastOrNull { it <= current } ?: breakLengthOptions(halfLengthMs).first()
        },
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeList(profiles: List<Profile>): String = json.encodeToString(profiles)

    /** Null unless [text] is a non-empty profile list, so the caller can fall back to the defaults. */
    fun decodeList(text: String): List<Profile>? =
        runCatching { json.decodeFromString<List<Profile>>(text) }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun encode(profile: Profile): String = json.encodeToString(profile)

    fun decode(text: String): Profile? = runCatching { json.decodeFromString<Profile>(text) }.getOrNull()
}
