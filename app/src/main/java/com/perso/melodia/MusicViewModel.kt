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
import com.perso.melodia.data.FAVORITES_ID
import com.perso.melodia.data.Folders
import com.perso.melodia.data.Lyrics
import com.perso.melodia.data.LyricsRepository
import com.perso.melodia.data.MusicRepository
import com.perso.melodia.data.Playlist
import com.perso.melodia.data.PlaylistStore
import com.perso.melodia.data.Settings
import com.perso.melodia.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val settings = Settings(app)
    private val store = PlaylistStore(app)

    // ---------- Bibliothèque ----------

    /** Tous les fichiers audio du téléphone */
    private val _all = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _all

    private val _selectedFolders = MutableStateFlow(settings.folders)
    /** Dossiers choisis (null = pas encore configuré) */
    val selectedFolders: StateFlow<Set<String>?> = _selectedFolders

    /** Seulement les titres des dossiers choisis */
    val songs: StateFlow<List<Song>> = combine(_all, _selectedFolders) { all, sel ->
        if (sel == null) all else all.filter { Folders.matches(it.folder, sel) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val songIndex: StateFlow<Map<Long, Song>> = _all
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val playlists: StateFlow<List<Playlist>> = store.items

    val favoriteIds: StateFlow<Set<Long>> = store.items
        .map { list -> list.firstOrNull { it.id == FAVORITES_ID }?.songIds?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ---------- Lecteur ----------

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _lyrics = MutableStateFlow<LyricsUi>(LyricsUi.Loading)
    val lyrics: StateFlow<LyricsUi> = _lyrics
    private var lyricsJob: Job? = null
    private var lyricsForId: Long? = null

    private var byId: Map<Long, Song> = emptyMap()
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
            byId = list.associateBy { it.id }
            _all.value = list
            _loading.value = false
            refresh()
        }
    }

    fun setFolders(folders: Set<String>) {
        settings.folders = folders
        _selectedFolders.value = folders
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: pending.add(action)
    }

    private fun refresh() {
        val c = controller ?: return
        val song = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { byId[it] }
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

    /** Lire juste après le titre en cours */
    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(song.toMediaItem()); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    /** Ajouter à la fin de la file d'attente */
    fun addToQueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(song.toMediaItem()); c.prepare(); c.play()
        } else {
            c.addMediaItem(song.toMediaItem())
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

    // ---------- Playlists ----------

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): Long =
        store.create(name, songs.map { it.id })

    fun addToPlaylist(playlistId: Long, songs: List<Song>) = store.add(playlistId, songs.map { it.id })
    fun removeFromPlaylist(playlistId: Long, song: Song) = store.remove(playlistId, song.id)
    fun renamePlaylist(playlistId: Long, name: String) = store.rename(playlistId, name)
    fun deletePlaylist(playlistId: Long) = store.delete(playlistId)
    fun toggleFavorite(song: Song) = store.toggleFavorite(song.id)

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
