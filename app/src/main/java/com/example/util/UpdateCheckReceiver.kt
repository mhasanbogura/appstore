package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.example.data.local.AppDatabase
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("drive_store_prefs", Context.MODE_PRIVATE)
        val appPrefs = context.getSharedPreferences("app_store_prefs", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("auto_update_check", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        }

        NotificationHelper.init(context)

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = prefs.getString("api_key", "AIzaSyA4ymjFIbuGVhFsKjxVV46RT-qWqNHNiY4") ?: "AIzaSyA4ymjFIbuGVhFsKjxVV46RT-qWqNHNiY4"
                val folderId = "1PBrhSIvDk0QrgNS6XeTeA1RDLFPeTqKV"
                val db = AppDatabase.getDatabase(context)
                val repository = AppRepository(context, db.appDao)

                withTimeout(10000) {
                    repository.syncJsonManifestFromDrive(folderId, apiKey)
                    repository.syncUpdatesFromDrive(folderId, apiKey)
                }

                val apps = repository.allApps.first()
                prefs.edit().putString("cached_apps_list", apps.joinToString("|||") { app ->
                    "${app.packageName}||${app.name}||${app.versionName}||${app.versionCode}||${app.driveModifiedTime}||${app.patchVersion}||${app.driveFileId}||${app.downloadUrl}||${app.fileName}"
                }).apply()
                checkAndNotify(context, prefs, apps)
            } catch (_: Exception) {
                val appsJson = prefs.getString("cached_apps_list", "") ?: ""
                if (appsJson.isNotEmpty()) {
                    val apps = appsJson.split("|||").mapNotNull { entry ->
                        val parts = entry.split("||")
                        if (parts.size < 6) return@mapNotNull null
                        CachedApp(parts[0], parts[1], parts[2], parts[3].toIntOrNull() ?: return@mapNotNull null, parts[4].toLongOrNull() ?: 0L, parts[5], parts.getOrElse(6) { "" }, parts.getOrElse(7) { "" }, parts.getOrElse(8) { "" })
                    }
                    checkAndNotify(context, prefs, apps)
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun checkAndNotify(context: Context, prefs: android.content.SharedPreferences, apps: List<*>): Boolean {
        val installed = try {
            context.packageManager.getInstalledPackages(0).associate {
                it.packageName to PackageInfoCompat.getLongVersionCode(it).toInt()
            }
        } catch (_: Exception) { return false }
        if (installed.isEmpty()) return false

        val selfPkg = "com.mahmuduls.appstore"
        val notifiedSet = mutableSetOf<String>()
        var hadFailures = false

        for (item in apps) {
            val app = if (item is CachedApp) item else {
                val a = item as? com.example.data.local.AppItem ?: continue
                CachedApp(a.packageName, a.name, a.versionName, a.versionCode, a.driveModifiedTime, a.patchVersion, a.driveFileId, a.downloadUrl, a.fileName)
            }
            if (!installed.containsKey(app.packageName)) continue
            try {
                val pkgInfo = context.packageManager.getPackageInfo(app.packageName, 0)
                val installedVerName = pkgInfo.versionName ?: ""
                val installedVerCode = installed[app.packageName] ?: 0
                val lastNotified = prefs.getLong("last_notified_${app.packageName}", 0L)
                val installedPatch = getInstalledPatchVersion(context, app.packageName)

                if (installedVerName.isNotEmpty() && isVersionNewer(
                        app.versionName, installedVerName,
                        app.versionCode, installedVerCode,
                        app.driveModifiedTime, lastNotified,
                        app.patchVersion, installedPatch
                    )
                ) {
                    if (notifiedSet.add("bg_${app.packageName}") && app.driveModifiedTime > lastNotified) {
                        val autoDownload = prefs.getBoolean("auto_download_updates", true)
                        if (autoDownload) {
                            val file = AutoDownloadHelper.downloadApk(
                                context, app.driveFileId, app.downloadUrl,
                                app.packageName, app.versionCode
                            )
                            if (file != null) {
                                NotificationHelper.showReadyToInstallNotification(
                                    context, app.name, app.versionName,
                                    app.packageName, file.absolutePath
                                )
                                prefs.edit().putLong("last_notified_${app.packageName}", app.driveModifiedTime).apply()
                                continue
                            }
                            hadFailures = true
                        }
                        NotificationHelper.showUpdateNotification(context, app.name, app.versionName, app.packageName)
                        prefs.edit().putLong("last_notified_${app.packageName}", app.driveModifiedTime).apply()
                    }
                }
            } catch (_: Exception) { }
        }

        val driverVerName = prefs.getString("self_update_version_name", null)
        val driverVerCode = prefs.getInt("self_update_version_code", 0)
        if (driverVerCode > (installed[selfPkg] ?: 0) && driverVerName != null) {
            if (notifiedSet.add("bg_$selfPkg")) {
                NotificationHelper.showUpdateNotification(context, "App Store", driverVerName, selfPkg)
            }
        }

        val intervalMinutes = AutoDownloadHelper.checkIntervalMinutes(prefs).coerceAtLeast(5)
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, intervalMinutes) }
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val alarmIntent = android.app.PendingIntent.getBroadcast(context, 0, Intent(context, UpdateCheckReceiver::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try {
            alarmMgr.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent)
        } catch (_: SecurityException) {
            alarmMgr.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, intervalMinutes * 60000L, alarmIntent)
        }

        if (hadFailures && prefs.getBoolean("auto_download_updates", true)) {
            // Retry failed downloads after the check interval in background
            val retryIntent = android.app.PendingIntent.getBroadcast(context, 999, Intent(context, UpdateCheckReceiver::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            try {
                alarmMgr.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMinutes * 60000L, retryIntent)
            } catch (_: Exception) { }
        }
        return hadFailures
    }

    private data class CachedApp(
        val packageName: String, val name: String, val versionName: String,
        val versionCode: Int, val driveModifiedTime: Long, val patchVersion: String,
        val driveFileId: String = "", val downloadUrl: String = "", val fileName: String = ""
    )

    private fun getInstalledPatchVersion(context: Context, packageName: String): String {
        return context.getSharedPreferences("installed_patch_versions", Context.MODE_PRIVATE).getString(packageName, "") ?: ""
    }

    private fun isVersionNewer(
        driveVersion: String, installedVersion: String,
        driveCode: Int, installedCode: Int,
        driveModifiedTime: Long, lastNotifiedTime: Long,
        drivePatchVersion: String, installedPatchVersion: String
    ): Boolean {
        if (driveCode != installedCode) return driveCode > installedCode
        val driveParts = driveVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val installedParts = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val hasHuge = { parts: List<Int> -> parts.any { it >= 10000 } }
        if (!hasHuge(driveParts) && !hasHuge(installedParts)) {
            val maxLen = maxOf(driveParts.size, installedParts.size)
            for (i in 0 until maxLen) {
                val d = driveParts.getOrElse(i) { 0 }; val ins = installedParts.getOrElse(i) { 0 }
                if (d != ins) return d > ins
            }
        }
        if (drivePatchVersion.isNotEmpty() && installedPatchVersion.isNotEmpty()) {
            val dp = drivePatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val ip = installedPatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(dp.size, ip.size)
            for (i in 0 until maxLen) { val d = dp.getOrElse(i) { 0 }; val ins = ip.getOrElse(i) { 0 }; if (d != ins) return d > ins }
        }
        return false
    }
}
