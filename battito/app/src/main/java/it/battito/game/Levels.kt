package it.battito.game

/**
 * Livelli scritti a mano come "disegni" di testo (vedi legenda in [Level]).
 * Ogni pezzo è una lista di righe dall'alto in basso; i pezzi vengono messi uno dopo l'altro.
 * Il test LevelSolvableTest verifica con un bot che ogni livello sia completabile.
 */
object Levels {
    private const val W8 = 8 // altezza muri invalicabili (non si saltano: devi cambiare colore)

    private fun wall(pattern: String, bottom: String = pattern): List<String> =
        List(W8 - 1) { pattern } + bottom

    val primo: Level = Level.fromChunks(
        "Primo Battito",
        listOf(
            // Partenza tranquilla
            listOf("..................."),
            // Spuntoni singoli e doppi
            listOf("......^.........^.........^^........"),
            // Gradini
            listOf(
                "...............###.........",
                "....^^....##...###....^....",
            ),
            // Primo muro ciano: sei ciano, quindi è solido → passa al magenta.
            // Poi muro magenta → torna ciano.
            wall("..........C..............M.............."),
            // Piattaforme colorate sopra un fossato di spuntoni
            listOf(
                "...##CCCC...MMMM...CCCC.......",
                "..###^^^^^^^^^^^^^^^^^^^......",
            ),
            // Anelli di salto sopra una lunga fila di spuntoni
            listOf(
                ".....o...o...o.......",
                ".....................",
                "...^^^^^^^^^^^^^.....",
            ),
            // Pedana sopra il muretto
            listOf(
                "..........###.........",
                "..........###.........",
                ".....p....###.........",
            ),
            // Finale: muri alternati con spuntoni in mezzo
            wall(
                "......C.......M.......C........M.........",
                "......C...^...M...^...C....^^..M.........",
            ),
            // Traguardo
            listOf("........................"),
        )
    )

    val all: List<Level> = listOf(primo)
}
