package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {
    private const val CHANNEL_UPDATES_ID = "app_store_updates"
    private const val CHANNEL_DOWNLOADS_ID = "app_store_downloads"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nameUpdates = "App Store Updates"
            val descUpdates = "Notifications when updates for your installed apps are available"
            val channelUpdates = NotificationChannel(
                CHANNEL_UPDATES_ID,
                nameUpdates,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = descUpdates
            }

            val nameDownloads = "App Store Downloads"
            val descDownloads = "Notifications for download progress and completion statuses"
            val channelDownloads = NotificationChannel(
                CHANNEL_DOWNLOADS_ID,
                nameDownloads,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = descDownloads
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channelUpdates)
            manager.createNotificationChannel(channelDownloads)
        }
    }

    fun showUpdateNotification(context: Context, appName: String, versionName: String, packageName: String) {        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", 2)
        }
        val notifId = ("notif_update_$packageName").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Available")
            .setContentText("A new version ($versionName) of $appName is ready to install!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showReadyToInstallNotification(
        context: Context,
        appName: String,
        versionName: String,
        packageName: String,
        apkPath: String
    ) {
        val intent = Intent(context, InstallReceiver::class.java).apply {
            action = InstallReceiver.ACTION_INSTALL_DOWNLOADED
            putExtra(InstallReceiver.EXTRA_APK_PATH, apkPath)
            putExtra(InstallReceiver.EXTRA_PACKAGE_NAME, packageName)
        }
        val notifId = ("notif_ready_$packageName").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update downloaded")
            .setContentText("Tap to install $versionName of $appName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showDownloadCompleteNotification(context: Context, appName: String, packageName: String, isSuccess: Boolean) {
        val title = if (isSuccess) "Download Complete" else "Download Failed"
        val text = if (isSuccess) "$appName downloaded successfully and installation has started." else "An error occurred while downloading $appName"
        val notifId = ("notif_download_$packageName").hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_tab", 3)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS_ID)
            .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
