# Android Alarm Keep-Alive Architecture (Android 12 → 17+)

A complete, production-shaped keep-alive design built on one idea: don't
fight the freezer — borrow the system's alarm whitelist. An exact-alarm
chain wakes the app on schedule, short work runs inside the broadcast's
whitelist window, heavy work escalates to a short-lived foreground
service, and the chain re-arms itself before anything else can fail.

以系统闹钟白名单为核心的完整保活架构：精确闹钟链式接力唤醒 → 短任务在广播窗口内直接执行 → 重任务才拉起短命前台服务 → 每次唤醒第一件事是预约下一次。适配 Android 12–17+ 的 Cached Apps Freezer 与 Doze。

> Revision note: this is the third design in this repo. The first was a
> resident foreground service with a WorkManager fallback; the second a
> first-generation alarm chain. Both were replaced at the author's
> direction and remain available in git history.

## How it works

1. **Ignition** — the app checks the exact-alarm privilege
   (`canScheduleExactAlarms()`), guides the user to the system grant page
   if missing, then arms the first alarm.
2. **Chain** — `AlarmKeepAliveManager` schedules with
   `setExactAndAllowWhileIdle` (Doze-penetrating), cancelling any previous
   instance first; without the privilege it degrades to
   `setAndAllowWhileIdle` instead of dying.
3. **Wake-up** — `AlarmKeepAliveReceiver` fires inside the system's
   temporary whitelist: it re-arms the next alarm **first**, takes a short
   WakeLock, and runs sub-10-second work via `goAsync()`; heavy work starts
   `WakeUpWorkerService` (typed FGS, `START_NOT_STICKY`, stops itself).
4. **Self-healing** — time/timezone changes re-align the chain
   (`TIME_SET` / `TIMEZONE_CHANGED` are exempt from the implicit-broadcast
   ban), and `BootRestoreReceiver` re-arms the chain 30 s after reboot —
   it deliberately schedules an *alarm* rather than starting a service,
   which is what Android 15 restricts from `BOOT_COMPLETED`.

## Notes

- The 15-minute interval matches the Doze quota for
  `setExactAndAllowWhileIdle` (about one fire per 15 min per app in deep
  Doze); shorter intervals get stretched by the system anyway.
- Declare **one** exact-alarm permission per app category:
  `SCHEDULE_EXACT_ALARM` (user-granted) for most apps, or
  `USE_EXACT_ALARM` only for genuine alarm/reminder apps (Play policy).
  The manifest here shows both for reference.
- Between wake-ups the process may be frozen or dead — that's the point:
  zero CPU, zero battery, nothing for the OOM killer or vendor task
  killers to hunt.

## Files

- `AndroidManifest.xml` — permissions (exact alarm, WakeLock, boot) and
  all component registrations
- `AlarmKeepAliveManager.kt` — permission check/guidance + the chained
  exact-alarm scheduler
- `AlarmKeepAliveReceiver.kt` — whitelist-window task runner (light work
  inline, heavy work escalated)
- `BootRestoreReceiver.kt` — re-arms the chain after reboot
- `WakeUpWorkerService.kt` — short-lived typed foreground service for
  heavy wake-up work (both `onTimeout` overloads handled)
