package com.acn.store.data

import android.content.Context
import android.util.Log
import com.acn.store.BuildConfig
import com.acn.store.model.Catalog
import com.acn.store.model.CatalogApp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object CatalogRepository {

    private const val TAG = "[Catalog]"
    private val PACKAGE_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private val SHA256_RE = Regex("^[a-f0-9]{64}$")
    private val gson = Gson()

    enum class Source { NETWORK, CACHE, BUNDLED }

    data class Result(val apps: List<CatalogApp>, val source: Source, val generatedAt: String)

    private fun cacheFile(context: Context) = File(context.filesDir, "catalog.json")

    /** Last known catalog: cached copy from a previous sync, else the one bundled in the APK. */
    fun loadLocal(context: Context): Result {
        cacheFile(context).takeIf { it.exists() }?.let { file ->
            parse(file.readText())?.let { return Result(it.apps, Source.CACHE, it.generatedAt) }
        }
        val bundled = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val catalog = parse(bundled) ?: Catalog()
        return Result(catalog.apps, Source.BUNDLED, catalog.generatedAt)
    }

    /** Fetch the published catalog: GitHub API first (fresh), raw CDN as fallback (anti-cache param). */
    suspend fun fetchRemote(context: Context): Result = withContext(Dispatchers.IO) {
        val sources = listOf(
            Request.Builder().url(BuildConfig.CATALOG_API_URL).header("Accept", "application/vnd.github.raw+json").build(),
            Request.Builder().url("${BuildConfig.CATALOG_URL}?t=${System.currentTimeMillis()}").header("Cache-Control", "no-cache").build()
        )
        var lastError: Exception = java.io.IOException("Catalogue indisponible")
        for (request in sources) {
            try {
                return@withContext download(context, request)
            } catch (e: Exception) {
                Log.w(TAG, "Catalog source failed (${request.url.host}): ${e.message}")
                lastError = e
            }
        }
        throw lastError
    }

    private fun download(context: Context, request: Request): Result =
        Network.github.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Catalogue indisponible (HTTP ${response.code})")
            val body = response.body?.string() ?: throw java.io.IOException("Catalogue vide")
            if (body.length > 2_000_000) throw java.io.IOException("Catalogue trop volumineux")
            val catalog = parse(body) ?: throw java.io.IOException("Catalogue invalide")
            if (catalog.apps.isEmpty()) throw java.io.IOException("Catalogue vide")
            cacheFile(context).writeText(body)
            Result(catalog.apps, Source.NETWORK, catalog.generatedAt)
        }

    private fun parse(json: String): Catalog? = try {
        val raw = gson.fromJson(json, Catalog::class.java) ?: return null
        raw.copy(apps = raw.apps.orEmpty().filter(::isValid).distinctBy { it.id })
    } catch (e: Exception) {
        Log.w(TAG, "Parse failed: ${e.message}")
        null
    }

    // Entries that could lead to an unsafe download are dropped, not trusted
    private fun isValid(app: CatalogApp?): Boolean {
        if (app == null) return false
        val ok = PACKAGE_RE.matches(app.id) &&
            app.name.isNotBlank() &&
            app.versionCode > 0 &&
            SHA256_RE.matches(app.sha256.lowercase()) &&
            Network.isTrustedUrl(app.downloadUrl) &&
            (app.iconUrl == null || Network.isTrustedUrl(app.iconUrl))
        if (!ok) Log.w(TAG, "Rejected catalog entry ${app.id}")
        return ok
    }
}
