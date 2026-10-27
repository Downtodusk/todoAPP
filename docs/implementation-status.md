# Implementation status

This file separates implemented code, host checks, and device acceptance. An unchecked device test is not a passing test.

## M1 — foundation

- Gradle application, R8/resource shrinking, Android 26–37, warm light/dark/dynamic themes and notebook launcher icon.
- Room v1 schema exported under `app/schemas/`; no destructive migration fallback. Future schema changes require explicit migrations and migration tests. No v0 database shipped, so no invented v0 migration.
- Atomic CRUD, multiple reminder rows, undo with revision conflict detection, recurring-task advancement and original monthly anchor.
- All-day ISO dates stay stable across timezone changes; timed items retain their absolute instant.
- Today/week navigation, previous/next week, return to today, All Todos search, editor, completion, deletion and undo.
- The AI destination is a clearly labeled M4 placeholder at this milestone. Reminder scheduling is M2, motion polish is M3.

### Validation

- Host checks passed: Debug APK, test APK, 24 JVM unit tests, Lint (no errors). Evidence: `.tools/downloads/m1-build.log` (ignored local artifact).
- Unit tests cover day boundaries, DST days, timezone switching, year/month/leap transitions, recurrence, agenda sections and duplicate-item prevention.
- Room instrumentation covers cascading deletion, restoring multiple reminders, recurrence and stale edit/undo protection. It must run on an emulator or device; assembling its APK alone is not execution.
- Device testing now runs through Windows USB / SSH on OPPO PLJ110, Android 17/API 37. The 5 Room repository tests and add → complete → undo UI test passed on 2026-10-27.

## M2 — reminder implementation, device acceptance pending

- Exact alarms, private receivers, high-importance notification channel, complete/+10-minute actions, detail navigation and further snooze choices.
- Save-time permission prompts; denial leaves the todo saved. Notification/channel blocking remains visible in self-check.
- Cold start / boot / package replacement / clock and timezone change recover pending reminders. Missed reminders are delivered once; permission-denied delivery remains pending for recovery.
- Calendar-based all-day reminder rebasing; absolute timed reminders; cancellation and undo recovery; stale notification/repeated-click guards.
- Self-check with notification, exact alarm, channel/sound/vibration, battery exemption and manufacturer guidance; persistent 10-second test task.
- User-approved optional system alarm mode (`setAlarmClock`), stored in DataStore and applied to pending reminders on change and recovery. New installations default to ordinary mode; the user's test phone is explicitly configured for system alarm mode.
- Default all-day reminder time in settings. Timed reminder offsets include presets and a custom value. Existing multiple-reminder records are retained unless the user explicitly changes the reminder selection.
- `docs/reminder-test-matrix.md` contains the device acceptance procedure. OEM components are candidates, not verified device behavior.
- Windows USB → SSH reverse ADB forwarding instructions and Linux wrapper are in `docs/remote-device-testing.md` and `scripts/adb-remote.sh`.

Host checks: Debug APK + AndroidTest APK, 24 JVM unit tests, Lint and R8/shrunk unsigned Release build. Build evidence: `.tools/downloads/m2-build.log`.

Latest production-mode build revalidated all these checks: `.tools/downloads/m2-reminder-mode-build.log`.

Device evidence (2026-10-27, OPPO PLJ110 / API 37):

- The original 15 instrumented tests (5 repository, 9 rescheduling, 1 UI) passed: `.tools/downloads/m2-device-instrumentation-r2.log`.
- Two additional real-system tests passed with the activity in the foreground: AlarmManager → receiver → notification → Complete, and notification → Postpone 10 minutes with duplicate action protection. Evidence: `.tools/downloads/m2-device-real-alarms-r4.log`.
- After adopting optional system alarm mode, the complete 18-test suite passed (5 repository, 9 rescheduling, 2 real system/action, 2 UI including mode persistence). Evidence: `.tools/downloads/m2-device-production-mode-suite.log`. The setting was enabled on this phone before this run and restored to that choice by the UI test.
- The first UI run failed in Espresso 3.5.0 before executing UI actions; pinning stable Espresso 3.7.0 fixed the removed InputManager reflection. App production dependencies did not change.
- OPPO denied UiAutomation's notification permission grant. The user enabled notifications through the normal system settings UI. Tests now require that permission instead of attempting shell grants.
- A first real-alarm test without a foreground activity timed out; the system alarm dump showed an OEM adjustment of approximately +3 days. Launching the activity resumed the test process. Foreground success does **not** establish background reliability; separate lock-screen results follow.
- `DeviceScenarioFixtureTest` is an opt-in preparation/cleanup helper, skipped in ordinary runs. Preparing a fixture is not a passing reminder test. `scripts/read-device-reminder-state.py` observes only the app's DB/WAL without launching the app, to avoid masking failure with cold-start catch-up.
- The first lock-screen fixture attempt is **INVALID**, not an app reliability result: Android logs show `Force stopping ... finished inst`, which removed the newly registered alarm. The corrected procedure finishes preparation, launches the app once before the deadline to register alarms, verifies an AlarmManager entry, then begins observation. Never run instrumentation during the observation window.
- Subsequent OPPO runs identified three independent settings: Android battery exemption, OEM “完全允许后台行为”, and autostart allowance. Without autostart, broadcast history explicitly recorded `SKIPPED: oplus startup`. With all enabled, a 60-second ordinary reminder successfully started a dead app process and produced a user-confirmed visible/audible lock-screen notification, but **44.222 seconds late**. Ordinary-mode punctuality remains **failed** on this device. See the full matrix for failed and invalid earlier runs.
- The user-approved system alarm comparison under lock/process death dispatched +1 ms after the target; the notification record was created +402 ms after the target, with user-confirmed visible/audible receipt. The option was then approved for production. This one-device result does not establish reboot/Doze or other-brand coverage.
- Production-mode reboot test failed timely recovery: OPPO deferred BootReceiver (`DEFER_BY_OPLUS / PRESSURE`); no alarm/PID and `fired=false` remained at target +87 seconds. A later top-activity launch led to catch-up and is not automatic boot recovery. The user asked to defer fixing reboot recovery and continue other tests; Doze was separately skipped at the user's request.
- Production-mode Recents-removal test passed: no process before trigger, system dispatch +1 ms, notification record +543 ms, user-confirmed visible/audible delivery. The user deferred battery-saver and timezone device changes as well; no such settings were modified.
- A new two-notification UI regression test exposed stale detail editor state when opening different todos consecutively. Different todo IDs now get separate navigation entries; reopening the currently displayed ID does not duplicate it. The original failing run remains in `m2-device-navigation-suite.log`. Final full suite: **19/19 PASS**, including notification-to-correct-detail routing, in `m2-device-final-suite.log`; final host validation in `m2-final-validation-build.log`. Test teardown restores ActivityScenario's launch intent after exercising `onNewIntent`.

The user chose Windows USB plus SSH ADB forwarding, now working on Linux loopback port 15037. No claim of three-brand coverage or completed lock/Doze/reboot acceptance is made.

## M3 — interaction implementation, device acceptance deferred

- Swipe left to complete/right to delete, threshold haptics, animated check/strike/fade-collapse, ripple/press scale and stable-key item placement.
- Shared list/detail container/title bounds, predictive-back transitions, FAB/sheet coordination and a fade-only reduced-motion sheet. Reduced mode uses the explicit user choice or the device low-RAM flag.
- Detail snapshots load before navigation starts; different todo IDs keep separate editor/navigation states. A canceled completion animation still submits the accepted action.
- Reusable loading skeleton; isolated `baselineprofile` tooling covers cold start, sheet, scrolling and detail. Unminified profile generation and optimized measurements are separate variants with a `.benchmark` application ID and synthetic data.
- Procedures and pending visual/performance checks: `docs/motion-and-performance.md`. Generated app baseline/startup profiles are absent until a real collection run succeeds. No frame-rate result is claimed.
- The M2 checkpoint's 19 passing instrumented tests do not establish M3 acceptance. A new gesture/undo test is assembled for later execution. The user explicitly deferred further phone operations and will download an APK to test when available.
- Host validation passed: Debug/test APKs, unminified profile and R8 benchmark target/test APKs, unsigned Release APK, 24 JVM tests and Lint. Evidence: `.tools/downloads/m3-final-build-r2.log`. The performance runner's merged manifest was checked to instrument its own package, not the measured app process.

## M4 — AI implementation, device/model acceptance deferred

- Encrypted BYOK settings, one-time send consent, test connection, OpenAI-compatible and Anthropic clients; fixed error messages and bounded responses.
- Editable selectable preview, atomic batch save and reminder scheduling, explicit local rules/manual fallback, source-retention setting off by default, and ACTION_SEND text intake without automatic sending.
- 32 synthetic paired evaluation fixtures and an explicitly opt-in evaluator. Real model accuracy has not been measured.
- Host validation: 47 passing JVM tests, 1 deliberately ignored real-model evaluation; Debug APK, AndroidTest APK, Lint and unsigned R8 Release build passed. Evidence: `.tools/downloads/m4-validation-r1.log`.
- New Room batch/rollback, Keystore and fake-client UI instrumented tests are assembled, **not executed**. See `docs/ai-import.md` for behavior and limits.

## Remaining milestones / acceptance

- M2: optional system alarm mode passed lock/process-death and Recents observations. Reboot recovery failed and its fix is deferred by the user; Doze, clock/timezone change and battery-saver device scenarios are not run by user choice. M2 is not fully accepted; the user authorized continuing other tests.
- M3: execute motion/gesture checks, collect measured baseline profiles and run frame/startup benchmarks when device testing resumes.
- M4: execute new UI/Keystore/batch tests on a device and evaluate real BYOK provider behavior when requested.
- M5: device matrix, measurements, privacy-safe crash export, signed release and installation instructions.

Final release signing identity has not been created or selected. Debug APKs are development artifacts.
