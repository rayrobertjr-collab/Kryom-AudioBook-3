package ca.raymond.livresaudio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bibliothèque : dossiers choisis, livres trouvés et position de lecture.
 * Tout est sauvegardé dans un petit fichier JSON (filesDir/library.json).
 */
object Library {
    private const val TAG = "Library"
    private val AUDIO_EXT = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "3gp", "amr")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

    private lateinit var appCtx: Context
    private lateinit var file: File
    private var initialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private val scanMutex = Mutex()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _marks = MutableStateFlow<List<Bookmark>>(emptyList())
    val marks: StateFlow<List<Bookmark>> = _marks.asStateFlow()

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _boostDb = MutableStateFlow(0f)
    val boostDb: StateFlow<Float> = _boostDb.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appCtx = context.applicationContext
        file = File(appCtx.filesDir, "library.json")
        load()
        initialized = true
    }

    fun find(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun lastPlayedBook(): Book? =
        _books.value.filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed }

    // ---------- Dossiers ----------

    fun addFolder(uri: Uri, singleBook: Boolean) {
        try {
            appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission persistante refusée", e)
        }
        val s = uri.toString()
        _folders.update { list -> list.filterNot { it.uri == s } + Folder(s, singleBook) }
        save()
        rescan()
    }

    fun removeFolder(uri: String) {
        try {
            appCtx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        _folders.update { list -> list.filterNot { it.uri == uri } }
        _books.update { list -> list.filterNot { it.folder == uri } }
        save()
    }

    // ---------- Progression ----------

    fun updateProgress(id: String, chapterIndex: Int, positionMs: Long, speed: Float? = null) {
        val now = System.currentTimeMillis()
        _books.update { list ->
            list.map {
                if (it.id == id) it.copy(
                    chapterIndex = chapterIndex.coerceIn(0, (it.chapters.size - 1).coerceAtLeast(0)),
                    positionMs = positionMs.coerceAtLeast(0),
                    speed = speed ?: it.speed,
                    lastPlayed = now,
                ) else it
            }
        }
        save()
    }

    // ---------- Réglages ----------

    fun setSkipSilence(on: Boolean) { _skipSilence.value = on; save() }
    fun setBoost(db: Float) { _boostDb.value = db.coerceIn(0f, 12f); save() }

    // ---------- Marque-pages et historique ----------

    fun addMark(bookId: String, chapterIndex: Int, positionMs: Long, title: String, auto: Boolean) {
        val now = System.currentTimeMillis()
        _marks.update { list ->
            if (auto) {
                val lastAuto = list.filter { it.bookId == bookId && it.auto }.maxByOrNull { it.time }
                if (lastAuto != null && lastAuto.chapterIndex == chapterIndex &&
                    kotlin.math.abs(lastAuto.positionMs - positionMs) < 10_000
                ) return@update list
            }
            val added = list + Bookmark(now, bookId, chapterIndex, positionMs.coerceAtLeast(0), title, now, auto)
            // garder au plus 30 entrées d'historique par livre
            val autos = added.filter { it.bookId == bookId && it.auto }.sortedByDescending { it.time }
            val drop = autos.drop(30).map { it.id }.toSet()
            added.filterNot { it.id in drop }
        }
        save()
    }

    fun deleteMark(id: Long) {
        _marks.update { list -> list.filterNot { it.id == id } }
        save()
    }

    // ---------- Modification d'un livre ----------

    fun editInfo(id: String, title: String, author: String?) {
        _books.update { list ->
            list.map { if (it.id == id) it.copy(title = title.ifBlank { it.title }, author = author?.ifBlank { null }) else it }
                .sortedWith(compareBy(NaturalOrder) { it.title })
        }
        save()
    }

    fun setCoverBytes(id: String, bytes: ByteArray) {
        val dir = File(appCtx.filesDir, "covers").apply { mkdirs() }
        val f = File(dir, "${id.hashCode().toUInt()}_${System.currentTimeMillis()}.img")
        try {
            f.writeBytes(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Couverture non enregistrée", e)
            return
        }
        val old = find(id)?.coverUri
        _books.update { list -> list.map { if (it.id == id) it.copy(coverUri = Uri.fromFile(f).toString()) else it } }
        // effacer l'ancienne image si elle nous appartient
        if (old != null && old.startsWith("file:")) {
            try { Uri.parse(old).path?.let { File(it).delete() } } catch (_: Exception) {}
        }
        save()
    }

    fun setCoverFromUri(id: String, uri: Uri) {
        val bytes = try {
            appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return
        setCoverBytes(id, bytes)
    }

    /** Retire le livre de la bibliothèque (les fichiers restent sur le téléphone). */
    fun hideBook(id: String) {
        _hidden.update { it + id }
        _books.update { list -> list.filterNot { it.id == id } }
        _marks.update { list -> list.filterNot { it.bookId == id } }
        save()
    }

    fun unhideAll() {
        _hidden.value = emptySet()
        save()
        rescan()
    }

    // ---------- Construction des pistes pour le lecteur ----------

    fun mediaItem(book: Book, index: Int): MediaItem {
        val ch = book.chapters[index]
        val md = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(ch.name)
            .setAlbumTitle(book.title)
            .setAlbumArtist(book.author)
            .setArtworkUri(book.coverUri?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(MediaIds.make(book.id, index))
            .setUri(Uri.parse(ch.uri))
            .setMediaMetadata(md)
            .build()
    }

    /** Reconstruit une piste complète à partir de son mediaId (le contrôleur n'envoie que l'id). */
    fun resolve(item: MediaItem): MediaItem {
        val (bookId, idx) = MediaIds.parse(item.mediaId) ?: return item
        val book = find(bookId) ?: return item
        if (idx !in book.chapters.indices) return item
        return mediaItem(book, idx)
    }

    // ---------- Balayage des dossiers ----------

    fun rescan() {
        scope.launch {
            scanMutex.withLock {
                _scanning.value = true
                try {
                    doScan()
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur de balayage", e)
                } finally {
                    _scanning.value = false
                }
            }
        }
    }

    private fun doScan() {
        val oldBooks = _books.value.associateBy { it.id }
        val oldChapters = _books.value.flatMap { it.chapters }.associateBy { it.uri }
        val found = mutableListOf<Book>()

        for (f in _folders.value) {
            val root = try {
                DocumentFile.fromTreeUri(appCtx, Uri.parse(f.uri))
            } catch (e: Exception) {
                null
            } ?: continue
            if (!root.canRead()) continue

            if (f.singleBook) {
                val files = collectAudio(root)
                if (files.isNotEmpty()) {
                    found += buildBook(f.uri, root.uri.toString(), root.name ?: "Livre", files, root, oldBooks, oldChapters)
                }
            } else {
                for (child in root.listFiles()) {
                    val name = child.name ?: continue
                    if (name.startsWith(".")) continue
                    if (child.isDirectory) {
                        val files = collectAudio(child)
                        if (files.isNotEmpty()) {
                            found += buildBook(f.uri, child.uri.toString(), name, files, child, oldBooks, oldChapters)
                        }
                    } else if (isAudio(name, child.type)) {
                        found += buildBook(f.uri, child.uri.toString(), name.substringBeforeLast('.'), listOf(child), null, oldBooks, oldChapters)
                    }
                }
            }
        }

        // Fusion avec la progression la plus récente (elle a pu changer pendant le balayage)
        _books.update { current ->
            val cur = current.associateBy { it.id }
            val hidden = _hidden.value
            found
                .filterNot { it.id in hidden }
                .distinctBy { it.id }
                .map { b ->
                    val c = cur[b.id]
                    if (c == null) b else b.copy(
                        chapterIndex = c.chapterIndex.coerceIn(0, (b.chapters.size - 1).coerceAtLeast(0)),
                        positionMs = c.positionMs,
                        lastPlayed = c.lastPlayed,
                        speed = c.speed,
                    )
                }
                .sortedWith(compareBy(NaturalOrder) { it.title })
        }
        save()
    }

    private fun isAudio(name: String, mime: String?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXT || (mime?.startsWith("audio/") == true && ext != "nomedia")
    }

    private fun collectAudio(dir: DocumentFile): List<DocumentFile> {
        val out = mutableListOf<Pair<String, DocumentFile>>()
        fun walk(d: DocumentFile, prefix: String, depth: Int) {
            if (depth > 4) return
            for (c in d.listFiles()) {
                val n = c.name ?: continue
                if (n.startsWith(".")) continue
                if (c.isDirectory) walk(c, "$prefix$n/", depth + 1)
                else if (isAudio(n, c.type)) out += ("$prefix$n" to c)
            }
        }
        walk(dir, "", 0)
        return out.sortedWith(compareBy(NaturalOrder) { it.first }).map { it.second }
    }

    private fun buildBook(
        folderUri: String,
        id: String,
        fallbackTitle: String,
        files: List<DocumentFile>,
        dir: DocumentFile?,
        oldBooks: Map<String, Book>,
        oldChapters: Map<String, Chapter>,
    ): Book {
        val chapters = files.map { f ->
            val u = f.uri.toString()
            oldChapters[u] ?: Chapter(u, f.name?.substringBeforeLast('.') ?: "?", readDuration(f.uri))
        }
        val old = oldBooks[id]
        var title = old?.title
        var author = old?.author
        var cover = old?.coverUri

        if (old == null || cover == null) {
            val meta = readMeta(files.first().uri)
            if (old == null) {
                title = if (dir == null) (meta.album ?: meta.title ?: fallbackTitle) else fallbackTitle
                author = meta.artist
            }
            cover = dir?.let { findImage(it) } ?: meta.picture?.let { saveCover(id, it) }
        }
        return Book(
            id = id,
            folder = folderUri,
            title = title ?: fallbackTitle,
            author = author,
            coverUri = cover,
            chapters = chapters,
        )
    }

    private fun findImage(dir: DocumentFile): String? =
        dir.listFiles()
            .filter { it.isFile && (it.name ?: "").substringAfterLast('.', "").lowercase() in IMAGE_EXT }
            .sortedByDescending {
                val n = it.name?.lowercase() ?: ""
                n.contains("cover") || n.contains("folder") || n.contains("front") || n.contains("couverture")
            }
            .firstOrNull()?.uri?.toString()

    private class Meta(val title: String?, val album: String?, val artist: String?, val picture: ByteArray?)

    private fun readMeta(uri: Uri): Meta {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(appCtx, uri)
            Meta(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                picture = r.embeddedPicture,
            )
        } catch (e: Exception) {
            Meta(null, null, null, null)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun readDuration(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(appCtx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun saveCover(id: String, bytes: ByteArray): String? = try {
        val dir = File(appCtx.filesDir, "covers").apply { mkdirs() }
        val f = File(dir, "${id.hashCode().toUInt()}.img")
        f.writeBytes(bytes)
        Uri.fromFile(f).toString()
    } catch (e: Exception) {
        null
    }

    // ---------- Persistance JSON ----------

    private fun save() {
        if (!::file.isInitialized) return
        scope.launch {
            saveMutex.withLock {
                try {
                    val json = toJson(_books.value, _folders.value)
                    val tmp = File(file.parentFile, "library.json.tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(file)) {
                        file.writeText(json)
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sauvegarde impossible", e)
                }
            }
        }
    }

    private fun toJson(books: List<Book>, folders: List<Folder>): String {
        val root = JSONObject()
        root.put("folders", JSONArray().apply {
            folders.forEach { put(JSONObject().put("uri", it.uri).put("single", it.singleBook)) }
        })
        root.put("books", JSONArray().apply {
            books.forEach { b ->
                val o = JSONObject()
                o.put("id", b.id)
                o.put("folder", b.folder)
                o.put("title", b.title)
                b.author?.let { o.put("author", it) }
                b.coverUri?.let { o.put("cover", it) }
                o.put("chapterIndex", b.chapterIndex)
                o.put("positionMs", b.positionMs)
                o.put("lastPlayed", b.lastPlayed)
                o.put("speed", b.speed.toDouble())
                o.put("chapters", JSONArray().apply {
                    b.chapters.forEach { c ->
                        put(JSONObject().put("uri", c.uri).put("name", c.name).put("d", c.durationMs))
                    }
                })
                put(o)
            }
        })
        root.put("marks", JSONArray().apply {
            _marks.value.forEach { m ->
                put(
                    JSONObject().put("id", m.id).put("book", m.bookId).put("ch", m.chapterIndex)
                        .put("pos", m.positionMs).put("title", m.title).put("time", m.time).put("auto", m.auto)
                )
            }
        })
        root.put("hidden", JSONArray().apply { _hidden.value.forEach { put(it) } })
        root.put("skipSilence", _skipSilence.value)
        root.put("boostDb", _boostDb.value.toDouble())
        return root.toString()
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val fa = root.optJSONArray("folders") ?: JSONArray()
            _folders.value = (0 until fa.length()).map {
                val o = fa.getJSONObject(it)
                Folder(o.getString("uri"), o.optBoolean("single", false))
            }
            val ba = root.optJSONArray("books") ?: JSONArray()
            _books.value = (0 until ba.length()).map { i ->
                val o = ba.getJSONObject(i)
                val ca = o.optJSONArray("chapters") ?: JSONArray()
                Book(
                    id = o.getString("id"),
                    folder = o.optString("folder", ""),
                    title = o.optString("title", "?"),
                    author = o.str("author"),
                    coverUri = o.str("cover"),
                    chapters = (0 until ca.length()).map { j ->
                        val c = ca.getJSONObject(j)
                        Chapter(c.getString("uri"), c.optString("name", "?"), c.optLong("d", 0))
                    },
                    chapterIndex = o.optInt("chapterIndex", 0),
                    positionMs = o.optLong("positionMs", 0),
                    lastPlayed = o.optLong("lastPlayed", 0),
                    speed = o.optDouble("speed", 1.0).toFloat(),
                )
            }
            val ma = root.optJSONArray("marks") ?: JSONArray()
            _marks.value = (0 until ma.length()).map { i ->
                val o = ma.getJSONObject(i)
                Bookmark(
                    id = o.getLong("id"),
                    bookId = o.getString("book"),
                    chapterIndex = o.optInt("ch", 0),
                    positionMs = o.optLong("pos", 0),
                    title = o.optString("title", ""),
                    time = o.optLong("time", 0),
                    auto = o.optBoolean("auto", false),
                )
            }
            val ha = root.optJSONArray("hidden") ?: JSONArray()
            _hidden.value = (0 until ha.length()).map { ha.getString(it) }.toSet()
            _skipSilence.value = root.optBoolean("skipSilence", false)
            _boostDb.value = root.optDouble("boostDb", 0.0).toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Lecture de la bibliothèque impossible", e)
        }
    }
}
