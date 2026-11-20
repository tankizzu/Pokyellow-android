package it.giallotouch.emu

/** Accesso al motore SameBoy compilato con l'NDK (libgbcore.so). */
object NativeCore {
    init {
        System.loadLibrary("gbcore")
    }

    const val WIDTH = 160
    const val HEIGHT = 144

    /** Tasti del Game Boy come maschera di bit (stesso ordine del motore). */
    const val KEY_RIGHT = 1
    const val KEY_LEFT = 2
    const val KEY_UP = 4
    const val KEY_DOWN = 8
    const val KEY_A = 16
    const val KEY_B = 32
    const val KEY_SELECT = 64
    const val KEY_START = 128

    @JvmStatic external fun nativeCreate(sampleRate: Int): Long
    @JvmStatic external fun nativeLoadRom(handle: Long, rom: ByteArray)
    @JvmStatic external fun nativeLoadBattery(handle: Long, save: ByteArray)
    @JvmStatic external fun nativeSaveBattery(handle: Long): ByteArray
    @JvmStatic external fun nativeSaveState(handle: Long): ByteArray
    @JvmStatic external fun nativeLoadState(handle: Long, state: ByteArray): Boolean
    @JvmStatic external fun nativeSetKeys(handle: Long, mask: Int)
    @JvmStatic external fun nativeRunFrame(handle: Long, pixels: IntArray, audio: ShortArray): Int
    @JvmStatic external fun nativeReadMemory(handle: Long, address: Int): Int
    @JvmStatic external fun nativeWriteMemory(handle: Long, address: Int, value: Int)
    @JvmStatic external fun nativeReadRange(handle: Long, address: Int, out: ByteArray)
    /**
     * Vista estesa: riempie [out] (160 × height) con l'inquadratura originale alla riga [frameTop] (−1 = al centro);
     * restituisce 1 se lo schermo mostra la mappa, 0 altrimenti.
     */
    @JvmStatic external fun nativeRenderExtended(handle: Long, out: IntArray, height: Int, frameTop: Int): Int
    @JvmStatic external fun nativeSetMapFromRow(handle: Long, row: Int)
    @JvmStatic external fun nativeEvDebug(handle: Long, what: Int): Int
    const val EXT_MAX_HEIGHT = 512
    /** Foglio 128×128 dei tile del gioco (carattere, riquadri) con i colori dei riquadri di testo: 1 = colori dallo schermo. */
    /** Sprite anche fuori dalle 144 righe (introduzione): 160 × height, riga 0 del gioco alla riga top. */
    @JvmStatic external fun nativeSprites(handle: Long, out: IntArray, height: Int, top: Int): Int
    @JvmStatic external fun nativeGameTiles(handle: Long, out: IntArray): Int
    /** Versione italiana riconosciuta: il carattere del gioco si prende dalla ROM (sulla mappa la memoria video non ce l'ha). */
    @JvmStatic external fun nativeTrustRomFont(handle: Long)
    @JvmStatic external fun nativeDestroy(handle: Long)
}
