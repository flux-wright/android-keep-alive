import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // 即使 App 被系统冻结或杀掉，系统调度器也会在满足条件时唤醒进程执行此任务
        return try {
            // 执行数据同步、状态上报等任务
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object TaskScheduler {
    fun schedulePeriodicKeepAlive(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // 仅在有网络时唤醒，节省电量
            .build()

        // 最小周期为 15 分钟（Android 系统硬性限制）
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "CoreKeepAliveWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
