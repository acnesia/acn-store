package com.acn.store.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.acn.store.engine.StoreEngine

/** Receives PackageInstaller session results. Not exported: only the system, via our PendingIntent, can reach it. */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Installer.ACTION_INSTALL_STATUS) return
        val packageName = intent.getStringExtra(Installer.EXTRA_PACKAGE) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                StoreEngine.onUserActionRequired(context, packageName, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> StoreEngine.onInstallSuccess(context, packageName)
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                StoreEngine.onInstallFailure(context, packageName, status, message)
            }
        }
    }
}
