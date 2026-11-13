import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

private const val WAKE_UP_INTERVAL_MS = 15 * 60 * 1000L // 示例：每 15 分钟唤醒一次

fun scheduleNextWakeUp(context: Context) {
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

val intent = Intent(context, AlarmWakeUpReceiver::class.java).apply {
action = "com.example.ACTION_KEEP_ALIVE_WAKEUP"
}

val pendingIntent = PendingIntent.getBroadcast(
context,
1002,
intent,
PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val triggerTime = System.currentTimeMillis() + WAKE_UP_INTERVAL_MS

// Android 6.0 (API 23)+ 必须使用 setExactAndAllowWhileIdle 穿透休眠
// Android 12+ 需要检查 canScheduleExactAlarms() 权限
try {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
alarmManager.setExactAndAllowWhileIdle(
AlarmManager.RTC_WAKEUP,
triggerTime,
pendingIntent
)
} else {
alarmManager.setExact(
AlarmManager.RTC_WAKEUP,
triggerTime,
pendingIntent
)
}
} catch (e: SecurityException) {
// 兜底处理：若无精确闹钟权限，降级为普通非精确闹钟
alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
}
}
}
