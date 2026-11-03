package com.acn.store.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.acn.store.data.CatalogRepository
import com.acn.store.data.Prefs
import com.acn.store.data.ReviewsApi
import com.acn.store.installer.ApkDownloader
import com.acn.store.installer.ApkVerifier
import com.acn.store.installer.Installer
import com.acn.store.model.AppStatus
import com.acn.store.model.CatalogApp
import com.acn.store.model.InstalledInfo
import com.acn.store.model.RatingSummary
import com.acn.store.model.StoreApp
import com.acn.store.model.TaskState
import com.acn.store.model.isBusy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Single source of truth for the catalog, the device's installed apps and every install task. */
object StoreEngine {

    private const val TAG = "[StoreEngine]"

    data class SyncState(
        val syncing: Boolean = false,
        val source: CatalogRepository.Source? = null,
        val error: String? = null,
        val lastSync: Long = 0
    )

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installLock = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val results = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var foreground: WeakReference<Activity>? = null
    private var pendingConfirm: Intent? = null
    private var pendingAfterPermission: String? = null

    private val catalog = MutableStateFlow<List<CatalogApp>>(emptyList())
    private val installed = MutableStateFlow<Map<String, InstalledInfo>>(emptyMap())
    private val tasks = MutableStateFlow<Map<String, TaskState>>(emptyMap())
    private val ratings = MutableStateFlow<Map<String, RatingSummary>>(emptyMap())
    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val apps: StateFlow<List<StoreApp>> by lazy {
        combine(catalog, installed, tasks, ratings) { cat, inst, t, r ->
            cat.map { StoreApp(it, inst[it.id], t[it.id] ?: TaskState.Idle, r[it.id]) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val local = CatalogRepository.loadLocal(appContext)
        catalog.value = local.apps
        _sync.value = SyncState(source = local.source)
        refreshInstalled()
        sync()
    }

    // ---- Catalog & device state ----

    fun sync() {
        if (_sync.value.syncing) return
        _sync.update { it.copy(syncing = true, error = null) }
        scope.launch {
            try {
                val remote = CatalogRepository.fetchRemote(appContext)
                catalog.value = remote.apps
                _sync.value = SyncState(source = remote.source, lastSync = System.currentTimeMillis())
                Log.i(TAG, "Catalog synced: ${remote.apps.size} apps")
            } catch (e: Exception) {
                Log.w(TAG, "Catalog sync failed: ${e.message}")
                _sync.update { it.copy(syncing = false, error = "Hors ligne : catalogue enregistré affiché") }
            }
            refreshInstalled()
            refreshRatings()
            if (Prefs.autoUpdate(appContext)) runBackgroundUpdates(emptyList())
        }
    }

    fun refreshRatings() {
        scope.launch {
            try {
                ratings.value = ReviewsApi.summaries(catalog.value.map { it.id })
            } catch (e: Exception) {
                Log.w(TAG, "Ratings unavailable: ${e.message}")
            }
        }
    }

    fun updateRating(appId: String, summary: RatingSummary) = ratings.update { it + (appId to summary) }

    fun refreshInstalled() {
        if (!::appContext.isInitialized) return
        val pm = appContext.packageManager
        installed.value = catalog.value.mapNotNull { app ->
            try {
                val info = pm.getPackageInfo(app.id, 0)
                app.id to InstalledInfo(info.versionName, PackageInfoCompat.getLongVersionCode(info))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.toMap()
    }

    fun installedLabelIcon(packageName: String) = try {
        appContext.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }

    // ---- Install pipeline ----

    fun onActivityResumed(activity: Activity) {
        foreground = WeakReference(activity)
        refreshInstalled()
        pendingConfirm?.let { intent ->
            pendingConfirm = null
            runCatching { activity.startActivity(intent) }
        }
        // Coming back from the "unknown sources" screen
        pendingAfterPermission?.let { pkg ->
            pendingAfterPermission = null
            if (Installer.canInstall(appContext)) install(pkg)
            else _messages.tryEmit("Autorisation refusée : ACN Store ne peut pas installer d'applications.")
        }
    }

    fun onActivityPaused() {
        foreground = null
    }

    fun install(packageName: String) {
        val app = catalog.value.firstOrNull { it.id == packageName } ?: return
        if (tasks.value[packageName]?.isBusy == true) return

        if (!Installer.canInstall(appContext)) {
            pendingAfterPermission = packageName
            val activity = foreground?.get()
            val intent = Installer.unknownSourcesSettingsIntent(appContext)
            _messages.tryEmit("Autorise ACN Store à installer des applications, puis reviens.")
            if (activity != null) activity.startActivity(intent)
            else appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (Prefs.wifiOnly(appContext) && isMetered()) {
            setTask(packageName, TaskState.Failed("Téléchargement en Wi-Fi uniquement (voir Paramètres)."))
            return
        }

        setTask(packageName, TaskState.Queued)
        jobs[packageName] = scope.launch {
            installLock.withLock { runInstall(app) }
        }.also { job -> job.invokeOnCompletion { jobs.remove(packageName) } }
    }

    fun updateAll() {
        apps.value.filter { it.status == AppStatus.UPDATE_AVAILABLE && !it.task.isBusy }
            // The store updates itself last: installing it restarts the process
            .sortedBy { it.id == appContext.packageName }
            .forEach { install(it.id) }
    }

    fun cancel(packageName: String) {
        jobs.remove(packageName)?.cancel()
        setTask(packageName, TaskState.Idle)
    }

    fun clearError(packageName: String) {
        if (tasks.value[packageName] is TaskState.Failed) setTask(packageName, TaskState.Idle)
    }

    /**
     * Android 12+ lets an installer update an app without any prompt when it is that app's
     * installer of record (the app was installed through ACN Store).
     */
    fun canUpdateSilently(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            appContext.packageManager.getInstallSourceInfo(packageName).installingPackageName == appContext.packageName
        } catch (_: Exception) {
            false
        }
    }

    /** Background auto-update (UpdateCheckWorker): installs every update that needs no confirmation. Returns the updated app names. */
    suspend fun runBackgroundUpdates(fresh: List<CatalogApp>): List<String> {
        if (fresh.isNotEmpty()) catalog.value = fresh
        if (!Installer.canInstall(appContext) || (Prefs.wifiOnly(appContext) && isMetered())) return emptyList()
        refreshInstalled()
        val eligible = apps.value.filter {
            it.status == AppStatus.UPDATE_AVAILABLE && !it.task.isBusy && it.id != appContext.packageName && canUpdateSilently(it.id)
        }
        val done = mutableListOf<String>()
        for (app in eligible) {
            setTask(app.id, TaskState.Queued)
            if (installLock.withLock { runInstall(app.info) }) done += app.info.name
        }
        Log.i(TAG, "Background updates: ${done.size}/${eligible.size}")
        return done
    }

    private suspend fun runInstall(app: CatalogApp): Boolean {
        val pkg = app.id
        try {
            setTask(pkg, TaskState.Downloading(0, app.sizeBytes))
            val apk = ApkDownloader.download(appContext, app) { done, total ->
                setTask(pkg, TaskState.Downloading(done, total))
            }
            setTask(pkg, TaskState.Verifying)
            ApkVerifier.verify(appContext, apk, app)

            setTask(pkg, TaskState.Installing)
            val result = CompletableDeferred<Boolean>()
            results[pkg] = result
            Installer.install(appContext, apk, pkg)
            // Waits for InstallReceiver; the user may need time on the system prompt
            val ok = withTimeoutOrNull(15 * 60 * 1000L) { result.await() }
            results.remove(pkg)
            if (ok == null) setTask(pkg, TaskState.Failed("L'installation n'a pas abouti."))
            return ok == true
        } catch (e: CancellationException) {
            results.remove(pkg)
            setTask(pkg, TaskState.Idle)
            throw e
        } catch (e: ApkVerifier.Rejected) {
            Log.w(TAG, "Rejected $pkg: ${e.message}")
            ApkDownloader.cleanup(appContext)
            setTask(pkg, TaskState.Failed(e.message ?: "Fichier refusé", e.canUninstall))
        } catch (e: SecurityException) {
            Log.w(TAG, "Integrity failure $pkg: ${e.message}")
            setTask(pkg, TaskState.Failed(e.message ?: "Fichier refusé"))
        } catch (e: Exception) {
            Log.w(TAG, "Install failed $pkg: ${e.message}")
            setTask(pkg, TaskState.Failed(friendly(e)))
        }
        return false
    }

    // ---- Callbacks from InstallReceiver ----

    fun onUserActionRequired(context: Context, packageName: String, confirm: Intent?) {
        setTask(packageName, TaskState.WaitingUser)
        confirm ?: return
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = foreground?.get()
        if (activity != null) {
            runCatching { activity.startActivity(confirm) }.onFailure { pendingConfirm = confirm }
        } else {
            // In background nobody can confirm now: shown when the store is next opened, queue moves on
            pendingConfirm = confirm
            results[packageName]?.complete(false)
        }
    }

    fun onInstallSuccess(context: Context, packageName: String) {
        val name = catalog.value.firstOrNull { it.id == packageName }?.name ?: packageName
        Log.i(TAG, "Installed $packageName")
        setTask(packageName, TaskState.Idle)
        refreshInstalled()
        ApkDownloader.cleanup(context)
        _messages.tryEmit("$name est installée")
        results[packageName]?.complete(true)
    }

    fun onInstallFailure(context: Context, packageName: String, status: Int, message: String?) {
        Log.w(TAG, "Install failure $packageName status=$status msg=$message")
        val state = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> TaskState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                TaskState.Failed("Conflit avec l'app déjà installée (signature différente). Désinstalle-la puis réessaie.", canUninstall = true)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> TaskState.Failed("Application incompatible avec cet appareil.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> TaskState.Failed("Espace de stockage insuffisant.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> TaskState.Failed("Installation bloquée par l'appareil.")
            PackageInstaller.STATUS_FAILURE_INVALID -> TaskState.Failed("Fichier APK invalide.")
            else -> TaskState.Failed(message?.takeIf { it.isNotBlank() } ?: "Échec de l'installation.")
        }
        setTask(packageName, state)
        refreshInstalled()
        results[packageName]?.complete(false)
    }

    // ---- Helpers ----

    private fun setTask(pkg: String, state: TaskState) = tasks.update { it + (pkg to state) }

    private fun isMetered(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Pas de connexion Internet."
        is java.net.SocketTimeoutException -> "Connexion trop lente, réessaie."
        is java.io.IOException -> e.message ?: "Erreur réseau."
        else -> e.message ?: "Erreur inattendue."
    }
}
