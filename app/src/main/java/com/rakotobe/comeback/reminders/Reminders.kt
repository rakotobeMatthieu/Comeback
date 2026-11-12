package com.rakotobe.comeback.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rakotobe.comeback.ComebackApp
import com.rakotobe.comeback.CrashLog
import com.rakotobe.comeback.MainActivity
import com.rakotobe.comeback.R
import com.rakotobe.comeback.data.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Notifications {
    const val CHANNEL = "rappels"
    const val EXTRA_HABIT = "habitId"
    const val EXTRA_DAY = "day"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rappels d'objectifs", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Un rappel quand un objectif n'est pas encore validé"
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, habit: Habit, day: Long) {
        if (!canNotify(context)) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val doneIntent = Intent(context, DoneReceiver::class.java)
            .putExtra(EXTRA_HABIT, habit.id)
            .putExtra(EXTRA_DAY, day)
        val done = PendingIntent.getBroadcast(context, habit.id.toInt(), doneIntent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(habit.name)
            .setContentText("Pas encore validé aujourd'hui")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Fait", done)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(habit.id.toInt(), notification)
        } catch (_: SecurityException) {
        }
    }
}

/** Bouton « Fait » d'une notification : valide l'objectif sans ouvrir l'app. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(Notifications.EXTRA_HABIT, -1L)
        val day = intent.getLongExtra(Notifications.EXTRA_DAY, -1L)
        if (habitId < 0 || day < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ComebackApp.repo(app).setDone(habitId, LocalDate.ofEpochDay(day), true)
                NotificationManagerCompat.from(app).cancel(habitId.toInt())
            } catch (t: Throwable) {
                CrashLog.record(app, t)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Toutes les 15 min : fige les journées terminées, envoie les rappels, met à jour le widget. */
class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = ComebackApp.repo(applicationContext)
            repo.lockDays()
            repo.sendDueReminders()
            repo.refreshWidgets()
            Result.success()
        } catch (t: Throwable) {
            CrashLog.record(applicationContext, t)
            Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("comeback-tick", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
