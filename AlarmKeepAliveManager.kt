import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object AlarmKeepAliveManager {

const val ACTION_WAKEUP = "com.example.ACTION_ALARM_WAKEUP"
private const val REQUEST_CODE = 8899

// 唤醒周期：15 分钟。与 Doze 深度休眠下 setExactAndAllowWhileIdle 的系统配额对齐
// （约 15 分钟最多放行一次）——定得再短，深度 Doze 时也会被系统自动拉长到配额线，链不断但节奏不准。
private const val INTERVAL_MS = 15 * 60 * 1000L

/**
* 1. 检查是否拥有精确闹钟权限（Android 12 / API 31+ 强制要求）
*/
fun hasExactAlarmPermission(context: Context): Boolean {
return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
alarmManager.canScheduleExactAlarms()
} else {
true
}
}

/**
* 2. 引导用户前往系统设置开启“闹钟和提醒”特权（仅在无权限时调用）
*/
fun requestExactAlarmPermission(context: Context) {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
data = Uri.parse("package:${context.packageName}")
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
}
}

/**
* 3. 启动/挂载下一次闹钟唤醒（核心链式接力方法）
*/
fun scheduleNextWakeUp(context: Context, delayMs: Long = INTERVAL_MS) {
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

val intent = Intent(context, AlarmKeepAliveReceiver::class.java).apply {
action = ACTION_WAKEUP
}

val pendingIntent = PendingIntent.getBroadcast(
context,
REQUEST_CODE,
intent,
PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

val triggerAtMillis = System.currentTimeMillis() + delayMs

// 先取消旧的同类闹钟，防止重复堆叠
alarmManager.cancel(pendingIntent)

try {
when {
// Android 12+ 且已获得精确闹钟特权：使用最高优先级穿透休眠
Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
if (alarmManager.canScheduleExactAlarms()) {
alarmManager.setExactAndAllowWhileIdle(
AlarmManager.RTC_WAKEUP,
triggerAtMillis,
pendingIntent
)
} else {
// 无精确权限时的降级保底（允许系统在省电窗口内合并唤醒，依然能穿透深度睡眠）
alarmManager.setAndAllowWhileIdle(
AlarmManager.RTC_WAKEUP,
triggerAtMillis,
pendingIntent
)
}
}
// Android 6.0 ~ Android 11：直接穿透 Doze 模式
Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
alarmManager.setExactAndAllowWhileIdle(
AlarmManager.RTC_WAKEUP,
triggerAtMillis,
pendingIntent
)
}
// Android 4.4 ~ Android 5.1
else -> {
alarmManager.setExact(
AlarmManager.RTC_WAKEUP,
triggerAtMillis,
pendingIntent
)
}
}
} catch (e: SecurityException) {
// 极端 ROM 权限异常兜底
alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
}
}

/**
* 4. 停止保活闹钟链
*/
fun cancelWakeUp(context: Context) {
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
val intent = Intent(context, AlarmKeepAliveReceiver::class.java).apply {
action = ACTION_WAKEUP
}
val pendingIntent = PendingIntent.getBroadcast(
context,
REQUEST_CODE,
intent,
PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
alarmManager.cancel(pendingIntent)
}
}
