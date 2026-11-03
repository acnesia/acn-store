package com.acn.store.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.acn.store.data.ReviewsApi
import com.acn.store.engine.StoreEngine
import com.acn.store.installer.Installer
import com.acn.store.model.AppStatus
import com.acn.store.model.Review
import com.acn.store.model.ReviewsPage
import com.acn.store.model.StoreApp
import com.acn.store.model.TaskState
import com.acn.store.model.isBusy
import com.acn.store.ui.components.AppIcon
import com.acn.store.ui.components.PrimaryWideButton
import com.acn.store.ui.components.Stars
import com.acn.store.ui.components.formatCount
import com.acn.store.ui.components.formatRating
import com.acn.store.ui.components.formatSize
import com.acn.store.ui.components.statusLine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

fun formatDate(raw: String): String = try {
    LocalDate.parse(raw.take(10)).format(dateFormat)
} catch (_: Exception) {
    raw
}

private val androidNames = mapOf(
    21 to "5.0", 22 to "5.1", 23 to "6.0", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1", 28 to "9",
    29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16"
)

private sealed interface ReviewsUi {
    data object Loading : ReviewsUi
    data class Error(val message: String) : ReviewsUi
    data class Loaded(val page: ReviewsPage) : ReviewsUi
}

@Composable
fun DetailsScreen(app: StoreApp, onBack: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reviews by remember(app.id) { mutableStateOf<ReviewsUi>(ReviewsUi.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var writeRating by remember { mutableIntStateOf(0) }
    var showReviewDialog by remember { mutableStateOf(false) }
    val isSelf = app.id == context.packageName

    LaunchedEffect(app.id, reload) {
        reviews = try {
            val page = ReviewsApi.reviews(context, app.id)
            StoreEngine.updateRating(app.id, page.summary)
            ReviewsUi.Loaded(page)
        } catch (e: Exception) {
            ReviewsUi.Error("Avis indisponibles pour le moment")
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            }
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { Header(app) }
                item { StatsRow(app, reviews) }
                item { ActionArea(app, isSelf, onOpen, context) }

                val shots = app.info.screenshots.orEmpty()
                if (shots.isNotEmpty()) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shots) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(220.dp)
                                        .width(124.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }

                item {
                    SectionTitle("À propos de cette appli")
                    Text(
                        app.info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(app.info.category)
                        Chip(app.info.developer)
                    }
                }

                if (app.info.changelog.isNotBlank()) {
                    item {
                        SectionTitle("Nouveautés")
                        Text(
                            "Version ${app.info.versionName} · ${formatDate(app.info.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(app.info.changelog, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                item { SecurityCard(app) }

                item {
                    SectionTitle("Notes et avis")
                    when (val r = reviews) {
                        ReviewsUi.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        is ReviewsUi.Error -> Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { reload++ }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Réessayer") }
                        }
                        is ReviewsUi.Loaded -> RatingOverview(r.page)
                    }
                }

                val loaded = reviews as? ReviewsUi.Loaded
                if (loaded != null) {
                    item {
                        RateThisApp(
                            mine = loaded.page.mine,
                            onPick = { stars -> writeRating = stars; showReviewDialog = true }
                        )
                    }
                    val others = loaded.page.reviews.filter { it.id != loaded.page.mine?.id }
                    items(others, key = { "r_" + it.id }) { ReviewItem(it) }
                    if (loaded.page.reviews.isEmpty()) {
                        item {
                            Text(
                                "Aucun avis pour l'instant. Sois le premier à donner ton avis !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                item { InfoTable(app) }
            }
        }
    }

    val loadedPage = (reviews as? ReviewsUi.Loaded)?.page
    if (showReviewDialog) {
        ReviewDialog(
            appName = app.info.name,
            initialRating = writeRating,
            existing = loadedPage?.mine,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment, author, done ->
                scope.launch {
                    try {
                        ReviewsApi.submit(context, app.id, rating, comment, author, app.installed?.versionName)
                        showReviewDialog = false
                        reload++
                        done(null)
                    } catch (e: Exception) {
                        done(e.message ?: "Envoi impossible")
                    }
                }
            },
            onDelete = {
                scope.launch {
                    runCatching { ReviewsApi.delete(context, app.id) }
                    showReviewDialog = false
                    reload++
                }
            }
        )
    }
}

@Composable
private fun Header(app: StoreApp) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 76.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(app.info.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(app.info.developer, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Fichier vérifié", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsRow(app: StoreApp, reviews: ReviewsUi) {
    val summary = (reviews as? ReviewsUi.Loaded)?.page?.summary ?: app.rating
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (summary != null && summary.count > 0) {
            Stat(top = "${formatRating(summary.average)} ★", bottom = "${formatCount(summary.count)} avis")
        } else {
            Stat(top = "—", bottom = "Aucun avis")
        }
        StatDivider()
        Stat(top = formatSize(app.info.sizeBytes), bottom = "Taille")
        StatDivider()
        Stat(top = app.info.versionName, bottom = "Version")
        StatDivider()
        Stat(top = "${androidNames[app.info.minSdk] ?: app.info.minSdk}+", bottom = "Android")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(top: String, bottom: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(bottom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun ActionArea(app: StoreApp, isSelf: Boolean, onOpen: () -> Unit, context: Context) {
    val haptic = LocalHapticFeedback.current
    val install = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); StoreEngine.install(app.id) }
    val uninstall = { context.startActivity(Installer.uninstallIntent(app.id)) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        val task = app.task
        when {
            task.isBusy -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (task is TaskState.Downloading && task.total > 0) {
                            LinearProgressIndicator(progress = { task.fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(statusLine(app), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task is TaskState.Downloading || task is TaskState.Queued) {
                        IconButton(onClick = { StoreEngine.cancel(app.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Annuler") }
                    }
                }
            }
            else -> {
                if (task is TaskState.Failed) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(task.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val canUninstall = app.installed != null && !isSelf
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canUninstall) {
                        OutlinedButton(
                            onClick = uninstall,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) { Text("Désinstaller") }
                    }
                    val mod = Modifier.weight(1f)
                    when (app.status) {
                        AppStatus.UPDATE_AVAILABLE -> PrimaryWideButton("Mettre à jour", install, mod)
                        AppStatus.INSTALLED -> if (isSelf) {
                            PrimaryWideButton("À jour", {}, mod, enabled = false)
                        } else {
                            PrimaryWideButton("Ouvrir", onOpen, mod)
                        }
                        AppStatus.INCOMPATIBLE -> PrimaryWideButton("Incompatible", {}, mod, enabled = false)
                        AppStatus.NOT_INSTALLED -> PrimaryWideButton(if (task is TaskState.Failed) "Réessayer" else "Installer", install, mod)
                    }
                }
                if (app.status == AppStatus.UPDATE_AVAILABLE && app.installed?.versionName != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Installée : ${app.installed.versionName} → disponible : ${app.info.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun SecurityCard(app: StoreApp) {
    SectionTitle("Sécurité")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SecurityLine(Icons.Outlined.Lock, "Téléchargement chiffré", "Depuis les serveurs GitHub uniquement, en HTTPS.")
        SecurityLine(Icons.Outlined.Fingerprint, "Intégrité vérifiée", "L'empreinte SHA-256 du fichier est contrôlée avant toute installation.")
        SecurityLine(Icons.Outlined.VerifiedUser, "Signature contrôlée", "Une mise à jour signée par une autre clé que ${app.info.name} est refusée.")
    }
}

@Composable
private fun SecurityLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingOverview(page: ReviewsPage) {
    val s = page.summary
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
            Text(if (s.count > 0) formatRating(s.average) else "—", fontSize = 52.sp, fontWeight = FontWeight.Normal)
            Stars(s.average, 12.dp)
            Spacer(Modifier.height(4.dp))
            Text("${s.count} avis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 5 downTo 1) {
                val n = s.histogram?.get(star.toString()) ?: 0
                val fraction = if (s.count > 0) n.toFloat() / s.count else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$star", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(14.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateThisApp(mine: Review?, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        if (mine != null) {
            Text("Votre avis", style = MaterialTheme.typography.titleMedium)
            ReviewBody(mine, padded = false)
            TextButton(onClick = { onPick(mine.rating) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Modifier votre avis") }
        } else {
            Text("Donner votre avis", style = MaterialTheme.typography.titleMedium)
            Text("Partagez votre expérience avec les autres", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (i in 1..5) {
                    IconButton(onClick = { onPick(i) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.StarOutline, "$i étoiles", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ReviewItem(review: Review) = ReviewBody(review, padded = true)

@Composable
private fun ReviewBody(review: Review, padded: Boolean) {
    Column(if (padded) Modifier.padding(horizontal = 16.dp, vertical = 10.dp) else Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(review.author.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Text(review.author, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stars(review.rating.toDouble(), 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(formatDate(review.updatedAt.ifBlank { review.createdAt }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!review.comment.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(review.comment, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val meta = listOfNotNull(review.appVersion?.let { "Version $it" }, review.deviceModel).joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun InfoTable(app: StoreApp) {
    SectionTitle("Informations sur l'appli")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoLine("Version", app.info.versionName)
        app.installed?.versionName?.let { InfoLine("Version installée", it) }
        InfoLine("Mise à jour", formatDate(app.info.updatedAt))
        InfoLine("Taille", formatSize(app.info.sizeBytes))
        InfoLine("Android requis", "${androidNames[app.info.minSdk] ?: "API ${app.info.minSdk}"} ou version ultérieure")
        InfoLine("Développeur", app.info.developer)
        InfoLine("Identifiant", app.info.id, mono = true)
        InfoLine("SHA-256", app.info.sha256.chunked(16).joinToString("\n"), mono = true)
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (mono) 12.sp else 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
