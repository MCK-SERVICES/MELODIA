@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.perso.melodia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perso.melodia.data.ArtworkLoader
import com.perso.melodia.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ───────────────────────── Palette néon ─────────────────────────

val Void = Color(0xFF05060B)
val Deep = Color(0xFF0B0E19)
val Cyan = Color(0xFF00E5FF)
val Violet = Color(0xFF8B5CF6)
val Magenta = Color(0xFFFF3DCB)
val TextDim = Color(0xFF8A90A6)
val GlassFill = Color.White.copy(alpha = 0.055f)

// Alias gardés pour compatibilité
val Accent = Cyan
val Bg = Void
val Surface2 = Color(0xFF151826)

val Neon = Brush.linearGradient(listOf(Cyan, Violet, Magenta))
val NeonH = Brush.horizontalGradient(listOf(Cyan, Violet, Magenta))
val Mono = FontFamily.Monospace

@Composable
fun MelodiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            onPrimary = Void,
            secondary = Violet,
            tertiary = Magenta,
            background = Void,
            onBackground = Color.White,
            surface = Deep,
            onSurface = Color.White,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextDim,
            surfaceContainerHigh = Surface2,
            surfaceContainerLow = Deep,
            surfaceContainer = Deep,
        ),
        content = content,
    )
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun fallbackColor(song: Song): Color =
    Color.hsv((abs(song.albumId.hashCode()) % 360).toFloat(), 0.75f, 0.85f)

// ───────────────────────── Effets ─────────────────────────

/** Panneau « verre dépoli » avec un liseré lumineux. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), fill: Color = GlassFill): Modifier =
    this
        .clip(shape)
        .background(fill)
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.03f))),
            shape,
        )

/** Colore un contenu (icône, texte) avec le dégradé néon. */
fun Modifier.neonTint(brush: Brush = Neon): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            onDrawWithContent {
                drawContent()
                drawRect(brush, blendMode = BlendMode.SrcAtop)
            }
        }

/** Fond animé : aurores lumineuses qui dérivent lentement + grille de points. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, tint: Color? = null) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Void)
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = a), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(tint ?: Violet, w * (0.25f + 0.18f * cos(phase)), h * (0.12f + 0.06f * sin(phase)), w * 0.95f,
            if (tint != null) 0.45f else 0.32f)
        blob(Cyan, w * (0.9f + 0.1f * sin(phase * 2)), h * (0.45f + 0.1f * cos(phase)), w * 0.7f, 0.16f)
        blob(Magenta, w * (0.1f + 0.12f * sin(phase)), h * (0.85f + 0.05f * cos(phase * 2)), w * 0.8f, 0.14f)

        val step = 26.dp.toPx()
        val dot = Color.White.copy(alpha = 0.035f)
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                drawCircle(dot, radius = 1.dp.toPx(), center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

/** Petit égaliseur animé. */
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier.size(16.dp), brush: Brush = Neon) {
    val t = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 380, 610, 450)
    val bars = durations.mapIndexed { i, d ->
        t.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Canvas(modifier) {
        val n = bars.size
        val gap = size.width * 0.14f
        val bw = (size.width - gap * (n - 1)) / n
        bars.forEachIndexed { i, s ->
            val f = if (playing) s.value else 0.25f
            val bh = size.height * f
            drawRoundRect(
                brush,
                topLeft = Offset(i * (bw + gap), size.height - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
fun GradientText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    brush: Brush = NeonH,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
        ),
    )
}

/** Petite étiquette technique, ex. « // BIBLIOTHÈQUE ». */
@Composable
fun TechLabel(text: String, modifier: Modifier = Modifier, color: Color = TextDim) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
    )
}

// ───────────────────────── Composants ─────────────────────────

/** Pochette d'album, ou un dégradé coloré si le fichier n'en a pas. */
@Composable
fun Artwork(song: Song, modifier: Modifier = Modifier, corner: Dp = 10.dp, size: Int = 300) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, song.id, size) {
        value = withContext(Dispatchers.IO) {
            ArtworkLoader.load(context, song, size)?.asImageBitmap()
        }
    }
    val c = fallbackColor(song)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.85f), Deep))),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .then(if (selected) Modifier.clip(shape).background(NeonH) else Modifier.glass(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Void else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Bouton rond en verre. */
@Composable
fun GlassIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Color.White,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Gros bouton rond lumineux (lecture). */
@Composable
fun NeonCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .shadow(size / 2, CircleShape, ambientColor = Cyan, spotColor = Violet)
            .clip(CircleShape)
            .background(Neon)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Void, modifier = Modifier.size(size * 0.55f))
    }
}

/** Bouton large lumineux avec texte. */
@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .then(
                if (enabled) Modifier.shadow(18.dp, shape, ambientColor = Cyan, spotColor = Violet) else Modifier
            )
            .clip(shape)
            .background(if (enabled) NeonH else Brush.horizontalGradient(listOf(Surface2, Surface2)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Void else TextDim,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

/** Boutons « Aléatoire » + grand bouton « Lecture ». */
@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIcon(Icons.Rounded.Shuffle, "Lecture aléatoire", onShuffle, size = 44.dp)
        NeonCircleButton(Icons.Rounded.PlayArrow, "Lecture", onPlay, size = 54.dp)
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val bg by animateColorAsState(
        if (isCurrent) Color.White.copy(alpha = 0.06f) else Color.Transparent, label = "rowbg",
    )
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(50.dp), corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (isCurrent) {
                GradientText(song.title, 16.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    song.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                song.artist,
                color = TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isCurrent) {
            Equalizer(isPlaying)
        } else {
            Text(formatTime(song.durationMs), color = TextDim, fontSize = 12.sp, fontFamily = Mono)
        }
        IconButton(onMore) {
            Icon(Icons.Rounded.MoreVert, "Options", tint = TextDim)
        }
    }
}
