package it.battito.game

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

class SongTest {

    @Test
    fun ilBranoSuonaPulitoEDuraQuantoIlLivello() {
        val sr = 44100
        val player = SongPlayer(sr)
        player.seek(0f); player.playing = true
        val total = (Song.LENGTH_SEC * sr).toInt()
        val all = FloatArray(total * 2)
        val buf = FloatArray(1024 * 2)
        var pos = 0
        while (pos < total) {
            val n = minOf(1024, total - pos)
            player.render(buf, n)
            System.arraycopy(buf, 0, all, pos * 2, n * 2)
            pos += n
        }
        var peak = 0f; var sum = 0.0
        for (x in all) { assertTrue("campione non valido", x.isFinite()); peak = maxOf(peak, abs(x)); sum += x * x }
        val rms = sqrt(sum / all.size)
        println("picco=%.3f  rms=%.3f (%.1f dBFS)".format(peak, rms, 20 * kotlin.math.log10(rms)))
        assertTrue("troppo forte", peak <= 1f)
        assertTrue("troppo piano", rms > 0.08)

        // La durata del brano (senza coda) coincide con quella del livello
        val levelSec = Levels.primo.width / Sim.SPEED
        assertTrue(abs(levelSec - Song.BARS * Song.BAR_SEC) < 0.01f)

        // Anteprima facoltativa: -Pbattito.wav=/percorso/brano.wav
        System.getProperty("battito.wav")?.let { writeWav(File(it), all, sr) }
    }

    private fun writeWav(f: File, data: FloatArray, sr: Int) {
        DataOutputStream(FileOutputStream(f).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun le16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            val bytes = data.size * 2
            o.writeBytes("RIFF"); le32(36 + bytes); o.writeBytes("WAVEfmt "); le32(16); le16(1); le16(2)
            le32(sr); le32(sr * 4); le16(4); le16(16); o.writeBytes("data"); le32(bytes)
            for (x in data) le16((x.coerceIn(-1f, 1f) * 32767).toInt())
        }
    }
}
