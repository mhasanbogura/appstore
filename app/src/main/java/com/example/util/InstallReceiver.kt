package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Fired when the user taps a "ready to install" notification produced by the
 * background auto-download. Opens the system installer for the downloaded APK.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_INSTALL_DOWNLOADED) return
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        try {
            val file = File(apkPath)
            if (!AutoDownloadHelper.isValidApk(context, file)) {
                Log.w("InstallReceiver", "Cached APK missing or invalid for $packageName")
                return
            }
            if (AutoDownloadHelper.hasSignatureConflict(context, file, packageName)) {
                // Different signer: uninstall first, remember pending install
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.getSharedPreferences("drive_store_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("pending_install_path", file.absolutePath)
                    .putString("pending_install_pkg", packageName)
                    .apply()
                context.startActivity(uninstallIntent)
            } else {
                AutoDownloadHelper.buildInstallIntent(context, file)?.let {
                    context.startActivity(it)
                }
            }
        } catch (e: Exception) {
            Log.e("InstallReceiver", "Failed to start install for $packageName", e)
        }
    }

    companion object {
        const val ACTION_INSTALL_DOWNLOADED = "com.mahmuduls.appstore.ACTION_INSTALL_DOWNLOADED"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
