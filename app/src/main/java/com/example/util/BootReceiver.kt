package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("drive_store_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_update_check", true)) return

        val hour = prefs.getInt("notif_hour", 8)
        val minute = prefs.getInt("notif_minute", 0)
        val intervalMinutes = com.example.util.AutoDownloadHelper.checkIntervalMinutes(prefs).coerceAtLeast(5)

        val alarmIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, UpdateCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        try {
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent
            )
        } catch (_: SecurityException) {
            alarmMgr.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                intervalMinutes * 60000L, alarmIntent
            )
        }
    }
}
