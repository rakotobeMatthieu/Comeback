package com.rakotobe.comeback

import android.app.Application
import android.content.Context
import com.rakotobe.comeback.data.Repository
import com.rakotobe.comeback.reminders.Notifications
import com.rakotobe.comeback.reminders.TickWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

class ComebackApp : Application() {
    lateinit var repository: Repository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        repository = Repository(this)
        Notifications.createChannel(this)
        TickWorker.schedule(this)
        appScope.launch {
            try {
                repository.lockDays()
            } catch (t: Throwable) {
                CrashLog.record(this@ComebackApp, t)
            }
        }
    }

    companion object {
        fun repo(context: Context): Repository = (context.applicationContext as ComebackApp).repository
    }
}

/** Journal d'erreurs consultable dans les réglages, pour pouvoir le copier et me l'envoyer. */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "crash.log")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(context, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable) {
        try {
            val f = file(context)
            if (f.exists() && f.length() > 200_000) f.delete()
            f.appendText("=== ${LocalDateTime.now()} · v${BuildConfig.VERSION_NAME}\n${error.stackTraceToString()}\n")
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String = try {
        val f = file(context)
        if (f.exists()) f.readText() else ""
    } catch (_: Throwable) {
        ""
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }
}
