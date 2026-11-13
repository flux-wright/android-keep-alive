import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WakeUpWorkerService : Service() {

private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
createChannel()
val notification = NotificationCompat.Builder(this, "wakeup_worker_channel")
.setContentTitle("数据同步中")
.setContentText("正在处理定时同步任务...")
.setSmallIcon(android.R.drawable.ic_popup_sync)
.build()

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
startForeground(9001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
} else {
startForeground(9001, notification)
}

scope.launch {
try {
// 执行重型任务...
delay(5000L)
} finally {
// 任务结束立刻销毁服务，回归“静默休眠”状态等待下一次闹钟
stopSelf(startId)
}
}

return START_NOT_STICKY
}

override fun onTimeout(startId: Int) {
stopSelf(startId)
}

/**
* Android 15 (API 35) 双参重载：带类型启动的服务超时走的是这个回调。
* 与单参版一起重写，不依赖框架默认实现的转调行为——超时必停，防系统强杀崩溃。
*/
override fun onTimeout(startId: Int, fgsType: Int) {
super.onTimeout(startId, fgsType)
stopSelf(startId)
}

private fun createChannel() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
val channel = NotificationChannel(
"wakeup_worker_channel",
"定时同步服务",
NotificationManager.IMPORTANCE_LOW
)
getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
}

override fun onDestroy() {
scope.cancel()
super.onDestroy()
}

override fun onBind(intent: Intent?): IBinder? = null
}
