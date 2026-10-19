package com.refclock.ui

import android.os.SystemClock
import android.content.pm.ApplicationInfo
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.LocalContentColor
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
import com.refclock.SettingsStore
import com.refclock.R
import com.refclock.match.Alert
import com.refclock.match.AlertKind
import com.refclock.match.AlertSlot
import com.refclock.match.MatchState
import com.refclock.match.Phase
import com.refclock.match.Profile
import com.refclock.match.Profiles
import com.refclock.match.formatElapsed
import com.refclock.match.formatLength
import com.refclock.match.formatRemaining
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Dim = Color(0xFF9E9E9E)
internal val OverTime = Color(0xFFFFB300)
internal val Reached = Color(0xFF80DEEA)
internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** How long a hold button must be held. Its ring fills over this time. */
private const val HOLD_MS = 600

private object Routes {
    const val MATCH = "match"
    const val PROFILES = "profiles"
    const val EDITOR = "editor"
    const val PICK = "pick/{field}"
    fun pick(field: LengthField) = "pick/${field.name}"
    const val SETTINGS = "settings"
    const val ALERT = "alert/{slot}"
    fun alert(slot: AlertSlot) = "alert/${slot.name}"
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
                    MatchRoute(
                        ambient,
                        ambientTick,
                        onChangeProfile = { navController.navigate(Routes.PROFILES) { launchSingleTop = true } },
                        onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    )
                }
                composable(Routes.PROFILES) {
                    ProfileListRoute(
                        onPicked = {
                            // Guarded: a second tap mustn't pop the match screen and leave nothing showing.
                            if (navController.currentDestination?.route == Routes.PROFILES) navController.popBackStack()
                        },
                        onEdit = { navController.navigate(Routes.EDITOR) { launchSingleTop = true } },
                    )
                }
                composable(Routes.EDITOR) {
                    ProfileEditorRoute(
                        onPick = { field -> navController.navigate(Routes.pick(field)) { launchSingleTop = true } },
                        onDone = { navController.popBackStack(Routes.PROFILES, inclusive = false) },
                    )
                }
                composable(Routes.PICK) { entry ->
                    val field = entry.arguments?.getString("field")?.let { name -> LengthField.entries.firstOrNull { it.name == name } }
                    if (field != null) LengthPickerRoute(field) { navController.popBackStack() }
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute { slot -> navController.navigate(Routes.alert(slot)) { launchSingleTop = true } }
                }
                composable(Routes.ALERT) { entry ->
                    val slot = entry.arguments?.getString("slot")?.let { name -> AlertSlot.entries.firstOrNull { it.name == name } }
                    if (slot != null) AlertSettingsRoute(slot)
                }
            }
        }
    }
}

@Composable
private fun MatchRoute(ambient: Boolean, ambientTick: Int, onChangeProfile: () -> Unit, onSettings: () -> Unit) {
    val state by MatchController.state.collectAsState()
    Scaffold(timeText = { TimeText() }) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Screen(state, ambient, ambientTick, onChangeProfile, onSettings)
        }
    }
}

@Composable
private fun Screen(state: MatchState, ambient: Boolean, ambientTick: Int, onChangeProfile: () -> Unit, onSettings: () -> Unit) =
    AcknowledgeOnTap {
        when (state.phase) {
            Phase.IDLE -> IdleScreen(state, onChangeProfile, onSettings)
            Phase.RUNNING, Phase.PAUSED -> HalfScreen(state, ambient, ambientTick)
            Phase.HALFTIME -> HalftimeScreen(state, ambient, ambientTick)
            Phase.FINISHED -> FullTimeScreen(state, ambient)
        }
    }

@Composable
private fun IdleScreen(state: MatchState, onChangeProfile: () -> Unit, onSettings: () -> Unit) {
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
        Text(profileName(profile), style = MaterialTheme.typography.title2)
        if (profile.name.isNotBlank()) {
            Text(halvesText(profile), style = MaterialTheme.typography.caption1, color = Dim)
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { MatchController.kickOff(context) },
            colors = ButtonDefaults.primaryButtonColors(),
            modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
        ) {
            ButtonIcon(R.drawable.ic_play, stringResource(R.string.cd_kick_off), size = 32.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(onClick = onChangeProfile, colors = ButtonDefaults.secondaryButtonColors()) {
                ButtonIcon(R.drawable.ic_list, stringResource(R.string.cd_change_profile), size = 20.dp)
            }
            CompactButton(onClick = onSettings, colors = ButtonDefaults.secondaryButtonColors()) {
                ButtonIcon(R.drawable.ic_settings, stringResource(R.string.cd_settings), size = 20.dp)
            }
        }
    }
}

@Composable
internal fun halvesText(profile: Profile) = stringResource(R.string.halves, formatLength(profile.halfLengthMs))

@Composable
private fun HalfScreen(state: MatchState, ambient: Boolean, ambientTick: Int) {
    val context = LocalContext.current
    val profile = state.profile
    val now = rememberNow(state, ambient, ambientTick)
    val elapsed = state.halfElapsedMs(now)
    val game = state.gameElapsedMs(now)
    val paused = state.phase == Phase.PAUSED
    val pending by MatchController.pendingAlert.collectAsState()

    // Ambient updates arrive about once a minute, so showing seconds there would be wrong most of the time.
    fun clock(ms: Long) = if (ambient) "${ms / 60_000}'" else formatElapsed(ms)

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HalfDots(state.half)
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
        StatusLine(height = 24.dp) {
            val alert = pending
            val next = profile.nextAlert(elapsed)
            when {
                paused -> ButtonIcon(R.drawable.ic_pause, stringResource(R.string.cd_paused), tint = Dim)
                alert != null -> ReachedAlert(alert, state.half, ambient)
                next == null -> AlertIcon(AlertKind.HALF_END, state.half, tint = OverTime)
                else -> {
                    val tint = if (ambient) Dim else Color.White
                    AlertIcon(next.kind, state.half, tint = tint)
                    if (!ambient) {
                        Text(
                            formatRemaining(next.atMs - elapsed),
                            style = MaterialTheme.typography.body1.merge(Tabular),
                            color = tint,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.game_total, clock(game)),
            style = MaterialTheme.typography.caption1.merge(Tabular),
            color = Dim,
        )

        if (!ambient) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (paused) {
                    Button(onClick = { MatchController.resume(context) }, colors = ButtonDefaults.primaryButtonColors()) {
                        ButtonIcon(R.drawable.ic_play, stringResource(R.string.cd_resume))
                    }
                } else {
                    HoldButton(R.drawable.ic_pause, stringResource(R.string.cd_pause)) { MatchController.pause(context) }
                }
                HoldButton(
                    R.drawable.ic_whistle,
                    stringResource(if (state.half == 1) R.string.cd_end_half else R.string.cd_end_game),
                ) { MatchController.endHalf(context) }
            }
        }
    }
}

/** The line under the big clock, laid out at a fixed height so the screen doesn't jump as it changes. */
@Composable
private fun StatusLine(height: Dp, content: @Composable () -> Unit) {
    Row(
        Modifier.height(height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** Filled dots for halves started: one in the first half, two in the second. */
@Composable
internal fun HalfDots(half: Int, color: Color = Dim) {
    val description = stringResource(if (half == 1) R.string.cd_first_half else R.string.cd_second_half)
    Row(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(2) { i ->
            Canvas(Modifier.size(8.dp)) {
                if (i < half) {
                    drawCircle(color)
                } else {
                    drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

/** An alert's icon: water drop for the quarter break, whistle for the end of a half. */
@Composable
private fun AlertIcon(kind: AlertKind, half: Int, tint: Color) {
    when (kind) {
        AlertKind.CHECKPOINT ->
            ButtonIcon(R.drawable.ic_water_drop, stringResource(R.string.cd_quarter_break), tint = tint)
        AlertKind.HALF_END ->
            ButtonIcon(
                R.drawable.ic_whistle,
                stringResource(if (half == 1) R.string.cd_half_end else R.string.cd_game_end),
                tint = tint,
            )
        AlertKind.HALFTIME_END ->
            Text(stringResource(R.string.halftime_short), style = MaterialTheme.typography.body1, color = tint)
    }
}

@Composable
private fun ReachedAlert(alert: Alert, half: Int, ambient: Boolean) {
    val tint = if (ambient) Color.LightGray else Reached
    PulsingCheck(animate = !ambient, tint = tint)
    AlertIcon(alert.kind, half, tint = tint)
}

@Composable
internal fun ButtonIcon(
    @DrawableRes icon: Int,
    description: String,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

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
        contentDescription = stringResource(R.string.cd_reached),
        tint = tint,
        modifier = Modifier.size(22.dp).alpha(alpha),
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
 * button: tapping to silence the buzz must never start the second half or begin a hold.
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
    // The summary rows make this the tallest screen; the top padding keeps "FT" clear of the time.
    Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.fulltime_short), style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(4.dp))
        SummaryRow(state.firstHalfMs) { HalfDots(1) }
        SummaryRow(state.halftimeMs) {
            Text(stringResource(R.string.halftime_short), style = MaterialTheme.typography.body2, color = Dim)
        }
        SummaryRow(state.endedHalfMs) { HalfDots(2) }
        // Sum the whole seconds each row shows, so the column always adds up on screen.
        val total = listOf(state.firstHalfMs, state.halftimeMs, state.endedHalfMs).sumOf { it / 1000 * 1000 }
        val totalDescription = stringResource(R.string.cd_total)
        SummaryRow(total, emphasized = true) {
            Text(
                "Σ",
                Modifier.semantics { contentDescription = totalDescription },
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!ambient) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = { MatchController.newMatch(context) }, colors = ButtonDefaults.primaryButtonColors()) {
                ButtonIcon(R.drawable.ic_replay, stringResource(R.string.cd_new_match))
            }
        }
    }
}

@Composable
private fun SummaryRow(ms: Long, emphasized: Boolean = false, label: @Composable () -> Unit) {
    Row(
        Modifier.width(120.dp).height(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        label()
        Text(
            formatElapsed(ms),
            style = MaterialTheme.typography.body2.merge(Tabular),
            color = if (emphasized) Color.White else Dim,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun HalftimeScreen(state: MatchState, ambient: Boolean, ambientTick: Int) {
    val context = LocalContext.current
    val now = rememberNow(state, ambient, ambientTick)
    val elapsed = state.halftimeElapsedMs(now)
    val length = state.profile.halftimeLengthMs
    val remaining = length - elapsed
    val over = remaining < 0
    val pending by MatchController.pendingAlert.collectAsState()
    val countUp = SettingsStore.settings.collectAsState().value.halftimeCountUp

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            when {
                countUp && ambient -> "${elapsed / 60_000}'"
                countUp -> formatElapsed(elapsed)
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
        // Same spot as the half screen's status line, directly under the clock.
        StatusLine(height = 24.dp) {
            val alert = pending
            if (alert != null) {
                ReachedAlert(alert, state.half, ambient)
            } else {
                // Counting up, the target goes here instead, since the clock no longer shows it.
                val label = stringResource(R.string.halftime_short)
                Text(
                    if (countUp) "$label ${formatElapsed(length)}" else label,
                    style = MaterialTheme.typography.body1.merge(Tabular),
                    color = Dim,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HalfDots(1)
            Text(formatElapsed(state.endedHalfMs), style = MaterialTheme.typography.caption1.merge(Tabular), color = Dim)
        }
        if (!ambient) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { MatchController.startSecondHalf(context) }, colors = ButtonDefaults.primaryButtonColors()) {
                    ButtonIcon(R.drawable.ic_play, stringResource(R.string.cd_start_second_half))
                }
                HoldButton(R.drawable.ic_close, stringResource(R.string.cd_end_match)) { MatchController.newMatch(context) }
            }
        }
    }
}

/**
 * Hold, not tap: a stray touch mid-run must never stop or end the half. A ring fills around the icon
 * while held, which also shows a first-time user that holding is what it wants.
 */
@Composable
internal fun HoldButton(@DrawableRes icon: Int, description: String, onHold: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val currentOnHold by rememberUpdatedState(onHold)
    Box(
        Modifier
            .size(ButtonDefaults.DefaultButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colors.surface)
            .semantics {
                contentDescription = description
                role = Role.Button
                onLongClick { currentOnHold(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnHold()
                    }
                    tryAwaitRelease()
                    hold.cancel()
                    scope.launch { progress.animateTo(0f, tween(150)) }
                })
            }
            .drawWithContent {
                drawContent()
                val stroke = 3.dp.toPx()
                drawArc(
                    color = Reached,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
