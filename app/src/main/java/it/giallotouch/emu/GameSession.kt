package it.giallotouch.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import it.giallotouch.bridge.BattleLayout
import it.giallotouch.bridge.GameCommand
import it.giallotouch.bridge.ItemAction
import it.giallotouch.bridge.ItemActionExecutor
import it.giallotouch.bridge.ItemMenus
import it.giallotouch.bridge.BattleAction
import it.giallotouch.bridge.BattleActionExecutor
import it.giallotouch.bridge.BattleMenus
import it.giallotouch.bridge.CommandExecutor
import it.giallotouch.bridge.MenuReader
import it.giallotouch.bridge.MenuKind
import it.giallotouch.bridge.GameSnapshot
import it.giallotouch.bridge.GameStateReader
import it.giallotouch.bridge.FreeRoam
import it.giallotouch.bridge.MapGrid
import it.giallotouch.bridge.WalkExecutor
import it.giallotouch.bridge.MemoryMapIt
import it.giallotouch.bridge.NameCodec
import it.giallotouch.bridge.NameExecutor
import it.giallotouch.bridge.NamingReader
import it.giallotouch.bridge.RomData
import it.giallotouch.bridge.TextAssist
import it.giallotouch.bridge.TileCanvas
import it.giallotouch.storage.SaveSlot
import it.giallotouch.storage.SaveStorage
import it.giallotouch.storage.SlotEntry
import it.giallotouch.storage.SlotInfo
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Una partita in corso: un thread dedicato fa avanzare il gioco un fotogramma alla volta,
 * manda l'audio all'altoparlante (che fa anche da orologio) e pubblica l'immagine.
 */
class GameSession(private val storage: SaveStorage, private val rom: ByteArray) {

    private val handle: Long = NativeCore.nativeCreate(SAMPLE_RATE)
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    @Volatile private var running = true
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var lastBattery: ByteArray? = null

    /** Ultimo fotogramma completo (160×144 ARGB), letto dalla vista del gioco. */
    val latestFrame = AtomicReference(IntArray(NativeCore.WIDTH * NativeCore.HEIGHT))

    /** Vista estesa a tutto schermo: altezza richiesta dalla vista (0 = spenta) e ultimo fotogramma esteso. */
    class ExtFrame(val pixels: IntArray, var height: Int = 0, var active: Boolean = false)
    @Volatile var extendedHeight = 0
    /** Riga dell'inquadratura originale nella vista estesa: al centro più [MAP_SHIFT] pixel del gioco (scelta del committente). */
    val extendedFrameTop: Int get() = ((extendedHeight - NativeCore.HEIGHT) / 2 + MAP_SHIFT).coerceIn(0, maxOf(0, extendedHeight - NativeCore.HEIGHT))
    val latestExtended = AtomicReference(ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT)))
    private var extBack = ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT))
    /** true quando lo schermo mostra la mappa estesa (serve ai tocchi fuori dall'inquadratura originale). */
    @Volatile var extendedActive = false
        private set
    @Volatile var onFrame: (() -> Unit)? = null
    /** Colpo che fa danno in lotta (vibrazione, D-069). Chiamata dal thread del gioco. */
    @Volatile var onHit: (() -> Unit)? = null
    private var lastEnemyHp = -1
    private var lastYellow = 0
    private var afterBattle = 0
    private var maskMapId = -1
    private val warpBlock = ByteArray(1 + 4 * 32)
    @Volatile var onWarp = false
    /** Pulsante START visibile in questo fotogramma (D-098). */
    @Volatile var startOk = false
    private var startMapId = -1
    private var startOnWarp = false
    private var doorPending = false
    private var doorUniform = false
    private var doorFrames = 0
    private var startMapCalm = 0
    private var leftHouse = false
    private var doorRecent = 0
    @Volatile var debugFrameInfo = ""
    /** Solo per il banco di prova: righe del gioco disegnate dalla mappa da questa in giù (-1 = normale). */
    @Volatile var forceMapFromRow = -1
    private var tipoTail = 0
    private var nameTail = 0
    private var battleMenuSeen = false
    private var shakeVibe = 0
    private var shakeFrames = 0
    private var shakeHold = 0
    private var lastScx = 0
    private var lastScy = 0
    private var keptText = IntArray(0)
    private var flashState = 0
    private var shockSeen = false
    private var titleWasShown = false
    /** Fotogrammi fatti andare veloci nel bianco dopo START (per il banco di prova). */
    @Volatile var fastFrames = 0
    /** Sprite dell'introduzione anche fuori dalle 144 righe (D-076), riga 0 del gioco alla riga [SPRITE_LAYER_TOP]. */
    val introSprites = AtomicReference<IntArray?>(null)
    /** Gli sprite dell'introduzione vanno disegnati anche sopra l'immagine del gioco (stelline di GAME FREAK). */
    @Volatile var introSpritesOver = false
    private var introSpritesMiss = 0
    /** Nuove opzioni del gioco (D-075): scritte nella memoria dal thread del gioco. */
    fun writeGameOptions(value: Int) {
        gameOptions = value
        tasks.add(Runnable { NativeCore.nativeWriteMemory(handle, MemoryMapIt.OPTIONS, value) })
    }
    /**
     * Mossa da dimenticare (D-085): il gioco mostra le 4 mosse (lista 4 voci in riga 8, colonna 5) per far spazio a una
     * nuova. [forgetMoves] = mosse attuali (D0E0..D0E3), [forgetNew] = mossa nuova (D0E4); null se non c'è la lista.
     */
    @Volatile var forgetMoves: IntArray? = null
    @Volatile var forgetNew = 0
    /**
     * Etere / PP-su (D-093): lista del gioco "Ricarica PP di quale tecnica?" (riquadro alla riga 7, colonna 4). Le
     * righe del testo del gioco sono in [etherText]; l'app mostra le mosse del Pokémon scelto nel suo riquadro.
     */
    @Volatile var etherList = false
    @Volatile var etherText: List<String> = emptyList()
    private var etherHash = 0
    private var etherQuestion = 0
    private var etherDone = false
    private var etherCalm = 0
    private var lastYn: YesNo? = null
    private var answerTail = 0
    private var ynGone = 99
    private var dialogWas = false
    private var dialogEnd = 0
    /** Riquadro SÌ/NO del gioco ancora sullo schermo dopo la risposta (D-087). */
    @Volatile var answerPending = false
    /** Tasto premuto direttamente per qualche fotogramma (scelte immediate, D-081/D-085). */
    private var directKey = 0
    private var directFrames = 0
    /** Scelta immediata in una lista del gioco: cursore scritto in memoria, un fotogramma libero, poi il tasto. */
    fun pickDirect(index: Int?, key: Int = NativeCore.KEY_A) {
        commandBusy = true
        tasks.add(Runnable {
            if (index != null) NativeCore.nativeWriteMemory(handle, MemoryMapIt.CURRENT_MENU_ITEM, index)
            directKey = key; directFrames = 5
        })
    }
    /**
     * Scelta in un menu del gioco premendo i tasti come una persona (D-093): GIÙ [index] volte, poi A (o [key]).
     * Serve dove la scelta diretta non basta (lista delle mosse dell'Etere: con la scelta diretta il gioco
     * consumava l'Etere senza ricaricare i PP).
     */
    fun pickByKeys(index: Int, key: Int = NativeCore.KEY_A) {
        commandBusy = true
        tasks.add(Runnable {
            val seq = ArrayList<Int>()
            repeat(6) { seq += 0 }
            repeat(index) { repeat(6) { seq += NativeCore.KEY_DOWN }; repeat(8) { seq += 0 } }
            repeat(12) { seq += key }; repeat(4) { seq += 0 }
            keyScript = seq.toIntArray(); keyScriptPos = 0
        })
    }
    private var keyScript: IntArray? = null
    private var keyScriptPos = 0
    /** Opzioni del gioco (byte a 0xD359: velocità del testo, animazione, stile lotta, suono). */
    @Volatile var gameOptions = 0x03
    private var lastPlayerHp = -1

    /** Tile del gioco (carattere, riquadri) per disegnare le aggiunte dell'app come il gioco (D-039); null all'inizio. */
    val tileSheet = AtomicReference<IntArray?>(null)
    private val sheetBuf = IntArray(TileCanvas.SHEET_SIZE)

    /** Lotta a tutto schermo: true quando la schermata di lotta va mostrata divisa (scena + riquadro del testo). */
    @Volatile var battleSplit = false
        private set
    private var splitHold = 0
    private var extHold = 0
    /** La vista estesa mostra la mappa (non la schermata del titolo a tutto schermo). */
    @Volatile var extendedIsMap = false
        private set
    private var tilesSaved = false
    private var mainHold = 0
    private var mainTail = 0
    private var cardClosing = 0
    /** La grafica contiene i riquadri (angolo 0x79 e lettera "A" non vuoti): si può salvare per la scelta del file. */
    private fun hasBoxTiles(sheet: IntArray): Boolean {
        fun dark(t: Int): Boolean {
            val sx = (t and 15) * 8; val sy = (t shr 4) * 8; val bg = sheet[sy * 128 + sx + 7 * 128 + 7]
            var n = 0
            for (y in 0 until 8) for (x in 0 until 8) if (sheet[(sy + y) * 128 + sx + x] != bg) n++
            return n >= 4
        }
        // 0x7A = lato orizzontale (ogni riga di un solo colore), 0x7C = lato verticale (ogni colonna di un solo colore)
        fun striped(t: Int, rows: Boolean): Boolean {
            val sx = (t and 15) * 8; val sy = (t shr 4) * 8
            for (a in 0 until 8) for (b in 1 until 8) {
                val p0 = if (rows) sheet[(sy + a) * 128 + sx] else sheet[sy * 128 + sx + a]
                val p = if (rows) sheet[(sy + a) * 128 + sx + b] else sheet[(sy + b) * 128 + sx + a]
                if (p != p0) return false
            }
            return true
        }
        return dark(0x79) && dark(0x7A) && dark(0x7C) && dark(0x80) && striped(0x7A, rows = true) && striped(0x7C, rows = false)
    }

    /**
     * Tendina prima della lotta a tutto schermo (D-067): le caselle che il gioco ha già coperto di nero nell'immagine
     * originale si allargano in proporzione sopra e sotto (righe estese), senza deformare la mappa.
     */
    private val wiped = BooleanArray(20 * 18)
    private var lastMapExt = IntArray(0)
    private var lastMapH = 0
    private var lastMapTop = 0
    private var wipeDark = 0
    private var wipeOn = false
    private var wipeColor = 0
    private val colorMap = HashMap<Int, Int>()
    /** Corrispondenza colori del fotogramma (anche schiariti o scuriti in una dissolvenza) → colori mostrati. */
    private val frameMap = HashMap<Int, Int>()
    private fun mix(c: Int, to: Int, t: Float): Int {
        fun ch(sh: Int) = (((c shr sh) and 255) + ((((to shr sh) and 255) - ((c shr sh) and 255)) * t)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    /**
     * L'immagine usa colori già visti nell'ultima immagine buona della mappa, oppure gli stessi schiariti verso il bianco
     * o scuriti verso il nero (dissolvenza, lampo): si prepara [frameMap] coi colori ricolorati schiariti/scuriti della
     * stessa misura (D-083). Falso se compare un colore che non si spiega così (lampi invertiti, altre schermate).
     */
    private fun remappable(frame: IntArray): Boolean {
        if (colorMap.isEmpty() || colorMap.all { it.key == it.value }) return false
        frameMap.clear()
        var i = 0
        while (i < frame.size) {
            val c = frame[i]; i += 3
            if (frameMap.containsKey(c)) continue
            val exact = colorMap[c]
            if (exact != null) { frameMap[c] = exact; continue }
            if (frameMap.size > 24) return false
            var best = Int.MAX_VALUE; var res = c
            for ((o, shown) in colorMap) for (to in intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
                // t dal canale più lontano dalla meta, poi errore sugli altri
                var t = -1f
                for (sh in intArrayOf(16, 8, 0)) {
                    val d = ((to shr sh) and 255) - ((o shr sh) and 255)
                    if (kotlin.math.abs(d) > 40) { t = (((c shr sh) and 255) - ((o shr sh) and 255)).toFloat() / d; break }
                }
                if (t < 0f || t > 1f) continue
                val m = mix(o, to, t)
                val err = intArrayOf(16, 8, 0).sumOf { kotlin.math.abs(((m shr it) and 255) - ((c shr it) and 255)) }
                if (err < best) { best = err; res = mix(shown, to, t) }
            }
            if (best > 24) return false
            frameMap[c] = res
        }
        return true
    }

    /** Righe fuori dall'immagine del gioco: stessa schiaritura del fotogramma (misurata sul bianco/nero più usato). */
    private fun fadeOutside(ext: IntArray, eh: Int, top: Int, fr: IntArray) {
        // misura: colore del fotogramma più frequente non bianco e sua corrispondenza
        var t = 0f; var to = 0xFFFFFFFF.toInt(); var bestN = 0
        val counts = HashMap<Int, Int>()
        var i = 0
        while (i < fr.size) { counts[fr[i]] = (counts[fr[i]] ?: 0) + 1; i += 7 }
        for ((c, n) in counts) {
            if (n <= bestN || colorMap.containsKey(c)) continue
            for ((o, _) in colorMap) for (dst in intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
                val d = ((dst shr 8) and 255) - ((o shr 8) and 255)
                if (kotlin.math.abs(d) < 40) continue
                val tt = (((c shr 8) and 255) - ((o shr 8) and 255)).toFloat() / d
                if (tt !in 0f..1f) continue
                val m = mix(o, dst, tt)
                val err = intArrayOf(16, 8, 0).sumOf { kotlin.math.abs(((m shr it) and 255) - ((c shr it) and 255)) }
                if (err <= 24 && n > bestN) { bestN = n; t = tt; to = dst }
            }
        }
        if (bestN == 0) return
        for (r in 0 until eh) {
            if (r in top until top + NativeCore.HEIGHT) continue
            val b = r * NativeCore.WIDTH
            for (x in 0 until NativeCore.WIDTH) ext[b + x] = mix(lastMapExt[b + x], to, t)
        }
    }

    /** Caselle già nere della tendina nell'immagine del gioco (riempie [wiped] e [wipeColor]). */
    private fun wipeTiles(frame: IntArray): Int {
        var dark = frame[0]; var darkL = Int.MAX_VALUE
        var i = 0
        while (i < frame.size) { val c = frame[i]; val l = (c shr 16 and 255) + (c shr 8 and 255) + (c and 255); if (l < darkL) { darkL = l; dark = c }; i += 13 }
        if (darkL > 150) { java.util.Arrays.fill(wiped, false); return 0 }
        wipeColor = dark
        var n = 0
        for (ty in 0 until 18) for (tx in 0 until 20) {
            var solid = true
            loop@ for (y in 0 until 8) for (x in 0 until 8) if (frame[(ty * 8 + y) * NativeCore.WIDTH + tx * 8 + x] != dark) { solid = false; break@loop }
            wiped[ty * 20 + tx] = solid; if (solid) n++
        }
        return n
    }
    /**
     * Riquadro di testo del gioco già disegnato nell'immagine (righe 96–143), con qualunque colori (anche nelle dissolvenze
     * rosse/gialle del Professore, D-104): una riga del bordo superiore (96–103) di un solo colore per tutta la larghezza e,
     * sotto, la riga vuota interna (106) quasi tutta di un altro colore. Il nero intorno alle stanze e gli scaffali no.
     */
    /** Riga interna del riquadro (106) di un colore solo: esclude il nero intorno alle stanze (per START, D-098). */
    private fun boxInnerClean(px: IntArray): Boolean {
        val inner = 106 * NativeCore.WIDTH; val fill = px[inner + 80]
        val l = ((fill shr 16) and 255) + ((fill shr 8) and 255) + (fill and 255)
        return l > 300 && (8 until NativeCore.WIDTH - 8).count { px[inner + it] == fill } >= 112
    }

    /**
     * Bordi superiore e inferiore scuri (righe 96–103 e 136–143) d'una sola tinta per quasi tutta la larghezza (D-109): il
     * terreno chiaro e uniforme (sabbia della Strada 2) aveva riga interna "pulita" e righe d'un colore solo, e sembrava
     * un riquadro.
     */
    private fun boxEdgesDark(px: IntArray): Boolean {
        fun darkRow(y0: Int): Boolean = (y0 until y0 + 8).any { y ->
            val b = y * NativeCore.WIDTH; val c = px[b + 80]
            val l = ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)
            l < 200 && (8 until NativeCore.WIDTH - 8).count { px[b + it] == c } >= 112
        }
        return darkRow(96) && darkRow(136)
    }

    private var doorWhiteRun = 0
    private var startCandRun = 0
    /** Quasi tutto bianco (almeno il 90% dei punti). */
    private fun nearlyWhite(px: IntArray): Boolean {
        var n = 0; var w = 0; var i = 0
        while (i < px.size) { val c = px[i]; n++; if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) > 720) w++; i += 7 }
        return w * 10 >= n * 9
    }
    /** Tutto scuro (fine della dissolvenza al nero). */
    private fun darkFrame(px: IntArray): Boolean {
        var i = 0
        while (i < px.size) { val c = px[i]; if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) > 150) return false; i += 7 }
        return true
    }

    private fun boxBorderDrawn(px: IntArray): Boolean {
        val inner = 106 * NativeCore.WIDTH
        val fill = px[inner + 80]
        var same = 0
        for (x in 8 until NativeCore.WIDTH - 8) if (px[inner + x] == fill) same++
        val innerOk = same >= 112   // qualche pixel può essere coperto da una persona (D-108)
        for (y in 96 until 104) {
            val b = y * NativeCore.WIDTH; val c = px[b + 80]
            if (innerOk && c == fill) continue
            var n = 0
            for (x in 8 until NativeCore.WIDTH - 8) if (px[b + x] == c) n++
            if (n < 120) continue
            // bordo scuro (colori normali): basta il bordo, anche se l'interno non è ancora pulito (come D-086);
            // bordo di un altro colore (dissolvenze): serve anche l'interno di un colore solo
            val dark = ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) < 200
            if (dark || innerOk) return true
        }
        return false
    }

    private fun extendWipe(frame: IntArray, ext: IntArray, height: Int, frameTop: Int) {
        // colore più scuro dell'immagine = nero della tendina
        var dark = frame[0]; var darkL = Int.MAX_VALUE
        var i = 0
        while (i < frame.size) { val c = frame[i]; val l = (c shr 16 and 255) + (c shr 8 and 255) + (c and 255); if (l < darkL) { darkL = l; dark = c }; i += 13 }
        var any = false
        for (ty in 0 until 18) for (tx in 0 until 20) {
            var solid = true
            loop@ for (y in 0 until 8) for (x in 0 until 8) if (frame[(ty * 8 + y) * NativeCore.WIDTH + tx * 8 + x] != dark) { solid = false; break@loop }
            wiped[ty * 20 + tx] = solid; if (solid) any = true
        }
        if (!any) return
        // Tendina a tutto schermo (D-099): il disegno del gioco (20×18 caselle) si allunga su tutta l'altezza, anche al
        // centro, sopra l'ultima immagine della mappa; prima al centro restava la tendina del gioco e sopra/sotto due
        // copie schiacciate.
        val full = lastMapH == height && lastMapExt.size >= height * NativeCore.WIDTH
        if (full) System.arraycopy(lastMapExt, 0, ext, 0, height * NativeCore.WIDTH)
        val below = height - frameTop - NativeCore.HEIGHT
        for (r in 0 until height) {
            val fr = if (full) r * NativeCore.HEIGHT / height else when {
                r < frameTop -> if (frameTop > 0) r * 72 / frameTop else continue
                r >= frameTop + NativeCore.HEIGHT -> if (below > 0) 72 + (r - frameTop - NativeCore.HEIGHT) * 72 / below else continue
                else -> continue
            }.coerceIn(0, NativeCore.HEIGHT - 1)
            val rowBase = r * NativeCore.WIDTH
            for (tx in 0 until 20) if (wiped[(fr.coerceIn(0, NativeCore.HEIGHT - 1) / 8) * 20 + tx]) java.util.Arrays.fill(ext, rowBase + tx * 8, rowBase + tx * 8 + 8, dark)
        }
    }

    /** Immagine quasi tutta bianca (dissolvenza del gioco in corso). */
    private fun mostlyWhite(px: IntArray): Boolean {
        val white = px[0]
        var other = 0
        var i = 0
        while (i < px.size) { if (px[i] != white) other++; i += 37 }
        return other < px.size / 37 / 20
    }
    private var cardOpening = 0
    /** Azione su strumenti o squadra fuori dalla lotta in corso: si mostra la mappa salvata (D-071). */
    private var itemHold = false
    private var itemHoldCalm = 0
    /** Ultima scheda allenatore vista (immagine del gioco 160×144), anche sul telefono per la volta dopo (D-068). */
    private var cardCache: IntArray? = null
    private var cardCacheLoaded = false
    private var instantCard = false
    private var cardLiveFrames = 0
    private var cardOpenCalm = 0
    /** La scheda allenatore si sta aprendo (l'app mostra ancora la mappa e il riquadro START, D-065). */
    /** Sullo schermo c'è una scelta (cursore ▶ di un menu del gioco, SÌ/NO, nomi, PC, mosse): niente tasto A nel vuoto. */
    @Volatile var choiceOnScreen = false
        private set
    /** Schermata del titolo del gioco (D-069). */
    @Volatile var titleShown = false
        private set
    /** Si cammina liberamente in questo fotogramma (l'istantanea della memoria è più lenta, ogni 15 fotogrammi). */
    @Volatile var freeRoamNow = false
        private set
    @Volatile var cardOpeningActive = false
        private set
    /** L'app sta chiudendo da sola i menu del gioco (niente SELECT né altri pulsanti per quei menu). */
    @Volatile var menusClosing = false
        private set
    private var cardCalm = 0
    /** Mappa (gioco e vista estesa) salvata quando si apre la scheda allenatore (D-065). */
    private val savedMap = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
    private val savedExt = ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT))
    /** Immagine ferma (passaggio con grafica rovinata del gioco, D-065). */
    private var holdImage = false
    private var menuHold = 0
    private val HOLD_FRAMES = 12
    /** In lotta il gioco chiede quale Pokémon mandare in campo (dopo un KO). */
    @Volatile var battlePartyAsked = false
        private set
    /** In lotta è aperto un menu del gioco diverso da quello di lotta (squadra, borsa…). */
    @Volatile var battleGameMenu = false
        private set
    private val frameTiles = ByteArray(MenuReader.SCREEN_SIZE)
    private val frameVars = ByteArray(MenuReader.MENU_VARS_SIZE)

    /** Menu iniziale del gioco sullo schermo: numero di voci del gioco (2 o 3), 0 se non c'è (D-040). */
    @Volatile var mainMenuItems = 0
        private set
    /** Menu di lotta del gioco (LOTTA/PKMN/STRUM./FUGA o mosse) sullo schermo: l'app lo copre, ha i suoi riquadri (D-045). */
    @Volatile var battleMenuShown = false
        private set
    /**
     * Domanda Sì/No del gioco sullo schermo (D-046): riquadro del gioco in caselle (x0, y0, x1, y1), voce del cursore e
     * caselle delle due risposte (2 per riga), per ridisegnarle nei riquadri dell'app sotto il dialogo.
     */
    class YesNo(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val item: Int, val yes: IntArray, val no: IntArray)
    @Volatile var yesNo: YesNo? = null
        private set
    /** Lista dei nomi proposti dal Professore sullo schermo (caselle di ogni voce): l'app la mostra toccabile (D-052). */
    @Volatile var nameList: List<IntArray>? = null
        private set

    /**
     * Caselle utilizzabili: la lettera "A" (0x80) ha inchiostro e carta di colori diversi. Durante le dissolvenze (per
     * esempio all'uscita da una lotta) il gioco sbianca la tavolozza: quelle caselle non vanno prese, altrimenti i
     * riquadri dell'app diventerebbero invisibili.
     */
    private fun sheetReadable(sheet: IntArray): Boolean {
        val sx = (0x80 and 15) * 8; val sy = (0x80 shr 4) * 8
        val paper = sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8]
        var ink = 0
        for (y in 0 until 8) for (x in 0 until 8) if (sheet[(sy + y) * 128 + sx + x] != paper) ink++
        val c = sheet[(sy + 3) * 128 + sx + 3]
        fun lum(v: Int) = ((v shr 16) and 255) + ((v shr 8) and 255) + (v and 255)
        return ink >= 8 && kotlin.math.abs(lum(paper) - lum(sheet.firstOrNull { it != paper } ?: paper)) > 150
    }

    /**
     * Scritte e riquadri dell'app sempre in bianco e nero (D-044, D-053): in alcuni momenti (per esempio all'inizio del
     * gioco) la tavolozza del testo è colorata (gialla); le caselle si riportano a carta bianca e inchiostro nero,
     * con i grigi intermedi in base alla luminosità.
     */
    private fun normalizeSheet(sheet: IntArray) {
        for (i in sheet.indices) {
            val c = sheet[i]
            val l = (((c shr 16) and 255) * 30 + ((c shr 8) and 255) * 59 + (c and 255) * 11) / 100
            val g = when { l >= 170 -> 0xF8; l < 70 -> 0x18; l >= 120 -> 0xA8; else -> 0x58 }
            sheet[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    private val frameMask = BoxMask()
    private val extMask = BoxMask()

    /**
     * Dialogo del gioco (riquadro in basso, righe 12–17) mentre si cammina: l'app lo mostra più in alto (D-048).
     * [dialogStrip]: i pixel del riquadro (160×48) dell'ultimo fotogramma, null se non c'è un dialogo.
     */
    @Volatile var dialogStrip: IntArray? = null
        private set
    private var stripA = IntArray(NativeCore.WIDTH * 48)
    private var stripB = IntArray(NativeCore.WIDTH * 48)

    private fun dialogAtBottom(t: ByteArray): Boolean {
        fun tile(x: Int, y: Int) = t[y * 20 + x].toInt() and 0xFF
        return tile(0, 12) == 0x79 && tile(19, 12) == 0x7B && tile(0, 17) == 0x7D && tile(19, 17) == 0x7E
    }

    private fun yesNoInfo(vars: ByteArray, tiles: ByteArray, item: Int): YesNo? {
        fun t(x: Int, y: Int) = if (x in 0 until 20 && y in 0 until 18) tiles[y * 20 + x].toInt() and 0xFF else -1
        val topY = vars[0].toInt() and 0xFF; val topX = vars[1].toInt() and 0xFF
        val x0 = topX - 1
        // angolo in alto a sinistra: subito sopra la prima voce (SÌ/NO) o una riga più su (CURA/ESCI)
        val y0 = listOf(topY - 1, topY - 2).firstOrNull { t(x0, it) == 0x79 } ?: return null
        val x1 = (x0 + 1 until 20).firstOrNull { t(it, y0) == 0x7B } ?: return null
        val y1 = (y0 + 1 until 18).firstOrNull { t(x0, it) == 0x7D } ?: return null
        fun word(y: Int) = IntArray(x1 - topX - 1) { t(topX + 1 + it, y) }.dropLastWhile { it == 0x7F }.toIntArray()
        return YesNo(x0, y0, x1, y1, item, word(topY), word(topY + 2))
    }

    /** Schermata dei nomi del gioco sullo schermo: l'app non la mostra mai (copre con la sua, D-046). */
    @Volatile var namingShown = false
        private set
    /** Menu del negozio sullo schermo (D-056) e articoli in vendita (codici). */
    @Volatile var shopMenuShown = false
        private set
    @Volatile var shopItems: List<Int> = emptyList()
        private set
    /** Menu del PC del giocatore sullo schermo (D-055): l'app mostra la sua schermata STRUMENTI PC. */
    @Volatile var pcMenuShown = false
        private set
    /** Scelta del PC del Centro sullo schermo (D-058): voci del gioco (vuota se non c'è). */
    @Volatile var pcChoice: List<String> = emptyList()
        private set
    /** Menu del Box Pokémon del Centro sullo schermo (D-058), Pokémon nel box attuale e numero del box. */
    @Volatile var boxMenuShown = false
        private set
    @Volatile var boxMons: List<it.giallotouch.bridge.BoxMon> = emptyList()
        private set
    @Volatile var boxNumber = 1
        private set
    private val boxBlock = ByteArray(MemoryMapIt.BOX_BLOCK_SIZE)
    /** La scheda allenatore del gioco è sullo schermo: l'app la mostra ridisposta (D-045). */
    @Volatile var trainerCardShown = false
        private set
    /** Cursore ▶ del menu iniziale ridisegnato (indice nelle voci dell'app), mosso dalla levetta. */
    @Volatile var startMenuCursor = 0

    /** Ultimo stato letto dalla memoria del gioco (squadra, soldi, posizione), aggiornato 4 volte al secondo. */
    val latestSnapshot = AtomicReference(GameSnapshot.EMPTY)
    private val stateBlock = ByteArray(GameStateReader.BLOCK_SIZE)

    /** Dati letti dalla ROM dell'utente (nomi delle mosse), solo per la versione italiana supportata. */
    val romData: RomData = RomData.load(rom, SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1)

    /**
     * Avvio come sul Game Boy (D-034): a ogni apertura il gioco riparte da capo, con il salvataggio del gioco
     * (batteria) già inserito, e mostra introduzione, titolo e menu iniziale. I salvataggi dell'app si caricano
     * da lì con "Carica partita".
     */
    init {
        NativeCore.nativeLoadRom(handle, rom)
        if (SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1) NativeCore.nativeTrustRomFont(handle)
        storage.readWithBackup(storage.batteryFile)?.let {
            NativeCore.nativeLoadBattery(handle, it)
            lastBattery = it
        }
    }

    /**
     * true quando si sta giocando una partita (si è camminato sulla mappa almeno una volta, o si è caricato
     * un salvataggio): solo allora si salva in automatico all'uscita e si mostra la squadra.
     */
    @Volatile var inGame = false
        private set

    /** Tasti premuti dal giocatore (levetta, tocchi); applicati a ogni fotogramma dal thread del gioco. */
    @Volatile private var userKeys = 0
    fun setKeys(mask: Int) { userKeys = mask }

    /** Testo automatico (0 = spento, 1–3 = lento/medio/veloce) e dito tenuto premuto (testo più veloce). */
    private val textAssist = TextAssist()
    @Volatile var autoTextSpeed: Int = 0
        set(value) { field = value.coerceIn(0, 3) }

    /** Gioco in bianco e nero (opzione del committente, D-054): l'immagine si riporta a 4 grigi. */
    @Volatile var blackWhite: Boolean = false

    /** Volume 0–5 (D-046), applicato al suono del gioco. */
    @Volatile var volume: Int = 5
        set(value) { field = value.coerceIn(0, 5); runCatching { audioTrack?.setVolume(field / 5f) } }
    fun setHolding(holding: Boolean) { textAssist.holding = holding }

    /** Esecutore dei comandi di lotta: quando è occupato, i suoi tasti sostituiscono quelli del giocatore. */
    private val executor = CommandExecutor()
    @Volatile var commandBusy = false
        private set
    private val menuVars = ByteArray(MenuReader.MENU_VARS_SIZE)
    private val tileMap = ByteArray(MenuReader.SCREEN_SIZE)

    /** Chiede di eseguire un comando di lotta (scegliere una mossa, fuggire…). */
    fun command(cmd: GameCommand) {
        commandBusy = true
        // Nomi del Professore e menu iniziale (D-081): si scrive la voce scelta nel cursore del gioco e si preme subito A.
        if (cmd is GameCommand.MainMenu && (nameList != null || mainMenuItems > 0)) {
            tasks.add(Runnable { NativeCore.nativeWriteMemory(handle, MemoryMapIt.CURRENT_MENU_ITEM, cmd.index); executor.start(cmd, direct = true) })
            return
        }
        tasks.add(Runnable { executor.start(cmd) })
    }

    /** Scrittura di un nome nella schermata dei nomi del gioco (tastiera del telefono, D-029). */
    private val nameExecutor = NameExecutor()
    private val nameBuffer = ByteArray(NamingReader.NAME_BUFFER_SIZE)
    @Volatile var nameBusy = false
        private set
    /** Esito dell'ultimo nome scritto: null finché lavora, true se il gioco l'ha accettato. */
    @Volatile var nameResult: Boolean? = null
        private set

    /** Chiede di scrivere [text] nella schermata dei nomi aperta; i caratteri non disponibili vengono scartati. */
    fun enterName(text: String) {
        val codes = NameCodec.encode(NameCodec.sanitize(text, 10)) ?: return
        if (codes.isEmpty()) return
        nameBusy = true
        nameResult = null
        tasks.add(Runnable { nameExecutor.start(codes) })
    }

    private fun readNaming() = run {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        NativeCore.nativeReadRange(handle, NamingReader.NAME_BUFFER, nameBuffer)
        NamingReader.read(menuVars, tileMap, nameBuffer)
    }

    /** Usa/Getta uno strumento passando dal menu originale STRUMENTI (menu Borsa nativo). */
    private val itemExecutor = ItemActionExecutor()
    @Volatile var itemBusy = false
        private set
    /** Esito dell'ultima azione: null mentre lavora, true se completata. */
    @Volatile var itemResult: Boolean? = null
        private set

    fun itemAction(action: ItemAction) {
        itemBusy = true
        itemResult = null
        if (action !is ItemAction.OpenTrainer && !latestSnapshot.get().inBattle) {
            // Strumenti, squadra e simili fuori dalla lotta (D-071): la mappa salvata resta a tutto schermo per tutto il
            // passaggio nei menu del gioco; si vedono solo i messaggi del risultato.
            tasks.add(Runnable {
                System.arraycopy(latestFrame.get(), 0, savedMap, 0, savedMap.size)
                val e = latestExtended.get()
                System.arraycopy(e.pixels, 0, savedExt.pixels, 0, e.pixels.size); savedExt.height = e.height; savedExt.active = e.active
                itemHold = true; itemHoldCalm = 0
            })
        }
        if (action is ItemAction.OpenTrainer) {
            holdSheet = true
            // immagine della mappa di adesso, da mostrare quando si chiude la scheda (D-065)
            tasks.add(Runnable {
                System.arraycopy(latestFrame.get(), 0, savedMap, 0, savedMap.size)
                val e = latestExtended.get()
                System.arraycopy(e.pixels, 0, savedExt.pixels, 0, e.pixels.size); savedExt.height = e.height; savedExt.active = e.active
                if (!cardCacheLoaded) { cardCacheLoaded = true; cardCache = storage.loadCardImage() }
                cardOpening = 60 * 6; cardOpenCalm = 0; cardOpeningActive = true
            })
        }
        tasks.add(Runnable { walker.cancel(); itemExecutor.start(action); currentItemAction = action })
    }
    private var currentItemAction: ItemAction? = null

    /** SQUADRA e BORSA in lotta dalle liste dell'app (D-050): l'esecutore guida i menu del gioco senza mostrarli. */
    private val battleExecutor = BattleActionExecutor()
    @Volatile var battleBusy = false
        private set
    fun battleAction(a: BattleAction) {
        battleBusy = true
        tasks.add(Runnable { walker.cancel(); battleExecutor.start(a) })
    }
    private fun stepBattleAction(): Int {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val scroll = NativeCore.nativeReadMemory(handle, MemoryMapIt.LIST_SCROLL_OFFSET)
        val scr = BattleMenus.read(menuVars, tileMap, scroll)
        val keys = battleExecutor.step(scr, menuVars.contentHashCode() * 31 + tileMap.contentHashCode())
        if (!battleExecutor.busy) battleBusy = false
        return keys
    }

    /**
     * Menu originali del gioco attraversati dall'app da sola (menu START, lista degli strumenti, chiusura della scheda):
     * non si vedono (D-049), resta l'immagine di prima. [trainerOpening]: fotogrammi di attesa della scheda allenatore.
     */
    private var trainerOpening = 0
    /** Dopo aver usato uno strumento su un Pokémon: si aspetta il ritorno ai menu del gioco per richiuderli. */
    @Volatile private var pendingClose = false
    private var pendingCloseFrames = 0
    private var pendingFree = 0
    private fun hidingOldMenus(): Boolean = !trainerCardShown &&
        (itemExecutor.busy || battleExecutor.busy || closingFrames > 0 || trainerOpening > 0 || (pendingClose && !textAtBottom(frameTiles)))

    private fun textAtBottom(t: ByteArray) = (t[12 * 20].toInt() and 0xFF) == 0x79 && (t[17 * 20].toInt() and 0xFF) == 0x7D

    /** Una volta per fotogramma: con [pendingClose], appena il gioco torna a un menu (lista, START) lo si chiude. */
    private fun checkPendingClose(free: Boolean) {
        if (!pendingClose) return
        // Si smette solo dopo un secondo di cammino libero: nei passaggi tra una schermata e l'altra il gioco può
        // sembrare per qualche fotogramma sulla mappa.
        pendingFree = if (free && closingFrames == 0) pendingFree + 1 else 0
        if (--pendingCloseFrames <= 0 || pendingFree >= 60) { pendingClose = false; return }
        if (closingFrames > 0) return
        // Etere / PP-su (D-093): il gioco aspetta la scelta della mossa (riquadro dell'app): non si chiude niente.
        if (etherList) { pendingFree = 0; return }
        val kind = ItemMenus.read(frameVars, frameTiles, 0).kind
        if (kind == ItemMenus.Kind.ITEM_LIST || kind == ItemMenus.Kind.START_MENU || kind == ItemMenus.Kind.USE_TOSS || kind == ItemMenus.Kind.PARTY) {
            closingFrames = 60 * 8; closingCalm = 0
        }
    }

    /**
     * Sulla scheda allenatore il gioco riusa le caselle dei riquadri per la cornice della scheda: finché la scheda è
     * aperta (o sta per aprirsi/chiudersi) l'app tiene le caselle lette prima (D-045).
     */
    @Volatile private var holdSheet = false

    /** INDIETRO della scheda allenatore: B finché si torna a camminare (chiude scheda e menu START). */
    fun closeMenus() { tasks.add(Runnable {
        closingFrames = 60 * 8; closingCalm = 0
        if (trainerCardShown && savedExt.height >= 0) { cardClosing = 60 * 3; cardCalm = 0 }
    }) }
    private var closingFrames = 0
    private var closingCalm = 0

    private fun stepItemAction(): Int {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val scroll = NativeCore.nativeReadMemory(handle, MemoryMapIt.LIST_SCROLL_OFFSET)
        val freeRoam = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(tileMap)
        val observation = menuVars.contentHashCode() * 31 + tileMap.contentHashCode()
        val keys = itemExecutor.step(ItemMenus.read(menuVars, tileMap, scroll), freeRoam, observation)
        if (!itemExecutor.busy) {
            itemResult = itemExecutor.result == ItemActionExecutor.Result.DONE
            itemBusy = false
            if (currentItemAction is ItemAction.OpenTrainer && itemResult == true) trainerOpening = 120
            // Usato su un Pokémon: dopo il messaggio del gioco ("…recupera 20 PS") si richiudono i menu vecchi (D-051).
            if ((currentItemAction as? ItemAction.Use)?.target != null && itemResult == true) { pendingClose = true; pendingCloseFrames = 60 * 60; pendingFree = 0 }
            currentItemAction = null
        }
        return keys
    }

    /** Camminata automatica verso un passo toccato sullo schermo (con eventuale interazione all'arrivo). */
    private val walker = WalkExecutor()

    /** Chiede di raggiungere il passo (x, y) della mappa; se è una persona o un oggetto, ci si parla. */
    fun walkTo(x: Int, y: Int) {
        tasks.add(Runnable { planWalk(x, y) })
    }

    private fun readBytes(address: Int, n: Int): ByteArray = ByteArray(n).also { NativeCore.nativeReadRange(handle, address, it) }

    /** Tocco sullo schermo mentre si cammina (D-049): parla con chi è vicino, altrimenti tasto A. */
    fun talkNearby() {
        tasks.add(Runnable { planTalk() })
    }

    private fun planTalk() {
        // Si controlla adesso, nel gioco, se si cammina davvero: lo stato letto dall'interfaccia può essere vecchio di
        // qualche fotogramma (per esempio appena parte un dialogo o il messaggio di un oggetto ricevuto). Se il gioco
        // è in un dialogo o in un menu, il tocco è sempre il tasto A: non va mai perso.
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val free = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 &&
            NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) == 0 && FreeRoam.screenIsMap(tileMap)
        if (!free) {
            // tocco durante un dialogo = A; con un menu o una domanda sullo schermo, niente (D-069)
            if (tileMap.none { (it.toInt() and 0xFF) == 0xED }) pendingA = 8
            return
        }
        val grid = MapGrid.build(::readBytes, rom) ?: run { pendingA = 8; return }
        val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
        val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
        for (dy in -2..2) for (dx in -2..2) {
            val t = (px + dx) to (py + dy)
            if (!grid.isInteractive(t.first, t.second)) continue
            val face = grid.interactionSpots(t.first, t.second)[px to py] ?: continue
            walker.start(emptyList(), face)
            return
        }
        pendingA = 8
    }

    private fun planWalk(tx: Int, ty: Int) {
        val grid = MapGrid.build(::readBytes, rom) ?: return
        val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
        val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
        if (grid.isInteractive(tx, ty)) {
            val spots = grid.interactionSpots(tx, ty)
            val path = grid.path(px, py, spots.keys) ?: return
            var end = px to py
            for (d in path) end = end.first + d.dx to end.second + d.dy
            walker.start(path, spots[end])
        } else {
            // Tocco nel vuoto: se accanto (o oltre un bancone) c'è qualcuno o un cartello, ci si gira e si parla;
            // altrimenti è il tasto A (D-044).
            for (dy in -2..2) for (dx in -2..2) {
                val t = (px + dx) to (py + dy)
                if (!grid.isInteractive(t.first, t.second)) continue
                val face = grid.interactionSpots(t.first, t.second)[px to py] ?: continue
                walker.start(emptyList(), face)
                return
            }
            if (TAP_WALKS_TO_GROUND && grid.isFree(tx, ty)) grid.path(px, py, setOf(tx to ty))?.let { walker.start(it, null) }
            else pendingA = 8
        }
    }

    /** Tasto A premuto da un tocco nel vuoto (fotogrammi rimasti: premuto, poi rilasciato). */
    private var pendingA = 0

    private fun currentKeys(): Int {
        keyScript?.let { ks ->
            if (keyScriptPos < ks.size) return ks[keyScriptPos++]
            keyScript = null; commandBusy = false
        }
        if (directFrames > 0) {
            directFrames--
            if (directFrames == 0) commandBusy = false
            return if (directFrames in 1..3) directKey else 0   // un fotogramma libero, tre col tasto, uno libero
        }
        if (itemExecutor.busy) return stepItemAction()
        if (battleExecutor.busy) return stepBattleAction()
        if (closingFrames > 0 && etherList) closingFrames = 0
        if (closingFrames > 0) {
            closingFrames--
            if (closingFrames % 24 == 0) {
                NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
                NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
                val menu = ItemMenus.read(menuVars, tileMap, 0).kind
                val free = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(tileMap)
                // Il gioco torna alla mappa prima di ridisegnare il menu START: si smette dopo 3 controlli di fila in giro.
                closingCalm = if (!ItemMenus.trainerCard(tileMap) && menu == ItemMenus.Kind.OTHER && free) closingCalm + 1 else 0
                if (closingCalm >= 3) closingFrames = 0
            }
            return if (closingFrames % 24 >= 20 && closingCalm == 0) NativeCore.KEY_B else 0
        }
        // Dopo aver usato uno strumento: il giocatore manda avanti solo il messaggio; i menu del gioco li chiude l'app.
        if (pendingClose) {
            NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
            if (!textAtBottom(tileMap)) return 0
        }
        if (pendingA > 0) { pendingA--; return if (pendingA >= 4) NativeCore.KEY_A else 0 }
        if (nameExecutor.busy) {
            val keys = nameExecutor.step(readNaming())
            if (!nameExecutor.busy) {
                nameResult = nameExecutor.result == NameExecutor.Result.DONE
                nameBusy = false
            }
            return keys
        }
        if (walker.busy) {
            if (userKeys != 0) walker.cancel() else {
                val pos = WalkExecutor.Position(
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP),
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD),
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD),
                )
                val gameBusy = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) != 0 ||
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                return walker.step(pos, gameBusy)
            }
        }
        if (!executor.busy) {
            commandBusy = false
            textAssist.autoSpeed = autoTextSpeed
            if (autoTextSpeed == 0 && !textAssist.holding) return userKeys
            NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
            return userKeys or textAssist.step(tileMap)
        }
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val inBattle = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
        val keys = executor.step(MenuReader.read(menuVars, tileMap), inBattle)
        if (!executor.busy) commandBusy = false
        return keys
    }

    fun start() {
        if (thread != null) return
        thread = Thread({ loop() }, "gioco").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** L'audio viene messo in pausa dal thread del gioco stesso, così non resta bloccato a metà scrittura. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Salvataggi dell'app presenti sul telefono (per "Carica partita"). */
    fun savedSlots(): List<SlotEntry> = storage.listSlots()

    /** Pulsante "Salva": salvataggio dell'app in questo punto. Attende fino a 3 secondi. */
    fun saveManual(): Boolean = inGame && runOnGameThread { persist(SaveSlot.MANUAL) }

    /** Uscita dall'app: salvataggio automatico (solo durante una partita) e salvataggio del gioco se cambiato. */
    fun saveNow(): Boolean = runOnGameThread { persist(if (inGame) SaveSlot.AUTO else null) }

    /** Salva, poi ferma definitivamente la sessione. */
    fun close() {
        runOnGameThread { persist(if (inGame) SaveSlot.AUTO else null) }
        running = false
        thread?.join(2000)
    }

    /**
     * "Carica partita": riporta il gioco al salvataggio scelto. Lo stato contiene anche il salvataggio del gioco
     * di quel momento (com'era la cartuccia): quello attuale viene prima copiato a parte, per sicurezza.
     */
    fun loadSlot(slot: SaveSlot): Boolean = runOnGameThread {
        val files = storage.slotFiles(slot)
        val state = storage.readWithBackup(files.state) ?: error("salvataggio mancante")
        val ok = NativeCore.nativeLoadState(handle, state) ||
            (storage.backupOf(files.state)?.let { NativeCore.nativeLoadState(handle, it) } ?: false)
        check(ok) { "salvataggio non leggibile" }
        startMapId = -1; doorPending = false   // partita caricata: non è un'entrata da una porta (D-098)
        lastBattery?.let { storage.writeAtomic(storage.batteryBeforeLoadFile, it) }
        walker.cancel(); executor.cancel(); nameExecutor.cancel(); itemExecutor.cancel(); battleExecutor.cancel(); battleBusy = false
        frameMask.reset(); extMask.reset()   // le immagini di prima appartengono a un'altra partita
        inGame = true
        readSnapshot()
    }

    private fun persist(slot: SaveSlot?) {
        if (slot != null) {
            val files = storage.slotFiles(slot)
            storage.writeAtomic(files.state, NativeCore.nativeSaveState(handle))
            val snap = latestSnapshot.get()
            val info = SlotInfo(
                savedAt = System.currentTimeMillis(),
                playerName = snap.playerName,
                money = snap.money,
                badges = Integer.bitCount(NativeCore.nativeReadMemory(handle, MemoryMapIt.BADGES)),
                party = snap.party.map { it.name to it.level },
                playMinutes = NativeCore.nativeReadMemory(handle, MemoryMapIt.PLAY_TIME_HOURS) * 60 +
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.PLAY_TIME_MINUTES),
            )
            storage.writeAtomic(files.info, info.encode().toByteArray(Charsets.UTF_8))
            runCatching { storage.writeAtomic(files.thumbnail, thumbnailPng()) }
        }
        saveBatteryIfChanged()
    }

    private fun thumbnailPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(latestFrame.get(), NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun saveBatteryIfChanged() {
        val battery = NativeCore.nativeSaveBattery(handle)
        if (battery.isNotEmpty() && !battery.contentEquals(lastBattery)) {
            storage.writeAtomic(storage.batteryFile, battery)
            lastBattery = battery
        }
    }

    /** Solo per il banco di prova: mappa del luogo attuale (passaggi, persone, cartelli), letta nel filo del gioco. */
    fun mapGridForTest(): MapGrid? {
        var g: MapGrid? = null
        runOnGameThread { g = MapGrid.build(::readBytes, rom) }
        return g
    }

    /** Solo per il banco di prova: cammina fino a (x, y) con il camminatore dell'app (passi precisi). */
    fun walkPathForTest(x: Int, y: Int) {
        tasks.add(Runnable {
            val grid = MapGrid.build(::readBytes, rom) ?: return@Runnable
            val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
            val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
            grid.path(px, py, setOf(x to y))?.let { walker.start(it, null) }
        })
    }

    /** Solo per il banco di prova: stato della chiusura automatica dei menu. */
    fun closingForTest() = "pendingClose=$pendingClose frames=$pendingCloseFrames closing=$closingFrames calm=$closingCalm item=${itemExecutor.busy}"

    /** Solo per il banco di prova: porte del luogo attuale (riga, colonna, mappa di arrivo): numero a D3B2, poi 4 byte. */
    fun warpsForTest(): List<Triple<Int, Int, Int>> {
        val b = ByteArray(1 + 4 * 32)
        runOnGameThread { NativeCore.nativeReadRange(handle, 0xD3B2, b) }
        val n = (b[0].toInt() and 0xFF).coerceAtMost(32)
        return (0 until n).map { i -> Triple(b[1 + 4 * i].toInt() and 0xFF, b[2 + 4 * i].toInt() and 0xFF, b[4 + 4 * i].toInt() and 0xFF) }
    }

    /** Solo per il banco di prova: salva lo stato del gioco in un file (partite di prova). */
    /**
     * Segnalazione (D-064): stato della partita in questo momento e una riga di informazioni (luogo, posizione,
     * schermate riconosciute) per riprodurre un difetto nel banco di prova. Null se il gioco non è avviato.
     */
    fun reportData(): Pair<ByteArray, String>? {
        var state: ByteArray? = null
        var info = ""
        runOnGameThread {
            state = NativeCore.nativeSaveState(handle)
            info = "luogo=${NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP)} x=${NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)} " +
                "y=${NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)} lotta=${NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE)}"
        }
        val st = state ?: return null
        val flags = "lottaDivisa=$battleSplit menuLotta=$battleMenuShown menuGiocoLotta=$battleGameMenu siNo=${yesNo != null} " +
            "dialogoSu=${dialogStrip != null} vistaEstesa=$extendedActive negozio=$shopMenuShown pc=$pcMenuShown box=$boxMenuShown " +
            "esecutore=${itemExecutor.busy}/${battleExecutor.busy}"
        return st to "$info\n$flags"
    }

    fun saveStateForTest(file: java.io.File): Boolean {
        var ok = false
        runOnGameThread { NativeCore.nativeSaveState(handle)?.let { file.writeBytes(it); ok = true } }
        return ok
    }

    /** Solo per il banco di prova: scrive un byte nella memoria del gioco. */
    fun writeMemoryForTest(address: Int, value: Int) { runOnGameThread { NativeCore.nativeWriteMemory(handle, address, value) } }

    /** Solo per il banco di prova: legge [n] byte di memoria da [address]. */
    fun memoryForTest(address: Int, n: Int): ByteArray {
        val b = ByteArray(n)
        runOnGameThread { NativeCore.nativeReadRange(handle, address, b) }
        return b
    }

    /** Solo per il banco di prova: variabili dei menu e mappa dello schermo. */
    /** Solo per il banco di prova, dal thread del gioco (onFrame): caselle dello schermo dell'ultimo fotogramma. */
    fun frameTilesForTest(): ByteArray = frameTiles.copyOf()

    /** Solo per il banco di prova: con [stepPermits] il gioco avanza di un fotogramma per ogni permesso. */
    @Volatile var stepPermits: java.util.concurrent.Semaphore? = null
    @Volatile var stepDone: java.util.concurrent.Semaphore? = null
    init {
        // banco di prova: fotogramma per fotogramma fin dall'accensione (analisi dell'inizio)
        if (testStepFromStart) { stepPermits = java.util.concurrent.Semaphore(0); stepDone = java.util.concurrent.Semaphore(0) }
    }

    fun screenForTest(): Pair<ByteArray, ByteArray> {
        val v = ByteArray(MenuReader.MENU_VARS_SIZE); val t = ByteArray(MenuReader.SCREEN_SIZE)
        runOnGameThread { NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, v); NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, t) }
        return v to t
    }

    /** Solo per il banco di prova: luogo e posizione del giocatore (mappa, x, y). */
    fun positionForTest(): Triple<Int, Int, Int> {
        var p = Triple(0, 0, 0)
        runOnGameThread {
            p = Triple(NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP), NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD),
                NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD))
        }
        return p
    }

    private fun runOnGameThread(action: () -> Unit): Boolean {
        if (thread == null || !running) {
            return runCatching { action() }.isSuccess
        }
        val done = CountDownLatch(1)
        var ok = false
        tasks.add(Runnable {
            ok = runCatching { action() }.onFailure { Log.e(TAG, "Errore", it) }.isSuccess
            done.countDown()
        })
        return done.await(3, TimeUnit.SECONDS) && ok
    }

    private fun loop() {
        val track = createAudioTrack()
        audioTrack = track
        runCatching { track?.setVolume(volume / 5f) }
        val audio = ShortArray(8192)
        var back = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
        var frames = 0L
        var nextFrameAt = SystemClock.elapsedRealtimeNanos()
        try {
            while (running) {
                while (true) tasks.poll()?.run() ?: break
                if (paused) {
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    Thread.sleep(16)
                    nextFrameAt = SystemClock.elapsedRealtimeNanos()
                    continue
                }
                if (track != null && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                // Banco di prova: un fotogramma alla volta, quando il test lo chiede (controllo dei lampeggi).
                stepPermits?.let { p -> if (!p.tryAcquire(200, java.util.concurrent.TimeUnit.MILLISECONDS)) continue }
                NativeCore.nativeSetKeys(handle, currentKeys())
                val count = NativeCore.nativeRunFrame(handle, back, audio)
                // Fine lotta sulla mappa (D-089): il gioco fa tutto bianco prima di tornare alla mappa, ma la vista estesa
                // disegnava ancora la mappa intorno (rettangolo bianco). Si tiene l'immagine di prima col dialogo finché
                // il bianco dura (solo appena finita una lotta).
                val inBattleNowMem = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                if (inBattleNowMem) afterBattle = 120 else if (afterBattle > 0) afterBattle--
                val c0 = back[0]
                val rawWhite = ((c0 shr 16) and 255) + ((c0 shr 8) and 255) + (c0 and 255) > 720 && back.all { it == c0 }
                // Cambio di luogo (D-110): dopo la dissolvenza al nero il gioco mostra per 1-2 fotogrammi lo schermo bianco
                // (con una persona sopra) prima del nero del luogo nuovo; intorno restava nero: banda bianca in mezzo. Si
                // tiene il nero di prima (fino a 8 fotogrammi: uscendo dal laboratorio il bianco ne dura 5, D-114).
                val doorWhite = inGame && !inBattleNowMem && afterBattle == 0 && doorWhiteRun < 8 && nearlyWhite(back) && darkFrame(latestFrame.get())
                if (doorWhite) doorWhiteRun++ else doorWhiteRun = 0
                val holdBattleEnd = (rawWhite && afterBattle > 0 && latestExtended.get().active) || doorWhite
                if (doorWhite) System.arraycopy(latestFrame.get(), 0, back, 0, back.size)
                // Scritte (C) all'accensione (D-102): nel gioco hanno 16 pixel a sinistra e 8 a destra; si spostano di 4 pixel
                // a sinistra così i margini sono uguali (12 e 12) (richiesta del committente).
                if (!inGame && !titleWasShown) {
                    val bg = back[0]
                    var minX = 999; var maxX = -1; var minY = 999; var maxY = -1
                    for (y in 0 until NativeCore.HEIGHT) { val b = y * NativeCore.WIDTH
                        for (x in 0 until NativeCore.WIDTH) if (back[b + x] != bg) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; maxY = y } }
                    if (minX == 16 && maxX == 151 && minY in 48..64 && maxY in 88..104) {
                        for (y in minY..maxY) { val b = y * NativeCore.WIDTH
                            System.arraycopy(back, b + 4, back, b, NativeCore.WIDTH - 4); for (x in NativeCore.WIDTH - 4 until NativeCore.WIDTH) back[b + x] = bg }
                    }
                }
                // Mosse in lotta (D-096): il gioco fa tremare lo schermo (SCX/SCY). Si vibra a tempo e il testo in alto
                // (righe 96–143 del gioco) resta fermo: prima scendeva e sopra comparivano pezzi della scena.
                if (inBattleNowMem) {
                    if (battleMenuShown) battleMenuSeen = true
                } else battleMenuSeen = false
                val scx = NativeCore.nativeReadMemory(handle, 0xFF43); val scy = NativeCore.nativeReadMemory(handle, 0xFF42)
                // tremolio = lo scorrimento cambia da un fotogramma all'altro (in lotta SCX può restare fermo a 2)
                // (alcune mosse spostano anche la "finestra" del gioco, WY tra 1 e 143: copia della scena spostata in giù)
                val wy = NativeCore.nativeReadMemory(handle, 0xFF4A)
                if (scx != lastScx || scy != lastScy) shakeFrames = 4 else if (shakeFrames > 0) shakeFrames--
                lastScx = scx; lastScy = scy
                // l'immagine arriva un fotogramma dopo i registri: il testo resta fermo ancora 3 fotogrammi
                if (shakeFrames > 0 || wy in 1..143) shakeHold = 3 else if (shakeHold > 0) shakeHold--
                val shaking = inBattleNowMem && battleMenuSeen && shakeHold > 0
                if (!(inBattleNowMem && battleMenuSeen && shakeFrames > 0)) shakeVibe = 0
                if (shaking) {
                    if (shakeFrames > 0 && shakeVibe-- <= 0) { onHit?.invoke(); shakeVibe = 6 }
                    if (battleSplit && keptText.isNotEmpty()) System.arraycopy(keptText, 0, back, 96 * NativeCore.WIDTH, keptText.size)
                } else {
                    if (inBattleNowMem) { if (keptText.isEmpty()) keptText = IntArray(48 * NativeCore.WIDTH); System.arraycopy(back, 96 * NativeCore.WIDTH, keptText, 0, keptText.size) }
                }
                // Ogni fotogramma: schermata di lotta divisa e menu iniziale ridisegnato (in sincronia con l'immagine).
                NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, frameTiles)
                NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, frameVars)
                val inBattleRaw = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                var split = inBattleRaw && BattleLayout.textBoxAtBottom(frameTiles)
                val frameMenu = MenuReader.read(frameVars, frameTiles)
                // Menu iniziale ridisegnato: resta finché il riquadro del gioco è ancora sullo schermo (dopo la scelta il
                // menu del gioco perde il cursore per qualche fotogramma ma si vede ancora, D-065).
                val mainRaw = if (frameMenu.kind == MenuKind.MAIN_MENU) frameMenu.items else 0
                // L'immagine segue la memoria dello schermo con qualche fotogramma di ritardo: si aspettano altri 4 fotogrammi.
                if (mainRaw > 0) { mainMenuItems = mainRaw; mainHold = 30; mainTail = 4 }
                else if (mainMenuItems > 0 && mainHold > 0 && (frameTiles[0].toInt() and 0xFF) == 0x79) mainHold--
                else if (mainMenuItems > 0 && mainTail > 0) mainTail--
                else { mainMenuItems = 0; mainHold = 0 }
                val menuRaw = frameMenu.kind == MenuKind.BATTLE_MAIN || frameMenu.kind == MenuKind.BATTLE_MOVES
                // In lotta, un altro menu del gioco con il cursore (squadra, borsa, cambio): si mostra il gioco intero e
                // i riquadri di lotta dell'app spariscono (D-049).
                var gameMenu = inBattleRaw && !menuRaw && frameTiles.any { (it.toInt() and 0xFF) == 0xED }
                // Il gioco chiede un Pokémon (dopo un KO): l'app mostra la sua lista SQUADRA sopra la lotta (D-054).
                battlePartyAsked = gameMenu && !battleExecutor.busy &&
                    BattleMenus.read(frameVars, frameTiles, 0).kind == BattleMenus.Kind.PARTY
                // Mentre l'app guida i menu di lotta del gioco si continua a mostrare la lotta (immagine di prima).
                if (battleExecutor.busy || battlePartyAsked) { gameMenu = false; split = true }
                if (gameMenu) split = false
                // Niente lampeggi (D-061): quando il gioco ridisegna il riquadro del testo o il menu di lotta, per qualche
                // fotogramma non si riconoscono; la disposizione della lotta e i riquadri dell'app restano finché il
                // cambio non dura davvero (12 fotogrammi, 0,2 s). Un menu del gioco aperto vale subito.
                // A fine lotta la memoria dice già "fuori lotta" mentre l'immagine mostra ancora la lotta per qualche
                // fotogramma: anche lì la disposizione resta (prima per un fotogramma il dialogo compariva due volte).
                val battleTail = !inBattleRaw && afterBattle > 0 && !latestExtended.get().active
                if (split) splitHold = HOLD_FRAMES else if ((inBattleRaw || battleTail) && !gameMenu && splitHold > 0) { splitHold--; split = true } else splitHold = 0
                var menuShown = menuRaw
                if (menuRaw) menuHold = HOLD_FRAMES else if (inBattleRaw && !gameMenu && menuHold > 0) { menuHold--; menuShown = true } else menuHold = 0
                battleSplit = split
                battleMenuShown = menuShown
                battleGameMenu = gameMenu
                // Scheda allenatore: chiudendola il gioco ricarica per qualche fotogramma la grafica della mappa (immagine
                // rovinata). Si tiene ferma l'ultima immagine della scheda finché la mappa non è stabile (D-065).
                // Chiusura: si mostra subito la mappa salvata all'apertura della scheda, finché la mappa vera non è stabile.
                val cardRaw = ItemMenus.trainerCard(frameTiles)
                val mapNow = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(frameTiles)
                freeRoamNow = mapNow && !inBattleRaw
                run {
                    val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD); val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
                    NativeCore.nativeReadRange(handle, 0xD3B2, warpBlock)
                    val nw = (warpBlock[0].toInt() and 0xFF).coerceAtMost(32)
                    onWarp = (0 until nw).any { (warpBlock[1 + 4 * it].toInt() and 0xFF) == py && (warpBlock[2 + 4 * it].toInt() and 0xFF) == px }
                    debugFrameInfo = "al=${(0..5).joinToString(",") { NativeCore.nativeEvDebug(handle, it).toString() }} start=$startOk sc=%d,%d wy=%d joy=%02x pos=%d,%d,%d warp=%s".format(NativeCore.nativeReadMemory(handle, 0xFF43), NativeCore.nativeReadMemory(handle, 0xFF42), NativeCore.nativeReadMemory(handle, 0xFF4A),NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE),
                        NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP), px, py, onWarp)
                }
                // Vibrazione ai colpi (D-069): i PS di uno dei due Pokémon in lotta scendono.
                if (inBattleRaw) {
                    val eh = (NativeCore.nativeReadMemory(handle, MemoryMapIt.ENEMY_MON_HP) shl 8) or NativeCore.nativeReadMemory(handle, MemoryMapIt.ENEMY_MON_HP + 1)
                    val ph = (NativeCore.nativeReadMemory(handle, MemoryMapIt.BATTLE_MON + 1) shl 8) or NativeCore.nativeReadMemory(handle, MemoryMapIt.BATTLE_MON + 2)
                    if ((lastEnemyHp in 1..999 && eh < lastEnemyHp) || (lastPlayerHp in 1..999 && ph < lastPlayerHp)) onHit?.invoke()
                    lastEnemyHp = eh; lastPlayerHp = ph
                } else { lastEnemyHp = -1; lastPlayerHp = -1 }
                gameOptions = NativeCore.nativeReadMemory(handle, MemoryMapIt.OPTIONS)
                val forgetList = (frameVars[0].toInt() and 0xFF) == 8 && (frameVars[1].toInt() and 0xFF) == 5 && (frameVars[4].toInt() and 0xFF) == 3 &&
                    (frameTiles[7 * 20 + 4].toInt() and 0xFF) == 0x79 && (frameTiles[8 * 20 + 4].toInt() and 0xFF) == 0x7C
                if (forgetList) {
                    val mv = ByteArray(5); NativeCore.nativeReadRange(handle, 0xD0E0, mv)
                    forgetNew = mv[4].toInt() and 0xFF
                    forgetMoves = IntArray(4) { mv[it].toInt() and 0xFF }
                } else forgetMoves = null
                fun ft(x: Int, y: Int) = frameTiles[y * 20 + x].toInt() and 0xFF
                val ether = !inBattleRaw && (frameVars[0].toInt() and 0xFF) == 7 && (frameVars[1].toInt() and 0xFF) == 5 &&
                    ft(4, 7) == 0x79 && ft(4, 8) == 0x7C && ft(19, 7) == 0x7B && ft(0, 12) == 0x79 && ft(0, 17) == 0x7D
                // Il gioco scrive il testo lettera per lettera: un A durante la scrittura la fa solo finire. La lista vale
                // quando il testo è fermo da qualche fotogramma.
                val textHash = if (ether) frameTiles.copyOfRange(13 * 20, 17 * 20).contentHashCode() else 0
                etherCalm = if (ether && textHash == etherHash) etherCalm + 1 else 0
                etherHash = textHash
                // Dopo la scelta il gioco scrive la risposta ("…PP ricaricati!") nello stesso riquadro: la lista finisce
                // appena il testo non è più la domanda, così il messaggio si vede come un dialogo normale.
                if (!ether) etherDone = false
                val etherReady = ether && !etherDone && (if (etherList) textHash == etherQuestion else etherCalm >= 6)
                if (etherReady && !etherList) {
                    etherQuestion = textHash
                    etherText = listOf(14, 16).map { r ->
                        it.giallotouch.bridge.TextCodec.decode(ByteArray(18) { frameTiles[r * 20 + 1 + it] }).trimEnd() }
                }
                // Finita la scelta (o INDIETRO): dopo il messaggio si richiudono borsa e menu del gioco, come per le Pozioni.
                if (etherList && !etherReady) { pendingClose = true; pendingCloseFrames = 60 * 60; pendingFree = 0; etherDone = true }
                etherList = etherReady
                // Introduzione (D-076): sprite anche fuori dalle 144 righe e sotto le bande, per non vederli tagliati.
                if (!inGame && !titleShown && mainMenuItems == 0) {
                    val buf = IntArray(NativeCore.WIDTH * SPRITE_LAYER_H)
                    val r = NativeCore.nativeSprites(handle, buf, SPRITE_LAYER_H, SPRITE_LAYER_TOP)
                    introSpritesOver = (r and 0x10000) != 0
                    // lettura vuota per un attimo (sprite spenti durante l'aggiornamento): si tiene la precedente per
                    // al massimo 2 fotogrammi, così Pikachu non perde i piedi per un fotogramma
                    if ((r and 0xFFFF) > 0) { introSprites.set(buf); introSpritesMiss = 0 }
                    else if (++introSpritesMiss > 2) introSprites.set(null)
                } else introSprites.set(null)
                // Introduzione (D-073): a ogni lampo del Tuonoshock il Pikachu grande perde il giallo (bianco o nero):
                // una vibrazione per lampo, a ritmo.
                if (!inGame) {
                    var yellow = 0
                    for (c in back) {
                        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                        if (r > 230 && g in 170..235 && b < 60) yellow++
                    }
                    if (lastYellow >= 1500 && yellow < 30) { onHit?.invoke(); shockSeen = true }
                    lastYellow = yellow
                    // Dopo il Tuonoshock lo schermo cambia colore (lampi neri, poi rosso e giallo): una vibrazione per lampo.
                    if (shockSeen) {
                        var dark = 0; var n = 0
                        for (i in back.indices step 7) { val c = back[i]; n++; if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) < 150) dark++ }
                        val bg = back[0]; val r = (bg shr 16) and 255; val b = bg and 255
                        val state = when { dark * 2 > n -> 1; r > 180 && b < 100 && !(r >= 240 && ((bg shr 8) and 255) >= 240) -> 2 + ((bg shr 8) and 255) / 128; else -> 0 }
                        if (state != flashState && state != 0) onHit?.invoke()
                        flashState = state
                    }
                    if (titleShown) shockSeen = false
                }
                // Titolo: la scritta (C) GAME FREAK alla riga 17 (E0 E1 E2); il logo usa anche la casella del cursore ▶.
                titleShown = (frameTiles[17 * 20 + 2].toInt() and 0xFF) == 0xE0 && (frameTiles[17 * 20 + 3].toInt() and 0xFF) == 0xE1 &&
                    (frameTiles[17 * 20 + 4].toInt() and 0xFF) == 0xE2
                // Testo in attesa (▼ in basso a destra): il tocco manda avanti anche se dietro c'è un menu (D-071).
                val textWaiting = (frameTiles[16 * 20 + 18].toInt() and 0xFF) == 0xEE
                choiceOnScreen = !titleShown && !textWaiting && (frameTiles.any { (it.toInt() and 0xFF) == 0xED } || frameMenu.kind == MenuKind.YES_NO)
                if (cardClosing > 0) {
                    cardCalm = if (mapNow && !cardRaw && closingFrames == 0) cardCalm + 1 else 0
                    cardClosing = if (cardCalm >= 8) 0 else cardClosing - 1
                }
                // Apertura: la mappa salvata resta finché la scheda non è completa (4 fotogrammi di fila) o l'esecutore
                // non si è fermato senza riuscirci.
                if (cardOpening > 0) {
                    cardOpenCalm = if (cardRaw && !mostlyWhite(back)) cardOpenCalm + 1 else 0
                    cardOpening = if (cardOpenCalm >= 4 || (!itemExecutor.busy && itemResult == false)) 0 else cardOpening - 1
                }
                cardOpeningActive = cardOpening > 0
                menusClosing = closingFrames > 0 || cardClosing > 0 || pendingClose
                if (itemHold) {
                    itemHoldCalm = if (!itemExecutor.busy && !pendingClose && closingFrames == 0 && mapNow) itemHoldCalm + 1 else 0
                    if (itemHoldCalm >= 8 || trainerCardShown) itemHold = false
                }
                holdImage = cardClosing > 0 || cardOpening > 0 || itemHold
                // Scheda già vista: compare subito quella memorizzata mentre il gioco carica la vera (D-068).
                instantCard = cardOpening > 0 && cardCache != null
                trainerCardShown = (cardRaw && !holdImage) || instantCard
                if (cardRaw && !holdImage) {
                    if (++cardLiveFrames == 10) {
                        val copy = back.copyOf()
                        cardCache = copy
                        Thread { storage.saveCardImage(copy) }.start()
                    }
                } else cardLiveFrames = 0
                val itemKind = ItemMenus.read(frameVars, frameTiles, 0).kind
                pcMenuShown = itemKind == ItemMenus.Kind.PC_MENU && closingFrames == 0
                shopMenuShown = itemKind == ItemMenus.Kind.SHOP_MENU && closingFrames == 0
                boxMenuShown = itemKind == ItemMenus.Kind.BOX_MENU && closingFrames == 0
                pcChoice = if (itemKind == ItemMenus.Kind.PC_CHOICE && closingFrames == 0)
                    (0 until 4).map { i -> it.giallotouch.bridge.TextCodec.decode(ByteArray(13) { frameTiles[(2 + 2 * i) * 20 + 2 + it] }).trim() }
                        .filter { it.isNotEmpty() } else emptyList()
                if (boxMenuShown || itemExecutor.busy && (itemKind == ItemMenus.Kind.ITEM_LIST || itemKind == ItemMenus.Kind.BOX_SUB)) {
                    NativeCore.nativeReadRange(handle, MemoryMapIt.BOX_COUNT, boxBlock)
                    boxMons = it.giallotouch.bridge.BoxReader.read(boxBlock)
                    // "BOX N° n" nel riquadro in basso a destra (riga 16, colonne 17–18).
                    val d1 = frameTiles[16 * 20 + 17].toInt() and 0xFF; val d2 = frameTiles[16 * 20 + 18].toInt() and 0xFF
                    if (boxMenuShown && d2 in 0xF6..0xFF) boxNumber = (if (d1 in 0xF6..0xFF) (d1 - 0xF6) * 10 else 0) + d2 - 0xF6
                }
                if (shopMenuShown) {
                    val b = ByteArray(12); NativeCore.nativeReadRange(handle, MemoryMapIt.SHOP_LIST, b)
                    val n = (b[0].toInt() and 0xFF).coerceAtMost(10)
                    shopItems = (1..n).map { b[it].toInt() and 0xFF }.takeWhile { it != 0xFF }
                }
                yesNo = if (frameMenu.kind == MenuKind.YES_NO) yesNoInfo(frameVars, frameTiles, frameMenu.item) else null
                nameList = if (frameMenu.kind == MenuKind.NAME_LIST) MenuReader.nameListEntries(frameTiles, frameMenu.items) else null
                if (nameList != null) nameTail = 10 else if (nameTail > 0) nameTail--
                namingShown = NamingReader.looksLikeNaming(frameTiles)
                // Dopo la schermata dei nomi l'immagine "di prima" sarebbe la tastiera del gioco: si butta (D-067).
                if (namingShown) frameMask.reset()
                val ynNow = yesNo?.takeIf { !trainerCardShown }   // anche in lotta (riquadro del gioco sulla scena)
                // Subito dopo la risposta (D-087) il riquadro SÌ/NO del gioco resta qualche fotogramma col cursore: si
                // continua a coprirlo, il dialogo resta al suo posto e SELECT non compare.
                if (ynNow != null) { lastYn = ynNow; answerTail = 0 }
                else if (lastYn != null && answerTail < 40) answerTail++
                else lastYn = null
                val ynBoxStill = ynNow == null && lastYn?.let { (frameTiles[it.y0 * 20 + it.x0].toInt() and 0xFF) == 0x79 } == true
                answerPending = ynBoxStill
                // L'immagine arriva un paio di fotogrammi dopo la memoria dello schermo: il riquadro SÌ/NO (CURA/ESCI) si
                // vedeva ancora per un fotogramma dopo essere sparito dalle caselle. Si copre ancora per 2 fotogrammi.
                if (ynNow != null || ynBoxStill) ynGone = 0 else if (ynGone < 99) ynGone++
                val yn = ynNow ?: lastYn?.takeIf { ynBoxStill || ynGone <= 2 }
                // Mentre l'app guida i menu del gioco le sue domande ("Su quale POKéMON…?") non si mostrano (D-071).
                var dialog = !itemExecutor.busy && !battleSplit && !trainerCardShown && !namingShown && mainMenuItems == 0 &&
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) == 0 && dialogAtBottom(frameTiles)
                // Senza un'immagine di prima del dialogo (partita caricata a dialogo aperto) il dialogo resta al suo posto.
                val onScreen = (inBattleRaw && (frameMenu.kind == MenuKind.BATTLE_MOVES || executor.navigatingFight)) || dialog || yn != null || nameList != null || nameTail > 0 || forgetMoves != null || etherList || shopMenuShown || pcChoice.isNotEmpty() || hidingOldMenus() ||
                    ((NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0 || pendingClose) && (frameTiles[1 * 20 + 4].toInt() and 0xFF) == 0x71)
                // Senza immagine di prima: nelle scene senza mappa (introduzione, sfondo uniforme) il posto del dialogo si
                // riempie col colore dello sfondo; sulla mappa il dialogo resta al suo posto.
                val plainBackground = !frameMask.ready && !extendedActive && back[0] == back[NativeCore.WIDTH - 1] &&
                    back[0] == back[11 * 8 * NativeCore.WIDTH] && back[0] == back[12 * 8 * NativeCore.WIDTH - 1]
                // Appena entrati in un luogo (D-094) l'immagine di prima è di un altro luogo (pezzo di città sotto la stanza):
                // si butta, e sotto il dialogo si disegna la mappa vera del luogo (vista estesa), senza immagine di prima.
                val mapIdNow = NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP)
                if (mapIdNow != maskMapId) { frameMask.reset(); maskMapId = mapIdNow }
                var mapUnderDialog = dialog && !frameMask.ready && extendedActive && extendedIsMap && !inBattleRaw
                if (dialog && !frameMask.ready && !plainBackground && !mapUnderDialog) dialog = false
                // Il riquadro deve essere già disegnato nell'immagine (bordo superiore scuro alla riga 96-103): nei primi
                // fotogrammi la mappa delle caselle lo ha già ma l'immagine no, e si spostava un pezzo di mappa (D-086).
                if (dialog && !boxBorderDrawn(back)) dialog = false
                // Fine dialogo: le caselle non hanno più il riquadro ma l'immagine sì (arriva dopo) per un fotogramma,
                // e il dialogo compariva in basso (D-092). Finché il riquadro è ancora disegnato si continua a spostarlo.
                if (!dialog && dialogWas && dialogEnd < 3 && !itemExecutor.busy && !battleSplit && !inBattleRaw && frameMask.ready) {
                    if (boxBorderDrawn(back)) { dialog = true; dialogEnd++ }
                }
                // Inizio dialogo (D-108): a volte l'immagine ha già il riquadro e le caselle non ancora; prima per un
                // fotogramma il riquadro si vedeva al suo posto in basso (con START). Riquadro largo tutto lo schermo,
                // bordo e interno puliti: è un dialogo.
                // Mai durante la dissolvenza d'una porta (D-113): al cancello della Strada 2 la terra sbiadita sembrava un riquadro.
                if (!dialog && !dialogWas && !doorPending && !itemExecutor.busy && !battleSplit && !inBattleRaw && (frameMask.ready || (extendedActive && extendedIsMap)) && mainMenuItems == 0 &&
                    !namingShown && !trainerCardShown && yn == null && nameList == null && boxBorderDrawn(back) && boxInnerClean(back) && boxEdgesDark(back)) {
                    dialog = true
                    if (!frameMask.ready) mapUnderDialog = true
                }
                if (dialog && dialogEnd == 0) dialogWas = true else if (!dialog) { dialogWas = false; dialogEnd = 0 }
                if (holdBattleEnd) {
                    // dialogo di prima (vedi D-089)
                } else if (dialog) {
                    System.arraycopy(back, 96 * NativeCore.WIDTH, stripA, 0, stripA.size)
                    dialogStrip = stripA
                    val t = stripA; stripA = stripB; stripB = t
                } else dialogStrip = null
                // Pulsante START (D-098): deciso sull'immagine di questo fotogramma, non sulla memoria del gioco (che arriva
                // prima o dopo): sparisce appena si entra in una porta e torna appena il luogo nuovo è sullo schermo; torna
                // subito a fine dialogo o lotta (anche se il gioco blocca ancora i tasti); non sparisce per un attimo
                // passando da un luogo all'altro.
                run {
                    val c0 = back[0]
                    var uniform = true; var q = 0
                    while (q < back.size) { if (back[q] != c0) { uniform = false; break }; q += 13 }
                    if (mapIdNow != startMapId) {
                        // il primo luogo (avvio, partita caricata) non è un'entrata da una porta
                        if (startMapId >= 0 && (onWarp || startOnWarp)) { doorPending = true; doorUniform = false; doorFrames = 0 }
                        startMapId = mapIdNow
                    }
                    startOnWarp = onWarp
                    if (doorPending) {
                        if (uniform) doorUniform = true else if (doorUniform) { doorPending = false; doorRecent = 60 }
                        if (++doorFrames > 400) doorPending = false
                    } else if (doorRecent > 0) doorRecent--
                    // Scene guidate (il Professore che accompagna, D-106): il gioco blocca i comandi e START non serve.
                    // Non vale subito dopo una porta o una lotta, dove il blocco dura pochi fotogrammi e START torna subito.
                    val joyBlocked = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) != 0 && afterBattle == 0 && doorRecent == 0
                    var boxDrawn = false
                    if (dialogStrip == null) boxDrawn = boxBorderDrawn(back) && boxInnerClean(back) && boxEdgesDark(back)   // D-115: pavimento del laboratorio
                    val cursor = frameTiles.any { (it.toInt() and 0xFF) == 0xED }
                    val mapish = latestExtended.get().active && extendedIsMap
                    if (mapish) startMapCalm = 0 else startMapCalm++
                    // Inizio partita (D-105, scelta del committente): START compare la prima volta che si esce di casa
                    // (casa del giocatore = luoghi 37 e 38, ancora senza Pokémon).
                    val noParty = NativeCore.nativeReadMemory(handle, MemoryMapIt.PARTY_COUNT) == 0
                    if (noParty && mapIdNow != 37 && mapIdNow != 38 && mapish) leftHouse = true
                    val startHome = noParty && !leftHouse && (mapIdNow == 37 || mapIdNow == 38)
                    // Per comparire servono 3 fotogrammi di fila (D-111, D-112): tra due passi d'una scena guidata il gioco sblocca i
                    // comandi per un fotogramma solo e START lampeggiava (vecchio di Smeraldopoli).
                    val cand = !startHome && !joyBlocked && !dialogAtBottom(frameTiles) && !inBattleRaw && !battleSplit && dialogStrip == null && !boxDrawn && !cursor && !uniform && !doorPending &&
                        !itemExecutor.busy && yesNo == null && nameList == null && !holdBattleEnd && (mapish || (startOk && startMapCalm < 30))
                    startCandRun = if (cand) startCandRun + 1 else 0
                    startOk = cand && (startOk || startCandRun >= 3)
                }
                if (trainerOpening > 0) trainerOpening = if (trainerCardShown) 0 else trainerOpening - 1
                checkPendingClose(NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(frameTiles))
                val hide = hidingOldMenus()
                val boxes = buildList {
                    // In lotta si copre solo la scena (righe 0–11): il testo del gioco ("…si è ripreso di 8!") resta.
                    // Anche la schermata della squadra che il gioco mostra dopo una Pozione resta nascosta.
                    val inBattleNow = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                    val partyScreen = (frameTiles[1 * 20 + 4].toInt() and 0xFF) == 0x71 && (inBattleNow || pendingClose)
                    if ((hide || partyScreen) && frameMask.ready) add(if (inBattleNow) BoxMask.Box(0, 0, 19, 11) else BoxMask.Box(0, 0, 19, 17))
                    if (yn != null && frameMask.ready) add(BoxMask.Box(yn.x0, yn.y0, yn.x1, yn.y1))
                    // in lotta senza immagine di prima (domanda "dimenticare una mossa?"): riquadro SÌ/NO del gioco riempito
                    else if (yn != null && inBattleRaw) add(BoxMask.Box(yn.x0, yn.y0, yn.x1, yn.y1, fill = back[0]))
                    // Nomi proposti: lo sfondo della scena è uniforme; con l'immagine di prima comparivano pezzi del
                    // personaggio che entra scorrendo (D-067). Si riempie col colore dello sfondo, anche la colonna accanto.
                    // dopo la scelta il gioco ridisegna la sua lista (▶NUOVO…) per qualche fotogramma: resta coperta (D-103)
                    if (nameList != null || nameTail > 0) add(BoxMask.Box(0, 0, 11, 11, fill = back[NativeCore.WIDTH - 1]))
                    // Mosse del gioco: il riquadro TIPO / PP compare sulla scena; l'app mostra già le mosse (D-069).
                    // Anche quando resta sotto un messaggio ("Non hai più PP per questa mossa!"): si riconosce dalla scritta TIPO/.
                    val tipoBox = (frameTiles[9 * 20 + 1].toInt() and 0xFF) == 0x93 && (frameTiles[9 * 20 + 2].toInt() and 0xFF) == 0x88 &&
                        (frameTiles[9 * 20 + 3].toInt() and 0xFF) == 0x8F
                    // L'immagine arriva un fotogramma dopo le caselle: si copre ancora per 3 fotogrammi (D-095).
                    val tipoNow = inBattleRaw && (frameMenu.kind == MenuKind.BATTLE_MOVES || executor.navigatingFight || tipoBox)
                    if (tipoNow) tipoTail = 3 else if (tipoTail > 0) tipoTail--
                    if ((tipoNow || (inBattleRaw && tipoTail > 0)) && frameMask.ready) add(BoxMask.Box(0, 8, 10, 11))
                    if (pcChoice.isNotEmpty() && frameMask.ready) add(BoxMask.Box(0, 0, 15, 9))
                    // Lista del gioco delle mosse da dimenticare: la sostituisce il riquadro dell'app (D-085).
                    if (forgetMoves != null) add(BoxMask.Box(4, 7, 19, 11, fill = back[0]))
                    // Etere (D-093): tutta l'immagine del gioco (squadra e lista) coperta con l'immagine di prima (mappa).
                    if (etherList) add(if (frameMask.ready) BoxMask.Box(0, 0, 19, 17) else BoxMask.Box(0, 0, 19, 17, fill = back[0]))
                    if (shopMenuShown && frameMask.ready) { add(BoxMask.Box(0, 0, 10, 6)); add(BoxMask.Box(11, 0, 19, 2)) }
                    if (dialog && !(mapUnderDialog && dialog)) add(if (frameMask.ready) BoxMask.Box(0, 12, 19, 17) else BoxMask.Box(0, 12, 19, 17, fill = back[0]))
                }
                frameMask.apply(back, NativeCore.HEIGHT, 0, boxes, boxesOnScreen = onScreen || holdImage)
                if (holdImage) {
                    System.arraycopy(if (instantCard) cardCache!! else savedMap, 0, back, 0, back.size)
                    back = latestFrame.getAndSet(back)
                } else back = latestFrame.getAndSet(back)
                val eh = extendedHeight
                if (instantCard) {
                    extBack.active = false; extendedActive = false
                    extBack = latestExtended.getAndSet(extBack)
                } else if (holdImage) {
                    if (savedExt.height > 0) {
                        System.arraycopy(savedExt.pixels, 0, extBack.pixels, 0, savedExt.pixels.size)
                        extBack.height = savedExt.height; extBack.active = savedExt.active; extendedActive = savedExt.active
                        extBack = latestExtended.getAndSet(extBack)
                    }
                } else if (holdBattleEnd) {
                    // immagine di prima (D-089): niente da pubblicare
                } else if (eh > NativeCore.HEIGHT) {
                    NativeCore.nativeSetMapFromRow(handle, if (forceMapFromRow >= 0) forceMapFromRow else if (mapUnderDialog && dialog) 96 else 1000)
                    val rendered = NativeCore.nativeRenderExtended(handle, extBack.pixels, eh, extendedFrameTop)
                    // Lotta già in corso con la disposizione della lotta (D-107): niente mappa estesa intorno (nella cattura
                    // di Pikachu lo sfondo del gioco resta la mappa e la scena compariva dentro la mappa).
                    val active = rendered != 0 && !(inBattleRaw && battleSplit)
                    extendedIsMap = rendered == 1
                    // Cambio di luogo: per qualche fotogramma la mappa nuova non si allinea ancora; si tiene l'ultima
                    // immagine buona invece di mostrare il riempimento (rettangolo di colore, D-067).
                    if (active && rendered == 1 && !inBattleRaw) {
                        // ultima immagine buona della mappa (per la tendina prima della lotta, D-083)
                        if (lastMapExt.size != extBack.pixels.size) lastMapExt = IntArray(extBack.pixels.size)
                        System.arraycopy(extBack.pixels, 0, lastMapExt, 0, extBack.pixels.size)
                        lastMapH = eh; lastMapTop = extendedFrameTop; wipeDark = 0; wipeOn = true
                        // colori dell'immagine del gioco → colori mostrati (ricolore dei luoghi), per i lampi prima della lotta
                        colorMap.clear()
                        val fr = latestFrame.get(); val top0 = extendedFrameTop.coerceAtLeast(0)
                        var j = 0
                        while (j < fr.size) { val r = top0 + j / NativeCore.WIDTH; if (r < eh) colorMap[fr[j]] = extBack.pixels[r * NativeCore.WIDTH + j % NativeCore.WIDTH]; j += 7 }
                    }
                    val battleLayoutOn = inBattleRaw && battleSplit   // scena di lotta già disposta: non è un lampo né una tendina (D-107)
                    if (!active && !battleLayoutOn && wipeOn && lastMapH == eh && remappable(latestFrame.get())) {
                        // Lampo prima della lotta coi colori originali del luogo (D-083): immagine del gioco coi colori ricolorati.
                        System.arraycopy(lastMapExt, 0, extBack.pixels, 0, minOf(lastMapExt.size, extBack.pixels.size))
                        val fr = latestFrame.get(); val top0 = lastMapTop.coerceAtLeast(0)
                        for (j in fr.indices) { val r = top0 + j / NativeCore.WIDTH; if (r < eh) extBack.pixels[r * NativeCore.WIDTH + j % NativeCore.WIDTH] = frameMap[fr[j]] ?: colorMap[fr[j]] ?: fr[j] }
                        // righe sopra e sotto: l'ultima mappa buona schiarita/scurita come il fotogramma
                        if (frameMap.any { (k, v) -> colorMap[k] == null }) fadeOutside(extBack.pixels, eh, top0, fr)
                        if (wipeTiles(fr) > 0) extendWipe(fr, extBack.pixels, eh, top0)   // tendina: anche sopra e sotto
                        extBack.height = eh; extBack.active = true; extendedActive = true
                        extBack = latestExtended.getAndSet(extBack)
                    } else
                    if (!active && !battleLayoutOn && inBattleRaw && wipeOn && lastMapH == eh && wipeTiles(latestFrame.get()) >= wipeDark - 8 && wipeDark < 20 * 18) {
                        // Tendina (D-083): il gioco non è più riconosciuto come mappa (quasi tutto nero) e si vedrebbero i
                        // colori originali; si usa l'ultima immagine della mappa coperta dalle caselle nere della tendina.
                        wipeDark = maxOf(wipeDark, wipeTiles(latestFrame.get()))
                        System.arraycopy(lastMapExt, 0, extBack.pixels, 0, minOf(lastMapExt.size, extBack.pixels.size))
                        val top = lastMapTop.coerceAtLeast(0)
                        for (ty in 0 until 18) for (tx in 0 until 20) if (wiped[ty * 20 + tx])
                            for (y in 0 until 8) { val r = top + ty * 8 + y; if (r < eh) java.util.Arrays.fill(extBack.pixels, r * NativeCore.WIDTH + tx * 8, r * NativeCore.WIDTH + tx * 8 + 8, wipeColor) }
                        extendWipe(latestFrame.get(), extBack.pixels, eh, top)
                        extBack.height = eh; extBack.active = true; extendedActive = true
                        extBack = latestExtended.getAndSet(extBack)
                    } else if (!active && extHold > 0 && !inBattleRaw && !trainerCardShown && mainMenuItems == 0) {
                        if (!active && inBattleRaw) wipeOn = false
                        extHold--
                    } else {
                        if (!active && inBattleRaw) wipeOn = false
                        if (active) extMask.apply(extBack.pixels, eh, extendedFrameTop, boxes, frameMask.older, boxesOnScreen = onScreen)
                        if (active && inBattleRaw) extendWipe(latestFrame.get(), extBack.pixels, eh, extendedFrameTop)
                        extBack.height = eh
                        extBack.active = active
                        extendedActive = active
                        extBack = latestExtended.getAndSet(extBack)
                        extHold = if (active) 8 else 0
                    }
                } else extendedActive = false
                onFrame?.invoke()
                frames++
                if (frames % AUTOSAVE_FRAMES == 0L) runCatching { saveBatteryIfChanged() }
                if (frames % SNAPSHOT_FRAMES == 0L) readSnapshot()
                stepDone?.release()
                if (holdSheet && !trainerCardShown && !itemExecutor.busy && closingFrames == 0 && latestSnapshot.get().freeRoam) holdSheet = false
                if (frames % SHEET_FRAMES == 0L && !holdSheet && !trainerCardShown && NativeCore.nativeGameTiles(handle, sheetBuf) >= 0 &&
                    sheetReadable(sheetBuf)) {
                    normalizeSheet(sheetBuf)
                    val old = tileSheet.get()
                    if (old == null || !old.contentEquals(sheetBuf)) tileSheet.set(sheetBuf.copyOf())
                    // una volta per partita: grafica salvata per le schermate di scelta del file (D-066)
                    if (!tilesSaved && latestSnapshot.get().freeRoam && hasBoxTiles(sheetBuf)) { tilesSaved = true; storage.saveGameTiles(sheetBuf.copyOf()) }
                }
                // Dopo START sul titolo il gioco resta bianco ~1,7 s mentre carica il salvataggio: quel tratto va veloce
                // (D-079), così il menu iniziale compare prima.
                if (titleShown) titleWasShown = true
                if (inGame || mainMenuItems > 0) titleWasShown = false
                val loadingWhite = titleWasShown && !titleShown && mainMenuItems == 0 && !inGame && back.all { it == back[0] }
                // Inizio lotta (D-097): dopo la tendina il gioco resta bianco qualche fotogramma prima della scena: va veloce,
                // così la lotta compare prima.
                val battleIntroWhite = inBattleNowMem && !battleMenuSeen && rawWhite
                if ((loadingWhite || battleIntroWhite) && stepPermits == null) {
                    fastFrames++
                    nextFrameAt = SystemClock.elapsedRealtimeNanos()
                } else if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // La scrittura si blocca quando il buffer è pieno: tiene il gioco alla velocità giusta.
                    // Scelta di una mossa dall'app: il "bip" di LOTTA non si sente, solo quello della mossa (D-069).
                    if (executor.navigatingFight) java.util.Arrays.fill(audio, 0, count, 0.toShort())
                    track.write(audio, 0, count)
                } else {
                    nextFrameAt += FRAME_NANOS
                    val wait = nextFrameAt - SystemClock.elapsedRealtimeNanos()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    else nextFrameAt = SystemClock.elapsedRealtimeNanos()
                }
            }
            while (true) tasks.poll()?.run() ?: break
        } finally {
            track?.release()
            audioTrack = null
            NativeCore.nativeDestroy(handle)
        }
    }

    private fun readSnapshot() {
        runCatching {
            NativeCore.nativeReadRange(handle, MemoryMapIt.BLOCK_START, stateBlock)
            val naming = readNaming()
            val base = GameStateReader.read(stateBlock)
            val joyIgnore = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE)
            val freeRoam = joyIgnore == 0 && !base.inBattle && FreeRoam.screenIsMap(tileMap)
            // In gioco solo con la mappa vera riconosciuta (vista estesa attiva): durante l'animazione iniziale
            // e il titolo lo schermo può sembrare "libero" ma non è una partita.
            if (freeRoam && extendedActive && extendedIsMap) inGame = true
            val snapshot = base.copy(
                menu = MenuReader.read(menuVars, tileMap), freeRoam = freeRoam, naming = naming,
                cursorOnScreen = tileMap.any { (it.toInt() and 0xFF) == 0xED },
            )
            if (snapshot != latestSnapshot.get()) latestSnapshot.set(snapshot)
        }.onFailure { Log.e(TAG, "Lettura stato non riuscita", it) }
    }

    private fun createAudioTrack(): AudioTrack? = runCatching {
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE * 4 / 12))
            .build()
    }.onFailure { Log.e(TAG, "Audio non disponibile", it) }.getOrNull()

    companion object {
        const val SPRITE_LAYER_TOP = 128
        const val SPRITE_LAYER_H = NativeCore.HEIGHT + 256
        /** Solo per il banco di prova: la sessione nasce già in modalità fotogramma per fotogramma. */
        @Volatile @JvmStatic var testStepFromStart = false
        private const val TAG = "GameSession"
        const val SAMPLE_RATE = 48000
        /** Il Game Boy disegna circa 59,73 fotogrammi al secondo. */
        private const val FRAME_NANOS = 16_742_706L
        private const val AUTOSAVE_FRAMES = 60L * 20
        private const val SNAPSHOT_FRAMES = 15L
        private const val SHEET_FRAMES = 30L
        /** La mappa è 5 caselle (40 pixel del gioco) più in basso del centro, per lasciare posto alla squadra in alto. */
        const val MAP_SHIFT = 40
        /** Scelta del committente nel Laboratorio (D-012): toccare il terreno NON fa camminare. */
        private const val TAP_WALKS_TO_GROUND = false
    }
}
