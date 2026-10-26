package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Shared background auto-update logic used by UpdateCheckWorker and
 * UpdateCheckReceiver. Downloads APKs to the same folder/file naming the
 * foreground downloader uses, so both paths stay compatible.
 */
object AutoDownloadHelper {
    private const val TAG = "AutoDownload"

    /** Check/retry interval in minutes. Migrates the legacy hours pref; fresh default 15 min. */
    fun checkIntervalMinutes(prefs: android.content.SharedPreferences): Int {
        val minutes = prefs.getInt("check_interval_minutes", -1)
        if (minutes > 0) return minutes
        if (prefs.contains("notif_interval_hours")) {
            return prefs.getInt("notif_interval_hours", 24).coerceIn(1, 48) * 60
        }
        return 15
    }

    /** Human-readable interval: "5 minutes", "1 hour", "2 hours", ... */
    fun formatInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            else -> "${minutes / 60} hours"
        }
    }

    fun downloadsDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
    }

    fun apkFileFor(context: Context, packageName: String, versionCode: Int): File {
        return File(downloadsDir(context), "${packageName}_${versionCode}.apk")
    }

    fun isValidApk(context: Context, file: File): Boolean {
        return try {
            file.exists() && file.length() > 0 &&
                context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_ACTIVITIES) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the APK for the given Drive file. Returns the downloaded file
     * on success, or an already-cached valid file. Returns null on failure.
     */
    fun downloadApk(
        context: Context,
        driveFileId: String,
        downloadUrl: String,
        packageName: String,
        versionCode: Int,
        fileName: String = ""
    ): File? {
        if (driveFileId.isBlank() && downloadUrl.isBlank()) return null
        return try {
            val dir = downloadsDir(context)
            val targetName = fileName.takeIf { it.isNotBlank() }?.replace("/", "_")?.trim()?.takeIf { it.isNotBlank() }
                ?: "${packageName}_${versionCode}.apk"
            // Delete stale cached files for this package (other versions, legacy naming)
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith("${packageName}_") && f.name != targetName &&
                    (f.name.endsWith(".apk") || f.name.endsWith(".tmp"))
                ) {
                    f.delete()
                }
            }
            val existingFile = File(dir, targetName)
            if (isValidApk(context, existingFile)) {
                Log.i(TAG, "Using cached APK for $packageName")
                return existingFile
            } else if (existingFile.exists()) {
                existingFile.delete()
            }

            val tmpFile = File(dir, "$targetName.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            val urls = if (driveFileId.startsWith("manifest_")) {
                listOfNotNull(downloadUrl.takeIf { it.isNotBlank() })
            } else {
                listOfNotNull(
                    "https://drive.google.com/uc?export=download&id=$driveFileId".takeIf { driveFileId.isNotBlank() },
                    downloadUrl.takeIf { it.isNotBlank() }
                )
            }
            if (urls.isEmpty()) return null

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            for (url in urls) {
                try {
                    val request = okhttp3.Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Server returned ${response.code}")
                        val body = response.body ?: throw Exception("Empty response body")
                        body.byteStream().use { input ->
                            FileOutputStream(tmpFile).use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                }
                                output.fd.sync()
                            }
                        }
                    }
                    tmpFile.renameTo(existingFile)
                    if (isValidApk(context, existingFile)) {
                        Log.i(TAG, "Downloaded APK for $packageName")
                        return existingFile
                    }
                    existingFile.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Download via $url failed for $packageName: ${e.message}")
                    if (tmpFile.exists()) tmpFile.delete()
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Auto-download failed for $packageName", e)
            null
        }
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent? {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build install intent", e)
            null
        }
    }

    fun hasSignatureConflict(context: Context, apkFile: File, packageName: String): Boolean {
        return try {
            val installedInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                ?: return false
            val apkInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                ?: return false
            val installedSigs = installedInfo.signatures?.map { it.toCharsString() }?.sorted() ?: return false
            val apkSigs = apkInfo.signatures?.map { it.toCharsString() }?.sorted() ?: return false
            installedSigs != apkSigs
        } catch (_: Exception) {
            false
        }
    }
}
