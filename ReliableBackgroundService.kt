import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
* 闹钟驱动的前台服务：被 AlarmManager 唤醒后执行一轮任务即退出，不常驻、不自启。
* 唤醒链见 AlarmScheduler + AlarmWakeUpReceiver。
*/
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

// 执行一轮任务，做完即退
runOneShotTask()

// 闹钟驱动模型：服务被杀不靠系统粘性重建——重建由下一次闹钟负责，因此返回 START_NOT_STICKY
return START_NOT_STICKY
}

private fun runOneShotTask() {
serviceScope.launch {
try {
// TODO: 在此处执行一轮核心任务（数据同步、本地队列处理、心跳上报）
// 本模型不常驻：一轮任务做完即退出，等待下一次闹钟唤醒——间歇期零占用、零电耗。
} catch (e: Exception) {
e.printStackTrace()
} finally {
stopSelf()
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
// 可在此处记录状态，等待下一轮闹钟唤醒时继续处理
stopSelf(startId)
}

private fun acquireWakeLock() {
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
wakeLock = powerManager.newWakeLock(
PowerManager.PARTIAL_WAKE_LOCK,
"MyApp::BackgroundTaskWakeLock"
).apply {
// 一轮任务通常以分钟计，1 小时是防任务跑飞的上限保护
acquire(60 * 60 * 1000L)
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
