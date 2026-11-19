package it.giallotouch

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.giallotouch.emu.GameSession
import it.giallotouch.storage.RomFinder
import it.giallotouch.storage.RomLoader
import it.giallotouch.storage.SaveSlot
import it.giallotouch.storage.SaveStorage
import it.giallotouch.storage.Settings
import it.giallotouch.ui.GameScreen
import it.giallotouch.ui.RomPickerScreen

class MainActivity : ComponentActivity() {

    private lateinit var storage: SaveStorage
    private lateinit var settings: Settings
    private var session by mutableStateOf<GameSession?>(null)

    /** Solo per il banco di prova dell'interfaccia (test sul computer). */
    internal val sessionForTest: GameSession? get() = session
    private var startupMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        storage = SaveStorage(this)
        settings = Settings(this)
        if (storage.hasRom()) checkStoredRomAndStart()
        setContent { App() }
    }

    @Composable
    private fun App() {
        var error by remember { mutableStateOf<String?>(null) }
        var pendingRom by remember { mutableStateOf<ByteArray?>(null) }
        var pendingTitle by remember { mutableStateOf<String?>(null) }
        var searching by remember { mutableStateOf(false) }
        fun handle(r: RomLoader.Result) {
            when (r) {
                is RomLoader.Result.Supported -> { error = null; pendingRom = null; pendingTitle = null; useRom(r.rom) }
                is RomLoader.Result.OtherGame -> { error = null; pendingRom = r.rom; pendingTitle = r.title }
                is RomLoader.Result.Invalid -> { error = r.message; pendingRom = null }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) { error = "Non riesco a leggere il file scelto."; return@rememberLauncherForActivityResult }
            handle(RomLoader.prepare(bytes))
        }
        // D-066: si sceglie una cartella e l'app trova da sola il file del gioco.
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
            if (tree == null) return@rememberLauncherForActivityResult
            searching = true
            Thread {
                val found = runCatching { RomFinder.find(this, tree) }.getOrDefault(RomFinder.Found.Nothing)
                runOnUiThread {
                    searching = false
                    when (found) {
                        is RomFinder.Found.Rom -> handle(found.result)
                        RomFinder.Found.Nothing -> { pendingRom = null; error = "In questa cartella non ho trovato il file del gioco. Prova un'altra cartella o SCEGLI FILE." }
                    }
                }
            }.start()
        }
        val current = session
        if (current != null) {
            GameScreen(current, settings)
        } else {
            // Primissima apertura: si apre subito la scelta della cartella di Android (nessuna schermata dell'app prima).
            var autoOpened by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            val tiles = remember { storage.loadGameTiles() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (!autoOpened && tiles == null && startupMessage == null) { autoOpened = true; folderPicker.launch(null) }
            }
            val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }
            RomPickerScreen(
                tiles = tiles,
                version = version.replace("-", " "),
                busy = searching,
                error = error ?: startupMessage,
                warning = if (pendingRom != null)
                    "Questo file è un altro gioco per Game Boy (${pendingTitle.orEmpty()}). Alcune funzioni dell'app non andranno."
                else null,
                onPickFolder = { folderPicker.launch(null) },
                onPickFile = { picker.launch(arrayOf("*/*")) },
                onContinueAnyway = pendingRom?.let { rom -> { pendingRom = null; useRom(rom) } }
            )
        }
    }

    private fun useRom(bytes: ByteArray) {
        storage.writeAtomic(storage.romFile, bytes)
        startupMessage = null
        startSession()
    }

    /**
     * La versione 0.1 salvava il file così com'era scelto: se era uno ZIP, il gioco non poteva partire.
     * Qui si ripara: se il file salvato è uno ZIP si estrae il gioco; se non è valido lo si scarta
     * (con il salvataggio automatico, che non può essere buono) e si chiede di sceglierlo di nuovo.
     */
    private fun checkStoredRomAndStart() {
        val stored = storage.readWithBackup(storage.romFile)
        val result = stored?.let { RomLoader.prepare(it) }
        when (result) {
            is RomLoader.Result.Supported, is RomLoader.Result.OtherGame -> {
                val rom = if (result is RomLoader.Result.Supported) result.rom else (result as RomLoader.Result.OtherGame).rom
                if (!rom.contentEquals(stored)) {
                    storage.writeAtomic(storage.romFile, rom)
                    storage.deleteSlot(SaveSlot.AUTO)
                }
                startSession()
            }
            else -> {
                storage.forgetGameFile()
                startupMessage = "Il file scelto prima non era valido (forse uno ZIP). Sceglilo di nuovo: ora l'app apre anche gli ZIP."
            }
        }
    }

    private fun startSession() {
        val rom = storage.readWithBackup(storage.romFile) ?: return
        session = GameSession(storage, rom).also { it.start() }
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
        session?.resume()
    }

    override fun onStop() {
        // Chiusura o app in secondo piano: si salva il punto esatto, per non perdere progressi.
        session?.let {
            it.pause()
            it.saveNow()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            session?.close()
            session = null
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
