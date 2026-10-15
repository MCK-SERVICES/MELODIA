package com.perso.melodia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perso.melodia.MusicViewModel
import com.perso.melodia.PlayerUi
import com.perso.melodia.data.Folders
import com.perso.melodia.data.Playlist
import com.perso.melodia.data.Song

data class SongGroup(
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val isAlbum: Boolean,
)

private sealed interface Detail {
    data class Group(val group: SongGroup) : Detail
    data class PlaylistView(val id: Long) : Detail
}

private data class SongMenu(val song: Song, val playlistId: Long?)

private val TABS = listOf(
    "Titres" to Icons.Rounded.GraphicEq,
    "Albums" to Icons.Rounded.Album,
    "Artistes" to Icons.Rounded.Person,
    "Playlists" to Icons.Rounded.LibraryMusic,
)

// ───────────────────────── Écran principal ─────────────────────────

@Composable
fun MainScreen(vm: MusicViewModel) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ui by vm.player.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val index by vm.songIndex.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var menu by remember { mutableStateOf<SongMenu?>(null) }
    var addSongs by remember { mutableStateOf<List<Song>?>(null) }
    var creating by remember { mutableStateOf(false) }

    // Le dernier BackHandler actif est prioritaire
    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = searching) { searching = false; query = "" }
    BackHandler(enabled = showFolders) { showFolders = false }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val play: (List<Song>, Int, Boolean) -> Unit = { list, i, shuffle -> vm.playSongs(list, i, shuffle) }
    val actions = RowActions(
        currentId = ui.song?.id,
        isPlaying = ui.isPlaying,
        onMore = { song, pid -> menu = SongMenu(song, pid) },
    )

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = detail,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith fadeOut(tween(160))
                    },
                    label = "detail",
                ) { d ->
                    when (d) {
                        null -> LibraryContent(
                            songs = songs,
                            loading = loading,
                            playlists = playlists,
                            index = index,
                            tab = tab,
                            query = query,
                            onQuery = { query = it },
                            searching = searching,
                            onSearching = { searching = it; if (!it) query = "" },
                            onRefresh = vm::loadLibrary,
                            onFolders = { showFolders = true },
                            onPlay = play,
                            onOpen = { detail = it },
                            onNewPlaylist = { creating = true },
                            actions = actions,
                        )

                        is Detail.Group -> GroupDetail(d.group, actions, onBack = { detail = null }, onPlay = play)

                        is Detail.PlaylistView -> {
                            val p = playlists.firstOrNull { it.id == d.id }
                            if (p == null) {
                                LaunchedEffect(Unit) { detail = null }
                            } else {
                                PlaylistDetail(
                                    playlist = p,
                                    songs = p.songIds.mapNotNull { index[it] },
                                    actions = actions,
                                    onBack = { detail = null },
                                    onPlay = play,
                                    onRename = { vm.renamePlaylist(p.id, it) },
                                    onDelete = { vm.deletePlaylist(p.id); detail = null },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = ui.song != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                MiniPlayer(vm, ui, onOpen = { showPlayer = true })
            }
            NavBar(tab) {
                tab = it; detail = null; searching = false; query = ""
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        AnimatedVisibility(
            visible = showFolders,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 },
        ) {
            FolderScreen(vm, firstRun = false, onDone = { showFolders = false })
        }

        AnimatedVisibility(
            visible = showPlayer && ui.song != null,
            enter = slideInVertically(tween(380)) { it } + fadeIn(),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(),
        ) {
            PlayerScreen(
                vm,
                onClose = { showPlayer = false },
                onAddToPlaylist = { addSongs = listOf(it) },
            )
        }
    }

    menu?.let { m ->
        SongMenuSheet(
            song = m.song,
            inPlaylist = m.playlistId,
            isFavorite = m.song.id in favorites,
            onDismiss = { menu = null },
            onPlayNext = { vm.playNext(m.song) },
            onQueue = { vm.addToQueue(m.song) },
            onAddToPlaylist = { addSongs = listOf(m.song) },
            onToggleFavorite = { vm.toggleFavorite(m.song) },
            onRemove = { pid -> vm.removeFromPlaylist(pid, m.song) },
        )
    }

    addSongs?.let { list ->
        AddToPlaylistSheet(
            playlists = playlists,
            onDismiss = { addSongs = null },
            onPick = { pid -> vm.addToPlaylist(pid, list) },
            onCreate = { name -> vm.createPlaylist(name, list) },
        )
    }

    if (creating) {
        NameDialog(
            title = "Nouvelle playlist",
            initial = "",
            confirm = "Créer",
            onDismiss = { creating = false },
            onConfirm = { name ->
                val id = vm.createPlaylist(name)
                creating = false
                detail = Detail.PlaylistView(id)
            },
        )
    }
}

private class RowActions(
    val currentId: Long?,
    val isPlaying: Boolean,
    val onMore: (Song, Long?) -> Unit,
)

// ───────────────────────── Bibliothèque ─────────────────────────

@Composable
private fun LibraryContent(
    songs: List<Song>,
    loading: Boolean,
    playlists: List<Playlist>,
    index: Map<Long, Song>,
    tab: Int,
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onFolders: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onOpen: (Detail) -> Unit,
    onNewPlaylist: () -> Unit,
    actions: RowActions,
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
        // ── En-tête
        if (searching) {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Titre, artiste ou album", color = TextDim) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Cyan) },
                trailingIcon = {
                    IconButton({ onSearching(false) }) { Icon(Icons.Rounded.Close, "Fermer la recherche") }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassFill,
                    unfocusedContainerColor = GlassFill,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Cyan,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .border(1.dp, NeonH, RoundedCornerShape(18.dp)),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    TechLabel("// ${TABS[tab].first}")
                    GradientText("Mélodia", 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                }
                GlassIcon(Icons.Rounded.Search, "Rechercher", { onSearching(true) })
                Spacer(Modifier.width(8.dp))
                GlassIcon(Icons.Rounded.Folder, "Choisir les dossiers", onFolders)
            }
        }

        // ── Contenu
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }

            searching -> SongList(filtered, actions, onPlay, header = false, onRefresh = onRefresh)

            else -> AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> if (songs.isEmpty()) EmptyLibrary(onFolders, onRefresh)
                    else SongList(songs, actions, onPlay, header = true, onRefresh = onRefresh)

                    1 -> if (songs.isEmpty()) EmptyLibrary(onFolders, onRefresh) else AlbumGrid(albums) { onOpen(Detail.Group(it)) }
                    2 -> if (songs.isEmpty()) EmptyLibrary(onFolders, onRefresh) else ArtistList(artists) { onOpen(Detail.Group(it)) }
                    else -> PlaylistGrid(playlists, index, onNew = onNewPlaylist) { onOpen(Detail.PlaylistView(it.id)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onFolders: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TechLabel("// aucun signal")
        Spacer(Modifier.height(8.dp))
        Text(
            "Aucune musique dans les dossiers choisis.",
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        NeonButton("Choisir mes dossiers", onFolders, Modifier.fillMaxWidth(0.8f))
        TextButton(onRefresh) { Text("Actualiser", color = TextDim) }
    }
}

@Composable
private fun SongList(
    list: List<Song>,
    actions: RowActions,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    header: Boolean,
    onRefresh: () -> Unit,
    playlistId: Long? = null,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (header) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${list.size} titres", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Actualiser", color = Cyan, fontSize = 12.sp, fontFamily = Mono,
                            modifier = Modifier.clickable(onClick = onRefresh).padding(vertical = 4.dp),
                        )
                    }
                    PlayShuffleButtons(
                        onPlay = { onPlay(list, 0, false) },
                        onShuffle = { onPlay(list, 0, true) },
                    )
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song = song,
                isCurrent = song.id == actions.currentId,
                isPlaying = actions.isPlaying,
                onClick = { onPlay(list, i, false) },
                onMore = { actions.onMore(song, playlistId) },
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<SongGroup>, onOpen: (SongGroup) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums) { album ->
            Column(
                Modifier
                    .padding(6.dp)
                    .glass(RoundedCornerShape(22.dp))
                    .clickable { onOpen(album) }
                    .padding(10.dp)
            ) {
                Artwork(album.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp, size = 400)
                Spacer(Modifier.height(10.dp))
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artists) { artist ->
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(62.dp).clip(CircleShape).background(NeonH).padding(2.dp),
                ) {
                    Artwork(artist.songs.first(), Modifier.fillMaxSize(), corner = 31.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        artist.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(artist.subtitle, color = TextDim, fontSize = 13.sp, fontFamily = Mono)
                }
            }
        }
    }
}

// ───────────────────────── Playlists ─────────────────────────

@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    index: Map<Long, Song>,
    onNew: () -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier
                    .padding(6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, NeonH, RoundedCornerShape(22.dp))
                    .background(GlassFill)
                    .clickable(onClick = onNew)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Neon),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = Void) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Nouvelle playlist", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Crée ta sélection", color = TextDim, fontSize = 13.sp)
                }
            }
        }
        items(playlists, key = { it.id }) { p ->
            val songs = remember(p, index) { p.songIds.mapNotNull { index[it] } }
            Column(
                Modifier
                    .padding(6.dp)
                    .glass(RoundedCornerShape(22.dp))
                    .clickable { onOpen(p) }
                    .padding(10.dp)
            ) {
                PlaylistCover(p, songs, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(10.dp))
                Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${songs.size} titre${if (songs.size > 1) "s" else ""}", color = TextDim, fontSize = 13.sp, fontFamily = Mono)
            }
        }
    }
}

@Composable
private fun PlaylistCover(p: Playlist, songs: List<Song>, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    when {
        p.isFavorites -> Box(modifier.clip(shape).background(Neon), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.4f))
        }

        songs.size >= 4 -> Column(modifier.clip(shape)) {
            for (r in 0..1) Row(Modifier.weight(1f)) {
                for (c in 0..1) Artwork(songs[r * 2 + c], Modifier.weight(1f).fillMaxHeight(), corner = 0.dp, size = 200)
            }
        }

        songs.isNotEmpty() -> Artwork(songs.first(), modifier, corner = 16.dp, size = 400)

        else -> Box(
            modifier.clip(shape).background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.6f), Deep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.38f))
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    songs: List<Song>,
    actions: RowActions,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val totalMin = songs.sumOf { it.durationMs } / 60000

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
                Spacer(Modifier.weight(1f))
                if (!playlist.isFavorites) {
                    GlassIcon(Icons.Rounded.Edit, "Renommer", { renaming = true })
                    Spacer(Modifier.width(8.dp))
                    GlassIcon(Icons.Rounded.Delete, "Supprimer", { deleting = true }, tint = Magenta)
                }
            }
        }
        item {
            DetailHeader(
                cover = { PlaylistCover(playlist, songs, Modifier.size(210.dp)) },
                label = "// playlist",
                title = playlist.name,
                subtitle = "${songs.size} titres · $totalMin min",
                canPlay = songs.isNotEmpty(),
                onPlay = { onPlay(songs, 0, false) },
                onShuffle = { onPlay(songs, 0, true) },
            )
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    if (playlist.isFavorites) "Touche ♥ dans le lecteur pour ajouter un titre ici."
                    else "Playlist vide. Touche ⋮ sur un titre puis « Ajouter à une playlist ».",
                    color = TextDim, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song = song,
                isCurrent = song.id == actions.currentId,
                isPlaying = actions.isPlaying,
                onClick = { onPlay(songs, i, false) },
                onMore = { actions.onMore(song, playlist.id) },
            )
        }
    }

    if (renaming) {
        NameDialog("Renommer", playlist.name, "Valider", onDismiss = { renaming = false }) {
            onRename(it); renaming = false
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Supprimer « ${playlist.name} » ?") },
            text = { Text("Les fichiers musicaux ne sont pas supprimés, seulement la playlist.") },
            confirmButton = {
                TextButton({ deleting = false; onDelete() }) { Text("Supprimer", color = Magenta) }
            },
            dismissButton = { TextButton({ deleting = false }) { Text("Annuler", color = TextDim) } },
            containerColor = Deep,
        )
    }
}

@Composable
private fun GroupDetail(
    group: SongGroup,
    actions: RowActions,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(12.dp)) { GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack) }
        }
        item {
            DetailHeader(
                cover = {
                    if (group.isAlbum) {
                        Artwork(group.songs.first(), Modifier.size(210.dp), corner = 22.dp, size = 600)
                    } else {
                        Box(Modifier.size(200.dp).clip(CircleShape).background(NeonH).padding(3.dp)) {
                            Artwork(group.songs.first(), Modifier.fillMaxSize(), corner = 100.dp, size = 600)
                        }
                    }
                },
                label = if (group.isAlbum) "// album" else "// artiste",
                title = group.title,
                subtitle = if (group.isAlbum) "${group.subtitle} · ${group.songs.size} titres" else group.subtitle,
                canPlay = true,
                onPlay = { onPlay(group.songs, 0, false) },
                onShuffle = { onPlay(group.songs, 0, true) },
            )
        }
        itemsIndexed(group.songs, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song = song,
                isCurrent = song.id == actions.currentId,
                isPlaying = actions.isPlaying,
                onClick = { onPlay(group.songs, i, false) },
                onMore = { actions.onMore(song, null) },
            )
        }
    }
}

@Composable
private fun DetailHeader(
    cover: @Composable () -> Unit,
    label: String,
    title: String,
    subtitle: String,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        cover()
        Spacer(Modifier.height(18.dp))
        TechLabel(label)
        Text(
            title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(subtitle, color = TextDim, fontFamily = Mono, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (canPlay) PlayShuffleButtons(onPlay = onPlay, onShuffle = onShuffle)
        Spacer(Modifier.height(10.dp))
    }
}

// ───────────────────────── Barres du bas ─────────────────────────

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), fill = Deep.copy(alpha = 0.75f))
            .padding(vertical = 6.dp),
    ) {
        TABS.forEachIndexed { i, (label, icon) ->
            NavItem(label, icon, selected == i, Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val labelColor by animateColorAsState(if (selected) Color.White else TextDim, label = "nav")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) Color.White else TextDim,
            modifier = Modifier.size(24.dp).then(if (selected) Modifier.neonTint() else Modifier),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = labelColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(width = if (selected) 16.dp else 0.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NeonH)
        )
    }
}

@Composable
private fun MiniPlayer(vm: MusicViewModel, ui: PlayerUi, onOpen: () -> Unit) {
    val song = ui.song ?: return
    val position by vm.position.collectAsStateWithLifecycle()
    val progress = if (ui.durationMs > 0) (position.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .padding(start = 14.dp, end = 14.dp, top = 6.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = Deep.copy(alpha = 0.85f))
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(46.dp), corner = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Equalizer(ui.isPlaying, Modifier.size(10.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(song.artist, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            NeonCircleButton(
                if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (ui.isPlaying) "Pause" else "Lecture",
                vm::togglePlay,
                size = 42.dp,
            )
            IconButton(vm::next) { Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White) }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NeonH))
        }
    }
}

// ───────────────────────── Dossiers ─────────────────────────

@Composable
fun FolderScreen(vm: MusicViewModel, firstRun: Boolean, onDone: () -> Unit) {
    val all by vm.allSongs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val saved by vm.selectedFolders.collectAsStateWithLifecycle()
    val folders = remember(all) { Folders.build(all) }
    var selected by remember(saved) { mutableStateOf(saved ?: emptySet()) }
    val count = remember(selected, all) { all.count { Folders.matches(it.folder, selected) } }

    fun coveredByParent(path: String) = selected.any { s -> s != path && path.startsWith("$s/") }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!firstRun) {
                    GlassIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onDone)
                    Spacer(Modifier.width(12.dp))
                }
                Spacer(Modifier.weight(1f))
                GlassIcon(Icons.Rounded.Refresh, "Actualiser", vm::loadLibrary)
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                TechLabel("// sources audio")
                GradientText("Tes dossiers", 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Coche les dossiers qui contiennent ta musique. Les sous-dossiers sont inclus. " +
                        "Tout le reste (notes vocales, sonneries…) sera ignoré.",
                    color = TextDim, fontSize = 14.sp,
                )
            }

            Box(Modifier.weight(1f)) {
                when {
                    loading -> CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.Center))
                    folders.isEmpty() -> Text(
                        "Aucun fichier audio trouvé sur le téléphone.",
                        color = TextDim, modifier = Modifier.align(Alignment.Center),
                    )

                    else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        items(folders, key = { it.path }) { f ->
                            val covered = coveredByParent(f.path)
                            val checked = covered || f.path in selected
                            FolderRow(
                                name = f.name,
                                label = f.label,
                                count = f.count,
                                depth = f.depth,
                                checked = checked,
                                enabled = !covered,
                            ) {
                                selected = if (f.path in selected) {
                                    selected - f.path
                                } else {
                                    selected.filterNot { it.startsWith("${f.path}/") }.toSet() + f.path
                                }
                            }
                        }
                    }
                }
            }

            NeonButton(
                text = if (selected.isEmpty()) "Choisis au moins un dossier" else "Valider · $count titres",
                onClick = { vm.setFolders(selected); onDone() },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    label: String,
    count: Int,
    depth: Int,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val bg by animateColorAsState(if (checked) Cyan.copy(alpha = 0.08f) else Color.Transparent, label = "folder")
    Row(
        Modifier
            .padding(start = (depth * 18).dp, top = 3.dp, bottom = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Cyan else TextDim,
            modifier = Modifier.size(24.dp).then(if (checked && enabled) Modifier.neonTint() else Modifier),
        )
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.Folder, null, tint = if (checked) Color.White else TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name, color = if (enabled) Color.White else TextDim, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (label != name) {
                Text(label, color = TextDim, fontSize = 11.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("$count", color = if (checked) Cyan else TextDim, fontFamily = Mono, fontSize = 13.sp)
    }
}

// ───────────────────────── Menus ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenuSheet(
    song: Song,
    inPlaylist: Long?,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Deep) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(54.dp), corner = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        MenuItem(Icons.Rounded.QueuePlayNext, "Lire ensuite") { onPlayNext(); onDismiss() }
        MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Ajouter à la file d'attente") { onQueue(); onDismiss() }
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist") { onDismiss(); onAddToPlaylist() }
        MenuItem(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
        ) { onToggleFavorite(); onDismiss() }
        if (inPlaylist != null) {
            MenuItem(Icons.Rounded.Delete, "Retirer de cette playlist", Magenta) { onRemove(inPlaylist); onDismiss() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Deep) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            TechLabel("// ajouter à")
            Text("Choisis une playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        MenuItem(Icons.Rounded.Add, "Nouvelle playlist", Cyan) { naming = true }
        playlists.forEach { p ->
            MenuItem(
                if (p.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                "${p.name}  ·  ${p.songIds.size}",
            ) { onPick(p.id); onDismiss() }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (naming) {
        NameDialog("Nouvelle playlist", "", "Créer", onDismiss = { naming = false }) { name ->
            naming = false
            onCreate(name)
            onDismiss()
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nom de la playlist") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    cursorColor = Cyan,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirm, color = if (name.isNotBlank()) Cyan else TextDim) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler", color = TextDim) } },
        containerColor = Deep,
    )
}
