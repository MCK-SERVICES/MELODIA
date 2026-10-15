package com.perso.melodia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.perso.melodia.LyricsUi
import com.perso.melodia.MusicViewModel
import com.perso.melodia.data.ArtworkLoader
import com.perso.melodia.data.LyricLine
import com.perso.melodia.data.Lyrics
import com.perso.melodia.data.Song
import com.perso.melodia.data.UNKNOWN_ARTIST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val ui by vm.player.collectAsStateWithLifecycle()
    val song = ui.song ?: return
    val position by vm.position.collectAsStateWithLifecycle()
    val lyricsUi by vm.lyrics.collectAsStateWithLifecycle()

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tint by produceState(fallbackColor(song), song.id) {
        value = withContext(Dispatchers.IO) { ArtworkLoader.dominantColor(context, song) }
            ?.let { Color(it) } ?: fallbackColor(song)
    }
    val animatedTint by animateColorAsState(tint, tween(600), label = "tint")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(animatedTint, Bg)))
            // empêche les appuis de traverser vers la bibliothèque
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Barre du haut
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Réduire", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EN LECTURE", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.5.sp)
                Text(
                    song.album, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(48.dp))
        }

        // Pochette ou paroles
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showLyrics) {
                LyricsView(
                    state = lyricsUi,
                    position = position,
                    onSeek = vm::seekTo,
                    onRetry = vm::retryLyrics,
                    onManual = { showSearchDialog = true },
                )
            } else {
                Artwork(song, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp, size = 800)
            }
        }

        Text(
            song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(song.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.height(8.dp))
        SeekBar(position, ui.durationMs, vm::seekTo)

        // Commandes
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(vm::toggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle, "Aléatoire",
                    tint = if (ui.shuffle) Accent else Color.White.copy(alpha = 0.8f),
                )
            }
            IconButton(vm::previous, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Box(
                Modifier.size(70.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Lecture",
                    tint = Color.Black, modifier = Modifier.size(40.dp),
                )
            }
            IconButton(vm::next, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(vm::cycleRepeat) {
                Icon(
                    if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Répéter",
                    tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) Accent else Color.White.copy(alpha = 0.8f),
                )
            }
        }

        // Bouton Paroles
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("Paroles", selected = showLyrics) { showLyrics = !showLyrics }
            if (showLyrics) {
                Spacer(Modifier.width(4.dp))
                IconButton({ showSearchDialog = true }) {
                    Icon(Icons.Rounded.Edit, "Corriger la recherche des paroles", tint = TextDim)
                }
            }
        }
    }

    if (showSearchDialog) {
        ManualLyricsDialog(
            song = song,
            onDismiss = { showSearchDialog = false },
            onSearch = { title, artist -> vm.searchLyricsManually(title, artist) },
        )
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = duration.coerceAtLeast(1)
    val value = dragging ?: (position.toFloat() / dur).coerceIn(0f, 1f)

    Slider(
        value = value,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * dur).toLong()) }
            dragging = null
        },
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime((value * dur).toLong()), color = TextDim, fontSize = 12.sp)
        Text(formatTime(duration), color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun LyricsView(
    state: LyricsUi,
    position: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
) {
    when (state) {
        LyricsUi.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text("Recherche des paroles…", color = TextDim)
        }

        LyricsUi.NotFound -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Paroles introuvables", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Vérifie ta connexion internet,\nou corrige le titre et l'artiste.",
                color = TextDim, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onRetry) { Text("Réessayer", color = Color.White) }
                Button(onManual, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Corriger", color = Color.Black)
                }
            }
        }

        is LyricsUi.Ready -> when (val l = state.lyrics) {
            is Lyrics.Synced -> SyncedLyrics(l.lines, position, onSeek)
            is Lyrics.Plain -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("Paroles non synchronisées", color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(l.text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, position: Long, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    // petite avance pour que la ligne s'allume pile au bon moment
    val current = lines.indexOfLast { it.timeMs <= position + 250 }

    LaunchedEffect(current) {
        if (current >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((current - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
        itemsIndexed(lines) { i, line ->
            val color by animateColorAsState(
                when {
                    i == current -> Color.White
                    i < current -> Color.White.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.6f)
                },
                label = "line",
            )
            Text(
                line.text.ifBlank { "♪" },
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 8.dp),
            )
        }
        item { Spacer(Modifier.height(200.dp)) }
    }
}

@Composable
private fun ManualLyricsDialog(song: Song, onDismiss: () -> Unit, onSearch: (String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(if (song.artist == UNKNOWN_ARTIST) "" else song.artist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chercher les paroles") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(artist, { artist = it }, label = { Text("Artiste") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                onSearch(title.trim(), artist.trim())
                onDismiss()
            }) { Text("Chercher", color = Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler", color = TextDim) } },
        containerColor = Surface2,
    )
}
