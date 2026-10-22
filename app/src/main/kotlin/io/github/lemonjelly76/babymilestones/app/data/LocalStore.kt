package io.github.lemonjelly76.babymilestones.app.data

import android.content.Context
import io.github.lemonjelly76.babymilestones.core.json.BabyJson
import kotlinx.serialization.KSerializer
import java.io.File

/** Settings the carer chose on this phone. */
data class NotificationSettings(
    val weeklyDigest: Boolean = true,
    val dueNow: Boolean = true,
    val appointmentWindows: Boolean = true,
    val quietNudge: Boolean = true,
)

/**
 * What this phone keeps: the server address and token in private preferences, and the last data and content it
 * saw as JSON files, so the app opens and works offline. Nothing here is backed up (see the manifest).
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("baby_milestones", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var selectedBabyId: String?
        get() = prefs.getString("selected_baby", null)
        set(value) = prefs.edit().putString("selected_baby", value).apply()

    var notifications: NotificationSettings
        get() = NotificationSettings(
            prefs.getBoolean("notify_weekly", true), prefs.getBoolean("notify_due_now", true),
            prefs.getBoolean("notify_appointments", true), prefs.getBoolean("notify_nudge", true),
        )
        set(value) = prefs.edit()
            .putBoolean("notify_weekly", value.weeklyDigest).putBoolean("notify_due_now", value.dueNow)
            .putBoolean("notify_appointments", value.appointmentWindows).putBoolean("notify_nudge", value.quietNudge)
            .apply()

    /** The in-app assistant calls the Claude API on the server, which costs money per message, so it's off until chosen. */
    var assistantEnabled: Boolean
        get() = prefs.getBoolean("assistant_enabled", false)
        set(value) = prefs.edit().putBoolean("assistant_enabled", value).apply()

    /** Keys of one-off reminders (Due now, appointment windows) this phone has already sent. */
    var notifiedOnce: Set<String>
        get() = prefs.getStringSet("notified_once", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("notified_once", value).apply()

    var lastDigestDate: String?
        get() = prefs.getString("last_digest", null)
        set(value) = prefs.edit().putString("last_digest", value).apply()

    fun <T> read(name: String, serializer: KSerializer<T>): T? = runCatching {
        File(dir, name).takeIf { it.exists() }?.readText()?.let { BabyJson.decodeFromString(serializer, it) }
    }.getOrNull()

    fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        File(dir, name).writeText(BabyJson.encodeToString(serializer, value))
    }

    fun readText(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    fun writeText(name: String, text: String) = File(dir, name).writeText(text)

    /** Signing out or deleting the family leaves nothing behind on the phone. */
    fun clear() {
        prefs.edit().clear().apply()
        dir.listFiles()?.forEach { it.delete() }
    }
}
