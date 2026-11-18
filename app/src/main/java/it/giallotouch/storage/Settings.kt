package it.giallotouch.storage

import android.content.Context

/** Impostazioni dell'app (non del gioco), salvate sul telefono. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    /** Testo automatico: 0 = spento, 1 = lento, 2 = medio, 3 = veloce. */
    var autoTextSpeed: Int
        get() = prefs.getInt("testo_automatico", 0)
        set(value) { prefs.edit().putInt("testo_automatico", value).apply() }

    /** Volume del gioco (D-046): 0 = muto … 5 = pieno. */
    var volume: Int
        get() = prefs.getInt("volume", 5)
        set(value) { prefs.edit().putInt("volume", value).apply() }

    /** Lato del pulsante laterale che fa comparire i pulsanti durante il gioco: "destra" o "sinistra" (D-037). */
    var panelSide: String
        get() = prefs.getString("lato_pulsanti", "destra") ?: "destra"
        set(value) { prefs.edit().putString("lato_pulsanti", value).apply() }
}
