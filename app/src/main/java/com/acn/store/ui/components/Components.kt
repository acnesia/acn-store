package com.acn.store.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import com.acn.store.engine.StoreEngine
import com.acn.store.model.AppStatus
import com.acn.store.model.StoreApp
import com.acn.store.model.TaskState
import java.util.Locale

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.FRANCE, "%.1f Go", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024))
    else -> String.format(Locale.FRANCE, "%d Ko", (bytes / 1024).coerceAtLeast(1))
}

fun formatRating(avg: Double): String = String.format(Locale.FRANCE, "%.1f", avg)

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.FRANCE, "%.1f M", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.FRANCE, "%.1f k", n / 1_000.0)
    else -> n.toString()
}

private val tilePalette = listOf(
    Color(0xFF01875F), Color(0xFF1A73E8), Color(0xFFE8710A), Color(0xFF9334E6), Color(0xFFD93025), Color(0xFF12B5CB)
)

/** Real launcher icon when installed, the catalog icon otherwise, a lettered tile as last resort. */
@Composable
fun AppIcon(app: StoreApp, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.22f)
    val installedIcon = remember(app.id, app.installed?.versionCode) {
        if (app.installed != null) StoreEngine.installedLabelIcon(app.id)?.toBitmap(192, 192)?.asImageBitmap() else null
    }
    val base = modifier.size(size).clip(shape)
    when {
        installedIcon != null -> Image(installedIcon, contentDescription = null, modifier = base)
        app.info.iconUrl != null -> SubcomposeAsyncImage(
            model = app.info.iconUrl,
            contentDescription = null,
            modifier = base,
            loading = { BundledIcon(app, size) },
            error = { BundledIcon(app, size) }
        )
        else -> Box(base) { LetterTile(app, size) }
    }
}

// Copy of the catalog icon shipped in the APK assets
@Composable
private fun BundledIcon(app: StoreApp, size: Dp) {
    SubcomposeAsyncImage(
        model = "file:///android_asset/icons/${app.id}.png",
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        error = { LetterTile(app, size) }
    )
}

@Composable
private fun LetterTile(app: StoreApp, size: Dp) {
    val color = tilePalette[(app.id.hashCode() and 0x7fffffff) % tilePalette.size]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = app.info.name.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Stars(rating: Double, size: Dp = 14.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Row {
        for (i in 1..5) {
            Icon(
                imageVector = if (rating >= i - 0.25) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size)
            )
        }
    }
}

/** "4,6 ★" or "Nouveau" when nobody has rated yet. */
@Composable
fun RatingLabel(app: StoreApp) {
    val r = app.rating
    if (r != null && r.count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatRating(r.average), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
        }
    } else {
        Text("Nouveau", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun statusLine(app: StoreApp): String = when (val t = app.task) {
    is TaskState.Queued -> "En attente…"
    is TaskState.Downloading -> "${formatSize(t.bytes)} sur ${formatSize(t.total)}"
    is TaskState.Verifying -> "Vérification de l'intégrité…"
    is TaskState.Installing -> "Installation…"
    is TaskState.WaitingUser -> "Confirme l'installation"
    is TaskState.Failed -> t.message
    TaskState.Idle -> when (app.status) {
        AppStatus.UPDATE_AVAILABLE -> "Mise à jour · ${formatSize(app.info.sizeBytes)}"
        AppStatus.INSTALLED -> "Installée"
        AppStatus.INCOMPATIBLE -> "Nécessite Android plus récent"
        AppStatus.NOT_INSTALLED -> formatSize(app.info.sizeBytes)
    }
}

/** Compact action button for list rows. */
@Composable
fun RowActionButton(app: StoreApp, onOpen: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val task = app.task
    when {
        task is TaskState.Downloading || task is TaskState.Queued || task is TaskState.Verifying ||
            task is TaskState.Installing || task is TaskState.WaitingUser -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                if (task is TaskState.Downloading && task.total > 0) {
                    CircularProgressIndicator(progress = { task.fraction }, strokeWidth = 3.dp, modifier = Modifier.size(34.dp), strokeCap = StrokeCap.Round)
                } else {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(34.dp), strokeCap = StrokeCap.Round)
                }
                if (task is TaskState.Downloading || task is TaskState.Queued) {
                    IconButton(onClick = { StoreEngine.cancel(app.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, "Annuler", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        app.status == AppStatus.UPDATE_AVAILABLE -> Button(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); StoreEngine.install(app.id) },
            modifier = Modifier.heightIn(min = 44.dp)
        ) { Text("Mettre à jour") }
        app.status == AppStatus.INSTALLED -> OutlinedButton(onClick = onOpen, modifier = Modifier.heightIn(min = 44.dp)) { Text("Ouvrir") }
        app.status == AppStatus.INCOMPATIBLE -> Unit
        else -> Button(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); StoreEngine.install(app.id) },
            modifier = Modifier.heightIn(min = 44.dp)
        ) { Text(if (task is TaskState.Failed) "Réessayer" else "Installer") }
    }
}

/** List row: icon, name, category · rating · size, action button. */
@Composable
fun AppRow(app: StoreApp, rank: Int? = null, onClick: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                "$rank",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
        }
        AppIcon(app, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.info.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val failed = app.task is TaskState.Failed
            Text(
                if (app.task == TaskState.Idle) "${app.info.category} · ${statusLine(app)}" else statusLine(app),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val t = app.task
            if (t is TaskState.Downloading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth(0.9f))
            } else {
                RatingLabel(app)
            }
        }
        Spacer(Modifier.width(8.dp))
        RowActionButton(app, onOpen)
    }
}

/** Square tile for horizontal carousels. */
@Composable
fun AppTile(app: StoreApp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppIcon(app, 96.dp)
        Text(app.info.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        RatingLabel(app)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 44.dp)) { Text(action) }
        }
    }
}

val ButtonShape = RoundedCornerShape(20.dp)

@Composable
fun PrimaryWideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        modifier = modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.buttonColors()
    ) { Text(text, fontWeight = FontWeight.Medium) }
}
