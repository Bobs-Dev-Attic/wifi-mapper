package com.bobsdevattic.wifimapper.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A list's remembered sort order (option + reversed), persisted across visits. */
class SortPref<T : Enum<T>>(
    private val prefs: SharedPreferences,
    private val key: String,
    default: T,
    entries: List<T>,
) {
    private val _sort = MutableStateFlow(entries.firstOrNull { it.name == prefs.getString(key, null) } ?: default)
    val sort: StateFlow<T> = _sort

    private val _reversed = MutableStateFlow(prefs.getBoolean("${key}_reversed", false))
    val reversed: StateFlow<Boolean> = _reversed

    fun select(s: T) { prefs.edit().putString(key, s.name).apply(); _sort.value = s }

    fun toggleReversed() {
        val r = !_reversed.value
        prefs.edit().putBoolean("${key}_reversed", r).apply(); _reversed.value = r
    }
}
