# Implementation decisions

## User-confirmed, 2026-10-27

- Functional completeness, correctness, reliability, and excellent user experience take priority. Lightweight packaging and low resource usage are bonus optimizations, not reasons to compromise functionality or experience. The former 8 MB APK limit is removed; APK size is measured and reported without a hard acceptance threshold. Existing startup and interaction performance requirements remain in effect.
- Visual preference: simple, restrained, literary; the user likes Claude's design atmosphere and typography. The user selected **U03 (Week strip and agenda) + I01 (Fine monoline)** on 2026-10-27. Use U03 for layout and I01 for interface and launcher icon art direction. The exact typeface remains an implementation choice within the system-font constraint.
- At the user's request, unused exploration files were removed from `design/`. Only the selected U03 and I01 reference images remain in `design/exploration-v1/`, together with the direction in `design/selected-direction.md` and `design/selection.json`. Generated sample text and dates do not override functional requirements.
- Keep Today as the primary screen and add an All Todos entry point so future tasks remain accessible.
- All-day tasks preserve their calendar date across device timezone changes. Timed tasks preserve their absolute instant.
- Monthly recurrence preserves the original day-of-month anchor: January 31 -> February's last day -> March 31. Extend the original data model to retain this anchor and all-day calendar dates.
- Completing an overdue recurring task advances to its next future occurrence, rebuilding reminders. Completion history is not retained, as specified.
- Treat ordinary process death, removal from Recents, and system Force Stop separately. Do not promise reminders during Force Stop; restore scheduling when Android permits the app to run again.
- Physical-device validation remains pending. The user has an Android 17 device and can install APKs for testing. Multi-brand reminder tests and real-device performance claims remain unverified until measured.
- Development-environment setup is complete and the visual direction is selected. The next implementation milestone is M1; physical-device validation remains pending.

## Environment setup

- Install tools and caches under the project's ignored `.tools/` directory. Load them with `source scripts/android-env.sh`.
- Use an isolated minimal project under `.tools/smoke/` to validate the build toolchain before starting application implementation.
- Keep one production module (`app`); the specified `baselineprofile` module is test tooling.
- No release signing identity has been created yet. Smoke-test APKs use a development-only debug identity.

## Implementation, 2026-10-27

- M1 checkpoint: `8a7eee1`. The first app schema stores `allDayDate`, `repeatAnchorDay`, and a monotonically increasing per-item `revision`. Revision guards prevent stale editors, notification actions or repeated taps from overwriting/advancing a newer item. Monthly time-only edits preserve the day-of-month anchor.
- Non-today agenda views show that date's tasks and completed items plus undated tasks. The overdue section appears on Today and is always calculated against the actual current date; this also avoids duplicate item keys when browsing the past.
- Base palette: warm white `#FAF7F0`, charcoal `#343330`, terracotta `#995139`. Titles use the system serif family; body uses Material system sans. Narrow week strips scroll horizontally to preserve 48 dp touch targets.
- API 31–32 uses `SCHEDULE_EXACT_ALARM`; API 33+ uses `USE_EXACT_ALARM`. This follows the approved sideload-only distribution decision. No startup permission dialog.
- Past reminders are not registered with AlarmManager. Recovery checks can catch up pending missed notifications. Denied notifications are not marked fired, allowing recovery after the user grants access. Each reminder delivery is serialized with its database fired flag; retry uses the same notification identity.
- Manufacturer component candidates are held in a constant map and checked against manifest package visibility before launch, with application-settings fallback. Their effectiveness remains unverified until each device is tested.
- Self-check creates a visible, persistent test todo rather than an invisible alarm that would be lost on process death. It can be deleted after the test.
- Release builds currently remain unsigned; no debug key is silently used as a production identity.
- The user is developing remotely and explicitly chose phone USB on Windows with SSH reverse forwarding for Linux-run device tests. The tunnel listens on Linux loopback port 15037 and forwards to Windows loopback 5037. Device reboot, forced Doze and system clock changes are separate tests requiring coordination with the user.

## First OPPO device validation, 2026-10-27

- Device: OPPO PLJ110 on Android 17/API 37. Production app baseline `19c4d18` passed 15 instrumented database/logic/UI tests and two additional foreground system-alarm/action tests, after explicitly pinning test-only Espresso 3.7.0 to avoid the old transitive 3.5.0 InputManager reflection failure.
- Real background testing must happen outside instrumentation: this device logs `Force stopping ... finished inst` at test termination and clears its alarms. Prepare fixtures, launch the normal app to reschedule, verify the alarm, and only then observe. For process-death tests, terminate the verified debug app PID under its own UID; do not conflate this with Android Force Stop.
- The user's explicit authorization covered enabling Todo's background behavior. The user enabled notification permission, lock-screen notification display, Android battery exemption and OEM autostart. OPPO's App details → Battery usage management was changed from “智能优化后台运行” to “完全允许后台行为” and verified on-screen.
- Those controls are independent: Android battery exemption alone did not remove a roughly three-day OEM alarm adjustment. OEM background allowance removed that adjustment, but with autostart still denied the system skipped the receiver with reason `oplus startup`. Standard permission success is therefore not a reliable “all checks passed” indication.
- The app self-check must explain these independent OEM controls, link to notification display settings, and retain fallback navigation. The observed OPPO autostart Activity requires a signature permission, so direct launching cannot be promised and its permission must not be bypassed.
- Timing/visibility and post-process-death outcomes are recorded separately in `docs/reminder-test-matrix.md`. These ordinary-mode results led to the explicitly approved comparison and optional-mode extension below; the failed observations remain recorded.

## User-confirmed reminder-mode extension, 2026-10-27

- After the ordinary API's observed 44.222-second dispatch delay, the user authorized an isolated `setAlarmClock` comparison. The device stayed locked, the app PID had been terminated without Force Stop, and the system alarm remained registered. Requested `20:37:53.605`, sent `20:37:53.606` (+1 ms); notification record created at `20:37:54.007` (+402 ms). The user confirmed normal visible/audible receipt. This is one controlled device observation, not a universal latency guarantee.
- The user then explicitly approved adding an optional “系统闹钟提醒” mode and enabling it on this test phone. New installations retain ordinary reminders by default. Explain the upcoming-alarm indicator and possible extra resource use; never silently select this mode by manufacturer.
- Both modes share stable PendingIntent identities, receiver/action paths and repository recovery. Store the user choice in DataStore and read it when scheduling, including cold start and reboot. Changing modes re-registers pending reminders. The system's upcoming-alarm entry opens the correct todo detail.
- The temporary debug-only experiment factory is removed after adoption; the production feature uses the normal manual AppContainer. No permanent foreground service, push SDK or backend is introduced. Specification §2/§5.5/§6.2 is amended to reflect this approved change.
- The production reboot test exposed OEM deferral of BootReceiver. The user explicitly requested leaving this issue unresolved for now and continuing other tests, then chose to skip forced Doze and defer battery-saver/timezone setting changes. Preserve these gaps in acceptance records; do not change boot handling or those system settings under that instruction. Recents-removal testing was completed successfully without those changes.
- Device UI regression testing revealed that `launchSingleTop` reused editor state across different todo IDs. Notification navigation now uses a distinct entry for a different ID and skips navigation only when the exact todo is already displayed. The two-notification regression and final 19-test device suite passed; original failure evidence is retained.
