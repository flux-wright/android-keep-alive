import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRestoreReceiver : BroadcastReceiver() {
override fun onReceive(context: Context, intent: Intent) {
if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
intent.action == "android.intent.action.QUICKBOOT_POWERON"
) {
// 手机重启后，立即重新点火启动闹钟唤醒链
AlarmKeepAliveManager.scheduleNextWakeUp(context, delayMs = 30_000L)
}
}
}
