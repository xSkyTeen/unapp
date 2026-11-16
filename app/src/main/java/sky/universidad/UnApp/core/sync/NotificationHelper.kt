package sky.universidad.UnApp.core.sync

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
import sky.universidad.UnApp.MainActivity
import sky.universidad.UnApp.R
import sky.universidad.UnApp.data.local.entities.ForumEntity
import sky.universidad.UnApp.data.local.entities.TaskEntity

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = context.getString(R.string.notification_channel_tasks_id)
            val name = context.getString(R.string.notification_channel_tasks_name)
            val desc = context.getString(R.string.notification_channel_tasks_desc)
            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = desc
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyNewTasks(newTasks: List<TaskEntity>) {
        notifyAcademicUpdates(newTasks = newTasks, newForums = emptyList())
    }

    fun notifyAcademicUpdates(newTasks: List<TaskEntity>, newForums: List<ForumEntity>) {
        if ((newTasks.isEmpty() && newForums.isEmpty()) || !hasNotificationPermission()) return
        ensureChannels()

        val channelId = context.getString(R.string.notification_channel_tasks_id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalCount = newTasks.size + newForums.size
        val title = when {
            newTasks.size == 1 && newForums.isEmpty() -> "Nueva tarea: ${newTasks.first().courseName}"
            newForums.size == 1 && newTasks.isEmpty() -> "Nuevo foro: ${newForums.first().courseName}"
            else -> "$totalCount novedades en tus cursos UNAP"
        }

        val lines = buildList {
            newTasks.forEach { add("Tarea · ${it.courseName}: ${it.title}") }
            newForums.forEach { add("Foro · ${it.courseName}: ${it.title}") }
        }
        val body = if (lines.size == 1) {
            newTasks.firstOrNull()?.title ?: newForums.first().title
        } else {
            lines.take(4).joinToString("\n")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val seedKey = newTasks.firstOrNull()?.remoteId ?: newForums.first().remoteId
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + (seedKey.hashCode() and 0x0FFF),
                notification
            )
        }
    }

    private companion object {
        const val NOTIFICATION_ID_BASE = 4100
    }
}
