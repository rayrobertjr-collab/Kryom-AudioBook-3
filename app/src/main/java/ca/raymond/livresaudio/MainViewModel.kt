package ca.raymond.livresaudio

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayState(
    val bookId: String? = null,
    val loaded: Boolean = false,
    val chapterIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val books = Library.books
    val folders = Library.folders
    val scanning = Library.scanning
    val sleepRemaining = SleepTimer.remainingMs
    val marks = Library.marks
    val hidden = Library.hidden
    val skipSilence = Library.skipSilence
    val boostDb = Library.boostDb

    // ---- Menu d'actions d'un livre (partagé bibliothèque / lecteur) ----
    private val _actionsFor = MutableStateFlow<String?>(null)
    val actionsFor: StateFlow<String?> = _actionsFor.asStateFlow()
    private val _editFor = MutableStateFlow<String?>(null)
    val editFor: StateFlow<String?> = _editFor.asStateFlow()
    private val _coverSearchFor = MutableStateFlow<String?>(null)
    val coverSearchFor: StateFlow<String?> = _coverSearchFor.asStateFlow()
    private val _deleteFor = MutableStateFlow<String?>(null)
    val deleteFor: StateFlow<String?> = _deleteFor.asStateFlow()

    private val _coverResults = MutableStateFlow<List<String>>(emptyList())
    val coverResults: StateFlow<List<String>> = _coverResults.asStateFlow()
    private val _coverLoading = MutableStateFlow(false)
    val coverLoading: StateFlow<Boolean> = _coverLoading.asStateFlow()

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private var controller: MediaController? = null

    init {
        Library.init(app)
        if (Library.folders.value.isNotEmpty()) Library.rescan() // détecte les nouveaux fichiers
        viewModelScope.launch {
            try {
                val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                controller = MediaController.Builder(app, token).buildAsync().await()
            } catch (_: Exception) {
            }
            while (isActive) {
                refresh()
                delay(250)
            }
        }
    }

    private fun loadedBookId(): String? =
        controller?.currentMediaItem?.mediaId?.let { MediaIds.parse(it)?.first }

    private fun refresh() {
        val c = controller
        val id = loadedBookId()
        if (c != null && id != null) {
            val book = Library.find(id)
            val dur = c.duration
            val idx = c.currentMediaItemIndex
            _state.value = PlayState(
                bookId = id,
                loaded = true,
                chapterIndex = idx,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (dur == C.TIME_UNSET || dur <= 0) (book?.chapters?.getOrNull(idx)?.durationMs ?: 0) else dur,
                isPlaying = c.isPlaying,
                speed = c.playbackParameters.speed,
            )
        } else {
            val b = Library.lastPlayedBook()
            _state.value = if (b == null) PlayState() else PlayState(
                bookId = b.id,
                loaded = false,
                chapterIndex = b.chapterIndex,
                positionMs = b.positionMs,
                durationMs = b.chapters.getOrNull(b.chapterIndex)?.durationMs ?: 0,
                isPlaying = false,
                speed = b.speed,
            )
        }
    }

    /** Charge un livre dans le lecteur (à la position mémorisée). */
    fun load(id: String, autoplay: Boolean) {
        val c = controller ?: return
        val cur = loadedBookId()
        if (cur == id) {
            if (autoplay) c.play()
            return
        }
        if (cur != null) {
            Library.updateProgress(cur, c.currentMediaItemIndex, c.currentPosition, c.playbackParameters.speed)
        }
        val book = Library.find(id) ?: return
        if (book.chapters.isEmpty()) return
        val items = book.chapters.indices.map {
            MediaItem.Builder().setMediaId(MediaIds.make(id, it)).build()
        }
        c.setMediaItems(items, book.chapterIndex.coerceIn(0, items.lastIndex), book.positionMs)
        c.setPlaybackSpeed(book.speed)
        c.prepare()
        c.playWhenReady = autoplay
        Library.updateProgress(id, book.chapterIndex, book.positionMs)
        refresh()
    }

    private fun ensureLoaded(): MediaController? {
        val c = controller ?: return null
        if (loadedBookId() == null) {
            val id = _state.value.bookId ?: return null
            load(id, autoplay = false)
        }
        return c
    }

    fun togglePlay() {
        val c = ensureLoaded() ?: return
        if (c.isPlaying) c.pause() else c.play()
        refresh()
    }

    fun skip(deltaMs: Long) {
        val c = ensureLoaded() ?: return
        val p = c.currentPosition + deltaMs
        val d = c.duration
        when {
            p < 0 -> c.seekTo(0)
            d > 0 && p > d -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.seekTo(d)
            else -> c.seekTo(p)
        }
        refresh()
    }

    /** Mémorise la position actuelle dans l'historique avant un saut. */
    private fun rememberHere(reason: String) {
        val c = controller ?: return
        val id = loadedBookId() ?: return
        val book = Library.find(id) ?: return
        val name = book.chapters.getOrNull(c.currentMediaItemIndex)?.name ?: ""
        Library.addMark(id, c.currentMediaItemIndex, c.currentPosition, "$reason · $name", auto = true)
    }

    fun seekTo(ms: Long) {
        if (kotlin.math.abs(ms - (controller?.currentPosition ?: ms)) > 30_000) rememberHere("Avant un saut")
        ensureLoaded()?.seekTo(ms)
        refresh()
    }

    fun goToChapter(index: Int) {
        rememberHere("Avant changement de chapitre")
        ensureLoaded()?.seekTo(index, 0)
        refresh()
    }

    fun previousChapter() {
        val c = ensureLoaded() ?: return
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
        refresh()
    }

    fun nextChapter() {
        val c = ensureLoaded() ?: return
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        refresh()
    }

    fun setSpeed(speed: Float) {
        val c = ensureLoaded() ?: return
        c.setPlaybackSpeed(speed)
        refresh()
    }

    fun setSleep(minutes: Int) = SleepTimer.start(minutes)

    fun toggleSkipSilence() = Library.setSkipSilence(!Library.skipSilence.value)
    fun setBoost(db: Float) = Library.setBoost(db)

    // ---- Marque-pages ----
    fun addBookmark(title: String) {
        val c = ensureLoaded() ?: return
        val id = loadedBookId() ?: return
        Library.addMark(id, c.currentMediaItemIndex, c.currentPosition, title, auto = false)
    }

    fun deleteMark(id: Long) = Library.deleteMark(id)

    fun jumpTo(mark: Bookmark) {
        if (loadedBookId() != mark.bookId) load(mark.bookId, autoplay = false)
        else rememberHere("Avant un saut")
        controller?.seekTo(mark.chapterIndex, mark.positionMs)
        refresh()
    }

    // ---- Actions sur un livre ----
    fun openActions(id: String) { _actionsFor.value = id }
    fun closeActions() { _actionsFor.value = null }
    fun startEdit(id: String) { _actionsFor.value = null; _editFor.value = id }
    fun closeEdit() { _editFor.value = null }
    fun saveInfo(id: String, title: String, author: String) {
        Library.editInfo(id, title.trim(), author.trim())
        _editFor.value = null
    }

    fun askDelete(id: String) { _actionsFor.value = null; _deleteFor.value = id }
    fun closeDelete() { _deleteFor.value = null }
    fun deleteBook(id: String) {
        if (loadedBookId() == id) {
            controller?.stop()
            controller?.clearMediaItems()
        }
        Library.hideBook(id)
        _deleteFor.value = null
    }

    fun unhideAll() = Library.unhideAll()

    fun setCoverFromFile(id: String, uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { Library.setCoverFromUri(id, uri) }
    }

    fun startCoverSearch(id: String) {
        _actionsFor.value = null
        _coverSearchFor.value = id
        val b = Library.find(id) ?: return
        searchCovers(listOfNotNull(b.title, b.author).joinToString(" "))
    }

    fun searchCovers(query: String) {
        if (query.isBlank()) return
        _coverLoading.value = true
        _coverResults.value = emptyList()
        viewModelScope.launch {
            _coverResults.value = CoverSearch.search(query)
            _coverLoading.value = false
        }
    }

    fun pickCover(id: String, url: String) {
        _coverLoading.value = true
        viewModelScope.launch {
            val bytes = CoverSearch.download(url)
            if (bytes != null) Library.setCoverBytes(id, bytes)
            _coverLoading.value = false
            _coverSearchFor.value = null
        }
    }

    fun closeCoverSearch() { _coverSearchFor.value = null }

    fun resetBook(id: String) {
        if (loadedBookId() == id) controller?.seekTo(0, 0)
        Library.updateProgress(id, 0, 0)
        refresh()
    }

    fun markFinished(id: String) {
        val b = Library.find(id) ?: return
        val last = (b.chapters.size - 1).coerceAtLeast(0)
        val end = b.chapters.getOrNull(last)?.durationMs ?: 0
        if (loadedBookId() == id) {
            controller?.pause()
            controller?.seekTo(last, end)
        }
        Library.updateProgress(id, last, end)
        refresh()
    }

    fun addFolder(uri: Uri, singleBook: Boolean) = Library.addFolder(uri, singleBook)
    fun removeFolder(uri: String) = Library.removeFolder(uri)
    fun rescan() = Library.rescan()

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
