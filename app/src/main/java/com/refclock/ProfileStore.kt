package com.refclock

import android.content.Context
import android.util.AtomicFile
import com.refclock.match.Profile
import com.refclock.match.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** The user's saved profiles, kept in a JSON file. Main thread only. */
object ProfileStore {
    private lateinit var file: AtomicFile

    private val _profiles = MutableStateFlow(Profiles.defaults)
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    fun init(context: Context) {
        file = AtomicFile(File(context.filesDir, "profiles.json"))
        _profiles.value = read() ?: Profiles.defaults
    }

    /** Saved profiles plus the built-in Test profile, which menus show only in developer mode. */
    fun byId(id: String): Profile? = (profiles.value + Profiles.test).firstOrNull { it.id == id }

    fun save(profile: Profile) = write(Profiles.withSaved(profiles.value, profile))

    fun delete(id: String) = write(Profiles.withDeleted(profiles.value, id))

    fun resetToDefaults() = write(Profiles.defaults)

    private fun read(): List<Profile>? =
        if (!file.baseFile.exists()) null
        else runCatching { Profiles.decodeList(file.readFully().decodeToString()) }.getOrNull()

    private fun write(list: List<Profile>) {
        _profiles.value = list
        val out = file.startWrite()
        try {
            out.write(Profiles.encodeList(list).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }
}
