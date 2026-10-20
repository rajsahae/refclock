package com.refclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.scrollAway
import com.refclock.AlertPlayer
import com.refclock.R
import com.refclock.SettingsStore
import com.refclock.match.AlertSlot
import com.refclock.match.AppSettings
import com.refclock.match.AlertStyle
import com.refclock.match.Pattern

@Composable
internal fun SettingsRoute(onAlert: (AlertSlot) -> Unit) {
    val settings by SettingsStore.settings.collectAsState()
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText(Modifier.scrollAway(listState)) }) {
        ScalingLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState) {
            item {
                ListHeader { ButtonIcon(R.drawable.ic_settings, stringResource(R.string.cd_settings), tint = Dim) }
            }
            items(AlertSlot.entries) { slot ->
                val style = settings.style(slot)
                Chip(
                    onClick = { onAlert(slot) },
                    icon = { SlotIcon(slot) },
                    label = { PatternBars(style.pattern, active = style.vibrate || style.beep) },
                    secondaryLabel = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OnOffIcon(R.drawable.ic_vibrate, stringResource(R.string.cd_vibrate), style.vibrate)
                            OnOffIcon(R.drawable.ic_beep, stringResource(R.string.cd_beep), style.beep)
                        }
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val countUp = settings.halftimeCountUp
                val description = stringResource(R.string.cd_halftime_count_up)
                ToggleChip(
                    checked = countUp,
                    onCheckedChange = { checked -> SettingsStore.update { it.copy(halftimeCountUp = checked) } },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.halftime_short))
                            ButtonIcon(
                                if (countUp) R.drawable.ic_count_up else R.drawable.ic_count_down,
                                description,
                                size = 20.dp,
                            )
                        }
                    },
                    toggleControl = { Switch(checked = countUp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                // Leaves profiles alone: they describe a league's format, settings how you like to be alerted.
                Box(Modifier.padding(top = 8.dp)) {
                    HoldButton(R.drawable.ic_restore, stringResource(R.string.cd_restore_settings)) {
                        SettingsStore.update { AppSettings() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlertSettingsRoute(slot: AlertSlot) {
    val context = LocalContext.current
    val settings by SettingsStore.settings.collectAsState()
    val style = settings.style(slot)
    val listState = rememberScalingLazyListState()

    fun save(next: AlertStyle) = SettingsStore.update { it.withStyle(slot, next) }

    /** Plays the pattern once, not the repeating minute a real alert gets. */
    fun preview(of: AlertStyle) = AlertPlayer.play(context, of, capMs = of.pattern.onOff.sum())

    // Settings are only reachable from the setup screen, so no real alert can be playing here.
    DisposableEffect(Unit) { onDispose { AlertPlayer.stop(context) } }

    Scaffold(timeText = { TimeText(Modifier.scrollAway(listState)) }) {
        ScalingLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState) {
            item { ListHeader { SlotIcon(slot) } }
            items(Pattern.entries) { pattern ->
                val selected = pattern == style.pattern
                Chip(
                    onClick = {
                        val next = style.copy(pattern = pattern)
                        save(next)
                        preview(next)
                    },
                    label = { PatternBars(pattern) },
                    icon = if (selected) {
                        { ButtonIcon(R.drawable.ic_check, stringResource(R.string.cd_selected), size = 20.dp) }
                    } else null,
                    colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchChip(R.drawable.ic_vibrate, stringResource(R.string.cd_vibrate), style.vibrate) {
                    save(style.copy(vibrate = it))
                }
            }
            item {
                SwitchChip(R.drawable.ic_beep, stringResource(R.string.cd_beep), style.beep) {
                    save(style.copy(beep = it))
                }
            }
            item {
                Button(
                    onClick = { preview(style) },
                    colors = ButtonDefaults.primaryButtonColors(),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    ButtonIcon(R.drawable.ic_play, stringResource(R.string.cd_preview))
                }
            }
        }
    }
}

@Composable
private fun SwitchChip(icon: Int, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onChange,
        label = { ButtonIcon(icon, description, size = 22.dp) },
        toggleControl = { Switch(checked = checked) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OnOffIcon(icon: Int, description: String, on: Boolean) {
    ButtonIcon(icon, description, size = 16.dp, tint = if (on) Color.White else Dim.copy(alpha = 0.4f))
}

/** Which alert a row is for: water drop, whistle with the half's dots, or HT. */
@Composable
private fun SlotIcon(slot: AlertSlot) {
    when (slot) {
        AlertSlot.QUARTER_BREAK -> ButtonIcon(R.drawable.ic_water_drop, stringResource(R.string.cd_quarter_break))
        AlertSlot.HALF_TIME, AlertSlot.FULL_TIME -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val description = stringResource(
                if (slot == AlertSlot.HALF_TIME) R.string.cd_slot_half_time else R.string.cd_slot_full_time,
            )
            ButtonIcon(R.drawable.ic_whistle, description, size = 20.dp)
            HalfDots(if (slot == AlertSlot.HALF_TIME) 1 else 2, color = LocalContentColor.current)
        }
        AlertSlot.HALFTIME_OVER -> {
            val description = stringResource(R.string.cd_slot_halftime_over)
            Text(
                stringResource(R.string.halftime_short),
                Modifier.semantics { contentDescription = description },
                style = MaterialTheme.typography.button,
            )
        }
    }
}

/**
 * A pattern drawn as bars, each as wide as its pulse is long, so it reads without words. Every
 * pattern uses the same scale, so a long pulse always looks longer than a quick one.
 */
@Composable
private fun PatternBars(pattern: Pattern, active: Boolean = true) {
    val description = stringResource(
        when (pattern) {
            Pattern.QUICK_2 -> R.string.pattern_quick_2
            Pattern.QUICK_3 -> R.string.pattern_quick_3
            Pattern.QUICK_4 -> R.string.pattern_quick_4
            Pattern.LONG_2 -> R.string.pattern_long_2
            Pattern.LONG_3 -> R.string.pattern_long_3
            Pattern.SUSTAINED -> R.string.pattern_sustained
            Pattern.LONG_QUICK -> R.string.pattern_long_quick
        },
    )
    val color = if (active) LocalContentColor.current else Dim
    val longest = Pattern.entries.maxOf { it.onOff.sum() }.toFloat()
    Canvas(Modifier.size(width = 96.dp, height = 10.dp).semantics { contentDescription = description }) {
        val perMs = size.width / longest
        var t = 0L
        pattern.onOff.forEachIndexed { i, ms ->
            if (i % 2 == 0) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(t * perMs, 0f),
                    size = Size(ms * perMs, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            t += ms
        }
    }
}
