package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AppItem
import com.example.data.repository.AppRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val percentage: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Downloaded(val filePath: String) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val appDao = AppDatabase.getDatabase(application).appDao
    private val repository = AppRepository(application, appDao)
    private val packageManager: PackageManager = application.packageManager

    // Configuration states (persisted in simple SharedPreferences for robust zero-config simplicity)
    private val prefs = application.getSharedPreferences("drive_store_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PRIMARY_DRIVE_FOLDER_ID = "1PBrhSIvDk0QrgNS6XeTeA1RDLFPeTqKV"
    }

    private val _driveFolderId = MutableStateFlow(PRIMARY_DRIVE_FOLDER_ID)
    val driveFolderId = _driveFolderId.asStateFlow()

    val driveApiKey: String
        get() = prefs.getString("api_key", "AIzaSyAX7T6Vd75LnhQg15IydOLEYqjfGUT8TO8") ?: "AIzaSyAX7T6Vd75LnhQg15IydOLEYqjfGUT8TO8"

    // Common Settings
    private val appStorePrefs = application.getSharedPreferences("app_store_prefs", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode = _isDarkMode.asStateFlow()

    private val _autoUpdateCheck = MutableStateFlow(prefs.getBoolean("auto_update_check", true))
    val autoUpdateCheck = _autoUpdateCheck.asStateFlow()

    private val _autoDownloadUpdates = MutableStateFlow(prefs.getBoolean("auto_download_updates", true))
    val autoDownloadUpdates = _autoDownloadUpdates.asStateFlow()

    private val _autoDeleteDownloads = MutableStateFlow(prefs.getBoolean("auto_delete_downloads", true))
    val autoDeleteDownloads = _autoDeleteDownloads.asStateFlow()

    private val _autoDeleteDays = MutableStateFlow((appStorePrefs.getString("auto_delete_days", "1")?.toIntOrNull() ?: 7).coerceIn(1, 30))
    val autoDeleteDays = _autoDeleteDays.asStateFlow()

    fun setAutoDeleteDays(days: Int) {
        val clamped = days.coerceIn(1, 30)
        appStorePrefs.edit().putString("auto_delete_days", clamped.toString()).apply()
        _autoDeleteDays.value = clamped
    }

    private val _useDeviceTheme = MutableStateFlow(prefs.getBoolean("use_device_theme", true))
    val useDeviceTheme = _useDeviceTheme.asStateFlow()

    fun setAutoDeleteDownloads(enabled: Boolean) {
        prefs.edit().putBoolean("auto_delete_downloads", enabled).apply()
        _autoDeleteDownloads.value = enabled
    }

    // Shared folder IDs (additional Google Drive folders to scan for APKs)
    private val _sharedFolderIds = MutableStateFlow(
        getSharedFolderIdsFromPrefs()
    )
    val sharedFolderIds = _sharedFolderIds.asStateFlow()

    private fun getSharedFolderIdsFromPrefs(): List<String> {
        val json = prefs.getString("shared_folder_ids", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSharedFolderIdsToPrefs(ids: List<String>) {
        val json = try {
            org.json.JSONArray(ids).toString()
        } catch (e: Exception) {
            "[]"
        }
        prefs.edit().putString("shared_folder_ids", json).apply()
    }

    fun addSharedFolder(folderId: String) {
        val current = _sharedFolderIds.value.toMutableList()
        if (folderId.isNotBlank() && !current.contains(folderId)) {
            current.add(folderId)
            _sharedFolderIds.value = current
            saveSharedFolderIdsToPrefs(current)
        }
    }

    fun removeSharedFolder(folderId: String) {
        val current = _sharedFolderIds.value.toMutableList()
        current.remove(folderId)
        _sharedFolderIds.value = current
        saveSharedFolderIdsToPrefs(current)
    }

    fun setDriveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
    }



    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        prefs.edit().putBoolean("auto_update_check", enabled).apply()
        _autoUpdateCheck.value = enabled
        scheduleUpdateWork(enabled)
    }

    fun setAutoDownloadUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("auto_download_updates", enabled).apply()
        _autoDownloadUpdates.value = enabled
    }

    fun getNotifHour(): Int = prefs.getInt("notif_hour", 8)
    fun getNotifMinute(): Int = prefs.getInt("notif_minute", 0)
    fun getNotifIntervalHours(): Int = prefs.getInt("notif_interval_hours", 24)
    fun setNotifIntervalHours(hours: Int) {
        prefs.edit().putInt("notif_interval_hours", hours.coerceIn(1, 48)).apply()
        scheduleUpdateWork(_autoUpdateCheck.value)
    }
    fun setNotifTime(hour: Int, minute: Int) {
        prefs.edit().putInt("notif_hour", hour).putInt("notif_minute", minute).apply()
        scheduleUpdateWork(_autoUpdateCheck.value)
    }

    // Check/retry interval in minutes (5, 10, 15, 30 min, 1..24 hours)
    fun getCheckIntervalMinutes(): Int = com.example.util.AutoDownloadHelper.checkIntervalMinutes(prefs)
    fun setCheckIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("check_interval_minutes", minutes.coerceAtLeast(5)).apply()
        scheduleUpdateWork(_autoUpdateCheck.value)
    }

    fun getTimeText(): String {
        val h = getNotifHour(); val m = getNotifMinute()
        val period = if (h < 12) "AM" else "PM"
        val hour12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "Starts at ${hour12}:${String.format("%02d", m)} $period, repeats every ${getNotifIntervalHours()} hours"
    }

    private fun scheduleUpdateWork(enabled: Boolean) {
        val ctx = getApplication<Application>()
        val workManager = androidx.work.WorkManager.getInstance(ctx)
        workManager.cancelUniqueWork("update_check")

        if (enabled) {
            val hour = getNotifHour()
            val minute = getNotifMinute()
            // WorkManager periodic minimum is 15 min; shorter intervals run via AlarmManager
            val intervalMinutes = getCheckIntervalMinutes().coerceAtLeast(15).toLong()

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val delayMillis = target.timeInMillis - now.timeInMillis

            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.util.UpdateCheckWorker>()
                .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("update_check")
                .build()

            workManager.enqueueUniqueWork(
                "update_check_initial",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )

            val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.UpdateCheckWorker>(
                intervalMinutes, java.util.concurrent.TimeUnit.MINUTES
            )
                .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("update_check")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "update_check",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }
    }

    fun setUseDeviceTheme(enabled: Boolean) {
        prefs.edit().putBoolean("use_device_theme", enabled).apply()
        _useDeviceTheme.value = enabled
    }


    val allApps: StateFlow<List<AppItem>> = repository.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selfUpdateApp: StateFlow<AppItem?> = allApps.map { apps ->
        apps.firstOrNull { it.packageName == getApplication<Application>().packageName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun isSelfUpdateAvailable(): Boolean {
        val app = selfUpdateApp.value ?: return false
        val installedCode = _installedPackages.value[app.packageName] ?: return false
        val installedName = try {
            packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        return isUpdateAvailable(app, installedCode, installedName)
    }

    fun startSelfUpdate() {
        val app = selfUpdateApp.value ?: return
        startDownload(app)
    }

    private fun scheduleRelaunch() {
        try {
            val ctx = getApplication<Application>()
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
            val pendingIntent = android.app.PendingIntent.getActivity(
                ctx, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmMgr = ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmMgr.setExact(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pendingIntent)
        } catch (_: Exception) {}
    }

    val favoriteApps: StateFlow<List<AppItem>> = repository.favoriteApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search term flow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Debounced search results
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<AppItem>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchApps(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live state tracking for downloads [packageName -> DownloadState]
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates = _downloadStates.asStateFlow()

    private val _updateApps = MutableStateFlow<List<AppItem>>(emptyList())
    val updateApps = _updateApps.asStateFlow()

    // Real-time map of installed packages and their version code on device
    private val _installedPackages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val installedPackages = _installedPackages.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val _syncCount = MutableStateFlow(0)
    val syncCount = _syncCount.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private val activeCalls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()
    private val notifiedUpdates = mutableSetOf<String>()

    private val _totalUsers = MutableStateFlow<Int?>(appStorePrefs.getInt("total_users", -1).takeIf { it >= 0 })
    val totalUsers = _totalUsers.asStateFlow()

    init {
        // Background update check always runs (no toggle in settings)
        prefs.edit().putBoolean("auto_update_check", true).apply()
        _autoUpdateCheck.value = true
        if (!prefs.contains("auto_download_updates")) {
            prefs.edit().putBoolean("auto_download_updates", true).apply()
        }
        NotificationHelper.init(application)
        refreshInstalledPackages()
        if (_autoDeleteDownloads.value) cleanupOldDownloads()
        fetchTotalUsers()
        scheduleUpdateWork(_autoUpdateCheck.value)
        forceSync()
    }

    fun lazyLoadDescription(app: AppItem, onResult: (String) -> Unit) {
        val desc = app.description
        if (desc.startsWith("__MD_ID:")) {
            val mdFileId = desc.removePrefix("__MD_ID:")
            val cacheKey = "cached_md_${mdFileId}"
            val cached = prefs.getString(cacheKey, null)
            if (!cached.isNullOrEmpty()) {
                onResult(cached)
            }
            viewModelScope.launch {
                val content = repository.fetchMdContent(mdFileId, driveApiKey)
                if (!content.isNullOrEmpty()) {
                    prefs.edit().putString(cacheKey, content).apply()
                    if (cached.isNullOrEmpty()) onResult(content)
                } else if (cached.isNullOrEmpty()) {
                    onResult("No description available.")
                }
            }
        }
    }

    fun fetchContactMd(onResult: (String) -> Unit) {
        val cached = prefs.getString("cached_contact_md", null)
        if (!cached.isNullOrEmpty()) {
            onResult(cached)
        }
        viewModelScope.launch {
            val content = repository.fetchMdByName(_driveFolderId.value, "Contact.md", driveApiKey)
            if (!content.isNullOrEmpty()) {
                prefs.edit().putString("cached_contact_md", content).apply()
                if (cached.isNullOrEmpty()) onResult(content)
            } else if (cached.isNullOrEmpty()) {
                onResult("")
            }
        }
    }

    fun fetchSelfUpdateDescription(onResult: (String) -> Unit) {
        val app = selfUpdateApp.value ?: return
        lazyLoadDescription(app, onResult)
    }

    private val updateHistoryDao = AppDatabase.getDatabase(application).updateHistoryDao

    fun markPackageUpdated(packageName: String, newVersionCode: Int, patchVersion: String = "") {
        val oldVersionCode = _installedPackages.value[packageName] ?: 0
        val oldVersionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
        val appName = allApps.value.firstOrNull { it.packageName == packageName }?.name ?: packageName
        val newVersionName = allApps.value.firstOrNull { it.packageName == packageName }?.versionName ?: ""

        _installedPackages.update { it + (packageName to newVersionCode) }
        if (patchVersion.isNotEmpty()) {
            val prefs = getApplication<Application>().getSharedPreferences("drive_store_prefs", android.content.Context.MODE_PRIVATE)
            val json = prefs.getString("installed_patch_versions", "{}") ?: "{}"
            try {
                val obj = org.json.JSONObject(json)
                obj.put(packageName, patchVersion)
                prefs.edit().putString("installed_patch_versions", obj.toString()).apply()
            } catch (_: Exception) { }
        }

        // Record update history to avoid re-updates
        if (oldVersionCode > 0 && newVersionCode > oldVersionCode) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    updateHistoryDao.insertUpdate(
                        com.example.data.local.UpdateHistory(
                            packageName = packageName,
                            appName = appName,
                            oldVersionName = oldVersionName,
                            oldVersionCode = oldVersionCode,
                            newVersionName = newVersionName,
                            newVersionCode = newVersionCode
                        )
                    )
                } catch (e: Exception) {
                    Log.e("StoreViewModel", "Record update history failed", e)
                }
            }
        }
    }

    fun getInstalledPatchVersion(packageName: String): String {
        val prefs = getApplication<Application>().getSharedPreferences("drive_store_prefs", android.content.Context.MODE_PRIVATE)
        val json = prefs.getString("installed_patch_versions", "{}") ?: "{}"
        return try {
            val obj = org.json.JSONObject(json)
            obj.optString(packageName, "")
        } catch (_: Exception) { "" }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Refresh installed package list dynamically
    fun refreshInstalledPackages() {
        val packages = mutableMapOf<String, Int>()
        try {
            val installedInfo = packageManager.getInstalledPackages(0)
            for (info in installedInfo) {
                packages[info.packageName] = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt()
            }
            _installedPackages.value = packages
            Log.d("StoreViewModel", "Discovered ${packages.size} installed packages.")
            checkPendingInstall()
        } catch (e: Exception) {
            Log.e("StoreViewModel", "Failed to retrieve installed packages", e)
        }
    }



    // Live Google Drive synchronization (primary + shared folders)
    private fun fetchTotalUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isRegistered = appStorePrefs.getBoolean("user_registered", false)
                val endpoint = if (isRegistered) "get" else "hit"
                val url = "https://countapi.mileshilliard.com/api/v1/$endpoint/appstore-global-total"
                val client = OkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val value = org.json.JSONObject(bodyStr).optInt("value", -1)
                        if (value >= 0) {
                            _totalUsers.value = value
                            appStorePrefs.edit().putInt("total_users", value).apply()
                        }
                    }
                    if (!isRegistered) {
                        appStorePrefs.edit().putBoolean("user_registered", true).apply()
                    }
                } else {
                    Log.w("StoreViewModel", "CountAPI returned ${response.code}: ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "CountAPI request failed", e)
            }
        }
    }

    fun hasCachedSelfUpdate(): Boolean {
        val appStorePkg = "com.mahmuduls.appstore"
        val cachedVersionName = prefs.getString("self_update_version_name", "") ?: ""
        val cachedVersionCode = prefs.getInt("self_update_version_code", 0)
        if (cachedVersionName.isEmpty() || cachedVersionCode == 0) return false
        val installedCode = try {
            val info = packageManager.getPackageInfo(appStorePkg, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt()
        } catch (_: Exception) { return false }
        val installedName = try {
            packageManager.getPackageInfo(appStorePkg, 0).versionName ?: ""
        } catch (_: Exception) { return false }
        if (installedName.isEmpty()) return false
        return cachedVersionCode > installedCode
    }

    fun getCachedSelfUpdateApp(): AppItem {
        val cachedVersionName = prefs.getString("self_update_version_name", "") ?: ""
        val cachedVersionCode = prefs.getInt("self_update_version_code", 0)
        val existing = allApps.value.firstOrNull { it.packageName == "com.mahmuduls.appstore" }
        return AppItem(
            id = existing?.driveFileId ?: "self_app_store",
            packageName = "com.mahmuduls.appstore",
            name = "App Store",
            versionName = cachedVersionName,
            versionCode = cachedVersionCode,
            size = existing?.size ?: 0L,
            driveFileId = existing?.driveFileId ?: "",
            fileName = existing?.fileName ?: "",
            downloadUrl = existing?.downloadUrl ?: "",
            iconUrl = existing?.iconUrl ?: "",
            description = existing?.description ?: "",
            platform = "Android"
        )
    }

    fun syncSelfUpdateOnly(callback: () -> Unit) {
        if (_isSyncing.value) { callback(); return }
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                val k = driveApiKey
                repository.syncSubfolderFromDrive(_driveFolderId.value, "Android", k, "Android")
                repository.syncFromDrive(_driveFolderId.value, k, deleteStale = false)
                val selfUpdate = allApps.value.firstOrNull { it.packageName == "com.mahmuduls.appstore" }
                if (selfUpdate != null) {
                    prefs.edit()
                        .putString("self_update_version_name", selfUpdate.versionName)
                        .putInt("self_update_version_code", selfUpdate.versionCode)
                        .apply()
                }
                val contactMd = repository.fetchMdByName(_driveFolderId.value, "Contact.md", k)
                if (!contactMd.isNullOrEmpty()) {
                    prefs.edit().putString("cached_contact_md", contactMd).apply()
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Self-update check failed", e)
            } finally {
                _isSyncing.value = false
                callback()
            }
        }
    }

    fun forceSync(callback: (() -> Unit)? = null) {
        if (_isSyncing.value) { callback?.invoke(); return }
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                _syncMessage.value = "Syncing from Drive..."

                val k = driveApiKey

                val appStorePkg = "com.mahmuduls.appstore"

                val activeFileIds = mutableListOf<String>()

                // Fallback: sync root folder as Android first (covers pre-subfolder structure or partial Drive sync)
                repository.syncFromDrive(_driveFolderId.value, k, deleteStale = false).getOrNull()?.let { activeFileIds.addAll(it.map { a -> a.driveFileId }) }

                // Sync platform-specific subfolders (primary source) after root so subfolder copies
                // (which carry the icon PNG alongside the APK) win over root copies without an icon
                repository.syncSubfolderFromDrive(_driveFolderId.value, "Android", k, "Android").getOrNull()?.let { activeFileIds.addAll(it.map { a -> a.driveFileId }) }
                repository.syncSubfolderFromDrive(_driveFolderId.value, "Windows", k, "Windows").getOrNull()?.let { activeFileIds.addAll(it.map { a -> a.driveFileId }) }

                for (folderId in _sharedFolderIds.value) {
                    // deleteStale=false here: the global cleanup below handles stale rows,
                    // otherwise this would wipe other folders' + manifest apps mid-sync
                    repository.syncFromDrive(folderId, k, deleteStale = false).getOrNull()?.let { activeFileIds.addAll(it.map { a -> a.driveFileId }) }
                }

                // Sync JSON manifest files from each folder
                for (folderId in listOf(_driveFolderId.value) + _sharedFolderIds.value) {
                    repository.syncJsonManifestFromDrive(folderId, k).forEach { activeFileIds.add(it.driveFileId) }
                }

                // Sync update apps from "update" subfolder of each shared folder
                val allUpdates = mutableListOf<AppItem>()
                for (folderId in listOf(_driveFolderId.value) + _sharedFolderIds.value) {
                    allUpdates.addAll(repository.syncUpdatesFromDrive(folderId, k))
                }
                _updateApps.value = allUpdates.distinctBy { it.packageName }

                // Clean up apps deleted from Drive (after all syncs collected active file IDs)
                repository.deleteStaleApps(activeFileIds)

                val updateEnabled = repository.isUpdateCheckEnabled(_driveFolderId.value, k)
                appStorePrefs.edit().putBoolean("update_check_enabled", updateEnabled).apply()

                _syncMessage.value = "Sync complete!"
                val selfUpdate = allApps.value.firstOrNull { it.packageName == appStorePkg }
                if (selfUpdate != null) {
                    prefs.edit()
                        .putString("self_update_version_name", selfUpdate.versionName)
                        .putInt("self_update_version_code", selfUpdate.versionCode)
                        .apply()
                    if (selfUpdate.description.startsWith("__MD_ID:")) {
                        val mdFileId = selfUpdate.description.removePrefix("__MD_ID:")
                        viewModelScope.launch {
                            val content = repository.fetchMdContent(mdFileId, k)
                            if (!content.isNullOrEmpty()) {
                                prefs.edit().putString("cached_md_$mdFileId", content).apply()
                            }
                        }
                    }
                }
                val appsJson = allApps.value.joinToString("|||") { app ->
                    "${app.packageName}||${app.name}||${app.versionName}||${app.versionCode}||${app.driveModifiedTime}||${app.patchVersion}"
                }
                prefs.edit().putString("cached_apps_list", appsJson).apply()
                viewModelScope.launch {
                    val contactMd = repository.fetchMdByName(_driveFolderId.value, "Contact.md", k)
                    if (!contactMd.isNullOrEmpty()) {
                        prefs.edit().putString("cached_contact_md", contactMd).apply()
                    }
                }
                _syncCount.value++
                checkForUpdatesAndNotify()
                fetchTotalUsers()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Force sync failed", e)
                _syncMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
                callback?.invoke()
                delay(2000)
                _syncMessage.value = null
            }
        }
    }

    // Trigger local download and prompt standard installation dialog
    fun startDownload(app: AppItem, retryCount: Int = 3) {
        try {
            if (activeDownloadJobs.containsKey(app.packageName)) return
        } catch (e: Exception) {
            Log.e("StoreViewModel", "startDownload check failed", e)
            _downloadStates.update { it + (app.packageName to DownloadState.Error("Start failed: ${e.message}")) }
            return
        }

        val downloadsDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: getApplication<Application>().cacheDir
        // Original Drive filename (sanitized), fallback to legacy naming
        val targetName = app.fileName.takeIf { it.isNotBlank() }?.replace("/", "_")?.trim()?.takeIf { it.isNotBlank() }
            ?: "${app.packageName}_${app.versionCode}.apk"
        val skipValidation = app.platform != "Android" || !targetName.endsWith(".apk", ignoreCase = true)
        // Delete old downloaded files for this package (other versions, legacy naming)
        downloadsDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("${app.packageName}_") && f.name != targetName && (f.name.endsWith(".apk") || f.name.endsWith(".tmp"))) {
                f.delete()
            }
        }
        val existingFile = File(downloadsDir, targetName)
        if (existingFile.exists()) {
            if (skipValidation || isValidApk(existingFile)) {
                _downloadStates.update { it + (app.packageName to DownloadState.Completed) }
                saveDownloadTime(app.packageName)
                trackInstalledPackage(app.packageName)
                installOrResolveConflict(existingFile, app.packageName)
                markPackageUpdated(app.packageName, app.versionCode, app.patchVersion)
                viewModelScope.launch {
                    delay(3000L)
                    _downloadStates.update { it + (app.packageName to DownloadState.Idle) }
                }
                return
            } else {
                existingFile.delete()
                Log.e("StoreViewModel", "Deleted corrupted cached APK for ${app.packageName}, re-downloading")
            }
        }

        val tmpFile = File(downloadsDir, "$targetName.tmp")

        val downloadUrls = if (app.driveFileId.startsWith("manifest_")) {
            // Manifest URL-apps: download straight via their downloadUrl
            listOfNotNull(app.downloadUrl.takeIf { it.isNotBlank() })
        } else {
            listOfNotNull(
                "https://drive.google.com/uc?export=download&id=${app.driveFileId}",
                app.downloadUrl
            )
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            var lastError: Exception? = null
            for (attempt in 0..retryCount) {
                for (url in downloadUrls) {
                    try {
                        if (attempt > 0) delay(3000L * (1 shl (attempt - 1)))
                        _downloadStates.update { it + (app.packageName to DownloadState.Progress(0f, 0, app.size)) }

                        if (existingFile.exists()) existingFile.delete()
                        if (tmpFile.exists()) tmpFile.delete()

                        val client = OkHttpClient.Builder()
                            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                        val request = Request.Builder().url(url).build()
                        val call = client.newCall(request)
                        activeCalls[app.packageName] = call
                        val response = call.execute()

                        if (!response.isSuccessful) {
                            throw Exception("Server returned code ${response.code}")
                        }

                        val body = response.body ?: throw Exception("Empty response body")
                        val totalBytes = if (body.contentLength() > 0) body.contentLength() else app.size

                                body.byteStream().use { inputStream ->
                                    FileOutputStream(tmpFile).use { outputStream ->
                                        val buffer = ByteArray(65536)
                                        var bytesDownloaded: Long = 0

                                        while (true) {
                                            kotlinx.coroutines.yield()
                                            val bytesRead = inputStream.read(buffer)
                                            if (bytesRead == -1) break
                                            outputStream.write(buffer, 0, bytesRead)
                                            bytesDownloaded += bytesRead
                                            val percent = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes) else 0f
                                            _downloadStates.update {
                                                it + (app.packageName to DownloadState.Progress(percent, bytesDownloaded, totalBytes))
                                            }
                                        }
                                        outputStream.fd.sync()
                                    }
                                }

                                tmpFile.renameTo(existingFile)

                        if (!skipValidation && !isValidApk(existingFile)) {
                            existingFile.delete()
                            throw Exception("Downloaded APK is corrupted")
                        }

                        _downloadStates.update { it + (app.packageName to DownloadState.Completed) }
                        saveDownloadTime(app.packageName)
                        activeDownloadJobs.remove(app.packageName)
                        activeCalls.remove(app.packageName)

                        // Read actual version from APK manifest for accurate update tracking
                        var actualVersionCode: Int = app.versionCode
                        var actualVersionName: String = app.versionName
                        if (!skipValidation) {
                            try {
                                val apkInfo = getApplication<Application>().packageManager.getPackageArchiveInfo(existingFile.absolutePath, 0)
                                if (apkInfo != null) {
                                    actualVersionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(apkInfo).toInt()
                                    actualVersionName = apkInfo.versionName ?: app.versionName
                                }
                            } catch (_: Exception) { }
                        }

                        NotificationHelper.showDownloadCompleteNotification(getApplication(), app.name, app.packageName, true)
                        trackInstalledPackage(app.packageName)
                        if (app.packageName == getApplication<Application>().packageName) {
                            val selfApkFile = java.io.File(downloadsDir, targetName)
                            viewModelScope.launch {
                                delay(600_000L)
                                if (selfApkFile.exists()) selfApkFile.delete()
                            }
                            scheduleRelaunch()
                        }
                        installOrResolveConflict(existingFile, app.packageName)
                        markPackageUpdated(app.packageName, actualVersionCode, app.patchVersion)
                        // Update database with actual APK version for accurate future comparisons
                        viewModelScope.launch {
                            repository.updateAppDetails(
                                packageName = app.packageName,
                                name = app.name,
                                versionName = actualVersionName,
                                versionCode = actualVersionCode,
                                size = app.size,
                                downloadUrl = app.downloadUrl,
                                iconUrl = app.iconUrl,
                                description = app.description,
                                category = app.category,
                                driveModifiedTime = app.driveModifiedTime,
                                patchVersion = app.patchVersion,
                                updateCheck = app.updateCheck
                            )
                        }
                        delay(3000L)
                        _downloadStates.update { it + (app.packageName to DownloadState.Idle) }
                        return@launch

                    } catch (e: java.util.concurrent.CancellationException) {
                        _downloadStates.update { it + (app.packageName to DownloadState.Idle) }
                        activeDownloadJobs.remove(app.packageName)
                        activeCalls.remove(app.packageName)
                        if (tmpFile.exists()) tmpFile.delete()
                        return@launch
                    } catch (e: Exception) {
                        if (tmpFile.exists()) tmpFile.delete()
                        activeCalls.remove(app.packageName)
                        lastError = e
                        Log.e("StoreViewModel", "Download attempt ${attempt + 1}/${retryCount + 1} of ${app.name} via $url failed: ${e.message}", e)
                    }
                }
            }
            Log.e("StoreViewModel", "Download of ${app.name} failed after all attempts", lastError)
            _downloadStates.update { it + (app.packageName to DownloadState.Error(lastError?.localizedMessage ?: "Unknown Error")) }
            activeDownloadJobs.remove(app.packageName)
            activeCalls.remove(app.packageName)
            NotificationHelper.showDownloadCompleteNotification(getApplication(), app.name, app.packageName, false)
            delay(600000L)
            if (_downloadStates.value[app.packageName] is DownloadState.Error) {
                startDownload(app, retryCount)
            }
        }
        activeDownloadJobs[app.packageName] = job
    }

    fun cancelAllDownloads() {
        val pkgs = activeDownloadJobs.keys.toSet() + activeCalls.keys.toSet()
        for (pkg in pkgs) cancelDownload(pkg)
    }

    fun cancelDownload(packageName: String) {
        activeCalls[packageName]?.cancel()
        activeCalls.remove(packageName)
        activeDownloadJobs[packageName]?.cancel()
        activeDownloadJobs.remove(packageName)
        try {
            val app = getApplication<Application>()
            val targetTmp = allApps.value.firstOrNull { it.packageName == packageName }
                ?.fileName?.takeIf { it.isNotBlank() }?.let { "$it.tmp" }
            val dirs = listOfNotNull(
                app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                app.cacheDir,
                app.externalCacheDir
            )
            for (dir in dirs) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("${packageName}_") && file.name.endsWith(".tmp")) {
                        file.delete()
                    }
                    if (targetTmp != null && file.name == targetTmp) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
        _downloadStates.update { it + (packageName to DownloadState.Idle) }
    }

    private fun hasSignatureConflict(apkFile: File, packageName: String): Boolean {
        if (!_installedPackages.value.containsKey(packageName)) return false
        val context = getApplication<Application>()
        return try {
            val installedInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val apkInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            if (installedInfo == null || apkInfo == null) return false
            val installedSigs = installedInfo.signatures?.map { it.toCharsString() }?.sorted() ?: return false
            val apkSigs = apkInfo.signatures?.map { it.toCharsString() }?.sorted() ?: return false
            installedSigs != apkSigs
        } catch (_: Exception) {
            false
        }
    }

    // Sideload package standard installer helper
    private fun triggerInstallation(file: File, packageName: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val context = getApplication<Application>()
                val authority = "${context.packageName}.fileprovider"
                val apkUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)

                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to start installation", e)
            }
        }
    }

    private fun uninstallFirstThenInstall(file: File, packageName: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val context = getApplication<Application>()
                val uninstallIntent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)
                prefs.edit().putString("pending_install_path", file.absolutePath).putString("pending_install_pkg", packageName).apply()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to start uninstall", e)
            }
        }
    }

    private fun checkPendingInstall() {
        val path = prefs.getString("pending_install_path", null) ?: return
        val pkg = prefs.getString("pending_install_pkg", null) ?: return
        prefs.edit().remove("pending_install_path").remove("pending_install_pkg").apply()
        if (!_installedPackages.value.containsKey(pkg)) {
            val file = File(path)
            if (file.exists()) {
                triggerInstallation(file, pkg)
            }
        }
    }

    private fun isValidApk(file: File): Boolean {
        return try {
            val context = getApplication<Application>()
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_ACTIVITIES) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun installOrResolveConflict(file: File, packageName: String) {
        if (!isValidApk(file)) {
            file.delete()
            Log.e("StoreViewModel", "Corrupted APK deleted for $packageName, will re-download")
            return
        }
        if (hasSignatureConflict(file, packageName)) {
            uninstallFirstThenInstall(file, packageName)
        } else {
            triggerInstallation(file, packageName)
        }
    }

    // Install a previously downloaded APK
    fun installDownloadedApp(packageName: String) {
        val state = _downloadStates.value[packageName]
        if (state is DownloadState.Downloaded) {
            val file = File(state.filePath)
            if (file.exists()) {
                trackInstalledPackage(packageName)
                installOrResolveConflict(file, packageName)
            }
        }
    }

    private fun trackInstalledPackage(packageName: String) {
        val existing = getInstalledByStorePackages().toMutableSet()
        existing.add(packageName)
        val json = org.json.JSONArray(existing.toList()).toString()
        prefs.edit().putString("installed_by_store", json).apply()
    }

    fun getInstalledByStorePackages(): Set<String> {
        val json = prefs.getString("installed_by_store", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun removeTrackedPackage(packageName: String) {
        val existing = getInstalledByStorePackages().toMutableSet()
        existing.remove(packageName)
        val json = org.json.JSONArray(existing.toList()).toString()
        prefs.edit().putString("installed_by_store", json).apply()
    }

    // Clear all app caches and database
    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                // Delete all downloaded APKs
                val downloadsDir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir?.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".apk") || file.name.endsWith(".tmp")) {
                        file.delete()
                    }
                }
                // Clear Room database
                app.deleteDatabase("drive_store_database")
                app.externalCacheDir?.deleteRecursively()
                app.cacheDir.deleteRecursively()
                com.example.data.local.AppDatabase.getDatabase(app).appDao.clearAll()
                // Re-populate defaults
                repository.forceRepopulate()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Clear cache failed: ${e.message}", e)
            }
        }
    }

    // Delete downloaded APK files older than 1 day
    fun cleanupOldDownloads() {
        if (!_autoDeleteDownloads.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val daysStr = appStorePrefs.getString("auto_delete_days", "1") ?: "7"
                val days = daysStr.toIntOrNull() ?: 7
                val cutoff = System.currentTimeMillis() - days * 86400000L
                val dirs = listOfNotNull(
                    app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    app.cacheDir,
                    app.externalCacheDir
                )
                for (dir in dirs) {
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { file ->
                            if ((file.name.endsWith(".apk") || file.name.endsWith(".zip") || file.name.endsWith(".tmp")) && file.lastModified() < cutoff) {
                                file.delete()
                                Log.d("StoreViewModel", "Deleted old download: ${file.name}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Cleanup old downloads failed", e)
            }
        }
    }

    fun deleteAllDownloads(onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val dirs = listOfNotNull(
                    app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    app.cacheDir,
                    app.externalCacheDir
                )
                for (dir in dirs) {
                    dir.listFiles()?.forEach { file ->
                        val name = file.name
                        if (name.endsWith(".apk") || name.endsWith(".tmp")) {
                            val pkgFromName = name.removeSuffix(".apk").removeSuffix(".tmp").split("_").dropLast(1).joinToString("_")
                            val state = _downloadStates.value[pkgFromName]
                            if (state !is DownloadState.Progress && state !is DownloadState.Completed) {
                                file.delete()
                            }
                        }
                    }
                }
                _downloadStates.update { current ->
                    current.mapValues { (pkg, state) ->
                        if (state is DownloadState.Downloaded && !activeDownloadJobs.containsKey(pkg)) DownloadState.Idle else state
                    }
                }
                withContext(Dispatchers.Main) { onDone() }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    fun deleteDownloadedFile(packageName: String) {
        _downloadStates.update { it + (packageName to DownloadState.Idle) }
    }

    fun saveDownloadTime(packageName: String, time: Long = System.currentTimeMillis()) {
        val json = appStorePrefs.getString("download_times", "{}") ?: "{}"
        val obj = try { org.json.JSONObject(json) } catch (_: Exception) { org.json.JSONObject() }
        obj.put(packageName, time)
        appStorePrefs.edit().putString("download_times", obj.toString()).apply()
    }

    fun getDownloadTime(packageName: String): Long {
        val json = appStorePrefs.getString("download_times", "{}") ?: "{}"
        return try {
            val obj = org.json.JSONObject(json)
            if (obj.has(packageName)) obj.getLong(packageName) else 0L
        } catch (_: Exception) { 0L }
    }

    // Toggle favorite bookmark state
    fun toggleFavorite(packageName: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(packageName, isFavorite)
        }
    }

    // Delete an application from local database
    fun deleteApp(packageName: String) {
        viewModelScope.launch {
            repository.deleteApp(packageName)
        }
    }

    fun uploadApkToDrive(apkFile: java.io.File, fileName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = driveApiKey
                if (apiKey.isEmpty()) {
                    onResult(false, "API key not set")
                    return@launch
                }
                val boundary = "Boundary_" + System.currentTimeMillis()
                val mediaBody = apkFile.readBytes().toRequestBody(
                    "application/vnd.android.package-archive".toMediaType()
                )
                val jsonHeaders = okhttp3.Headers.Builder()
                    .add("Content-Type", "application/json; charset=UTF-8")
                    .build()
                val apkHeaders = okhttp3.Headers.Builder()
                    .add("Content-Type", "application/vnd.android.package-archive")
                    .build()
                val multipartBody = okhttp3.MultipartBody.Builder(boundary)
                    .setType(okhttp3.MultipartBody.FORM)
                    .addPart(
                        jsonHeaders,
                        """{"name": "$fileName", "parents": ["$PRIMARY_DRIVE_FOLDER_ID"]}""".toRequestBody(null)
                    )
                    .addPart(apkHeaders, mediaBody)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&key=$apiKey")
                    .post(multipartBody)
                    .build()
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val idPattern = "\"id\":\\s*\"([^\"]+)\"".toRegex()
                    val fileId = idPattern.find(body)?.groupValues?.get(1) ?: "unknown"
                    onResult(true, "Uploaded successfully (ID: $fileId)")
                } else {
                    onResult(false, "Upload failed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                onResult(false, "Upload error: ${e.message}")
            }
        }
    }

    private fun getLastNotifiedModTime(pkg: String): Long {
        val json = prefs.getString("notified_mod_times", "{}") ?: "{}"
        return try { org.json.JSONObject(json).optLong(pkg, 0L) } catch (_: Exception) { 0L }
    }

    private fun setLastNotifiedModTime(pkg: String, time: Long) {
        try {
            val json = prefs.getString("notified_mod_times", "{}") ?: "{}"
            val obj = org.json.JSONObject(json)
            obj.put(pkg, time)
            prefs.edit().putString("notified_mod_times", obj.toString()).apply()
        } catch (_: Exception) { }
    }

    fun isUpdateAvailable(app: AppItem, installedCode: Int, installedName: String): Boolean {
        if (installedName.isEmpty()) return false
        if (!app.updateCheck) return false
        if (!appStorePrefs.getBoolean("update_check_enabled", true)) return false
        val lastSeen = getLastNotifiedModTime(app.packageName)
        val installedPatch = getInstalledPatchVersion(app.packageName)
        return isVersionNewer(app.versionName, installedName, app.versionCode, installedCode, app.driveModifiedTime, lastSeen, app.patchVersion, installedPatch)
    }

    // Scan database & check what's installed to locate any pending updates and raise Android notifications
    fun checkForUpdatesAndNotify() {
        if (!_autoUpdateCheck.value) return
        val installed = _installedPackages.value
        val allAvailableApps = allApps.value
        
        if (installed.isEmpty() && allAvailableApps.isEmpty()) return

        // Also check app store self-update from update folder
        val selfPkg = "com.mahmuduls.appstore"
        val selfUpdate = _updateApps.value.firstOrNull { it.packageName == selfPkg }
        if (selfUpdate != null && (installed[selfPkg] ?: 0) < selfUpdate.versionCode) {
            if (notifiedUpdates.add(selfPkg)) {
                NotificationHelper.showUpdateNotification(
                    context = getApplication(),
                    appName = "App Store",
                    versionName = selfUpdate.versionName,
                    packageName = selfPkg
                )
            }
        }

        for (app in allAvailableApps) {
            if (!installed.containsKey(app.packageName)) continue
            try {
                val pkgInfo = getApplication<Application>().packageManager.getPackageInfo(app.packageName, 0)
                val installedVerName = pkgInfo.versionName ?: ""
                val installedVerCode = installed[app.packageName] ?: 0
                val lastNotified = getLastNotifiedModTime(app.packageName)
                val installedPatch = getInstalledPatchVersion(app.packageName)
                if (installedVerName.isNotEmpty() && isVersionNewer(app.versionName, installedVerName, app.versionCode, installedVerCode, app.driveModifiedTime, lastNotified, app.patchVersion, installedPatch)) {
                    if (notifiedUpdates.add(app.packageName)) {
                        NotificationHelper.showUpdateNotification(
                            context = getApplication(),
                            appName = app.name,
                            versionName = app.versionName,
                            packageName = app.packageName
                        )
                        setLastNotifiedModTime(app.packageName, app.driveModifiedTime)
                        Log.d("StoreViewModel", "Notification fired for pending update of: ${app.name}")
                    }
                }
            } catch (_: Exception) { }
        }
    }
}

private fun isVersionNewer(driveVersion: String, installedVersion: String, driveCode: Int = 0, installedCode: Int = 0, driveModifiedTime: Long = 0L, lastNotifiedTime: Long = 0L, drivePatchVersion: String = "", installedPatchVersion: String = ""): Boolean {
    if (driveCode != installedCode) return driveCode > installedCode
    val driveParts = driveVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val installedParts = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val hasHuge = { parts: List<Int> -> parts.any { it >= 10000 } }
    if (!hasHuge(driveParts) && !hasHuge(installedParts)) {
        val maxLen = maxOf(driveParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val d = driveParts.getOrElse(i) { 0 }
            val inst = installedParts.getOrElse(i) { 0 }
            if (d != inst) return d > inst
        }
    }
    if (drivePatchVersion.isNotEmpty() && installedPatchVersion.isNotEmpty()) {
        val drivePatchParts = drivePatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val installedPatchParts = installedPatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val maxPatchLen = maxOf(drivePatchParts.size, installedPatchParts.size)
        for (i in 0 until maxPatchLen) {
            val d = drivePatchParts.getOrElse(i) { 0 }
            val inst = installedPatchParts.getOrElse(i) { 0 }
            if (d != inst) return d > inst
        }
    }
    return false
}
