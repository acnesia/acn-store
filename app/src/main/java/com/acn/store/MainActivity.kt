package com.acn.store

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.acn.store.data.Prefs
import com.acn.store.engine.StoreEngine
import com.acn.store.ui.screens.StoreRoot
import com.acn.store.ui.theme.ACNStoreTheme
import com.acn.store.work.UpdateCheckWorker

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }

    // Installs/uninstalls done anywhere (system prompt, settings) refresh the store immediately
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = StoreEngine.refreshInstalled()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Prefs.setUpdateNotifications(this, granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StoreEngine.init(this)
        UpdateCheckWorker.createChannel(this)
        UpdateCheckWorker.schedule(this)
        askNotificationsOnce()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val openUpdates = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
        setContent {
            ACNStoreTheme {
                StoreRoot(openUpdates = openUpdates)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StoreEngine.onActivityResumed(this)
    }

    override fun onPause() {
        StoreEngine.onActivityPaused()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = Prefs.of(this)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
