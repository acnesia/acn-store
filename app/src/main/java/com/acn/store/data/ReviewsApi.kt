package com.acn.store.data

import android.content.Context
import android.os.Build
import com.acn.store.BuildConfig
import com.acn.store.model.RatingSummary
import com.acn.store.model.ReviewsPage
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom

/** Public reviews stored on the ACN backend (routes/store.js). */
object ReviewsApi {

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun base() = BuildConfig.REVIEWS_API.trimEnd('/')

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Random per-install secret: identifies "my review" without any account. Never leaves this app otherwise. */
    fun deviceSecret(context: Context): String {
        val prefs = Prefs.of(context)
        prefs.getString("device_secret", null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("device_secret", secret).apply()
        return secret
    }

    suspend fun summaries(ids: List<String>): Map<String, RatingSummary> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val request = Request.Builder().url("${base()}/summary?ids=${encode(ids.joinToString(","))}").build()
        Network.api.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val type = object : TypeToken<Map<String, RatingSummary>>() {}.type
            gson.fromJson<Map<String, RatingSummary>>(root.get("summaries"), type).orEmpty()
        }
    }

    suspend fun reviews(context: Context, appId: String): ReviewsPage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/apps/${encode(appId)}/reviews?limit=50")
            .header("X-Store-Device", deviceSecret(context))
            .build()
        Network.api.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            gson.fromJson(response.body?.string().orEmpty(), ReviewsPage::class.java) ?: ReviewsPage()
        }
    }

    suspend fun submit(context: Context, appId: String, rating: Int, comment: String, author: String, appVersion: String?) =
        withContext(Dispatchers.IO) {
            val payload = gson.toJson(
                mapOf(
                    "rating" to rating,
                    "comment" to comment.take(1000),
                    "author" to author.take(40),
                    "app_version" to appVersion,
                    "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}".take(60)
                )
            )
            val request = Request.Builder()
                .url("${base()}/apps/${encode(appId)}/reviews")
                .header("X-Store-Device", deviceSecret(context))
                .post(payload.toRequestBody(JSON))
                .build()
            Network.api.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(errorOf(response.body?.string()) ?: "Envoi impossible (HTTP ${response.code})")
            }
        }

    suspend fun delete(context: Context, appId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/apps/${encode(appId)}/reviews")
            .header("X-Store-Device", deviceSecret(context))
            .delete()
            .build()
        Network.api.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Suppression impossible (HTTP ${response.code})")
        }
    }

    private fun errorOf(body: String?): String? = try {
        JsonParser.parseString(body.orEmpty()).asJsonObject.get("error")?.asString
    } catch (_: Exception) {
        null
    }
}
