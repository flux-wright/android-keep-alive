import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.*

class AlarmKeepAliveReceiver: BroadcastReceiver() {

override fun onReceive(context: Context, intent: Intent) {
val action = intent.action?: return

if (action == AlarmKeepAliveManager.ACTION_WAKEUP ||
action == Intent.ACTION_TIME_SET ||
action == Intent.ACTION_TIMEZONE_CHANGED
) {
// 第一时间挂载下一次闹钟，防止后续任务异常导致链条断裂
AlarmKeepAliveManager.scheduleNextWakeUp(context)

// 申请局部唤醒锁（保底 15 秒），防止执行期间 CPU 再次睡死
val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
val wakeLock = pm.newWakeLock(
PowerManager.PARTIAL_WAKE_LOCK,
"AppKeepAlive::AlarmWakeLock"
).apply {
acquire(15_000L)
}

// 使用 goAsync() 在广播内安全执行异步短任务（最长允许约 10~30 秒）
val pendingResult = goAsync()
CoroutineScope(Dispatchers.IO).launch {
try {
executeCoreBusinessTask(context)
} finally {
if (wakeLock.isHeld) wakeLock.release()
pendingResult.finish()
}
}
}
}

private suspend fun executeCoreBusinessTask(context: Context) {
// 情况 A：如果你的业务（如心跳上报、拉取指令、状态同步）在 10 秒内能完成，直接在此处执行！
// 无需启动任何 Service，干完活直接让系统重新冻结进程，零功耗且 100% 不被杀。
delay(1000L) // 模拟网络请求

// 情况 B：如果发现有重型长任务需要处理（如大文件同步），此时系统处于白名单免死窗口，可合法拉起前台服务
val hasHeavyWork = false
if (hasHeavyWork) {
val serviceIntent = Intent(context, WakeUpWorkerService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
context.startForegroundService(serviceIntent)
} else {
context.startService(serviceIntent)
}
}
}
}
