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
- Device testing not yet performed: no device attached at the initial check.

## M2 — reminder implementation, device acceptance pending

- Exact alarms, private receivers, high-importance notification channel, complete/+10-minute actions, detail navigation and further snooze choices.
- Save-time permission prompts; denial leaves the todo saved. Notification/channel blocking remains visible in self-check.
- Cold start / boot / package replacement / clock and timezone change recover pending reminders. Missed reminders are delivered once; permission-denied delivery remains pending for recovery.
- Calendar-based all-day reminder rebasing; absolute timed reminders; cancellation and undo recovery; stale notification/repeated-click guards.
- Self-check with notification, exact alarm, channel/sound/vibration, battery exemption and manufacturer guidance; persistent 10-second test task.
- Default all-day reminder time in settings. Timed reminder offsets include presets and a custom value. Existing multiple-reminder records are retained unless the user explicitly changes the reminder selection.
- `docs/reminder-test-matrix.md` contains the device acceptance procedure. OEM components are candidates, not verified device behavior.
- Windows USB → SSH reverse ADB forwarding instructions and Linux wrapper are in `docs/remote-device-testing.md` and `scripts/adb-remote.sh`.

Host checks: Debug APK + AndroidTest APK, JVM unit tests, Lint and R8/shrunk unsigned Release build. Current test sources contain **15 instrumented tests** (5 repository, 9 rescheduling, 1 UI); execution is **pending the device tunnel**. Build evidence: `.tools/downloads/m2-build.log`.

The user chose Windows USB plus SSH ADB forwarding. The Linux-side device connection will use loopback port 15037. No physical tests have been reported as passed.

## Remaining milestones / acceptance

- M2: execute instrumented and real notification tests on the connected phone; complete/review acceptance evidence before moving to motion polish.
- M3: gestures, shared transitions, completion animation, performance tooling and device motion validation.
- M4: encrypted BYOK, two providers, editable import preview, share intake, fallback and evaluation set.
- M5: device matrix, measurements, privacy-safe crash export, signed release and installation instructions.

Final release signing identity has not been created or selected. Debug APKs are development artifacts.
