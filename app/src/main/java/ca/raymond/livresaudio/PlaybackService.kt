package ca.raymond.livresaudio

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lecture en arrière-plan + notification + écran verrouillé + écouteurs Bluetooth. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Library.init(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause quand on débranche les écouteurs
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = saveNow()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = saveNow()
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = saveNow()
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) = saveNow()
        })

        // Sauvegarde régulière de la position pendant la lecture
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) saveNow()
            }
        }

        SleepTimer.onExpire = { player.pause() }
    }

    private fun saveNow() {
        val item = player.currentMediaItem ?: return
        val (bookId, _) = MediaIds.parse(item.mediaId) ?: return
        Library.updateProgress(
            bookId,
            player.currentMediaItemIndex,
            player.currentPosition,
            player.playbackParameters.speed
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveNow()
        SleepTimer.onExpire = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class Callback : MediaSession.Callback {
        /** Le contrôleur n'envoie que des identifiants : on reconstruit les pistes complètes. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { Library.resolve(it) }.toMutableList())

        /** Bouton « lecture » des écouteurs quand l'appli est fermée : reprendre le dernier livre. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val book = Library.lastPlayedBook()
            if (book == null || book.chapters.isEmpty()) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Aucun livre"))
            }
            val items = book.chapters.indices.map { Library.mediaItem(book, it) }
            player.setPlaybackSpeed(book.speed)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    items,
                    book.chapterIndex.coerceIn(0, items.lastIndex),
                    book.positionMs
                )
            )
        }
    }
}

/** Minuterie de sommeil : met en pause après X minutes. */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()
    var onExpire: (() -> Unit)? = null

    fun start(minutes: Int) {
        job?.cancel()
        if (minutes <= 0) {
            _remainingMs.value = 0
            return
        }
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        job = scope.launch {
            while (true) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) break
                _remainingMs.value = left
                delay(1_000)
            }
            _remainingMs.value = 0
            onExpire?.invoke()
        }
    }

    fun cancel() = start(0)
}
