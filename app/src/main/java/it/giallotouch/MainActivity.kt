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
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) { error = "Non riesco a leggere il file scelto."; return@rememberLauncherForActivityResult }
            when (val r = RomLoader.prepare(bytes)) {
                is RomLoader.Result.Supported -> { error = null; pendingRom = null; pendingTitle = null; useRom(r.rom) }
                is RomLoader.Result.OtherGame -> { error = null; pendingRom = r.rom; pendingTitle = r.title }
                is RomLoader.Result.Invalid -> { error = r.message; pendingRom = null }
            }
        }
        val current = session
        if (current != null) {
            GameScreen(current, settings)
        } else {
            RomPickerScreen(
                error = error ?: startupMessage,
                warning = if (pendingRom != null)
                    "Questo file è un gioco per Game Boy («${pendingTitle.orEmpty()}») ma non la versione italiana di Giallo per cui è pensata l'app. Il gioco potrebbe funzionare, ma squadra in alto, comandi di lotta e tocco sulle persone no."
                else null,
                onPick = { picker.launch(arrayOf("*/*")) },
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
