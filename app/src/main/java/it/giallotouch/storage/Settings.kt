package it.giallotouch.storage

import android.content.Context

/** Impostazioni dell'app (non del gioco), salvate sul telefono. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    /** Testo automatico: 0 = spento, 1 = lento, 2 = medio, 3 = veloce. */
    var autoTextSpeed: Int
        get() = prefs.getInt("testo_automatico", 0)
        set(value) { prefs.edit().putInt("testo_automatico", value).apply() }
}
