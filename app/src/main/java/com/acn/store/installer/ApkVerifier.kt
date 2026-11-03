package com.acn.store.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.acn.store.model.CatalogApp
import java.io.File
import java.security.MessageDigest

/** Checks a downloaded APK is exactly the app the catalog announced, and can replace the installed one. */
object ApkVerifier {

    class Rejected(message: String, val canUninstall: Boolean = false) : Exception(message)

    @Suppress("DEPRECATION")
    private val SIG_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    fun verify(context: Context, apk: File, app: CatalogApp) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, SIG_FLAGS)
            ?: throw Rejected("Fichier APK illisible.")

        if (archive.packageName != app.id) {
            throw Rejected("Le fichier ne correspond pas à ${app.name} (${archive.packageName}).")
        }
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        if (archiveCode != app.versionCode) {
            throw Rejected("Version inattendue du fichier ($archiveCode au lieu de ${app.versionCode}).")
        }

        val installed = try {
            pm.getPackageInfo(app.id, SIG_FLAGS)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: return

        if (PackageInfoCompat.getLongVersionCode(installed) > archiveCode) {
            throw Rejected("Une version plus récente est déjà installée.", canUninstall = true)
        }
        val newCerts = certHashes(archive)
        val oldCerts = certHashes(installed)
        if (newCerts.isNotEmpty() && oldCerts.isNotEmpty() && newCerts.intersect(oldCerts).isEmpty()) {
            throw Rejected(
                "La mise à jour est signée avec une autre clé que l'app installée. Désinstalle ${app.name} puis réinstalle-la (les données locales de l'app seront perdues).",
                canUninstall = true
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun certHashes(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
