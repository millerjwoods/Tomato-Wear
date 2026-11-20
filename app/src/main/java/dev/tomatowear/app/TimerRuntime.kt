/* GPL-3.0-or-later. Standalone Wear OS timer scheduler. */
package dev.tomatowear.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.net.Uri

object TimerRuntime {
    private const val ALARM_REQUEST = 1234
    private const val RUN_NOTIFICATION = 101
    private const val DONE_NOTIFICATION = 102
    private const val RUN_CHANNEL = "tomato_wear_running_v1"
    private const val DONE_CHANNEL = "tomato_wear_done_v1"
    private const val SILENT_CHANNEL = "tomato_wear_silent_v1"

    private fun alarms(context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)
    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context).canScheduleExactAlarms()

    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST, Intent(context, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notifications(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun channels(context: Context) {
        val manager = notifications(context)
        manager.createNotificationChannel(NotificationChannel(
            RUN_CHANNEL, "Timer in progress", NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null); enableVibration(false) })
        manager.createNotificationChannel(NotificationChannel(
            DONE_CHANNEL, "Timer finished", NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) })
        manager.createNotificationChannel(NotificationChannel(
            SILENT_CHANNEL, "Quiet timer finished", NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null); enableVibration(false) })
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun reconcile(context: Context) {
        val store = TimerStore(context)
        val finished = store.finishIfDue()
        if (finished != null) finished(context, finished)
        refresh(context)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = alarms(app)
        val pending = alarmIntent(app)
        manager.cancel(pending)
        val store = TimerStore(app)
        val s = store.state()
        if (!s.running) {
            notifications(app).cancel(RUN_NOTIFICATION)
            return
        }
        val due = s.deadlineEpochMs.coerceAtLeast(System.currentTimeMillis() + 1)
        try {
            if (canUseExactAlarms(app)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            } else {
                // Degraded mode: Doze can delay this wakeup. The countdown itself is still correct.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
        }
        runningNotification(app, s)
    }

    private fun runningNotification(context: Context, s: TimerState) {
        if (!hasNotificationPermission(context)) return
        channels(context)
        val n = Notification.Builder(context, RUN_CHANNEL)
            .setSmallIcon(R.drawable.ic_tomato)
            .setContentTitle("${s.phase.title} · ${s.topic}")
            .setContentText("Tap to manage your timer")
            .setContentIntent(openApp(context))
            .setWhen(s.deadlineEpochMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notifications(context).notify(RUN_NOTIFICATION, n)
    }

    private fun finished(context: Context, phase: Phase) {
        val config = TimerStore(context).config()
        if (config.vibrate) {
            runCatching {
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 320), -1))
            }
        }
        if (!hasNotificationPermission(context)) return
        channels(context)
        notifications(context).notify(DONE_NOTIFICATION,
            Notification.Builder(context, if (config.sound) DONE_CHANNEL else SILENT_CHANNEL)
                .setSmallIcon(R.drawable.ic_tomato)
                .setContentTitle("${phase.title} finished")
                .setContentText("Next: ${TimerStore(context).state().phase.title}")
                .setContentIntent(openApp(context))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
        )
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TimerRuntime.reconcile(context)
    }
}

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Reschedules on reboot, wall-clock changes, or newly granted exact-alarm access.
        TimerRuntime.reconcile(context)
    }
}
