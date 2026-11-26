package com.bobsdevattic.wifimapper.settings

import android.content.Context
import com.bobsdevattic.wifimapper.capture.BleSort
import com.bobsdevattic.wifimapper.capture.WifiSort
import com.bobsdevattic.wifimapper.model.MapSort
import com.bobsdevattic.wifimapper.model.DistanceUnit
import com.bobsdevattic.wifimapper.viz.SignalPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

/** How the radar is drawn. */
enum class RadarStyle(val label: String) {
    TRACKER("Motion tracker"), CLASSIC("Classic compass")
}

/**
 * User preferences, persisted in SharedPreferences and exposed as StateFlows so
 * every screen (and the app theme) updates immediately when one changes.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        // One-time carry-over of the palette chosen in v0.4/0.5's viewer menu.
        val legacy = context.getSharedPreferences("viewer_settings", Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_PALETTE) && legacy.contains(KEY_PALETTE)) {
            prefs.edit().putString(KEY_PALETTE, legacy.getString(KEY_PALETTE, null)).apply()
        }
    }

    /** Remembered sort orders for the nearby-signal and Find my device lists. */
    val wifiSort = SortPref(prefs, "sort_wifi", WifiSort.SIGNAL, WifiSort.entries)
    val bleSort = SortPref(prefs, "sort_ble", BleSort.SIGNAL, BleSort.entries)
    val findSort = SortPref(prefs, "sort_find_device", BleSort.SIGNAL, BleSort.entries)   // Find my device → Nearby now
    val pairedSort = SortPref(prefs, "sort_paired", BleSort.SIGNAL, BleSort.entries)      // Find my device → paired
    val mapSort = SortPref(prefs, "sort_maps", MapSort.NEWEST, MapSort.entries)            // home: saved maps

    private val _palette = MutableStateFlow(SignalPalette.fromName(prefs.getString(KEY_PALETTE, null)))
    val palette: StateFlow<SignalPalette> = _palette

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _floorSpacingM = MutableStateFlow(prefs.getFloat(KEY_SPACING, DEFAULT_SPACING_M))
    /** Vertical distance between stacked floors in the 3D view (metres). */
    val floorSpacingM: StateFlow<Float> = _floorSpacingM

    private val _radarStyle = MutableStateFlow(
        RadarStyle.entries.firstOrNull { it.name == prefs.getString(KEY_RADAR, null) } ?: RadarStyle.TRACKER
    )
    val radarStyle: StateFlow<RadarStyle> = _radarStyle

    private val _pingSound = MutableStateFlow(prefs.getBoolean(KEY_PING, true))
    val pingSound: StateFlow<Boolean> = _pingSound

    private val _pingVibrate = MutableStateFlow(prefs.getBoolean(KEY_VIBRATE, true))
    /** Tracker: vibrate when the ping wave reaches the blip. */
    val pingVibrate: StateFlow<Boolean> = _pingVibrate

    private val _distanceUnit = MutableStateFlow(
        DistanceUnit.entries.firstOrNull { it.name == prefs.getString(KEY_UNITS, null) } ?: DistanceUnit.default()
    )
    /** Metres or feet for every distance shown. Defaults from the phone's region. */
    val distanceUnit: StateFlow<DistanceUnit> = _distanceUnit
    fun setDistanceUnit(u: DistanceUnit) { prefs.edit().putString(KEY_UNITS, u.name).apply(); _distanceUnit.value = u }

    fun setRadarStyle(s: RadarStyle) { prefs.edit().putString(KEY_RADAR, s.name).apply(); _radarStyle.value = s }
    fun setPingSound(on: Boolean) { prefs.edit().putBoolean(KEY_PING, on).apply(); _pingSound.value = on }
    fun setPingVibrate(on: Boolean) { prefs.edit().putBoolean(KEY_VIBRATE, on).apply(); _pingVibrate.value = on }

    fun setPalette(p: SignalPalette) { prefs.edit().putString(KEY_PALETTE, p.name).apply(); _palette.value = p }
    fun setThemeMode(m: ThemeMode) { prefs.edit().putString(KEY_THEME, m.name).apply(); _themeMode.value = m }
    fun setDynamicColor(on: Boolean) { prefs.edit().putBoolean(KEY_DYNAMIC, on).apply(); _dynamicColor.value = on }
    fun setFloorSpacingM(m: Float) {
        val v = m.coerceIn(MIN_SPACING_M, MAX_SPACING_M)
        prefs.edit().putFloat(KEY_SPACING, v).apply(); _floorSpacingM.value = v
    }

    companion object {
        private const val KEY_PALETTE = "signal_palette"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_SPACING = "floor_spacing_m"
        private const val KEY_RADAR = "radar_style"
        private const val KEY_PING = "radar_ping_sound"
        private const val KEY_VIBRATE = "radar_ping_vibrate"
        private const val KEY_UNITS = "distance_unit"
        const val DEFAULT_SPACING_M = 3.0f
        const val MIN_SPACING_M = 2.0f
        const val MAX_SPACING_M = 6.0f
    }
}
