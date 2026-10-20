package com.refclock.ui

import android.app.RemoteInput
import android.content.pm.ApplicationInfo
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState
import androidx.wear.compose.material.scrollAway
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.refclock.MatchController
import com.refclock.ProfileStore
import com.refclock.R
import com.refclock.match.Profile
import com.refclock.match.Profiles
import com.refclock.match.formatElapsed
import com.refclock.match.formatLength
import java.util.UUID

/** The profile being edited, shared by the editor and its picker screens. Main thread only. */
internal object ProfileDraft {
    var profile by mutableStateOf<Profile?>(null)
    var isNew by mutableStateOf(false)

    fun edit(existing: Profile) {
        profile = existing
        isNew = false
    }

    fun create() {
        profile = Profiles.newProfile(UUID.randomUUID().toString())
        isNew = true
    }
}

internal enum class LengthField { HALF, HALFTIME, BREAK }

/** A profile left unnamed shows its length instead. */
@Composable
internal fun profileName(profile: Profile) = profile.name.ifBlank { halvesText(profile) }

@Composable
internal fun rememberMenuProfiles(): List<Profile> {
    val context = LocalContext.current
    val saved by ProfileStore.profiles.collectAsState()
    // Not the Developer options setting: on a real watch the system reports it to apps as off.
    val debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return if (debugBuild) saved + Profiles.test else saved
}

@Composable
internal fun ProfileListRoute(onPicked: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val profiles = rememberMenuProfiles()
    val listState = rememberScalingLazyListState()
    // The time slides away as the list scrolls instead of overlapping its items.
    Scaffold(timeText = { TimeText(Modifier.scrollAway(listState)) }) {
        ScalingLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState) {
            item {
                ListHeader {
                    ButtonIcon(R.drawable.ic_list, stringResource(R.string.cd_profiles), tint = Dim)
                }
            }
            items(profiles) { profile ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Chip(
                        onClick = {
                            MatchController.selectProfile(context, profile.id)
                            onPicked()
                        },
                        label = { Text(profileName(profile), maxLines = 1) },
                        // An unnamed profile already shows its length as its name.
                        secondaryLabel = if (profile.name.isNotBlank()) {
                            { Text(halvesText(profile)) }
                        } else null,
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    if (!profile.debugOnly) {
                        CompactButton(
                            onClick = {
                                ProfileDraft.edit(profile)
                                onEdit()
                            },
                            colors = ButtonDefaults.secondaryButtonColors(),
                        ) {
                            ButtonIcon(R.drawable.ic_edit, stringResource(R.string.cd_edit_profile), size = 18.dp)
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            ProfileDraft.create()
                            onEdit()
                        },
                        colors = ButtonDefaults.primaryButtonColors(),
                    ) {
                        ButtonIcon(R.drawable.ic_add, stringResource(R.string.cd_new_profile))
                    }
                    // Settings apply to every profile, so they live with the list rather than on one profile.
                    Button(onClick = onSettings, colors = ButtonDefaults.secondaryButtonColors()) {
                        ButtonIcon(R.drawable.ic_settings, stringResource(R.string.cd_settings))
                    }
                    HoldButton(R.drawable.ic_restore, stringResource(R.string.cd_restore_defaults)) {
                        ProfileStore.restoreDefaults()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileEditorRoute(onPick: (LengthField) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val draft = ProfileDraft.profile
    // Only after the app was restarted on this screen, which drops the in-memory draft.
    if (draft == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val saved by ProfileStore.profiles.collectAsState()
    val deletable = !ProfileDraft.isNew && saved.size > 1
    val listState = rememberScalingLazyListState()

    val namePrompt = stringResource(R.string.profile_name_prompt)
    val nameInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(NAME_KEY)
        if (text != null) ProfileDraft.profile = ProfileDraft.profile?.copy(name = text.toString().trim())
    }

    Scaffold(timeText = { TimeText(Modifier.scrollAway(listState)) }) {
        ScalingLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState) {
            item {
                FieldChip(R.drawable.ic_edit, stringResource(R.string.cd_name), profileName(draft)) {
                    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                    RemoteInputIntentHelper.putRemoteInputsExtra(
                        intent,
                        listOf(
                            RemoteInput.Builder(NAME_KEY)
                                .setLabel(namePrompt)
                                .wearableExtender {
                                    setEmojisAllowed(false)
                                    setInputActionType(EditorInfo.IME_ACTION_DONE)
                                }
                                .build(),
                        ),
                    )
                    nameInput.launch(intent)
                }
            }
            item {
                FieldChip(R.drawable.ic_timer, stringResource(R.string.cd_half_length), formatLength(draft.halfLengthMs)) {
                    onPick(LengthField.HALF)
                }
            }
            item {
                Chip(
                    onClick = { onPick(LengthField.HALFTIME) },
                    label = { Text(formatLength(draft.halftimeLengthMs)) },
                    icon = {
                        Text(stringResource(R.string.halftime_short), style = MaterialTheme.typography.button)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val on = draft.breakLengthMs != null
                val breakDescription = stringResource(R.string.cd_break)
                SplitToggleChip(
                    checked = on,
                    onCheckedChange = { checked ->
                        ProfileDraft.profile = draft.copy(
                            breakLengthMs = if (checked) Profiles.breakLengthOptions(draft.halfLengthMs).last { it <= 60_000L } else null,
                        )
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ButtonIcon(R.drawable.ic_water_drop, breakDescription, size = 20.dp)
                            Text(draft.breakLengthMs?.let(::formatLength) ?: "–")
                        }
                    },
                    onClick = { if (on) onPick(LengthField.BREAK) },
                    toggleControl = { Switch(checked = on) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            draft.breakAtMs?.let { breakAt ->
                item {
                    val description = stringResource(R.string.cd_break_start)
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "$description ${formatElapsed(breakAt)}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ButtonIcon(R.drawable.ic_water_drop, "", size = 16.dp, tint = Dim)
                        Text(formatElapsed(breakAt), style = MaterialTheme.typography.caption1.merge(Tabular), color = Dim)
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            ProfileStore.save(draft)
                            if (ProfileDraft.isNew) MatchController.selectProfile(context, draft.id)
                            onDone()
                        },
                        colors = ButtonDefaults.primaryButtonColors(),
                    ) {
                        ButtonIcon(R.drawable.ic_check, stringResource(R.string.cd_save))
                    }
                    if (deletable) {
                        HoldButton(R.drawable.ic_delete, stringResource(R.string.cd_delete_profile)) {
                            ProfileStore.delete(draft.id)
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldChip(icon: Int, description: String, value: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(value, maxLines = 1) },
        icon = { ButtonIcon(icon, description, size = 20.dp) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun LengthPickerRoute(field: LengthField, onDone: () -> Unit) {
    val draft = ProfileDraft.profile
    if (draft == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val options = when (field) {
        LengthField.HALF -> Profiles.halfLengthOptions
        LengthField.HALFTIME -> Profiles.halftimeLengthOptions
        LengthField.BREAK -> Profiles.breakLengthOptions(draft.halfLengthMs)
    }
    val current = when (field) {
        LengthField.HALF -> draft.halfLengthMs
        LengthField.HALFTIME -> draft.halftimeLengthMs
        LengthField.BREAK -> draft.breakLengthMs
    }
    val state = rememberPickerState(
        initialNumberOfOptions = options.size,
        initiallySelectedOption = options.indexOf(current).coerceAtLeast(0),
        repeatItems = false,
    )
    val description = stringResource(
        when (field) {
            LengthField.HALF -> R.string.cd_half_length
            LengthField.HALFTIME -> R.string.cd_halftime_length
            LengthField.BREAK -> R.string.cd_break
        },
    )
    // The crown turns the picker only while it has focus.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(timeText = { TimeText() }) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                when (field) {
                    LengthField.HALF -> ButtonIcon(R.drawable.ic_timer, description, tint = Dim)
                    LengthField.HALFTIME ->
                        Text(stringResource(R.string.halftime_short), style = MaterialTheme.typography.body1, color = Dim)
                    LengthField.BREAK -> ButtonIcon(R.drawable.ic_water_drop, description, tint = Dim)
                }
            }
            Picker(
                state = state,
                contentDescription = description,
                modifier = Modifier.size(width = 120.dp, height = 110.dp).focusRequester(focus),
            ) { index ->
                Text(formatLength(options[index]), style = MaterialTheme.typography.display3)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val value = options[state.selectedOption]
                    ProfileDraft.profile = when (field) {
                        LengthField.HALF -> Profiles.withHalfLength(draft, value)
                        LengthField.HALFTIME -> draft.copy(halftimeLengthMs = value)
                        LengthField.BREAK -> draft.copy(breakLengthMs = value)
                    }
                    onDone()
                },
                colors = ButtonDefaults.primaryButtonColors(),
            ) {
                ButtonIcon(R.drawable.ic_check, stringResource(R.string.cd_save))
            }
        }
    }
}

private const val NAME_KEY = "name"
