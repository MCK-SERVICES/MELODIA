package com.perso.scanix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Ink = Color(0xFF07090E)
val Panel = Color(0xFF10141C)
val Panel2 = Color(0xFF1A2030)
val Mint = Color(0xFF22E3A5)
val Azure = Color(0xFF1E7BFF)
val Danger = Color(0xFFFF5470)
val Dim = Color(0xFF8C95A8)
val Accent = Brush.linearGradient(listOf(Mint, Azure))

@Composable
fun ScanixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Ink,
            secondary = Azure,
            background = Ink,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White,
            surfaceVariant = Panel2,
            onSurfaceVariant = Dim,
            surfaceContainer = Panel,
            surfaceContainerLow = Panel,
            surfaceContainerHigh = Panel2,
            error = Danger,
        ),
        content = content,
    )
}

fun Modifier.card(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this
        .clip(shape)
        .background(Panel)
        .border(1.dp, Color.White.copy(alpha = 0.06f), shape)

/** Bouton principal en dégradé (Scanner…) */
@Composable
fun AccentButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(height)
            .shadow(20.dp, shape, ambientColor = Mint, spotColor = Azure)
            .clip(shape)
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** Bouton rond secondaire */
@Composable
fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 58.dp, tint: Color = Color.White) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Panel2)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

/** Action avec icône + libellé (barre du bas d'un document) */
@Composable
fun ToolAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (tint == Color.White) Dim else tint, fontSize = 11.sp, maxLines = 1)
    }
}

fun friendlyDate(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "Aujourd'hui " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))
    else SimpleDateFormat("dd MMM yyyy", Locale.FRANCE).format(Date(ms))
}
