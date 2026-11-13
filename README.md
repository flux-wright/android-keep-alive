# Android 15+ Background Service Architecture

An event-driven background architecture for Android 15+: instead of a
resident process fighting the OOM killer, an AlarmManager chain wakes the
app on schedule, a typed foreground service does one round of work and
exits, and the next alarm is already armed. Zero CPU and zero battery
between wake-ups.

适配 Android 15+ 的事件驱动后台架构：闹钟链接力唤醒 → 类型化前台服务干一轮活即退出 → 下一次闹钟提前布好。唤醒间歇期零 CPU、零电耗。

## Design: borrow the alarm whitelist, don't fight the freezer

Modern Android kills resident keep-alive tricks at the system level (App
Standby Buckets, Cached Apps Freezer). The system's own alarms, however,
carry a temporary whitelist: when an exact alarm fires, the process is
unfrozen and may start a foreground service. So this architecture
"reserves" wake-ups with the OS instead of squatting in memory:

1. `AlarmScheduler` arms an exact alarm (`setExactAndAllowWhileIdle`,
   Doze-penetrating), 15 minutes out.
2. `AlarmWakeUpReceiver` fires inside the whitelist window — it re-arms
   the next alarm **first** (the chain can never break, even if this run
   fails), then starts the service.
3. `ReliableBackgroundService` runs one work cycle and stops itself.

> Revision note: the first version of this repo used a resident service
> (infinite heartbeat, `START_STICKY`) with a WorkManager fallback. It was
> replaced by this alarm-driven design at the author's direction; the old
> design remains in git history (initial commit).

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
  (high-priority FCM push, alarm delivery, user interaction).
- **Cached Apps Freezer** — a backgrounded app without an FGS is frozen
  within seconds (CPU timeslices drop to zero) — the reason a resident
  heartbeat is a dead end, and scheduled wake-ups are the way out.

## Architecture

| Layer | Role |
|---|---|
| `AlarmScheduler` + `AlarmWakeUpReceiver` | Wake-up chain: exact alarm penetrates Doze; the receiver re-arms the next alarm before anything else, then starts the service; falls back to an inexact alarm if the exact-alarm permission is denied |
| `ReliableBackgroundService` | Short-lived typed foreground service: notification channel + typed `startForeground`, one work cycle on `Dispatchers.IO`, then `stopSelf()`; `START_NOT_STICKY` (revival is the alarm's job); `onTimeout` (both overloads) handled; WakeLock with a 1h safety cap, released in `onDestroy` |

## Service type note

The manifest declares both `dataSync` and `mediaProcessing`. The demo code
starts the service as `dataSync`; for media workloads (e.g. video
transcoding), pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING`
to `startForeground` instead.

## Practice notes

- **Exact-alarm quota**: `setExactAndAllowWhileIdle` alarms are
  quota-limited in Doze (roughly one per ~15 minutes per app). The 15-minute
  chain interval sits exactly at that budget — don't shorten it.
- **Exact-alarm permission**: on Android 12+ most apps are denied
  `SCHEDULE_EXACT_ALARM` by default; guide users to grant it, or declare
  `USE_EXACT_ALARM` only for genuine alarm/reminder apps (store review).
  The scheduler catches the denial and degrades gracefully.
- **Battery whitelist**: on aggressive vendor ROMs (Xiaomi/Huawei/OPPO/
  vivo/Honor), pair the alarm chain with guiding the user to set the app to
  "Unrestricted" battery use:

  ```kotlin
  val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = android.net.Uri.parse("package:${context.packageName}")
  }
  context.startActivity(intent)
  ```
- **Android 15 BOOT_COMPLETED restriction**: receivers can no longer
  directly start dataSync/mediaProcessing foreground services on boot —
  re-arm the alarm chain on boot instead, and let the alarm do the start.
- **Prefer push over residency**: if the goal is "receive messages anytime",
  the most reliable and battery-friendly path is system push (FCM
  high-priority abroad; vendor push channels — Xiaomi/Huawei/OPPO/vivo/Honor —
  in China). A high-priority push temporarily unfreezes the process and
  grants a background-start exemption.

## Files

- `AlarmScheduler.kt` — exact-alarm scheduling chain (15 min interval,
  graceful fallback when the exact-alarm permission is denied)
- `AlarmWakeUpReceiver.kt` — woken by the system alarm whitelist; re-arms
  the next alarm first, then starts the service
- `AlarmManifestPermissions.xml` — the exact-alarm permission snippet
  (`SCHEDULE_EXACT_ALARM`) to merge into the manifest
- `ReliableBackgroundService.kt` — the short-lived foreground service
  (Kotlin, coroutines)
- `AndroidManifest.xml` — the author's original permissions and service
  declaration (declares both `dataSync` and `mediaProcessing` types)
