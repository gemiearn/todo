package com.personal.todo.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.personal.todo.MainActivity
import com.personal.todo.R
import com.personal.todo.data.ReminderSettingsStore
import com.personal.todo.data.db.DatabaseProvider
import com.personal.todo.data.db.Task
import com.personal.todo.data.db.TodoDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 任务提醒的通知：一个渠道、一条通知、两个按钮。
 *
 * 做的是「到点能看见」这一件事，所以刻意没有抄竞品的那一整套（多提醒、持续响铃、
 * 位置提醒）。通知上只留两个动作——**完成**和**稍后 10 分钟**，都直接写库或重设闹钟，
 * 不打开 App。用户在被闹钟打断的那一刻，最不想做的就是先解锁再进一个 App。
 *
 * 通知 id 就用任务 id：同一条任务反复提醒只会替换自己，不会在通知栏堆一列。
 */
object ReminderNotifications {

    /** 渠道 id。改了它等于换新渠道，老的静音设置会失效，所以定死不动。 */
    const val CHANNEL_ID = "task_reminder"

    private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 建渠道。重复调用安全；Application 启动时调一次即可。 */
    fun ensureChannel(context: Context) {
        // minSdk 是 26，通知渠道从这个版本起就有，不必再判断 SDK_INT。
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 到点发通知。
     *
     * 先复核一次任务状态：闹钟设下之后用户可能已经勾掉、删掉或改掉了提醒，那时通知
     * 不该再弹。**过期不补发**——设备关机期间攒下的提醒，开机后不会补响。
     */
    suspend fun show(
        context: Context,
        taskId: Long,
        expectedAt: Long? = null,
        db: TodoDatabase = DatabaseProvider.get(context),
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val task = db.taskDao().getById(taskId) ?: return false
        if (task.deletedAt != null || task.completedAt != null) return false
        val originalAt = task.reminderAt ?: return false
        val at = ReminderSettingsStore(context).readSnooze(taskId, originalAt) ?: originalAt
        // 防止已派发的旧闹钟在用户改期之后仍弹通知。正常的省电延迟允许晚到。
        if (expectedAt != null && expectedAt != at) return false
        if (at > nowMillis) return false
        ensureChannel(context)
        val listName = db.taskListDao().getById(task.listId)?.name
        return notify(context, taskId, build(context, task, listName, at))
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    /** 使用同一个提醒渠道验证手机能否显示通知，区别于闹钟是否能唤醒应用。 */
    fun showTest(context: Context): Boolean {
        ensureChannel(context)
        return notify(context, 0L, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.reminder_test_title))
            .setContentText(context.getString(R.string.reminder_test_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build())
    }

    /** 应用总开关和单独的「任务提醒」渠道都必须开启。 */
    fun areEnabled(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun build(context: Context, task: Task, listName: String?, at: Long): Notification {
        // 通知上显示的是「当初设的那个时刻」，不是 now：闹钟可能比它晚几分钟才送到
        // （非精确模式），显示 now 会让用户以为提醒设错了。
        val clock = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            .toLocalTime().format(HourMinute)
        val text = if (listName.isNullOrBlank()) {
            clock
        } else {
            context.getString(R.string.reminder_notification_text, listName, clock)
        }
        val note = task.notes?.trim()?.takeIf { it.isNotEmpty() }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note ?: text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, task.id))
            .addAction(
                0,
                context.getString(R.string.reminder_action_complete),
                actionIntent(context, task.id, ReminderActionReceiver.ACTION_COMPLETE)
            )
            .addAction(
                0,
                context.getString(
                    R.string.reminder_action_snooze,
                    ReminderSettingsStore.SnoozeMinutes
                ),
                actionIntent(context, task.id, ReminderActionReceiver.ACTION_SNOOZE)
            )
            .build()
    }

    /** 直接用 Activity PendingIntent，点击后打开对应任务。 */
    private fun openIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun actionIntent(context: Context, taskId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderActionReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, taskId: Long, notification: Notification): Boolean {
        // Android 13 起通知要运行时授权；没授权时系统会静默丢弃，这里先挡一下，
        // 免得在一个没有权限的机器上反复构造通知。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!areEnabled(context)) return false
        return try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            true
        } catch (_: SecurityException) {
            // 权限可能在检查后被收回，不得把这次记为已投递。
            false
        }
    }
}
