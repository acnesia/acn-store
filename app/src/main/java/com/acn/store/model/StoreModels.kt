package com.acn.store.model

import com.google.gson.annotations.SerializedName

// Remote catalog entry, as produced by tools/build_catalog.py
data class CatalogApp(
    val id: String = "",
    val name: String = "",
    val developer: String = "ACN Development",
    val category: String = "",
    val tagline: String = "",
    val description: String = "",
    @SerializedName("version_name") val versionName: String = "",
    @SerializedName("version_code") val versionCode: Long = 0,
    @SerializedName("min_sdk") val minSdk: Int = 21,
    @SerializedName("size_bytes") val sizeBytes: Long = 0,
    val sha256: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String>? = null,
    val changelog: String = "",
    val featured: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String = ""
) {
    val isGame: Boolean get() = category.equals("Jeux", ignoreCase = true)
}

data class Catalog(
    val schema: Int = 1,
    @SerializedName("generated_at") val generatedAt: String = "",
    val apps: List<CatalogApp> = emptyList()
)

data class InstalledInfo(
    val versionName: String?,
    val versionCode: Long
)

// Live state of one app's install pipeline
sealed interface TaskState {
    data object Idle : TaskState
    data object Queued : TaskState
    data class Downloading(val bytes: Long, val total: Long) : TaskState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Verifying : TaskState
    data object Installing : TaskState
    data object WaitingUser : TaskState
    data class Failed(val message: String, val canUninstall: Boolean = false) : TaskState
}

val TaskState.isBusy: Boolean
    get() = this is TaskState.Queued || this is TaskState.Downloading || this is TaskState.Verifying ||
        this is TaskState.Installing || this is TaskState.WaitingUser

enum class AppStatus { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, INCOMPATIBLE }

// Catalog entry merged with what is on the device
data class StoreApp(
    val info: CatalogApp,
    val installed: InstalledInfo?,
    val task: TaskState,
    val rating: RatingSummary?
) {
    val id: String get() = info.id
    val status: AppStatus
        get() = when {
            installed != null && info.versionCode > installed.versionCode -> AppStatus.UPDATE_AVAILABLE
            installed != null -> AppStatus.INSTALLED
            android.os.Build.VERSION.SDK_INT < info.minSdk -> AppStatus.INCOMPATIBLE
            else -> AppStatus.NOT_INSTALLED
        }
}

data class RatingSummary(
    val count: Int = 0,
    val average: Double = 0.0,
    val histogram: Map<String, Int>? = null
)

data class Review(
    val id: Long = 0,
    val author: String = "",
    val rating: Int = 0,
    val comment: String? = null,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class ReviewsPage(
    val summary: RatingSummary = RatingSummary(),
    val reviews: List<Review> = emptyList(),
    val mine: Review? = null
)
