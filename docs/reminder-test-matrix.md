# Reminder validation matrix

Status values: **PENDING** = not executed; **PASS** = observed on the named device/build; **FAIL** = evidence recorded. Host compilation is not device validation.

## Automated coverage

`RescheduleAllTest` uses real in-memory Room with fake alarms/notifications to verify: idempotent catch-up, permission recovery, stable alarm identity, delete/undo, completed-task suppression, stale recurring actions, duplicate snooze, timezone rebase, early/duplicate receiver delivery.

`TodoRepositoryTest` covers CRUD/foreign-key cascade/multiple reminders/recurrence/revision guards. `TodoFlowTest` covers add → complete → undo.

Execution status: **PENDING device**. Instrumentation APK compilation is tracked separately in implementation-status.md.

## Device matrix

| Device / Android / build | 1 minute reminder locked | Recents removal | Process death | Doze | Reboot | Time/timezone change | Battery saver | Complete / +10 min | Predictive back |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| User phone / Android 17 / pending identification | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING | PENDING |
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
