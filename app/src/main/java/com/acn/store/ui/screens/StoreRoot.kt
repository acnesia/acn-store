package com.acn.store.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acn.store.engine.StoreEngine
import com.acn.store.installer.Installer
import com.acn.store.model.AppStatus
import com.acn.store.model.StoreApp
import com.acn.store.ui.components.AppIcon
import com.acn.store.ui.components.AppRow
import com.acn.store.ui.components.AppTile
import com.acn.store.ui.components.PrimaryWideButton
import com.acn.store.ui.components.RatingLabel
import com.acn.store.ui.components.RowActionButton
import com.acn.store.ui.components.SectionHeader
import java.text.Normalizer

private enum class Tab(val label: String) { HOME("Accueil"), GAMES("Jeux"), APPS("Applis"), MINE("Mes applis") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreRoot(openUpdates: Boolean) {
    val context = LocalContext.current
    val apps by StoreEngine.apps.collectAsState()
    val sync by StoreEngine.sync.collectAsState()
    var tab by rememberSaveable { mutableStateOf(if (openUpdates) Tab.MINE else Tab.HOME) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        StoreEngine.messages.collect { snackbar.showSnackbar(it) }
    }

    val updates = apps.count { it.status == AppStatus.UPDATE_AVAILABLE }
    val open: (StoreApp) -> Unit = { app ->
        if (!Installer.launch(context, app.id)) Toast.makeText(context, "Impossible d'ouvrir ${app.info.name}", Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = detailsId != null) { detailsId = null }
    BackHandler(enabled = detailsId == null && searching) { searching = false }
    BackHandler(enabled = detailsId == null && !searching && tab != Tab.HOME) { tab = Tab.HOME }

    val details = detailsId?.let { id -> apps.firstOrNull { it.id == id } }
    AnimatedContent(
        targetState = details != null,
        transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
        label = "details"
    ) { showDetails ->
        if (showDetails && details != null) {
            DetailsScreen(app = details, onBack = { detailsId = null }, onOpen = { open(details) })
        } else if (searching) {
            SearchScreen(apps = apps, onClose = { searching = false }, onSelect = { detailsId = it.id }, onOpen = open)
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    StoreSearchBar(
                        updates = updates,
                        onSearch = { searching = true },
                        onSettings = { showSettings = true }
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            val selected = tab == t
                            NavigationBarItem(
                                selected = selected,
                                onClick = { tab = t },
                                label = { Text(t.label) },
                                icon = {
                                    BadgedBox(badge = { if (t == Tab.MINE && updates > 0) Badge { Text("$updates") } }) {
                                        Icon(tabIcon(t, selected), contentDescription = t.label)
                                    }
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    PullToRefreshBox(isRefreshing = sync.syncing, onRefresh = { StoreEngine.sync() }) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(apps, sync.error, onSelect = { detailsId = it.id }, onOpen = open, onUpdates = { tab = Tab.MINE })
                            Tab.GAMES -> CategoryScreen("Jeux", apps.filter { it.info.isGame }, onSelect = { detailsId = it.id }, onOpen = open)
                            Tab.APPS -> CategoryScreen("Applications", apps.filter { !it.info.isGame }, onSelect = { detailsId = it.id }, onOpen = open)
                            Tab.MINE -> MyAppsScreen(apps, onSelect = { detailsId = it.id }, onOpen = open)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
}

private fun tabIcon(t: Tab, selected: Boolean) = when (t) {
    Tab.HOME -> if (selected) Icons.Filled.Storefront else Icons.Outlined.Storefront
    Tab.GAMES -> if (selected) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports
    Tab.APPS -> if (selected) Icons.Filled.Apps else Icons.Outlined.Apps
    Tab.MINE -> if (selected) Icons.Filled.SystemUpdate else Icons.Outlined.SystemUpdate
}

@Composable
private fun StoreSearchBar(updates: Int, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                "Rechercher des applis et jeux",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            BadgedBox(badge = { if (updates > 0) Badge() }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Paramètres")
            }
        }
    }
}

@Composable
private fun OfflineBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(
    apps: List<StoreApp>,
    syncError: String?,
    onSelect: (StoreApp) -> Unit,
    onOpen: (StoreApp) -> Unit,
    onUpdates: () -> Unit
) {
    val updates = apps.filter { it.status == AppStatus.UPDATE_AVAILABLE }
    val featured = apps.firstOrNull { it.info.featured } ?: apps.firstOrNull()
    // The store itself is not an app to discover
    val selfId = LocalContext.current.packageName
    val suggestions = apps.filter { it.installed == null && it.id != selfId && it.status != AppStatus.INCOMPATIBLE }
    val ranked = apps.filter { it.id != selfId }.sortedWith(compareByDescending<StoreApp> { it.rating?.average ?: 0.0 }.thenByDescending { it.rating?.count ?: 0 }.thenBy { it.info.name })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (syncError != null) item { OfflineBanner(syncError) }
        if (updates.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onUpdates)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (updates.size == 1) "1 mise à jour disponible" else "${updates.size} mises à jour disponibles",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(updates.joinToString(", ") { it.info.name }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PrimaryWideButton("Tout", onClick = { StoreEngine.updateAll() })
                }
            }
        }
        if (featured != null) item { FeaturedCard(featured, onClick = { onSelect(featured) }, onOpen = { onOpen(featured) }) }
        if (suggestions.isNotEmpty()) {
            item { SectionHeader("Recommandé pour vous") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions, key = { it.id }) { AppTile(it, onClick = { onSelect(it) }) }
                }
            }
        }
        item { SectionHeader("Meilleures applis ACN") }
        itemsIndexed(ranked, key = { _, a -> "rank_" + a.id }) { i, app ->
            AppRow(app, rank = i + 1, onClick = { onSelect(app) }, onOpen = { onOpen(app) })
        }
        if (apps.isEmpty()) item { EmptyState("Catalogue vide", "Tire vers le bas pour actualiser.") }
    }
}

@Composable
private fun FeaturedCard(app: StoreApp, onClick: () -> Unit, onOpen: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.04f))))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text("À LA UNE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(app.info.tagline.ifBlank { app.info.name }, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(app.info.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.info.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.info.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RatingLabel(app)
                }
            }
            RowActionButton(app, onOpen)
        }
    }
}

@Composable
private fun CategoryScreen(title: String, apps: List<StoreApp>, onSelect: (StoreApp) -> Unit, onOpen: (StoreApp) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SectionHeader("$title · ${apps.size}") }
        items(apps.sortedBy { it.info.name }, key = { it.id }) { app ->
            AppRow(app, onClick = { onSelect(app) }, onOpen = { onOpen(app) })
        }
        if (apps.isEmpty()) item { EmptyState("Rien ici pour l'instant", "De nouvelles applis arrivent bientôt.") }
    }
}

@Composable
private fun MyAppsScreen(apps: List<StoreApp>, onSelect: (StoreApp) -> Unit, onOpen: (StoreApp) -> Unit) {
    val updates = apps.filter { it.status == AppStatus.UPDATE_AVAILABLE }
    val installed = apps.filter { it.status == AppStatus.INSTALLED }
    val others = apps.filter { it.installed == null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (updates.isNotEmpty()) {
            item { SectionHeader("Mises à jour disponibles (${updates.size})", "Tout mettre à jour") { StoreEngine.updateAll() } }
            items(updates, key = { "u_" + it.id }) { app -> AppRow(app, onClick = { onSelect(app) }, onOpen = { onOpen(app) }) }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Toutes vos applis sont à jour", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (installed.isNotEmpty()) {
            item { SectionHeader("Installées (${installed.size})") }
            items(installed, key = { "i_" + it.id }) { app -> AppRow(app, onClick = { onSelect(app) }, onOpen = { onOpen(app) }) }
        }
        if (others.isNotEmpty()) {
            item { SectionHeader("Pas encore installées") }
            items(others, key = { "o_" + it.id }) { app -> AppRow(app, onClick = { onSelect(app) }, onOpen = { onOpen(app) }) }
        }
    }
}

private fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
private fun SearchScreen(apps: List<StoreApp>, onClose: () -> Unit, onSelect: (StoreApp) -> Unit, onOpen: (StoreApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = normalize(query.trim())
    val results = if (q.isEmpty()) apps else apps.filter {
        normalize("${it.info.name} ${it.info.category} ${it.info.tagline} ${it.info.description}").contains(q)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                TextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    placeholder = { Text("Rechercher des applis et jeux") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Effacer") }
                }
            }
            LazyColumn(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                items(results, key = { it.id }) { app -> AppRow(app, onClick = { onSelect(app) }, onOpen = { onOpen(app) }) }
                if (results.isEmpty()) item { EmptyState("Aucun résultat", "Essaie un autre mot-clé.") }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
