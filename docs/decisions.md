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

## Continued implementation with device testing deferred

- The user asked to continue according to plan and will download an APK for testing later. Do not operate the connected phone, run instrumentation or alter its settings until device testing is requested again. Continue host builds/unit checks and preserve pending/failed acceptance items.
- M3 uses a fade-only bottom-aligned modal editor in reduced mode because the pinned Material 3 1.4 library keeps its motion-scheme override internal. Ordinary mode uses ModalBottomSheet. No internal API suppression or unstable dependency upgrade is introduced for this fallback.
- Performance tooling runs in its own process and targets only `com.example.todo.benchmark`. Fixture code is excluded from normal debug/release APKs. Collect unobfuscated baseline rules from the non-debuggable `profile` build; rebuild and measure R8 code with `benchmark`. Collection is explicit through the remote-ADB script and is never a side effect of assembling a release.

## M4 AI implementation

- BYOK remains optional. Configuration requires HTTPS with no URL credentials/query parameters; changing provider/base URL requires re-entering a key, whose encrypted envelope is bound to that provider/address using AES-GCM associated data. Model changes can retain the key. No plaintext key is saved in instance state.
- Data-send consent precedes both test connection and recognition. Sharing a notice only fills the import input; clipboard reads occur only after a tap. Source retention is off by default. Explicit manual creation from an unrecognized notice carries the original text into the editable note as specified.
- JSON-mode rejection retries once without response_format; HTTP redirects and transport retries are disabled. All errors shown to users are fixed descriptions, never remote response bodies. Response reading is bounded at 256 KiB.
- Selected preview items are saved atomically before one rescheduling callback. No network error silently selects local rules. Local fallback is deliberately one-item and always goes through preview confirmation.
- The 32 evaluation fixtures are synthetic. Hosted model evaluation is opt-in only; tests use a fake client or loopback MockWebServer. No user key was requested or used during implementation.

## M5 delivery preparation

- Added bounded private uncaught-exception records with exception class and call locations only. Exception messages, filenames, thread names, suppressed errors and arbitrary payloads are omitted entirely instead of relying on secret-pattern redaction. Export uses the system document picker only on user action; no analytics/crash service or storage permission is added.
- Added explicit Android 12+ cloud/device-transfer exclusions in addition to disabled backup, so private local data and Keystore-bound ciphertext do not enter automatic migration.
- Created a local release signing identity under ignored `signing/`, separate from the existing development identity; the private config is ignored and restricted to owner access. A non-overwriting initialization script and build-time requirement make identity reuse explicit. The user authorized implementation and APK delivery; creating this local signing artifact neither publishes the app nor installs it anywhere.
- Deliver both signed optimized Release and the existing-signature Debug update. The user's current debug installation should receive the Debug update to retain data; Release cannot overwrite a different signature. Version 0.2.0 uses versionCode 2 and preserves Room schema v1.
- Device testing remains deferred. Performance scripts are explicit opt-in tooling, and no new phone operation or system-setting change was made while completing M4/M5.

- Final review fixed share-intake sequencing: read configuration and route before consuming the share event, because consuming changes the LaunchedEffect key and cancels it. Success feedback similarly uses a separate UI coroutine after resetting import state. A share-intake device regression is prepared but not run. JSON decoding/validation runs on the default dispatcher to avoid main-thread response parsing.

## 0.3.0 U03 + I01 alignment

- The user requested a second visual pass against U03 + I01 while retaining functionality and motion, then explicitly accepted Source Serif 4 / Source Sans 3 plus Source Han Sans SC as open alternatives to Claude's fonts. This supersedes the original system-font-only requirement; neither proprietary Claude fonts nor an unverified claim of exact matching is introduced. Fonts are embedded unmodified with OFL notices and pinned provenance.
- The base palette is now warm white `#FAF8F2`, charcoal `#30302D`, terracotta `#A8583D`. Dynamic color defaults to false when unset; an explicit stored preference remains unchanged. Dark mode has matching warm containers and system-bar icon contrast.
- Home uses a compact seven-day strip with dots, separate all-day/timed sections, left-hand times, circular completion controls, fine dividers and a round add button. Settings, all-todos, week navigation, completed folding, gestures and undo remain available.
- Add/edit uses a real Monday-first month calendar, an outlined title, icon/value rows, field dialogs and a pinned save button. Opening the sheet shows its calendar first; the keyboard appears when the title is tapped. The full date picker still supports distant date/year selection. Copy/delete/snooze are in detail overflow; reminders, recurrence, location and notes remain editable. The schema and reminder scheduling implementation are unchanged.
- API 29+ composes Latin fonts with a Chinese fallback through public APIs. Android 8/9 uses the bundled Chinese face's Latin glyphs for compatibility. This is a documented typography difference, not a claim of full cross-version visual equivalence.
- UI tests now use the pinned Compose library's `junit4.v2.createAndroidComposeRule`. The legacy rule stalled an animated detail transition and failed during lifecycle teardown; the v2 dispatcher completed the same production path. Navigation/Compose production dependency versions and motion were not changed to work around the test runner.
- Verification uses an isolated API 36 emulator with synthetic fixtures, dedicated ADB port 15038 and serial emulator-5580. It does not use the user's remote phone or port 15037. No reboot, Doze, battery-saver or phone-setting operation is part of this visual pass. Historical OPPO reboot failure remains deferred.
- Version 0.3.0 / code 3 reuses both existing signing identities and Room schema v1. Debug is the update path for the user's current test installation.

## 0.3.1 user feedback fixes, 2026-10-28

- User approval explicitly replaces swipe-to-execute with swipe-to-reveal: left Complete/Reopen and right Delete. `AnchoredDraggable` holds the row at an 88dp tray; only explicit button clicks mutate data, and tapping the foreground closes the tray. Checkbox and accessibility actions remain explicit actions; undo is retained.
- Shared list/detail containers use `ResizeMode.RemeasureToBounds`. The mismatched nested shared title targets (list Text versus whole OutlinedTextField) are removed so the transition cannot enlarge the glyphs with their containers. Container movement, fades and back navigation remain.
- Week cells clip ripple to the same rounded capsule as their outline.
- The user clarified that open Source Han Serif is acceptable for the requested Chinese Song typeface. It replaces the Chinese sans asset and license, with pinned source and hash; Latin font choices stay unchanged.
- The separate date form row is removed from add/edit. The upper calendar selects dates; tapping its month/year heading opens the full picker for distant dates. Clear-date remains a calendar action and clears time/recurrence as before.
- Sheet cancellation/back and successful save coordinate IME dismissal before sheet exit, await actual fade completion in reduced mode, and keep the editor enabled-looking while exiting. The visible home list remains stable while the sheet is open, then reflects saved items after dismissal. Input/save callbacks reject duplicate closing requests.
- Version 0.3.1 / code 4 retains both signing identities, application ID and Room schema v1. The user's phone is not operated during implementation; the OPPO reboot issue remains explicitly deferred.

## 0.4.0 — alignment, time wheels and personalization

- The user requested aligned all-day/timed rows, larger times/ellipsis, wheel-based time selection, English support and editable colors. All-day, undated and completed rows now reserve the same leading column as timed pending rows; HH:mm grows from 14sp to 18sp, overflow grows from 18dp to 24dp, while both action targets stay 48dp. Column width also scales with font size.
- Hour/minute NumberPicker wheels cover 00:00–23:59 without keyboard entry. Editor, AI preview and default all-day reminder settings share the component. Cancel does not commit; clearing a task time still makes it all day. Date selection remains in the calendar.
- AppCompat manages System / 简体中文 / English independently of theme. English default resources and Chinese resources cover navigation, editors, settings, accessibility labels, errors and notification actions. User content and AI prompts/parser semantics are not translated. The local rules fallback still recognizes Chinese and is labeled accordingly in English.
- Color personalization means 5 presets plus an opaque RGB accent via hex or sliders. Existing warm surfaces/typography remain the visual base; explicit dynamic color and dark mode remain available. Selecting a custom/preset color transactionally disables wallpaper colors. Foreground/background pairs for custom accents are adjusted to at least 4.5:1 contrast in light and dark themes.
- Version 0.4.0 / code 5 keeps Room v1 and both signing identities. Work uses an isolated emulator on ADB port 15038; no user-phone operation, OEM reminder change, real model call, or performance-acceptance claim is made.

## 0.4.1 — completed rows and detail transition cost

- The user requested no time alignment for completed items and reported poor list/detail transition smoothness. Completed rows now reclaim the leading time column; pending all-day and timed rows still align. This also applies in All tasks.
- Replace the 0.3.1 full-editor `RemeasureToBounds` transition with fixed-size page placement and alpha animation. The calendar, text fields and rows no longer continuously reflow to a list-row rectangle; text is not scaled. Forward/return last 220/180ms, with a 90ms opacity handoff and a theme-colored backing surface. Reduced motion remains fade-only. This supersedes the earlier shared-container implementation in response to the user's visual/performance feedback.
- Add an isolated R8/Full-AOT list/detail benchmark and compare 0.4.0 with 0.4.1 on the same software-rendered emulator. Do not interpret these samples as hardware acceptance, infer FPS from CPU frame duration, or mark Baseline Profile collection complete.
- Existing test runner signing and seed-activity restart/order assumptions were corrected to make the previously assemble-only performance tools runnable. Production Debug/Release identities, DB schema and phone settings are untouched.

## User-requested detail motion comparison, 2026-10-28

- Deliver two interchangeable 0.4.2 builds. A retains the 0.4.0 shared-container trajectory and timing, while fixing the heavy editor at its lookahead layout size. B retains 0.4.1 entry, adds bounded predictive-back card scaling with rounded corners, then vertically retracts on committed return. Cancellation restores the existing draft.
- Both use the same application ID, versionCode 7 and existing signing identity per build type, so A/B can replace each other without clearing data. Build property `detailMotion=A|B` selects behavior; default B is an implementation default for the comparison, not a user selection of the final preferred animation. No persisted preference is changed.
- Reduced motion takes priority. Device work remains deferred; use the isolated emulator for regression and controlled comparisons, with no claim of OPPO gesture smoothness until user feedback. The known deferred boot-reminder issue is outside this change.

## User-confirmed return handoff, 2026-10-28

- Improve B as 0.4.3: bounded shrink and rounded corners, followed by detail content moving downward and fading completely out; only then fade in the actual row content and land on its current position. The user explicitly selected opacity only, without blur.
- A saved task returns to its real, fully visible row, including a newly created task. Cancelled creation, deletion, filtering or an unavailable visible target returns to the center without scrolling the list. Capture current row content rather than reconstructing it from stale editor data.
- Keep 0.4.1 entry and reduced-motion behavior. Native predictive-back progress is amplified within a bounded 10% shrink; button Back also visibly shrinks. An already shrunken gesture proceeds directly into retraction instead of pausing for the full button prelude.
- Verify System UI edge input in the isolated emulator as well as dispatcher regression coverage. Emulator frames and screenshots do not establish OPPO hardware smoothness. No user-phone operation or deferred boot-reminder fix is included.
