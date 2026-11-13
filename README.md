# Android 15+ Background Service Architecture

A standards-compliant background-execution architecture for Android 15+:
typed foreground service with the new timeout callbacks handled (no crash),
managed WakeLock, and a WorkManager fallback layer.

适配 Android 15+ 最新后台机制的标准架构：合规前台服务（类型声明 + 超时回调防崩溃）+ WorkManager 双保险兜底。

## What Android 14/15 changed

- **Typed foreground services are mandatory** — since Android 14 every FGS
  must declare a concrete `foregroundServiceType` (dataSync,
  mediaProcessing, location, …) in the manifest and pass the type to
  `startForeground(id, notification, type)`.
- **New runtime caps in Android 15** — `dataSync` and `mediaProcessing`
  services have a cumulative runtime budget (per 24h). When it runs out the
  system calls `Service.onTimeout()`; if the service doesn't stop itself,
  the system throws and the app crashes. Both overloads are handled here.
- **No background FGS starts** — an app in the background generally cannot
  start a foreground service, except for a few exemption triggers
  (high-priority FCM push, certain system broadcasts, user interaction).
- **Cached Apps Freezer** — a backgrounded app without an FGS is frozen
  within seconds (CPU timeslices drop to zero), which is why this design
  pairs the foreground service with periodic WorkManager wake-ups.

## Architecture

| Layer | Role |
|---|---|
| `ReliableBackgroundService` | Foreground service: notification channel + typed `startForeground`, coroutine heartbeat loop on `Dispatchers.IO`, `onTimeout` (both overloads) → `stopSelf`, WakeLock acquired with a 1h cap, renewed by the heartbeat loop whenever it has expired, released in `onDestroy` |
| `SyncWorker` + `TaskScheduler` | WorkManager periodic fallback (15 min minimum period, network-connected constraint, `KEEP` policy) — the system scheduler wakes the process even after it was frozen or killed |

## Service type note

The manifest declares both `dataSync` and `mediaProcessing`. The demo code
starts the service as `dataSync`; for media workloads (e.g. video
transcoding), pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING`
to `startForeground` instead.

## Practice notes

- **Battery whitelist**: for high-reliability use cases, guide the user to
  set the app to "Unrestricted" battery use:

  ```kotlin
  val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = android.net.Uri.parse("package:${context.packageName}")
  }
  context.startActivity(intent)
  ```
- **Android 15 BOOT_COMPLETED restriction**: receivers can no longer
  directly start dataSync/mediaProcessing foreground services on boot —
  route boot recovery through WorkManager and start the FGS from an allowed
  trigger.
- **Prefer push over residency**: if the goal is "receive messages anytime",
  the most reliable and battery-friendly path is system push (FCM
  high-priority abroad; vendor push channels — Xiaomi/Huawei/OPPO/vivo/Honor —
  in China). A high-priority push temporarily unfreezes the process and
  grants a background-start exemption.

## Files

- `ReliableBackgroundService.kt` — the foreground service (Kotlin, coroutines)
- `SyncWorker.kt` — WorkManager worker + periodic scheduler
- `AndroidManifest.xml` — the author's original permissions and service
  declaration (declares both `dataSync` and `mediaProcessing` types)
