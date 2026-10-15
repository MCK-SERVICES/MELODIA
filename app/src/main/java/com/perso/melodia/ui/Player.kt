@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.perso.melodia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun PlayerScreen(vm: MusicViewModel, onClose: () -> Unit, onAddToPlaylist: (Song) -> Unit) {
    val ui by vm.player.collectAsStateWithLifecycle()
    val song = ui.song ?: return
    val position by vm.position.collectAsStateWithLifecycle()
    val lyricsUi by vm.lyrics.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val isFav = song.id in favorites

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tint by produceState(fallbackColor(song), song.id) {
        value = withContext(Dispatchers.IO) { ArtworkLoader.dominantColor(context, song) }
            ?.let { Color(it) } ?: fallbackColor(song)
    }
    val animatedTint by animateColorAsState(tint, tween(700), label = "tint")

    Box(
        Modifier
            .fillMaxSize()
            // empêche les appuis de traverser vers la bibliothèque
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        AuroraBackground(tint = animatedTint)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Void.copy(alpha = 0.85f))))
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // ── Barre du haut
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(Icons.Rounded.KeyboardArrowDown, "Réduire", onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TechLabel("// en lecture", color = Color.White.copy(alpha = 0.7f))
                    Text(
                        song.album, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist", { onAddToPlaylist(song) })
            }

            // ── Pochette ou paroles
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                    label = "lyrics",
                ) { lyrics ->
                    if (lyrics) {
                        LyricsView(
                            state = lyricsUi,
                            position = position,
                            onSeek = vm::seekTo,
                            onRetry = vm::retryLyrics,
                            onManual = { showSearchDialog = true },
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            SwipeArtwork(
                                song = song,
                                playing = ui.isPlaying,
                                glow = animatedTint,
                                onClose = onClose,
                                onNext = vm::next,
                                onPrevious = vm::previous,
                            )
                        }
                    }
                }
            }

            // ── Titre + favori
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                        label = "title",
                    ) { s ->
                        Column {
                            Text(
                                s.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val heartScale by animateFloatAsState(
                    if (isFav) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "heart",
                )
                IconButton({ vm.toggleFavorite(song) }) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFav) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (isFav) Magenta else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { scaleX = heartScale; scaleY = heartScale }
                            .then(if (isFav) Modifier.neonTint(Brush.linearGradient(listOf(Magenta, Violet))) else Modifier),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            NeonSeekBar(position, ui.durationMs, vm::seekTo)
            Spacer(Modifier.height(6.dp))

            // ── Commandes
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(vm::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle, "Aléatoire",
                        tint = if (ui.shuffle) Cyan else Color.White.copy(alpha = 0.7f),
                    )
                }
                IconButton(vm::previous, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                NeonCircleButton(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Lecture",
                    vm::togglePlay,
                    size = 74.dp,
                )
                IconButton(vm::next, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                IconButton(vm::cycleRepeat) {
                    Icon(
                        if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Répéter",
                        tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) Cyan else Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            // ── Paroles
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Equalizer(ui.isPlaying, Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Pill("Paroles", selected = showLyrics) { showLyrics = !showLyrics }
                if (showLyrics) {
                    Spacer(Modifier.width(8.dp))
                    GlassIcon(Icons.Rounded.Edit, "Corriger la recherche des paroles", { showSearchDialog = true }, size = 38.dp)
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

/** Pochette avec anneau néon tournant ; glisser à gauche/droite = suivant/précédent, vers le bas = fermer. */
@Composable
private fun SwipeArtwork(
    song: Song,
    playing: Boolean,
    glow: Color,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val close by rememberUpdatedState(onClose)
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrevious)

    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "angle")
    val scale by animateFloatAsState(if (playing) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "scale")
    val ringAlpha by animateFloatAsState(if (playing) 1f else 0.35f, tween(500), label = "ringAlpha")

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val shape = RoundedCornerShape(30.dp)
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    translationX = dx.value * 0.6f
                    translationY = dy.value.coerceAtLeast(0f) * 0.5f
                    rotationZ = dx.value / 60f
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val x = dx.value
                            val y = dy.value
                            when {
                                y > 220f && abs(y) > abs(x) -> close()
                                x < -160f -> next()
                                x > 160f -> prev()
                            }
                            scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.7f)) }
                            scope.launch { dy.animateTo(0f, spring(dampingRatio = 0.7f)) }
                        },
                        onDragCancel = {
                            scope.launch { dx.animateTo(0f) }
                            scope.launch { dy.animateTo(0f) }
                        },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { dx.snapTo(dx.value + amount.x) }
                        scope.launch { dy.snapTo(dy.value + amount.y) }
                    }
                }
                .shadow(48.dp, shape, ambientColor = glow, spotColor = glow)
                .clip(shape)
                .drawBehind {
                    rotate(angle) {
                        drawCircle(
                            Brush.sweepGradient(listOf(Cyan, Violet, Magenta, glow, Cyan)),
                            radius = size.maxDimension,
                            alpha = ringAlpha,
                        )
                    }
                }
                .padding(3.dp)
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(200)) },
                label = "art",
            ) { s ->
                Artwork(s, Modifier.fillMaxSize(), corner = 27.dp, size = 900)
            }
        }
    }
}

@Composable
private fun NeonSeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = duration.coerceAtLeast(1)
    val frac = drag ?: (position.toFloat() / dur).coerceIn(0f, 1f)
    val seek by rememberUpdatedState(onSeek)
    val thumb by animateFloatAsState(if (drag != null) 9f else 6f, label = "thumb")

    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(dur) {
                detectTapGestures { o -> seek(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { seek((it * dur).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val h = 4.dp.toPx()
            val x = size.width * frac
            drawRoundRect(
                Color.White.copy(alpha = 0.12f),
                topLeft = Offset(0f, y - h / 2),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h),
            )
            drawRoundRect(
                Brush.horizontalGradient(listOf(Cyan, Violet, Magenta), startX = 0f, endX = size.width),
                topLeft = Offset(0f, y - h / 2),
                size = Size(x.coerceAtLeast(h), h),
                cornerRadius = CornerRadius(h),
            )
            val r = 16.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(Violet.copy(alpha = 0.55f), Color.Transparent), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y),
            )
            drawCircle(Color.White, radius = thumb.dp.toPx(), center = Offset(x, y))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime((frac * dur).toLong()), color = TextDim, fontSize = 12.sp, fontFamily = Mono)
        Text(formatTime(duration), color = TextDim, fontSize = 12.sp, fontFamily = Mono)
    }
}

// ───────────────────────── Paroles ─────────────────────────

@Composable
private fun LyricsView(
    state: LyricsUi,
    position: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            LyricsUi.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Cyan)
                Spacer(Modifier.height(14.dp))
                TechLabel("// recherche des paroles…")
            }

            LyricsUi.NotFound -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TechLabel("// signal perdu")
                Spacer(Modifier.height(6.dp))
                Text("Paroles introuvables", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Vérifie ta connexion internet,\nou corrige le titre et l'artiste.",
                    color = TextDim, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Réessayer", selected = false, onClick = onRetry)
                    Pill("Corriger", selected = true, onClick = onManual)
                }
            }

            is LyricsUi.Ready -> when (val l = state.lyrics) {
                is Lyrics.Synced -> SyncedLyrics(l.lines, position, onSeek)
                is Lyrics.Plain -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    TechLabel("// paroles non synchronisées")
                    Spacer(Modifier.height(10.dp))
                    Text(l.text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
                }
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
            val active = i == current
            val alpha by animateFloatAsState(
                when {
                    active -> 1f
                    i < current -> 0.35f
                    else -> 0.55f
                },
                tween(300),
                label = "line",
            )
            val scale by animateFloatAsState(if (active) 1.04f else 1f, tween(300), label = "lineScale")
            Text(
                line.text.ifBlank { "♪" },
                style = if (active) {
                    TextStyle(brush = NeonH, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                } else {
                    TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 9.dp),
            )
        }
        item { Spacer(Modifier.height(220.dp)) }
    }
}

@Composable
private fun ManualLyricsDialog(song: Song, onDismiss: () -> Unit, onSearch: (String, String) -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(if (song.artist == UNKNOWN_ARTIST) "" else song.artist) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, cursorColor = Cyan)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chercher les paroles") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true, colors = colors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(artist, { artist = it }, label = { Text("Artiste") }, singleLine = true, colors = colors)
            }
        },
        confirmButton = {
            TextButton({
                onSearch(title.trim(), artist.trim())
                onDismiss()
            }) { Text("Chercher", color = Cyan) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler", color = TextDim) } },
        containerColor = Deep,
    )
}
