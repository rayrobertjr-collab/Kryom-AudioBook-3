package ca.raymond.livresaudio

data class Chapter(
    val uri: String,
    val name: String,
    val durationMs: Long,
)

data class Folder(
    val uri: String,
    /** true = le dossier entier est UN livre; false = chaque sous-dossier/fichier est un livre */
    val singleBook: Boolean,
)

data class Book(
    val id: String,
    val folder: String,
    val title: String,
    val author: String?,
    val coverUri: String?,
    val chapters: List<Chapter>,
    val chapterIndex: Int = 0,
    val positionMs: Long = 0,
    val lastPlayed: Long = 0,
    val speed: Float = 1f,
) {
    val totalMs: Long get() = chapters.sumOf { it.durationMs }
    val playedMs: Long
        get() = chapters.take(chapterIndex).sumOf { it.durationMs } + positionMs
    val progress: Float
        get() = if (totalMs > 0) (playedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
}

/** Identifiant de piste: "<idLivre>|#|<indexChapitre>" */
object MediaIds {
    private const val SEP = "|#|"
    fun make(bookId: String, index: Int) = "$bookId$SEP$index"
    fun parse(mediaId: String): Pair<String, Int>? {
        val k = mediaId.lastIndexOf(SEP)
        if (k < 0) return null
        val idx = mediaId.substring(k + SEP.length).toIntOrNull() ?: return null
        return mediaId.substring(0, k) to idx
    }
}

/** Tri « naturel » : Chapitre 2 avant Chapitre 10. */
val NaturalOrder = Comparator<String> { a, b ->
    val re = Regex("\\d+|\\D+")
    val pa = re.findAll(a.lowercase()).map { it.value }.toList()
    val pb = re.findAll(b.lowercase()).map { it.value }.toList()
    var i = 0
    while (i < pa.size && i < pb.size) {
        val x = pa[i]
        val y = pb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val xs = x.trimStart('0')
            val ys = y.trimStart('0')
            if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
        } else x.compareTo(y)
        if (c != 0) return@Comparator c
        i++
    }
    pa.size - pb.size
}

fun formatTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatRemaining(ms: Long): String {
    val totalMin = (ms.coerceAtLeast(0) / 60000)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
        else -> "$m min"
    }
}
