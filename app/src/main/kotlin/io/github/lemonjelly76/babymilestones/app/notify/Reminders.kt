package io.github.lemonjelly76.babymilestones.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import io.github.lemonjelly76.babymilestones.app.data.BabyView
import io.github.lemonjelly76.babymilestones.app.data.LocalStore
import io.github.lemonjelly76.babymilestones.app.data.NotificationSettings
import io.github.lemonjelly76.babymilestones.app.data.Repository
import io.github.lemonjelly76.babymilestones.app.ui.MainActivity
import io.github.lemonjelly76.babymilestones.core.schedule.AppointmentStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** [once] reminders (Due now, appointment windows) are remembered so each is sent a single time. */
data class Reminder(val key: String, val title: String, val text: String, val once: Boolean = false)

/** Decides which reminders are due today. Pure, so it can be tested without Android. */
object ReminderPlanner {
    const val QUIET_DAYS = 21L

    fun plan(
        view: BabyView,
        settings: NotificationSettings,
        alreadyNotified: Set<String>,
        digestSentOn: LocalDate?,
    ): List<Reminder> {
        val today = view.today
        val name = view.baby.name
        val out = mutableListOf<Reminder>()

        if (settings.dueNow) {
            view.aroundNow.filter { "${view.baby.id}:${it.id}" !in alreadyNotified }.forEach {
                out += Reminder("${view.baby.id}:${it.id}", "Around now for $name",
                    "${it.milestone.title}. Seen it yet? Tap to log it.", once = true)
            }
        }
        if (settings.appointmentWindows) {
            view.schedule.appointments.filter {
                it.status == AppointmentStatus.OPEN && "appt:${view.baby.id}:${it.rule.id}" !in alreadyNotified
            }.forEach {
                out += Reminder("appt:${view.baby.id}:${it.rule.id}", "$name's ${it.rule.title}",
                    "The window for this is open now. ${it.rule.with} will usually be in touch; you can mark it booked in the app.",
                    once = true)
            }
        }
        val digestDue = today.dayOfWeek == DayOfWeek.SUNDAY && digestSentOn != today
        if (settings.weeklyDigest && digestDue) {
            val watch = (view.aroundNow + view.watchFor).take(3).joinToString("; ") { it.milestone.title.lowercase() }
            out += Reminder("digest:${view.baby.id}", "This week with $name",
                if (watch.isEmpty()) "${view.snapshot.period.name}: open the guide for this week's play ideas."
                else "Watch for: $watch.")
        }
        val lastLog = view.snapshot.lastLogDate()
        val quiet = lastLog == null || lastLog.isBefore(today.minusDays(QUIET_DAYS))
        if (settings.quietNudge && quiet && today.dayOfWeek == DayOfWeek.SATURDAY && view.baby.dateOfBirth.isBefore(today.minusDays(QUIET_DAYS))) {
            out += Reminder("nudge:${view.baby.id}", "Anything new with $name?",
                "Nothing has been logged for 3 weeks. Tap to see what's around now.")
        }
        return out
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = LocalStore(applicationContext)
        if (store.token == null) return Result.success()
        val repository = Repository(store)
        val content = repository.content()
        val me = runCatching { repository.refreshMe() }.getOrNull() ?: repository.cachedMe() ?: return Result.success()
        val today = LocalDate.now()
        val notified = store.notifiedOnce.toMutableSet()
        var digestSent = false

        me.babies.forEach { baby ->
            val data = runCatching { repository.refreshBaby(baby.id) }.getOrNull() ?: repository.cachedData(baby.id) ?: return@forEach
            val view = BabyView(data, content, today)
            ReminderPlanner.plan(view, store.notifications, notified, store.lastDigestDate?.let(LocalDate::parse)).forEach {
                post(it)
                if (it.key.startsWith("digest:")) digestSent = true
                if (it.once) notified += it.key
            }
        }
        store.notifiedOnce = notified
        if (digestSent) store.lastDigestDate = today.toString()
        return Result.success()
    }

    private fun post(reminder: Reminder) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.key.hashCode(), notification)
    }
}

object Reminders {
    const val CHANNEL = "reminders"
    private const val WORK = "daily-reminders"

    fun schedule(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Milestone reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Weekly digest, things around now, appointment windows"
            },
        )
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
