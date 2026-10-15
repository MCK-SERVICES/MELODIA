package com.perso.melodia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perso.melodia.MusicViewModel
import com.perso.melodia.PlayerUi
import com.perso.melodia.data.Song

data class SongGroup(
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val isAlbum: Boolean,
)

@Composable
fun MainScreen(vm: MusicViewModel) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ui by vm.player.collectAsStateWithLifecycle()

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var openGroup by remember { mutableStateOf<SongGroup?>(null) }

    BackHandler(enabled = openGroup != null) { openGroup = null }
    BackHandler(enabled = searching && openGroup == null) { searching = false; query = "" }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val play: (List<Song>, Int, Boolean) -> Unit = { list, i, shuffle -> vm.playSongs(list, i, shuffle) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                val group = openGroup
                if (group != null) {
                    GroupDetail(group, ui.song?.id, onBack = { openGroup = null }, onPlay = play)
                } else {
                    LibraryContent(
                        songs = songs,
                        loading = loading,
                        currentId = ui.song?.id,
                        tab = tab,
                        onTab = { tab = it },
                        query = query,
                        onQuery = { query = it },
                        searching = searching,
                        onSearching = { searching = it; if (!it) query = "" },
                        onRefresh = vm::loadLibrary,
                        onPlay = play,
                        onOpenGroup = { openGroup = it },
                    )
                }
            }
            if (ui.song != null) MiniPlayer(vm, ui, onOpen = { showPlayer = true })
            Spacer(Modifier.navigationBarsPadding())
        }

        AnimatedVisibility(
            visible = showPlayer && ui.song != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PlayerScreen(vm, onClose = { showPlayer = false })
        }
    }
}

@Composable
private fun LibraryContent(
    songs: List<Song>,
    loading: Boolean,
    currentId: Long?,
    tab: Int,
    onTab: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onOpenGroup: (SongGroup) -> Unit,
) {
    val albums = remember(songs) {
        songs.groupBy { it.albumId }.values.map { list ->
            val artists = list.map { it.artist }.distinct()
            SongGroup(
                title = list.first().album,
                subtitle = if (artists.size == 1) artists.first() else "Artistes variés",
                songs = list,
                isAlbum = true,
            )
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(songs) {
        songs.groupBy { it.artist }.map { (name, list) ->
            SongGroup(name, "${list.size} titre${if (list.size > 1) "s" else ""}", list, isAlbum = false)
        }.sortedBy { it.title.lowercase() }
    }
    val filtered = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) songs else songs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // En-tête
        if (searching) {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Titre, artiste ou album") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    IconButton({ onSearching(false) }) { Icon(Icons.Rounded.Close, "Fermer la recherche") }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ma musique", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.weight(1f),
                )
                IconButton({ onSearching(true) }) { Icon(Icons.Rounded.Search, "Rechercher", tint = Color.White) }
                IconButton(onRefresh) { Icon(Icons.Rounded.Refresh, "Actualiser", tint = Color.White) }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Titres", "Albums", "Artistes").forEachIndexed { i, label ->
                    Pill(label, selected = tab == i) { onTab(i) }
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            songs.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune musique trouvée sur ce téléphone.\nTélécharge des fichiers audio puis appuie sur ↻.",
                    color = TextDim, textAlign = TextAlign.Center,
                )
            }

            searching -> SongList(filtered, currentId, onPlay, showHeader = false)
            tab == 0 -> SongList(songs, currentId, onPlay, showHeader = true)
            tab == 1 -> AlbumGrid(albums, onOpenGroup)
            else -> ArtistList(artists, onOpenGroup)
        }
    }
}

@Composable
private fun SongList(
    list: List<Song>,
    currentId: Long?,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    showHeader: Boolean,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (showHeader) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${list.size} titres", color = TextDim, modifier = Modifier.weight(1f))
                    PlayShuffleButtons(
                        onPlay = { onPlay(list, 0, false) },
                        onShuffle = { onPlay(list, 0, true) },
                    )
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, song ->
            SongRow(song, song.id == currentId) { onPlay(list, i, false) }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<SongGroup>, onOpen: (SongGroup) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums) { album ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(album) }
                    .padding(8.dp)
            ) {
                Artwork(album.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 8.dp, size = 400)
                Spacer(Modifier.height(8.dp))
                Text(
                    album.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(album.subtitle, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<SongGroup>, onOpen: (SongGroup) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(artists) { artist ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(artist) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(artist.songs.first(), Modifier.size(60.dp), corner = 30.dp)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        artist.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(artist.subtitle, color = TextDim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GroupDetail(
    group: SongGroup,
    currentId: Long?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            IconButton(onBack, Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    group.songs.first(), Modifier.size(200.dp),
                    corner = if (group.isAlbum) 10.dp else 100.dp, size = 600,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    group.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(group.subtitle, color = TextDim)
                Spacer(Modifier.height(12.dp))
                PlayShuffleButtons(
                    onPlay = { onPlay(group.songs, 0, false) },
                    onShuffle = { onPlay(group.songs, 0, true) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(group.songs, key = { _, s -> s.id }) { i, song ->
            SongRow(song, song.id == currentId) { onPlay(group.songs, i, false) }
        }
    }
}

@Composable
private fun MiniPlayer(vm: MusicViewModel, ui: PlayerUi, onOpen: () -> Unit) {
    val song = ui.song ?: return
    val position by vm.position.collectAsStateWithLifecycle()
    val progress = if (ui.durationMs > 0) (position.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(44.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(song.artist, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(vm::togglePlay) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Lecture",
                    tint = Color.White, modifier = Modifier.size(30.dp),
                )
            }
            IconButton(vm::next) { Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White) }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
        }
    }
}
