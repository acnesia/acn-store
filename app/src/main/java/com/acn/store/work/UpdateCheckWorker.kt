package com.acn.store.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acn.store.MainActivity
import com.acn.store.R
import com.acn.store.data.CatalogRepository
import com.acn.store.data.Prefs
import com.acn.store.engine.StoreEngine
import java.util.concurrent.TimeUnit

/** Background check (every 6 h): installs silent updates, then notifies about the ones left. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Prefs.updateNotifications(ctx) && !Prefs.autoUpdate(ctx)) return Result.success()
        val catalog = try {
            CatalogRepository.fetchRemote(ctx)
        } catch (e: Exception) {
            Log.w("[UpdateWorker]", "Catalog fetch failed: ${e.message}")
            return Result.retry()
        }

        // Play-style auto-update: silent installs for apps this store installed (Android 12+)
        if (Prefs.autoUpdate(ctx)) {
            StoreEngine.init(ctx)
            val updated = StoreEngine.runBackgroundUpdates(catalog.apps)
            if (updated.isNotEmpty() && Prefs.updateNotifications(ctx)) {
                val title = if (updated.size == 1) "${updated[0]} a été mise à jour" else "${updated.size} applis mises à jour"
                post(ctx, DONE_ID, title, updated.joinToString(", "))
            }
        }

        val pm = ctx.packageManager
        val updates = catalog.apps.filter { app ->
            try {
                app.versionCode > PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(app.id, 0))
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        // Notify only when the set of pending updates changed since the last notification
        val key = updates.joinToString(",") { "${it.id}:${it.versionCode}" }
        val prefs = Prefs.of(ctx)
        if (updates.isEmpty()) {
            prefs.edit().remove("last_update_key").apply()
            NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
        } else if (Prefs.updateNotifications(ctx) && key != prefs.getString("last_update_key", null)) {
            val title = if (updates.size == 1) "Mise à jour disponible" else "${updates.size} mises à jour disponibles"
            post(ctx, NOTIFICATION_ID, title, updates.joinToString(", ") { it.name })
            prefs.edit().putString("last_update_key", key).apply()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "acn_store_update_check"
        private const val CHANNEL_ID = "updates"
        private const val NOTIFICATION_ID = 1001
        private const val DONE_ID = 1002

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mises à jour disponibles pour vos applications ACN"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        private fun post(context: Context, id: Int, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
