package com.perso.melodia

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.perso.melodia.data.Lyrics
import com.perso.melodia.data.LyricsRepository
import com.perso.melodia.data.MusicRepository
import com.perso.melodia.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlayerUi(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object NotFound : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val lyricsRepo = LyricsRepository(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    private var byId: Map<String, Song> = emptyMap()

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _lyrics = MutableStateFlow<LyricsUi>(LyricsUi.Loading)
    val lyrics: StateFlow<LyricsUi> = _lyrics
    private var lyricsJob: Job? = null
    private var lyricsForId: Long? = null

    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val controllerFuture: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            pending.forEach { it(c) }
            pending.clear()
            refresh()
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(200)
            }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) {
                runCatching { MusicRepository.loadSongs(getApplication()) }.getOrDefault(emptyList())
            }
            _songs.value = list
            byId = list.associateBy { it.id.toString() }
            _loading.value = false
            refresh()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: pending.add(action)
    }

    private fun refresh() {
        val c = controller ?: return
        val song = c.currentMediaItem?.mediaId?.let { byId[it] }
        _player.value = PlayerUi(
            song = song,
            isPlaying = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            durationMs = c.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
        if (song != null && song.id != lyricsForId) loadLyrics(song)
    }

    // ---------- Commandes ----------

    fun playSongs(list: List<Song>, index: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) Random.nextInt(list.size) else index.coerceIn(0, list.lastIndex)
            c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); _position.value = ms }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------- Paroles ----------

    private fun loadLyrics(song: Song, title: String? = null, artist: String? = null) {
        lyricsForId = song.id
        lyricsJob?.cancel()
        _lyrics.value = LyricsUi.Loading
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepo.getLyrics(song, title, artist)
            _lyrics.value = if (result != null) LyricsUi.Ready(result) else LyricsUi.NotFound
        }
    }

    fun retryLyrics() {
        _player.value.song?.let { loadLyrics(it) }
    }

    fun searchLyricsManually(title: String, artist: String) {
        _player.value.song?.let { loadLyrics(it, title, artist) }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(MusicRepository.albumArtUri(albumId))
                .build()
        )
        .build()
}
