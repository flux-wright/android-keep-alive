import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ReliableBackgroundService: Service() {

private val serviceJob = SupervisorJob()
private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
private var wakeLock: PowerManager.WakeLock? = null

companion object {
private const val CHANNEL_ID = "reliable_bg_channel"
private const val NOTIFICATION_ID = 1001

fun startService(context: Context) {
val intent = Intent(context, ReliableBackgroundService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
context.startForegroundService(intent)
} else {
context.startService(intent)
}
}

fun stopService(context: Context) {
val intent = Intent(context, ReliableBackgroundService::class.java)
context.stopService(intent)
}
}

override fun onCreate() {
super.onCreate()
createNotificationChannel()
acquireWakeLock()
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
val notification = buildNotification("后台任务运行中", "正在处理核心业务数据...")

// Android 14 (API 34) 起必须传入与 Manifest 声明一致的前台服务类型。
// 本示例演示 dataSync；媒体处理场景（如视频转码）改传
// ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING 即可（Manifest 已同时声明两种类型）。
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
} else {
startForeground(NOTIFICATION_ID, notification)
}

// 启动你的持续性后台任务（例如网络长连接心跳、转码队列等）
startPersistentTask()

// START_STICKY：若系统因内存极度紧张杀掉服务，待资源恢复后尝试重建服务
return START_STICKY
}

private fun startPersistentTask() {
serviceScope.launch {
while (isActive) {
try {
// WakeLock 只有 1 小时超时，心跳却是无限循环：锁过期后 CPU 可能休眠、心跳会漂移，
// 因此每轮先检查，锁已释放就重新 acquire，保证长时间运行不中断。
renewWakeLockIfNeeded()
// TODO: 在此处执行你的核心后台逻辑（如心跳维持、本地队列处理）
delay(15_000L) // 示例：每 15 秒执行一次
} catch (e: CancellationException) {
break
} catch (e: Exception) {
e.printStackTrace()
}
}
}
}

/**
* Android 14/15+ 生命周期超时回调
* 当 dataSync 或 mediaProcessing 类型服务达到系统允许的最大运行时长时触发。
* 必须在此处停止服务，否则系统将在几秒后直接抛出 Fatal Exception 导致 App 崩溃！
*/
override fun onTimeout(startId: Int) {
super.onTimeout(startId)
stopSelf(startId)
}

/**
* Android 15 (API 35) 新增重载方法，支持区分具体超时的前台服务类型
*/
override fun onTimeout(startId: Int, fgsType: Int) {
super.onTimeout(startId, fgsType)
// 可在此处记录状态，或切换至 WorkManager 延迟调度接管任务
stopSelf(startId)
}

private fun acquireWakeLock() {
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
wakeLock = powerManager.newWakeLock(
PowerManager.PARTIAL_WAKE_LOCK,
"MyApp::BackgroundTaskWakeLock"
).apply {
// 设置最大超时时间防止意外耗电，按需续期
acquire(60 * 60 * 1000L)
}
}

/**
* 续锁：WakeLock 超时自动释放后（isHeld == false），重新 acquire。
* 由心跳循环每轮调用，保证无限循环的后台任务不会在第 61 分钟失去唤醒锁。
*/
private fun renewWakeLockIfNeeded() {
wakeLock?.let {
if (!it.isHeld) {
it.acquire(60 * 60 * 1000L)
}
}
}

private fun createNotificationChannel() {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
val channel = NotificationChannel(
CHANNEL_ID,
"后台运行服务",
NotificationManager.IMPORTANCE_LOW // 低打扰级别，不发提示音但常驻通知栏
).apply {
description = "用于保持核心任务在后台稳定运行"
}
val manager = getSystemService(NotificationManager::class.java)
manager.createNotificationChannel(channel)
}
}

private fun buildNotification(title: String, content: String): Notification {
return NotificationCompat.Builder(this, CHANNEL_ID)
.setContentTitle(title)
.setContentText(content)
.setSmallIcon(android.R.drawable.ic_popup_sync)
.setOngoing(true) // 禁止用户侧滑直接清除（Android 14+ 部分系统允许滑动但仍在任务管理器显示）
.setCategory(NotificationCompat.CATEGORY_SERVICE)
.build()
}

override fun onDestroy() {
serviceJob.cancel()
wakeLock?.let {
if (it.isHeld) it.release()
}
super.onDestroy()
}

override fun onBind(intent: Intent?): IBinder? = null
}
