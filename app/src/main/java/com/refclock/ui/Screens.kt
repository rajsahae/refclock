package com.refclock.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.refclock.MatchController
import com.refclock.ProfileStore
import com.refclock.R
import com.refclock.match.MatchState
import com.refclock.match.Phase
import com.refclock.match.Profile
import com.refclock.match.Profiles
import com.refclock.match.formatElapsed
import com.refclock.match.formatLength
import com.refclock.match.formatRemaining
import kotlinx.coroutines.delay

private val Dim = Color(0xFF9E9E9E)
private val OverTime = Color(0xFFFFB300)
private val Reached = Color(0xFF80DEEA)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private object Routes {
    const val MATCH = "match"
    const val PROFILES = "profiles"
}

@Composable
fun RefClockApp(ambient: Boolean, ambientTick: Int) {
    val navController = rememberSwipeDismissableNavController()
    MaterialTheme {
        // OLED burn-in: the ambient clock can stay on screen for a whole half, so nudge it each minute.
        val step = with(LocalDensity.current) { 4.dp.roundToPx() }
        val shift = if (ambient) IntOffset((ambientTick % 3 - 1) * step, (ambientTick / 3 % 3 - 1) * step) else IntOffset.Zero
        Box(Modifier.fillMaxSize().background(Color.Black).offset { shift }) {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.MATCH) {
                composable(Routes.MATCH) {
                    MatchRoute(ambient, ambientTick) {
                        navController.navigate(Routes.PROFILES) { launchSingleTop = true }
                    }
                }
                composable(Routes.PROFILES) {
                    ProfilesRoute {
                        // Guarded: a second tap mustn't pop the match screen and leave nothing showing.
                        if (navController.currentDestination?.route == Routes.PROFILES) navController.popBackStack()
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRoute(ambient: Boolean, ambientTick: Int, onChangeProfile: () -> Unit) {
    val state by MatchController.state.collectAsState()
    Scaffold(timeText = { TimeText() }) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Screen(state, ambient, ambientTick, onChangeProfile)
        }
    }
}

@Composable
private fun Screen(state: MatchState, ambient: Boolean, ambientTick: Int, onChangeProfile: () -> Unit) =
    AcknowledgeOnTap {
        when (state.phase) {
            Phase.IDLE -> IdleScreen(state, onChangeProfile)
            Phase.RUNNING, Phase.PAUSED -> HalfScreen(state, ambient, ambientTick)
            Phase.HALFTIME -> HalftimeScreen(state, ambient, ambientTick)
            Phase.FINISHED -> FullTimeScreen(state, ambient)
        }
    }

@Composable
private fun IdleScreen(state: MatchState, onChangeProfile: () -> Unit) {
    val context = LocalContext.current
    val profiles = rememberMenuProfiles()
    val profile = profiles.firstOrNull { it.id == state.profileId } ?: profiles.first()
    // Kick off starts state.profileId, so a last-used profile that's now hidden or deleted must be
    // replaced there too.
    LaunchedEffect(profile.id) {
        if (profile.id != state.profileId) MatchController.selectProfile(context, profile.id)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(profile.name, style = MaterialTheme.typography.title2)
        Text("${formatLength(profile.halfLengthMs)} halves", style = MaterialTheme.typography.caption1, color = Dim)
        Spacer(Modifier.height(4.dp))
        Chip(
            onClick = { MatchController.kickOff(context) },
            label = { Text("Kick off") },
            colors = ChipDefaults.primaryChipColors(),
        )
        CompactChip(
            onClick = onChangeProfile,
            label = { Text("Change preset") },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

@Composable
private fun ProfilesRoute(onPicked: () -> Unit) {
    val context = LocalContext.current
    val profiles = rememberMenuProfiles()
    val listState = rememberScalingLazyListState()
    // The time slides away as the list scrolls instead of overlapping its items.
    Scaffold(timeText = { TimeText(Modifier.scrollAway(listState)) }) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            ProfileList(profiles, listState) { profile ->
                MatchController.selectProfile(context, profile.id)
                onPicked()
            }
        }
    }
}

@Composable
private fun rememberMenuProfiles(): List<Profile> {
    val context = LocalContext.current
    val saved by ProfileStore.profiles.collectAsState()
    val developer = remember {
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }
    return if (developer) saved + Profiles.test else saved
}

@Composable
private fun ProfileList(profiles: List<Profile>, listState: ScalingLazyListState, onPick: (Profile) -> Unit) {
    ScalingLazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { ListHeader { Text("Presets") } }
        items(profiles) { profile ->
            Chip(
                onClick = { onPick(profile) },
                label = { Text(profile.name) },
                secondaryLabel = { Text("${formatLength(profile.halfLengthMs)} halves") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HalfScreen(state: MatchState, ambient: Boolean, ambientTick: Int) {
    val context = LocalContext.current
    val profile = state.profile
    val now = rememberNow(state, ambient, ambientTick)
    val elapsed = state.halfElapsedMs(now)
    val game = state.gameElapsedMs(now)
    val paused = state.phase == Phase.PAUSED
    val reached by MatchController.pendingAlert.collectAsState()

    // Ambient updates arrive about once a minute, so showing seconds there would be wrong most of the time.
    fun clock(ms: Long) = if (ambient) "${ms / 60_000}'" else formatElapsed(ms)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (state.half == 1) "1st half" else "2nd half",
            style = MaterialTheme.typography.caption1,
            color = Dim,
        )
        Text(
            clock(elapsed),
            style = Tabular,
            fontSize = 46.sp,
            fontWeight = if (ambient) FontWeight.Light else FontWeight.Medium,
            color = when {
                ambient -> Color.LightGray
                paused -> Dim
                elapsed >= profile.halfLengthMs -> OverTime
                else -> Color.White
            },
        )
        val pending = reached
        when {
            paused -> Text("Paused", style = MaterialTheme.typography.body1, color = Dim)
            pending != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val tint = if (ambient) Color.LightGray else Reached
                PulsingCheck(animate = !ambient, tint = tint)
                Text(
                    if (pending.isHalfEnd) overText(state.half) else pending.label,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
            else -> Text(
                nextAlertText(profile, state.half, elapsed, ambient),
                style = MaterialTheme.typography.body1.merge(Tabular),
                color = if (ambient) Dim else Color.White,
            )
        }
        Text("Game ${clock(game)}", style = MaterialTheme.typography.caption1.merge(Tabular), color = Dim)

        if (!ambient) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paused) {
                    CompactChip(
                        onClick = { MatchController.resume(context) },
                        label = { Text("Resume") },
                        colors = ChipDefaults.primaryChipColors(),
                    )
                } else {
                    HoldButton("Pause") { MatchController.pause(context) }
                }
                HoldButton(if (state.half == 1) "End half" else "End game") { MatchController.endHalf(context) }
            }
        }
    }
}

private fun nextAlertText(profile: Profile, half: Int, elapsed: Long, ambient: Boolean): String {
    val next = profile.nextAlert(elapsed) ?: return overText(half)
    val label = if (!next.isHalfEnd) next.label else if (half == 1) "Half ends" else "Game ends"
    return if (ambient) "Next: $label" else "$label in ${formatRemaining(next.atMs - elapsed)}"
}

private fun overText(half: Int) = if (half == 1) "Half over" else "Game over"

@Composable
private fun PulsingCheck(animate: Boolean, tint: Color) {
    val alpha = if (animate) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "alpha",
        ).value
    } else 1f
    Icon(
        painterResource(R.drawable.ic_check_circle),
        contentDescription = "Reached",
        tint = tint,
        modifier = Modifier.size(20.dp).alpha(alpha),
    )
}

@Composable
private fun rememberNow(state: MatchState, ambient: Boolean, ambientTick: Int): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state, ambient, ambientTick) {
        now = SystemClock.elapsedRealtime()
        val clockZero = when (state.phase) {
            Phase.RUNNING -> state.clockZero
            Phase.HALFTIME -> state.halftimeStartedAt
            else -> null
        }
        // Wake on each whole second of whichever clock is running so digits change exactly on the second.
        while (!ambient && clockZero != null) {
            delay(1000 - (now - clockZero) % 1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/**
 * While an alert buzzes, a full-screen layer takes the first tap so it only acknowledges, even on a
 * button: tapping to silence the buzz must never start the second half or show a "Hold" hint instead.
 */
@Composable
private fun AcknowledgeOnTap(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val pending by MatchController.pendingAlert.collectAsState()
    // Centered: screens that don't fill the space, such as Full time, would otherwise sit top-left.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
        if (pending != null) {
            Box(Modifier.fillMaxSize().clickable { MatchController.acknowledge(context) })
        }
    }
}

@Composable
private fun FullTimeScreen(state: MatchState, ambient: Boolean) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Full time", style = MaterialTheme.typography.title2)
        Spacer(Modifier.height(6.dp))
        val parts = listOf("1st half" to state.firstHalfMs, "Halftime" to state.halftimeMs, "2nd half" to state.endedHalfMs)
        parts.forEach { (label, ms) -> SummaryRow(label, ms) }
        // Sum the whole seconds each row shows, so the column always adds up on screen.
        SummaryRow("Total", parts.sumOf { (_, ms) -> ms / 1000 * 1000 }, emphasized = true)
        if (!ambient) {
            Spacer(Modifier.height(10.dp))
            Chip(
                onClick = { MatchController.newMatch(context) },
                label = { Text("New match") },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, ms: Long, emphasized: Boolean = false) {
    val color = if (emphasized) Color.White else Dim
    val weight = if (emphasized) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.body2, color = color, fontWeight = weight)
        Text(formatElapsed(ms), style = MaterialTheme.typography.body2.merge(Tabular), color = color, fontWeight = weight)
    }
}

@Composable
private fun HalftimeScreen(state: MatchState, ambient: Boolean, ambientTick: Int) {
    val context = LocalContext.current
    val now = rememberNow(state, ambient, ambientTick)
    val remaining = state.profile.halftimeLengthMs - state.halftimeElapsedMs(now)
    val over = remaining < 0
    val pending by MatchController.pendingAlert.collectAsState()

    // Two stacked buttons make this screen taller than the half screen; the top padding keeps the
    // countdown clear of the time of day.
    Column(
        Modifier.fillMaxSize().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            when {
                ambient && over -> "+${-remaining / 60_000}'"
                ambient -> "${(remaining + 59_999) / 60_000}'"
                over -> "+${formatElapsed(-remaining)}"
                else -> formatRemaining(remaining)
            },
            style = Tabular,
            fontSize = 46.sp,
            fontWeight = if (ambient) FontWeight.Light else FontWeight.Medium,
            color = when {
                ambient -> Color.LightGray
                over -> OverTime
                else -> Color.White
            },
        )
        // Same spot as the half screen's checkpoint line, directly under the clock.
        if (pending != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val tint = if (ambient) Color.LightGray else Reached
                PulsingCheck(animate = !ambient, tint = tint)
                Text("Halftime over", style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold, color = tint)
            }
        } else {
            Text("Halftime", style = MaterialTheme.typography.body1, color = Dim)
        }
        Text(
            "1st half ${formatElapsed(state.endedHalfMs)}",
            style = MaterialTheme.typography.caption1.merge(Tabular),
            color = Dim,
        )
        if (!ambient) {
            Spacer(Modifier.height(10.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactChip(
                    onClick = { MatchController.startSecondHalf(context) },
                    label = { Text("Start 2nd half") },
                    colors = ChipDefaults.primaryChipColors(),
                )
                HoldButton("End match") { MatchController.newMatch(context) }
            }
        }
    }
}

/**
 * Hold, not tap: a stray touch mid-run must never stop or end the half. A tap flips the label to
 * "Hold" in place; a Toast would queue one popup per tap and keep showing them after the action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldButton(label: String, onHold: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var hinting by remember { mutableStateOf(false) }
    LaunchedEffect(hinting) {
        if (hinting) {
            delay(1500)
            hinting = false
        }
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface)
            .combinedClickable(
                onClick = { hinting = true },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHold()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = MaterialTheme.typography.button
        // The label stays laid out (just invisible) so the button keeps its width while hinting.
        Text(label, style = style, color = MaterialTheme.colors.onSurface, modifier = Modifier.alpha(if (hinting) 0f else 1f))
        if (hinting) Text("Hold", style = style, color = Reached)
    }
}
