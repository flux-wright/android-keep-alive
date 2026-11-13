import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmWakeUpReceiver : BroadcastReceiver() {
override fun onReceive(context: Context, intent: Intent) {
if (intent.action == "com.example.ACTION_KEEP_ALIVE_WAKEUP") {
// 此时系统已授予临时白名单，进程被唤醒。
// 先安排下一次唤醒，保证接力链不断——即使本次拉起服务失败，链也不能断在这里。
AlarmScheduler.scheduleNextWakeUp(context)

// 拉起前台服务执行一轮任务（干完即退，不常驻）
try {
val serviceIntent = Intent(context, ReliableBackgroundService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
context.startForegroundService(serviceIntent)
} else {
context.startService(serviceIntent)
}
} catch (e: Exception) {
// 拉起失败不中断接力：下一轮闹钟仍会唤醒重试
e.printStackTrace()
}
}
}
}
