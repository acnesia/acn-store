package com.acn.store.installer

import android.content.Context
import com.acn.store.data.Network
import com.acn.store.model.CatalogApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object ApkDownloader {

    // Hard cap so a bad catalog entry can't fill the phone
    private const val MAX_APK_BYTES = 500L * 1024 * 1024

    fun apkDir(context: Context) = File(context.filesDir, "apks").apply { mkdirs() }

    fun targetFile(context: Context, app: CatalogApp) = File(apkDir(context), "${app.id}-${app.versionCode}.apk")

    /**
     * Downloads the APK into app-private storage and checks its SHA-256 against the catalog.
     * A previously verified file for the same version is reused.
     */
    suspend fun download(context: Context, app: CatalogApp, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = targetFile(context, app)
            if (target.exists() && sha256(target) == app.sha256.lowercase()) return@withContext target
            target.delete()

            val part = File(target.path + ".part")
            part.delete()
            val request = Request.Builder().url(app.downloadUrl).build()
            val digest = MessageDigest.getInstance("SHA-256")

            Network.github.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement impossible (HTTP ${response.code})")
                val body = response.body ?: throw IOException("Réponse vide")
                val total = body.contentLength().takeIf { it > 0 } ?: app.sizeBytes
                if (total > MAX_APK_BYTES) throw IOException("Fichier trop volumineux")

                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Long = 0
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (read > MAX_APK_BYTES) throw IOException("Fichier trop volumineux")
                            if (read - lastReport > 128 * 1024 || read == total) {
                                lastReport = read
                                onProgress(read, total)
                            }
                        }
                        onProgress(read, total)
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != app.sha256.lowercase()) {
                part.delete()
                throw SecurityException("Fichier corrompu ou modifié : l'empreinte SHA-256 ne correspond pas au catalogue.")
            }
            if (!part.renameTo(target)) throw IOException("Impossible d'enregistrer le fichier")
            target
        }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Remove downloaded APKs (after install, or stale ones). */
    fun cleanup(context: Context, keep: File? = null) {
        apkDir(context).listFiles()?.forEach { if (it != keep) it.delete() }
    }
}
