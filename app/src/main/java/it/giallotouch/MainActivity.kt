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
import it.giallotouch.storage.SaveStorage
import it.giallotouch.ui.GameScreen
import it.giallotouch.ui.RomPickerScreen

class MainActivity : ComponentActivity() {

    private lateinit var storage: SaveStorage
    private var session by mutableStateOf<GameSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        storage = SaveStorage(this)
        if (storage.hasRom()) startSession()
        setContent { App() }
    }

    @Composable
    private fun App() {
        var error by remember { mutableStateOf<String?>(null) }
        var pendingRom by remember { mutableStateOf<ByteArray?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            when {
                bytes == null -> error = "Non riesco a leggere il file scelto."
                bytes.size < 0x8000 || bytes.size > 8 * 1024 * 1024 -> error = "Questo file non sembra un gioco per Game Boy."
                SaveStorage.sha1(bytes) == SaveStorage.SUPPORTED_SHA1 -> { error = null; useRom(bytes) }
                else -> { error = null; pendingRom = bytes }
            }
        }
        val current = session
        if (current != null) {
            GameScreen(current)
        } else {
            RomPickerScreen(
                error = error,
                warning = if (pendingRom != null)
                    "Questo file non è la versione italiana per cui è pensata l'app. Il gioco potrebbe funzionare, ma le funzioni future (menu nativi, mappa a tutto schermo) no."
                else null,
                onPick = { picker.launch(arrayOf("*/*")) },
                onContinueAnyway = pendingRom?.let { rom -> { pendingRom = null; useRom(rom) } }
            )
        }
    }

    private fun useRom(bytes: ByteArray) {
        storage.writeAtomic(storage.romFile, bytes)
        startSession()
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
