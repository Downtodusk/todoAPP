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

## M5 — delivery implementation, acceptance pending

- Local bounded crash capture and user-selected log export/clear. Reports omit exception messages and user data; host tests verify redaction by omission, bounded cause cycles, retention and cleanup.
- Version **0.2.0 / code 2**, unchanged Room schema v1. Release uses a distinct locally generated signing identity; Debug retains the development identity for updating the existing test installation without losing data. Private signing files are ignored and owner-only.
- Android 12+ backup/device-transfer exclusions added alongside disabled backup. Production Release is non-debuggable and excludes performance seed components.
- Final host checks passed: **50 JVM tests; 1 intentionally skipped network evaluation**, Lint with zero errors, Debug/test APKs, signed R8 Release, both profile/benchmark target and runner APKs. Evidence: `.tools/downloads/final-validation-r2.log`; the subsequently added share-intake regression was assembled and lint-checked in `final-share-test-assembly.log`, not executed on a device.
- Signature verification passed (APK Signature Scheme v2) for Release; distribution packaging verifies both APKs and writes SHA256SUMS plus certificate reports. Release APK **2,436,785 bytes (2.32 MiB)**; Debug APK **14,707,187 bytes (14.03 MiB)**. These are packaging measurements, not performance results.
- Install/signing/diagnostic instructions: `docs/release-and-diagnostics.md`. No device operation or real model request was performed during M4/M5 implementation. The new Room/Keystore/AI UI/share regression tests and system log-export path remain unexecuted on hardware.

## Remaining milestones / acceptance

- M2: optional system alarm mode passed lock/process-death and Recents observations. Reboot recovery failed and its fix is deferred by the user; Doze, clock/timezone change and battery-saver device scenarios are not run by user choice. M2 is not fully accepted; the user authorized continuing other tests.
- M3: execute motion/gesture checks, collect measured baseline profiles and run frame/startup benchmarks when device testing resumes.
- M4: execute new UI/Keystore/batch tests on a device and evaluate real BYOK provider behavior when requested.
- M5: resume device matrix and startup/frame/memory measurement; manually validate diagnostic export and signed Release installation when the user is available. Signed APK delivery and crash-log implementation are complete.

All milestones now have implementation or test tooling, but full acceptance is **not complete**. In particular, reboot reminder recovery remains a known failed device case, whose fix was explicitly deferred. APKs are provided for user feedback, not as evidence that pending acceptance has passed.

## 0.3.0 — U03 + I01 visual alignment

- Home and add/edit now follow the selected warm palette, week/month calendars, monoline icons, circular completion controls, time column, quiet field rows and pinned save action. Existing motion, CRUD, reminders and AI paths remain available.
- User-approved Source Serif 4 / Source Sans 3 / Source Han Sans SC fonts are bundled offline with OFL notices. This supersedes the earlier system-font-only constraint. API 26–28 uses the bundled CJK font's own Latin glyphs; older-OS runtime verification remains pending.
- Host validation: **52 JVM tests passed, 1 opt-in network evaluation skipped**, Lint zero errors, Debug/test APKs and signed R8 Release. Profile/benchmark variants also build; no measured frame/startup or generated baseline-profile result is implied.
- Isolated API 36 emulator: **29 instrumentation tests passed, 1 opt-in manual scenario fixture skipped**; narrow 360dp/130% font UI regression **3/3 passed**; final appearance regression **3/3 passed**. New editor coverage verifies the actual persisted calendar date, note/location, custom reminder and recurrence; existing gesture/undo, fake-client AI, share-intake, Room and Keystore tests now have emulator execution evidence.
- This updates the earlier “assembled but not executed” status for those tests only on an emulator. It does not replace hardware acceptance, real BYOK evaluation, OPPO background/reboot tests or performance measurement. No operation was performed on the user's phone.
- Actual rendered screenshots, verification logs and typography limits are documented in [ui-alignment-0.3.0.md](ui-alignment-0.3.0.md). Version **0.3.0 / code 3**, schema v1, existing Debug and Release signing identities retained.

## 0.3.1 — user-reported interaction fixes

- Swipe now reveals action buttons without changing data until tapped; complete/reopen/delete and undo remain. Shared container transitions remeasure instead of scaling glyphs; mismatched nested title sharing is removed. Week ripple is capsule-clipped. Chinese is now user-approved Source Han Serif.
- Add/edit dates are selected through the calendar; the separate date form row is removed. Calendar clear-date and month/year picker preserve date management. Quick-add back/save waits for coordinated IME/sheet exit and updates the home list after dismissal; reduced fade completion is observed rather than estimated by a delay.
- Host: 52 JVM tests passed, 1 opt-in real-model evaluation skipped; Lint zero errors; Debug/test, signed R8 Release and profile/benchmark builds passed. Emulator: 32 tests passed, 1 opt-in fixture skipped; expanded interaction 3/3 and narrow 360dp/130% font 3/3 passed; final press/transition capture 1/1 passed. No phone operations, OEM reminder validation or frame-rate measurement were performed.
- Version **0.3.1 / code 4**, same signing identities and schema. See [fix descriptions, evidence and actual screenshots](ui-fixes-0.3.1.md). Subjective motion acceptance and device predictive-back verification remain for user feedback; the previously deferred reboot failure is unchanged.

## 0.4.0 — time wheels, English and personalized colors

- All-day/timed rows share a reserved time column, with 18sp times and 24dp overflow icons. Native hour/minute wheels replace manual time entry in the editor, AI preview and all-day reminder settings.
- AppCompat per-app locales expose System / 简体中文 / English. 268 paired resources cover UI, date labels, accessibility, validation and notification actions; user content and the Chinese local parser are unchanged.
- Five palettes and custom RGB/hex accents persist in DataStore, coordinate with wallpaper colors, and adapt foreground contrast to light/dark themes. Existing typography, motion and swipe confirmation remain.
- Host checks: 54 JVM passes, 1 opt-in real-model evaluation skipped; bilingual coverage/format validation passes; Lint zero errors (25 warnings, 1 hint). Debug/test, signed R8 Release and profile/benchmark variants build. The language-aware performance journey is assembled, not measured.
- Isolated API 36 emulator: 36 instrumented passes, 1 opt-in fixture skipped; narrow 360dp/130% font 7/7 passes. Final locale initialization refinement passed all 4 new UI tests again. These cover wheel interaction and persistence, alignment coordinates, English notification actions, locale recreation and custom palette validation/persistence/dark mode.
- Version **0.4.0 / code 5**, same Room schema and signing identities. [Screenshots, changes and remaining limits](personalization-0.4.0.md). No phone operation; older-OS locale paths and OEM behavior remain unverified, and the deferred reboot reminder issue is unchanged.

## 0.4.1 — completed layout and detail transition performance

- Completed rows no longer reserve the pending-items time column. Detail navigation now animates fixed-size page placement and opacity with a fade-through handoff; the shared full-editor remeasurement and text/page scaling are removed. Other gestures, quick-add motion and reduced mode remain.
- Debug/test, signed R8 Release and profile/benchmark target/runner builds pass; runner signatures verified. 54 JVM passes, 1 opt-in real-model skip; Lint zero errors. Full API 36 instrumentation: 38 passes, 1 manual-fixture skip. Narrow 360dp/130% font and final normal-size focused tests each pass 2/2. New tests verify completed-item positions and stable long-title layout during normal/reduced transitions.
- Actual focused R8/Full-AOT emulator benchmark: 10 iterations × 3 round trips per version. Frame CPU P95 was 80.41ms in 0.4.0 and 40.77ms in 0.4.1; frame deadline overrun P95 was 96.03ms and 35.36ms. These are software-rendered emulator results, not phone FPS or ≤10% dropped-frame acceptance. Baseline Profile collection and hardware testing remain pending.
- Performance runner signing and fixture restart/selection were corrected after their first installation/execution checks. See [method, raw metrics, screenshots and limits](ui-fixes-0.4.1.md). Version **0.4.1 / code 6**, same signing identities and schema; deferred OPPO boot behavior unchanged.
