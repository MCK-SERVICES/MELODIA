package com.perso.melodia.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perso.melodia.data.ArtworkLoader
import com.perso.melodia.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

val Accent = Color(0xFFFF7A45)
val Bg = Color(0xFF0E0E10)
val Surface1 = Color(0xFF18181C)
val Surface2 = Color(0xFF26262B)
val TextDim = Color(0xFFA7A7AE)

@Composable
fun MelodiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            secondary = Accent,
            background = Bg,
            onBackground = Color.White,
            surface = Surface1,
            onSurface = Color.White,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextDim,
        ),
        content = content,
    )
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun fallbackColor(song: Song): Color =
    Color.hsv((abs(song.albumId.hashCode()) % 360).toFloat(), 0.45f, 0.38f)

/** Pochette d'album, ou une note de musique si le fichier n'en a pas. */
@Composable
fun Artwork(song: Song, modifier: Modifier = Modifier, corner: Dp = 6.dp, size: Int = 300) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, song.id, size) {
        value = withContext(Dispatchers.IO) {
            ArtworkLoader.load(context, song, size)?.asImageBitmap()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null, tint = TextDim,
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Boutons « Aléatoire » + grand bouton « Lecture ». */
@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Surface2).clickable(onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Lecture aléatoire", tint = Color.White)
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Accent).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow, contentDescription = "Lecture", tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(52.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Accent else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} • ${song.album}",
                color = TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(formatTime(song.durationMs), color = TextDim, fontSize = 12.sp)
    }
}
