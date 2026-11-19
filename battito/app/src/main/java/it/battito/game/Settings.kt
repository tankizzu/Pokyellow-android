package it.battito.game

import android.content.Context

/** Impostazioni salvate sul telefono. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("battito", Context.MODE_PRIVATE)

    var musicVolume: Float = prefs.getFloat("music_vol", 0.8f)
        set(v) { field = v.coerceIn(0f, 1f); prefs.edit().putFloat("music_vol", field).apply() }

    var sfxVolume: Float = prefs.getFloat("sfx_vol", 0.8f)
        set(v) { field = v.coerceIn(0f, 1f); prefs.edit().putFloat("sfx_vol", field).apply() }

    var vibration: Boolean = prefs.getBoolean("vibration", true)
        set(v) { field = v; prefs.edit().putBoolean("vibration", v).apply() }

    fun best(level: Level): Int = prefs.getInt("best_" + level.name, 0)
    fun saveBest(level: Level, pct: Int) { prefs.edit().putInt("best_" + level.name, pct).apply() }
}
