package com.refclock

import android.content.Context
import android.util.AtomicFile
import com.refclock.match.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/** App-wide settings, kept in a JSON file. Main thread only. */
object SettingsStore {
    private lateinit var file: AtomicFile

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        file = AtomicFile(File(context.filesDir, "settings.json"))
        _settings.value = read() ?: AppSettings()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        val out = file.startWrite()
        try {
            out.write(AppSettings.encode(next).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }

    private fun read(): AppSettings? =
        if (!file.baseFile.exists()) null
        else runCatching { AppSettings.decode(file.readFully().decodeToString()) }.getOrNull()
}
