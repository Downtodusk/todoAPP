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

- Host build and test evidence: `.tools/downloads/m1-build.log` (ignored local artifact).
- Unit tests cover day boundaries, DST days, timezone switching, year/month/leap transitions, recurrence, agenda sections and duplicate-item prevention.
- Room instrumentation covers cascading deletion, restoring multiple reminders, recurrence and stale edit/undo protection. It must run on an emulator or device; assembling its APK alone is not execution.
- Device testing not yet performed: no device attached at the initial check.

## Remaining milestones

- M2: alarm scheduling, receivers, permission flow and reminder self-check.
- M3: gestures, shared transitions, completion animation, performance tooling and device motion validation.
- M4: encrypted BYOK, two providers, editable import preview, share intake, fallback and evaluation set.
- M5: device matrix, measurements, privacy-safe crash export, signed release and installation instructions.

Final release signing identity has not been created or selected. Debug APKs are development artifacts.
