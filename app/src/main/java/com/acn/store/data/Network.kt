package com.acn.store.data

import com.acn.store.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object Network {

    // APKs, icons and the catalog may only come from GitHub over HTTPS, redirects included
    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "api.github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun isTrustedUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && parsed.host in TRUSTED_HOSTS
    }

    // Network interceptor: runs for every hop, so a redirect to an untrusted host is cut off
    private val hostGuard = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps || url.host !in TRUSTED_HOSTS) {
            throw IOException("Hôte non autorisé : ${url.host}")
        }
        chain.proceed(chain.request())
    }

    private val userAgent = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "ACNStore/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE})")
                .build()
        )
    }

    // GitHub downloads: catalog, icons, APKs
    val github: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .addInterceptor(userAgent)
            .addNetworkInterceptor(hostGuard)
            .build()
    }

    // Reviews API on the ACN backend, no redirects
    val api: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(userAgent)
            .build()
    }
}
