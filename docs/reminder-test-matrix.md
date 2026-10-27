# Reminder validation matrix

Status values: **PENDING** = not executed; **PASS** = observed on the named device/build; **FAIL** = evidence recorded. Host compilation is not device validation.

## Automated coverage

`RescheduleAllTest` uses real in-memory Room with fake alarms/notifications to verify: idempotent catch-up, permission recovery, stable alarm identity, delete/undo, completed-task suppression, stale recurring actions, duplicate snooze, timezone rebase, early/duplicate receiver delivery.

`TodoRepositoryTest` covers CRUD/foreign-key cascade/multiple reminders/recurrence/revision guards. `TodoFlowTest` covers add → complete → undo and reminder mode persistence across navigation.

Execution status: **PASS** for 15 original instrumented tests on OPPO PLJ110, Android 17/API 37, 2026-10-27. Two further real-system foreground tests also passed: exact alarm delivery/complete action and snooze action. Background behavior remains separate below.

Latest production-mode implementation: **18/18 PASS** (5 repository, 9 rescheduling, 2 real-system/action, 2 UI). System alarm mode enabled on this phone with user approval; UI test restores the original setting. Evidence: `m2-device-production-mode-suite.log`; host build/JVM/Lint/release evidence: `m2-reminder-mode-build.log`.

Final implementation: **19/19 PASS** (5 repository, 9 rescheduling, 2 real-system/action, 3 UI), evidence `m2-device-final-suite.log`. The added UI test posts two distinct notifications and invokes each real content PendingIntent while the app is running, verifying the correct editable detail for each. It initially exposed reuse of the previous todo's editor state; navigation now isolates different todo IDs. Test teardown also restores its original ActivityScenario launch intent. Original failure: `m2-device-navigation-suite.log`; isolated fixed UI run: `m2-device-navigation-entry-fix.log`. This validates intent routing, not a human tap or lock-screen interaction.

### First device evidence

- Manufacturer/model: OPPO PLJ110, device OP5E17L1, Android 17, API 37.
- Build: `OPPO/PLJ110/OP5E17L1:17/CP2A.260605.016/B.46c413f-20895a6-20bdbdd:user/release-keys`.
- Connection: Windows USB via SSH loopback forward; notification permission enabled manually by the user; no battery exemption changes made yet.
- Baseline app: `19c4d18`; test-only Espresso 3.7.0 compatibility fix and real-device test additions in the working tree.
- `m2-device-instrumentation-r2.log`: 15/15 pass. `m2-device-real-alarms-r4.log`: 2/2 pass with foreground activity. Logs are local ignored artifacts in `.tools/downloads/`.
- Initial background-only instrumentation attempt: **FAIL**, alarm remained pending beyond the 30-second test deadline; system dump showed `adjustment` roughly +3 days. The process resumed when the activity was opened. Cause is not yet isolated; do not infer that foreground success fixes background delivery.
- First lock-screen/no-process attempt: **INVALID fixture**. System log showed `Force stopping com.example.todo ... finished inst`; instrumentation termination removed the alarm, so the absence of a process was not a valid simulation of normal process death. No acceptance claim is based on that run.
- Corrected lock-screen scenario: completed with late delivery; see timing observations below. After the preparation instrumentation exits, launch Todo once before its due time and verify the actual system alarm entry. Then observe without instrumentation/app launches.

### OPPO observations during lock/background validation

1. Valid locked-device run with live app process: requested at `20:14:08.470`, AlarmManager sent at `20:15:15.284` (66.814 seconds late). DB `fired=true` was observed without launching the app. The user reported vibration but no visible reminder. Record **DELIVERED LATE**, not punctuality PASS.
2. The app's lock-screen visibility override was `-1` (hidden). Global lock-screen notifications were enabled. The user subsequently enabled Todo's lock-screen notification setting; visible delivery was confirmed in run 7 below.
3. The user granted Android battery-optimization exemption; `dumpsys deviceidle whitelist` confirmed `user,com.example.todo,10461`. This alone did not remove OEM background adjustment (about +3 days).
4. On the device's normal App details → Battery usage management screen, the selected option remained “智能优化后台运行”. With the user's explicit authorization it was changed to “完全允许后台行为”; the selected radio was re-read and confirmed. This removed the +3-day adjustment, but a roughly 67-second dispatch window was still visible.
5. For a separate normal process-death run, the target app PID was explicitly killed using its own debuggable UID; external snapshot confirmed no app PID and a registered system alarm. Requested at `20:23:34.127`, system sent at `20:23:44.052` (9.925 seconds late). No app process started and `fired` remained false. Broadcast history explicitly reported `SKIPPED` and `reason: oplus startup`. Record **FAIL — OEM autostart blocked**, not a timing-only failure.
6. The actual autostart component belongs to `com.oplus.battery/com.oplus.startupapp.view.StartupAppListActivity`; direct launch requires `oplus.permission.OPLUS_COMPONENT_SAFE` and is blocked for shell/ordinary apps. The user enabled autostart through normal Settings. The app must retain its fallback settings navigation and textual guidance; do not attempt to bypass this signature permission.
7. The user enabled Todo autostart through normal Settings. Repeated a **60-second** reminder after terminating only the verified app PID under its own UID, then locking the phone. Before observation: app PID absent, `fired=false`, alarm registered, no +3-day adjustment. Requested at `20:30:41.957`; AlarmManager sent at `20:31:26.179` (**44.222 seconds late**); ActivityManager started the process for `ReminderReceiver` at `20:31:26.203`. External DB snapshot found `fired=true`, and system notification record was present with lock-screen hiding override cleared. The user confirmed visible lock-screen notification plus sound/vibration. **PASS for eventual delivery after process death and lock; FAIL for punctuality.** Evidence: `m2-device-autostart-before.json`, `m2-device-autostart-after2.json`, `m2-device-autostart-delivery-evidence.log`.

8. User-approved `setAlarmClock` comparison with the phone locked and the app PID terminated normally: requested `20:37:53.605`, sent `20:37:53.606` (**+1 ms**); process started at `20:37:53.633`, notification record created at `20:37:54.007` (**+402 ms**). External DB snapshot showed `fired=true`; the user confirmed normal visible/audible receipt. **PASS for this lock/process-death observation.** Evidence: `m2-device-alarm-clock-before.json`, `m2-device-alarm-clock-after.json`, `m2-device-alarm-clock-delivery-evidence.log`. The user subsequently approved the production option and its activation on this phone.
9. Production system-alarm mode, user-authorized reboot test: created a 180-second reminder due `20:52:57.629`; before reboot it was registered as an alarm clock with zero window. The user rebooted, unlocked once and relocked without opening Todo during the initial observation. After boot there was no Todo PID or alarm; broadcast diagnostics showed `BOOT_COMPLETED` for `BootReceiver` as `DEFERRED OFFLOAD`, `DEFER_BY_OPLUS SUB_REASON: PRESSURE`, `reason: mBroadcastConsumerDeferApply`. At target +87 seconds the DB still had `fired=false`. A later snapshot had `fired=true`, but ActivityManager recorded a **top-activity** launch at `20:55:25.368`, so this is cold-start catch-up evidence, **not** automatic boot recovery. **FAIL for timely reboot recovery.** The user explicitly asked to defer fixing this issue and continue other tests. Evidence: `m2-device-reboot-before.json`, `m2-device-reboot-restored.json`, `m2-device-reboot-after2.json`, `m2-device-reboot-broadcast-detail.log`, `m2-device-reboot-final.json`, `m2-device-reboot-late-delivery.log`.

10. Production system-alarm mode after removal from Recents: the user swiped away only Todo and locked the phone. External snapshot confirmed no app PID, `fired=false` and the pending zero-window alarm. Requested `20:57:51.894`, sent `20:57:51.895` (**+1 ms**), process started for `ReminderReceiver` at `20:57:51.924`, notification record created at `20:57:52.437` (**+543 ms**). The user confirmed normal visible/audible delivery. **PASS for this Recents-removal run.** Evidence: `m2-device-recents-before.json`, `m2-device-recents-removed.json`, `m2-device-recents-after.json`, `m2-device-recents-delivery.log`.

Doze, battery saver and timezone switching were offered separately; the user chose not to adjust those system settings for now. Record **NOT RUN — user deferred**, not PASS. Repository/host timezone tests remain distinct from device acceptance.

These are observations from one device/build. They do not establish behavior of other OPPO versions or other brands. Failed/invalid runs remain part of the evidence and are not overwritten by successful retests. Timings above use the phone's local Asia/Shanghai timezone.

## Device matrix

| Device / Android / build | 1 minute reminder locked | Recents removal | Process death | Doze | Reboot | Time/timezone change | Battery saver | Complete / +10 min | Predictive back |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OPPO PLJ110 / Android 17 / baseline 19c4d18 | DELIVERED, +44.222 s (60 s test) | PENDING | DELIVERED after PID kill, +44.222 s | PENDING | PENDING | PENDING | PENDING | PASS (foreground) | PENDING |
| Same OPPO / system alarm comparison + production option | PASS, dispatch +1 ms, notification +402 ms | PASS, dispatch +1 ms, notification +543 ms | PASS, same locked run | NOT RUN (user deferred) | FAIL: boot broadcast deferred; cold-start catch-up only | NOT RUN (user deferred) | NOT RUN (user deferred) | PASS (production foreground) | PENDING |
| Second brand / pending | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING |
| Third brand / pending | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING |

At least three of Xiaomi / Huawei or Honor / OPPO or vivo / Samsung / Pixel must be covered for final acceptance. Emulator results do not satisfy OEM coverage.

## Reproducible device procedure

1. Install the development APK. Record model, manufacturer, Android build fingerprint, app commit, battery settings and permission/channel states.
2. Open Settings → Reminder self-check. Grant notification/exact-alarm permissions when requested. Schedule the 10-second test. Observe notification and tap to open its correct detail.
3. Create a timed task one minute in the future with an on-time reminder. Lock the phone. Record scheduled and observed delivery time (including any delay).
4. Repeat separately with removal from Recents, ordinary process kill, Doze, reboot before trigger, and battery saver. Device-state changes such as reboot, forcing Doze or changing system time require the user's confirmation; restore changes afterwards.
5. Tap Complete: one-shot task becomes completed; recurring task advances once. Reusing a stale notification must not complete the next occurrence.
6. Tap Postpone 10 minutes: notification closes; exactly one new reminder appears. The original due date remains unchanged. Also test one hour / tomorrow at the current local time from detail.
7. Delete then undo an item with a future reminder; confirm delivery still occurs. Complete before the trigger; confirm no delivery.
8. Set an all-day item and a timed item. Change timezone: all-day calendar date and local reminder time stay fixed; timed absolute instant stays fixed. Restore timezone.
9. Deny notifications, save a task and confirm non-blocking warning. Grant permission through self-check and verify recovery of a missed reminder exactly once.
10. Force Stop is a separate platform limitation: no guarantee while stopped. Open the app again and verify pending scheduling is restored.

OEM setting components are best-effort candidates, not verified capabilities. Missing components fall back to application settings.
