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

    fun seekTo(ms: Long) {
        ensureLoaded()?.seekTo(ms)
        refresh()
    }

    fun goToChapter(index: Int) {
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
