package com.acn.store.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.acn.store.BuildConfig
import com.acn.store.data.CatalogRepository
import com.acn.store.data.Prefs
import com.acn.store.engine.StoreEngine
import com.acn.store.installer.ApkDownloader
import com.acn.store.installer.Installer
import com.acn.store.model.Review
import java.text.DateFormat
import java.util.Date

@Composable
fun ReviewDialog(
    appName: String,
    initialRating: Int,
    existing: Review?,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, comment: String, author: String, done: (String?) -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(initialRating.coerceIn(1, 5)) }
    var comment by remember { mutableStateOf(existing?.comment.orEmpty()) }
    var author by remember { mutableStateOf(existing?.author ?: Prefs.authorName(context)) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Noter $appName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "$i étoiles",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it.take(40) },
                    label = { Text("Votre nom (public)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(1000) },
                    label = { Text("Décrivez votre expérience (facultatif)") },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = { Text("${comment.length}/1000") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Les avis sont publics. Un seul avis par appareil, modifiable à tout moment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    error = null
                    Prefs.setAuthorName(context, author)
                    onSubmit(rating, comment.trim(), author.trim().ifBlank { "Utilisateur" }) { err ->
                        sending = false
                        error = err
                    }
                },
                modifier = Modifier.heightIn(min = 44.dp)
            ) { Text(if (sending) "Envoi…" else "Publier") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onDelete, enabled = !sending, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !sending, modifier = Modifier.heightIn(min = 44.dp)) { Text("Annuler") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sync by StoreEngine.sync.collectAsState()
    var notifications by remember { mutableStateOf(Prefs.updateNotifications(context)) }
    var wifiOnly by remember { mutableStateOf(Prefs.wifiOnly(context)) }
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdate(context)) }
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
        Prefs.setUpdateNotifications(context, granted)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Paramètres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingSwitch("Notifications de mises à jour", "Prévenir quand une app installée a une nouvelle version", notifications) { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notifications = on
                    Prefs.setUpdateNotifications(context, on)
                }
            }
            SettingSwitch(
                "Mises à jour automatiques",
                "En arrière-plan, sans confirmation, pour les applis installées depuis ACN Store",
                autoUpdate
            ) { on ->
                autoUpdate = on
                Prefs.setAutoUpdate(context, on)
            }
            SettingSwitch("Télécharger en Wi-Fi uniquement", "Évite d'utiliser vos données mobiles", wifiOnly) { on ->
                wifiOnly = on
                Prefs.setWifiOnly(context, on)
            }
            SettingAction(
                "Installation d'applications",
                if (canInstall) "Autorisée" else "Non autorisée — touchez pour l'activer"
            ) {
                context.startActivity(Installer.unknownSourcesSettingsIntent(context))
                canInstall = Installer.canInstall(context)
            }
            SettingAction("Vider le cache de téléchargement", "Supprime les APK téléchargés non installés") {
                ApkDownloader.cleanup(context)
                StoreEngine.refreshInstalled()
            }
            SettingAction("Actualiser le catalogue", catalogLine(sync)) { StoreEngine.sync() }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            Text(
                "ACN Store ${BuildConfig.VERSION_NAME} · ACN Development",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun catalogLine(sync: StoreEngine.SyncState): String = when {
    sync.syncing -> "Synchronisation…"
    sync.lastSync > 0 -> "Synchronisé à ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sync.lastSync))}"
    sync.source == CatalogRepository.Source.CACHE -> "Catalogue enregistré (hors ligne)"
    else -> "Catalogue intégré (hors ligne)"
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingAction(title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
