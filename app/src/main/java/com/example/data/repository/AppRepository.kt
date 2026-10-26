package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDao
import com.example.data.local.AppItem
import com.example.data.remote.DriveApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppRepository(
    private val context: Context,
    private val appDao: AppDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val driveService = retrofit.create(DriveApiService::class.java)

    // Exposed Flows
    val allApps: Flow<List<AppItem>> = appDao.getAllApps()
    val favoriteApps: Flow<List<AppItem>> = appDao.getFavoriteApps()

    fun searchApps(query: String): Flow<List<AppItem>> {
        return appDao.searchApps(query)
    }

    suspend fun getAppByPackage(packageName: String): AppItem? = withContext(Dispatchers.IO) {
        appDao.getAppByPackage(packageName)
    }

    suspend fun toggleFavorite(packageName: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        appDao.updateFavorite(packageName, isFavorite)
    }

    suspend fun deleteApp(packageName: String) = withContext(Dispatchers.IO) {
        appDao.deleteApp(packageName)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        appDao.clearAll()
    }

    suspend fun forceRepopulate() {
        prepopulateDefaultApps()
    }

    suspend fun fetchMdContent(mdFileId: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$mdFileId?alt=media&key=$apiKey"
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string()?.trim() else null
        } catch (_: Exception) { null }
    }

    suspend fun fetchMdByName(folderId: String, fileName: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "'$folderId' in parents and name='$fileName' and trashed=false"
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&key=$apiKey&fields=files(id)"
            val client = OkHttpClient()
            val listRequest = okhttp3.Request.Builder().url(listUrl).build()
            val listResponse = client.newCall(listRequest).execute()
            if (!listResponse.isSuccessful) return@withContext null
            val body = listResponse.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(body)
            val files = json.optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) return@withContext null
            val fileId = files.getJSONObject(0).getString("id")
            fetchMdContent(fileId, apiKey)
        } catch (_: Exception) { null }
    }

    suspend fun insertCustomApp(app: AppItem) = withContext(Dispatchers.IO) {
        appDao.insertApps(listOf(app))
    }

    suspend fun updateAppDetails(
        packageName: String,
        name: String,
        versionName: String,
        versionCode: Int,
        size: Long,
        downloadUrl: String,
        iconUrl: String,
        description: String,
        category: String,
        driveModifiedTime: Long = 0L,
        patchVersion: String = "",
        updateCheck: Boolean = true
    ) = withContext(Dispatchers.IO) {
        appDao.updateAppDetails(
            packageName = packageName,
            name = name,
            versionName = versionName,
            versionCode = versionCode,
            size = size,
            downloadUrl = downloadUrl,
            iconUrl = iconUrl,
            description = description,
            category = category,
            lastUpdated = System.currentTimeMillis(),
            driveModifiedTime = driveModifiedTime,
            patchVersion = patchVersion,
            updateCheck = updateCheck
        )
    }

    init {
        // Runs prepopulate on startup if database is empty
        repositoryScope.launch {
            try {
                val currentApps = allApps.first()
                if (currentApps.isEmpty()) {
                    Log.d("AppRepository", "Prepopulating app database...")
                    prepopulateDefaultApps()
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Prepopulate failed", e)
            }
        }
    }

    private suspend fun prepopulateDefaultApps() = withContext(Dispatchers.IO) {
        // No hardcoded apps — all apps come from Drive sync
    }

    // Check if there's a marker file named _update_off in the Drive folder
    suspend fun isUpdateCheckEnabled(folderId: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || folderId.isEmpty()) return@withContext true
        try {
            val query = "'$folderId' in parents and trashed = false and name = '_update_off.md'"
            val response = driveService.listFiles(query = query, fields = "files(id)", apiKey = apiKey)
            response.files.isEmpty()
        } catch (_: Exception) { true }
    }

    // Performs live sync from Google Drive if a folder ID is supplied
    suspend fun findSubfolderId(parentFolderId: String, folderName: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || parentFolderId.isEmpty()) return@withContext null
        try {
            val query = "'$parentFolderId' in parents and trashed = false and mimeType = 'application/vnd.google-apps.folder' and name = '$folderName'"
            val response = driveService.listFiles(query = query, apiKey = apiKey)
            response.files.firstOrNull()?.id
        } catch (_: Exception) { null }
    }

    suspend fun syncFromDrive(folderId: String, apiKey: String, deleteStale: Boolean = true): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || folderId.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Folder ID and API Key required for real sync"))
        }
        try {
            val apps = syncDriveFolder(folderId, apiKey, insertIntoDb = true, deleteStale = deleteStale)
            Result.success(apps)
        } catch (e: Exception) {
            Log.e("AppRepository", "Drive Sync failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncSubfolderFromDrive(parentFolderId: String, folderName: String, apiKey: String, platform: String): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || parentFolderId.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val subId = findSubfolderId(parentFolderId, folderName, apiKey)
            if (subId != null) {
                val apps = syncDriveFolder(subId, apiKey, insertIntoDb = true, platform = platform, deleteStale = false)
                Result.success(apps)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Sync subfolder $folderName failed", e)
            Result.success(emptyList())
        }
    }

    suspend fun deleteStaleApps(activeFileIds: List<String>) = withContext(Dispatchers.IO) {
        val validIds = activeFileIds.filter { it.isNotBlank() }
        if (validIds.isNotEmpty()) {
            appDao.deleteStaleApps(validIds)
        }
    }

    suspend fun syncUpdatesFromDrive(parentFolderId: String, apiKey: String): List<AppItem> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || parentFolderId.isEmpty()) return@withContext emptyList()
        try {
            val folderQuery = "'$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and name = 'update' and trashed = false"
            val folderResponse = driveService.listFiles(query = folderQuery, fields = "files(id, name)", apiKey = apiKey)
            val updateFolder = folderResponse.files.firstOrNull() ?: return@withContext emptyList()
            syncDriveFolder(updateFolder.id, apiKey, insertIntoDb = false)
        } catch (e: Exception) {
            Log.e("AppRepository", "Updates sync failed", e)
            emptyList()
        }
    }

    suspend fun syncJsonManifestFromDrive(parentFolderId: String, apiKey: String): List<AppItem> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || parentFolderId.isEmpty()) return@withContext emptyList()
        try {
            // Manifest + its md/icon files live in the dedicated Web subfolder only
            val webId = try {
                driveService.listFiles(
                    query = "'$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and name = 'Web' and trashed = false",
                    fields = "files(id)",
                    apiKey = apiKey
                ).files.firstOrNull()?.id
            } catch (_: Exception) { null } ?: return@withContext emptyList()
            val query = "'$webId' in parents and name = 'store_manifest.json' and trashed = false"
            val response = driveService.listFiles(query = query, fields = "files(id, name)", apiKey = apiKey)
            val manifestFile = response.files.firstOrNull() ?: return@withContext emptyList()

            val mediaUrl = "https://www.googleapis.com/drive/v3/files/${manifestFile.id}?alt=media&key=$apiKey"
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder().url(mediaUrl).build()
            val mediaResponse = client.newCall(request).execute()
            if (!mediaResponse.isSuccessful) return@withContext emptyList()

            val jsonStr = mediaResponse.body?.string() ?: return@withContext emptyList()
            val jsonArr = org.json.JSONArray(jsonStr)
            // Resolve md/icon filenames inside the Web subfolder (for shared url/md/icon entries)
            val siblingFiles = try {
                driveService.listFiles(
                    query = "'$webId' in parents and trashed = false",
                    fields = "files(id, name, mimeType)",
                    apiKey = apiKey
                ).files
            } catch (_: Exception) { emptyList() }
            val siblingMd = mutableMapOf<String, String>()
            siblingFiles
                .filter { it.name.endsWith(".md", ignoreCase = true) }
                .forEach { if (!siblingMd.containsKey(it.name.dropLast(3).trim())) siblingMd[it.name.dropLast(3).trim()] = it.id }
            val siblingIcons = mutableMapOf<String, String>()
            siblingFiles
                .filter { (it.mimeType ?: "").startsWith("image/") }
                .forEach { file ->
                    val key = file.name.removeSuffix(".png").removeSuffix(".PNG").removeSuffix(".jpg").removeSuffix(".jpeg").removeSuffix(".webp").trim()
                    if (!siblingIcons.containsKey(key)) siblingIcons[key] =
                        "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media&key=$apiKey"
                }
            fun siblingKey(name: String): String {
                var k = name.trim()
                if (k.endsWith(".md", ignoreCase = true)) k = k.dropLast(3)
                for (ext in listOf(".png", ".PNG", ".jpg", ".jpeg", ".webp")) {
                    if (k.endsWith(ext)) { k = k.dropLast(ext.length); break }
                }
                return k.trim()
            }
            // Size accepts bytes number or human string like "52.4 MB" / "1.2 GB" / "500 KB"
            fun parseManifestSize(obj: org.json.JSONObject): Long {
                if (!obj.has("size")) return 0L
                return when (val raw = obj.get("size")) {
                    is Number -> raw.toLong()
                    is String -> {
                        val m = Regex("""([\d.]+)\s*([KMGT]?B)?""", RegexOption.IGNORE_CASE).find(raw.trim())
                            ?: return 0L
                        val num = m.groupValues[1].toDoubleOrNull() ?: 0.0
                        val mult = when (m.groupValues[2].uppercase()) {
                            "GB" -> 1024L * 1024L * 1024L
                            "MB" -> 1024L * 1024L
                            "KB" -> 1024L
                            else -> 1L
                        }
                        (num * mult).toLong()
                    }
                    else -> 0L
                }
            }
            val apps = (0 until jsonArr.length()).mapNotNull { i ->
                try {
                    val obj = jsonArr.getJSONObject(i)
                    val pkgName = obj.optString("packageName", "").trim().ifBlank {
                        "com.manifest." + obj.optString("name", "app").lowercase()
                            .replace("[^a-z0-9]+".toRegex(), ".").trim('.')
                            .ifBlank { "app" }
                    }
                    val appName = obj.optString("name", "").trim().ifBlank { pkgName }
                    val mdKey = siblingKey(obj.optString("mdFileName", ""))
                    val mdId = siblingMd[mdKey]
                    val iconKey = siblingKey(obj.optString("iconFileName", ""))
                    val iconUrl = obj.optString("iconUrl", "").ifBlank {
                        siblingIcons[iconKey]
                            ?: "https://cdn-icons-png.flaticon.com/512/888/888857.png"
                    }
                    AppItem(
                        id = "manifest_${System.currentTimeMillis()}_$i",
                        packageName = pkgName,
                        name = appName,
                        versionName = obj.optString("versionName", "").trim().ifBlank { "1.0" },
                        versionCode = obj.optInt("versionCode", 1),
                        size = parseManifestSize(obj),
                        driveFileId = "manifest_${System.currentTimeMillis()}_$i",
                        fileName = obj.optString("fileName", ""),
                        downloadUrl = obj.optString("downloadUrl", ""),
                        iconUrl = iconUrl,
                        description = if (mdId != null) "__MD_ID:$mdId" else obj.optString("description", ""),
                        category = obj.optString("category", "Manifest"),
                        // Manifest entries are Windows apps only
                        platform = "Windows",
                        lastUpdated = System.currentTimeMillis()
                    ).takeIf { it.name.isNotBlank() }
                } catch (_: Exception) { null }
            }
            if (apps.isNotEmpty()) {
                appDao.insertApps(apps)
            }
            apps
        } catch (e: Exception) {
            Log.e("AppRepository", "JSON manifest sync failed", e)
            emptyList()
        }
    }

    private suspend fun syncDriveFolder(folderId: String, apiKey: String, insertIntoDb: Boolean, platform: String = "Android", deleteStale: Boolean = true): List<AppItem> {
        // Get all files (APKs + images + markdown) with pagination
        val queryStr = "'$folderId' in parents and trashed = false"
        val allFiles = mutableListOf<com.example.data.remote.DriveFile>()
        var pageToken: String? = null
        do {
            val response = driveService.listFiles(query = queryStr, apiKey = apiKey, pageToken = pageToken)
            allFiles.addAll(response.files)
            pageToken = response.nextPageToken
        } while (pageToken != null)
        if (allFiles.isEmpty()) return emptyList()

        val isWindows = platform == "Windows"
        val appFiles = allFiles.filter { file ->
            if (isWindows) file.mimeType == "application/zip" || file.name.endsWith(".zip", ignoreCase = true)
            else file.mimeType == "application/vnd.android.package-archive" || file.name.endsWith(".apk", ignoreCase = true)
        }
        val icons = allFiles.filter { it.mimeType.startsWith("image/") }
        val markdownFiles = allFiles.filter { it.name.endsWith(".md") }
        if (appFiles.isEmpty()) return emptyList()

        val iconMap = icons.mapNotNull { file ->
            val key = file.name.removeSuffix(".png").removeSuffix(".PNG").removeSuffix(".jpg").removeSuffix(".jpeg").removeSuffix(".webp").trim()
            key to "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media&key=$apiKey"
        }.toMap()

        // Record .md file IDs for lazy loading (content fetched on demand)
        val mdFileMap = markdownFiles.mapNotNull { mdFile ->
            val baseKey = mdFile.name.removeSuffix(".md").trim()
            baseKey to mdFile.id
        }.toMap()

        // Parse per-app update config from Update.md
        // Supported formats:
        //   "AppName_com.package.name"          → updates OFF (listed = off)
        //   "com.package.name = off"            → updates OFF
        //   "com.package.name = on"             → updates ON
        // Apps not listed default to "on"
        val configFile = allFiles.find { it.name.equals("Update.md", ignoreCase = true) }
        val updateDisabledPackages = if (configFile != null) {
            val configContent = fetchMdContent(configFile.id, apiKey)
            if (!configContent.isNullOrBlank()) {
                val listedPackages = mutableSetOf<String>()
                configContent.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .forEach { line ->
                        val eqParts = line.split("=", limit = 2)
                        if (eqParts.size == 2) {
                            // Format: "package.name = on/off"
                            val pkg = eqParts[0].trim()
                            val setting = eqParts[1].trim().lowercase()
                            if (setting == "off") listedPackages.add(pkg)
                        } else {
                            // Format: "AppName_com.package.name" → extract package name, disable updates
                            val segments = line.split('_')
                            val pkgIndex = segments.indexOfLast { it.contains('.') }
                            if (pkgIndex >= 0) {
                                val pkg = segments.drop(pkgIndex).joinToString(".")
                                listedPackages.add(pkg)
                            }
                        }
                    }
                listedPackages.toSet()
            } else emptySet()
        } else emptySet()

        fun parseIso8601(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            return try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(iso)?.time ?: 0L
            } catch (_: Exception) { try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(iso)?.time ?: 0L
            } catch (_: Exception) { 0L } }
        }

        val parsedApps = appFiles.map { file ->
            val fileId = file.id
            val streamUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&key=$apiKey"

            val stripped = file.name.removeSuffix(".apk").removeSuffix(".APK").removeSuffix(".zip").removeSuffix(".ZIP")

            val versionPattern = "_v([^_]+)".toRegex()
            val buildPattern = "_(?:build|buid)_(\\d+)".toRegex(RegexOption.IGNORE_CASE)
            val patchPattern = "(?:_patches_v|_(?:build|buid)_\\d+_)([\\d.]+)".toRegex(RegexOption.IGNORE_CASE)

            // Find FIRST _v version match AFTER the package name (which contains dots)
            val versionMatch = versionPattern.findAll(stripped).firstOrNull { match ->
                stripped.substring(0, match.range.first).contains('.')
            }
            val buildMatch = buildPattern.find(stripped)
            val patchMatch = patchPattern.find(stripped)

            // Windows ZIPs have no package prefix: accept a plain _vN.N version segment
            val winVersionMatch = if (!isWindows) null
                else if (versionMatch != null) null
                else "_v(\\d[\\d.]*)".toRegex().find(stripped)

            // For Windows ZIP files, also try hyphen/underscore version suffix or v-prefixed version
            val zipVersionMatch = if (!isWindows) null
                else if (versionMatch != null) null
                else "[-_]([\\d.]+)$".toRegex().find(stripped)
            val vPrefixMatch = if (!isWindows) null
                else if (versionMatch != null || winVersionMatch != null || zipVersionMatch != null) null
                else "[- ]v([\\d.]+)$".toRegex(RegexOption.IGNORE_CASE).find(stripped)
            val dashSpaceVersion = if (!isWindows) null
                else if (versionMatch != null || winVersionMatch != null || zipVersionMatch != null || vPrefixMatch != null) null
                else "[- ]([\\d.]+)$".toRegex().find(stripped)

            // For Windows ZIPs, extract embedded 3+ digit version code and build number
            val winBuildNum = if (!isWindows) null
                else "(?i)build(\\d+)".toRegex().find(stripped)
            val winCodeNum = if (!isWindows) null
                else "(?<=[a-zA-Z])(\\d{3,})".toRegex().find(stripped)

            val verName = versionMatch?.groupValues?.get(1)
                ?: winVersionMatch?.groupValues?.get(1)
                ?: zipVersionMatch?.groupValues?.get(1)
                ?: vPrefixMatch?.groupValues?.get(1)
                ?: dashSpaceVersion?.groupValues?.get(1)
                ?: winCodeNum?.groupValues?.get(1)
                ?: "1.0"
            val verCode = winBuildNum?.groupValues?.get(1)?.toIntOrNull()
                ?: buildMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: winVersionMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
                ?: winCodeNum?.groupValues?.get(1)?.toIntOrNull()
                ?: versionMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
                ?: zipVersionMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
                ?: vPrefixMatch?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
                ?: dashSpaceVersion?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()
                ?: 1
            val patchVersion = patchMatch?.groupValues?.get(1) ?: ""

            val beforeVersion = if (versionMatch != null) {
                stripped.substring(0, versionMatch.range.first).trimEnd('_')
            } else if (winVersionMatch != null) {
                stripped.substring(0, winVersionMatch.range.first).trimEnd('_')
            } else if (zipVersionMatch != null) {
                stripped.substring(0, zipVersionMatch.range.first).trimEnd('-', '_', ' ')
            } else if (vPrefixMatch != null) {
                stripped.substring(0, vPrefixMatch.range.first).trimEnd('-', '_', ' ')
            } else if (dashSpaceVersion != null) {
                stripped.substring(0, dashSpaceVersion.range.first).trimEnd('-', '_', ' ')
            } else {
                stripped
            }

            // If beforeVersion doesn't match any md/png exactly, check if a md/png key is a word-boundary prefix
            val effectiveName = if (beforeVersion in mdFileMap || beforeVersion in iconMap) beforeVersion
                else (mdFileMap.keys + iconMap.keys).firstOrNull { key ->
                    beforeVersion.startsWith(key) && beforeVersion.length > key.length &&
                        !beforeVersion[key.length].isLetterOrDigit()
                } ?: beforeVersion

            // Extract package name and app name from beforeVersion (not effectiveName)
            val segments = beforeVersion.split('_')
            val pkgIndex = segments.indexOfLast { it.contains('.') && !it.contains(' ') }
            val pkgName = if (pkgIndex >= 0) {
                segments.drop(pkgIndex).joinToString(".")
            } else {
                "com.drive.app.${segments.joinToString("").lowercase().replace("+", "plus").replace(" ", "")}"
            }
            val appName = if (pkgIndex > 0) {
                segments.take(pkgIndex).joinToString(" ").trim()
            } else {
                beforeVersion.replace("_", " ").trim()
            }

            val iconUrl = iconMap[effectiveName]
                ?: iconMap[file.name.removeSuffix(".apk").removeSuffix(".APK").removeSuffix(".zip").removeSuffix(".ZIP")]
                ?: file.thumbnailLink
                ?: file.iconLink
                ?: "https://cdn-icons-png.flaticon.com/512/888/888857.png"

            val mdFileId = mdFileMap[effectiveName]
                ?: mdFileMap[file.name.removeSuffix(".apk").removeSuffix(".APK").removeSuffix(".zip").removeSuffix(".ZIP")]
            val description = if (mdFileId != null) "__MD_ID:$mdFileId"
                else if (isWindows) "Windows app: ${file.name.removeSuffix(".zip").removeSuffix(".ZIP")}."
                else "Discovered APK from Google Drive: ${file.name}."

            AppItem(
                id = fileId,
                packageName = pkgName,
                name = appName,
                versionName = verName,
                versionCode = verCode,
                size = file.size?.toLongOrNull() ?: 5000000,
                driveFileId = fileId,
                fileName = file.name,
                downloadUrl = streamUrl,
                iconUrl = iconUrl,
                description = description,
                category = "Discovered",
                lastUpdated = System.currentTimeMillis(),
                driveModifiedTime = parseIso8601(file.modifiedTime),
                patchVersion = patchVersion,
                updateCheck = pkgName !in updateDisabledPackages,
                platform = platform
            )
        }
        if (insertIntoDb) {
            // Deduplicate by packageName - keep the entry with better data
            val dedupedApps = parsedApps.groupBy { it.packageName }.map { (_, apps) ->
                apps.maxByOrNull { it.driveFileId.length } ?: apps.first()
            }
            appDao.insertApps(dedupedApps)
            if (deleteStale) {
                val activeIds = dedupedApps.map { it.driveFileId }.filter { it.isNotBlank() }
                if (activeIds.isNotEmpty()) {
                    appDao.deleteStaleApps(activeIds)
                }
            }
        }
        return parsedApps
    }
}
