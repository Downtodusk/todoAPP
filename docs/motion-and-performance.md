# M3 motion and performance verification

The user deferred further phone operations on 2026-10-27 and will download APKs for later feedback. Building a test APK is not execution. The user accepted 0.4.3 motion and requested a modest speed increase in 0.4.4. App Profile generation now has isolated-emulator execution evidence; hardware frame-rate acceptance remains pending.

## Implemented behavior

- Left swipe reveals Complete/Reopen, right swipe reveals Delete. Releasing the gesture never mutates data; the user taps the exposed action to execute it. Tapping the row closes the tray. Threshold crossing retains haptic feedback; completion/deletion retain revision protection and undo.
- Checkbox completion draws the check (200 ms), adds the title strike (150 ms), then fades/collapses (150 ms). Database mutation follows feedback; cancellation of feedback must not discard the accepted action. Stable list IDs retain item placement animation.
- Rows use ripple and 0.97 press scale. FAB fades while the quick-add sheet is open; successful save hides the sheet before removing it.
- Version 0.4.4 modestly accelerates the user-accepted 0.4.3 B motion: bounded rounded-card shrink, detail content moving downward and fading fully out, then actual row content fading in and landing on its current visible position. Missing targets and cancelled creation retract to the center. Fixed image layers avoid per-frame editor remeasurement; reduced motion remains fade-only. See [implementation and verification](detail-motion-0.4.3.md). The [0.4.2 A/B comparison](detail-motion-0.4.2.md) remains historical evidence; hardware predictive-back acceptance is pending.
- Settings' reduced-motion choice takes priority; automatic mode checks `isLowRamDevice`. Reduced mode disables page translation/press scale/collapse and uses fades. Loading placeholders are static in reduced mode; the same shimmer component is available for AI import.

## Device acceptance, all pending

1. Add, cancel and save from the sheet with keyboard visible; verify no jump and no covered controls.
2. Swipe partially and release: no mutation; swipe past threshold: haptic feedback and a menu, no data change until its button is tapped; undo completion/deletion, including a recurring task and a task with reminders.
3. Complete several tasks; check animation order and item placement, repeat with reduced motion on.
4. Enter/return from list and All Todos detail, including long titles and large system text. Test predictive-back completion and cancellation; reopen two different notification details.
5. Repeat light/dark/dynamic colors and narrow layouts. Confirm accessibility checkbox actions and minimum hit targets.
6. Generate a profile, rebuild, then measure cold start and frames. Keep raw results and device settings; evaluate the specified frame budget with actual frame samples, not inferred compilation success.

`TodoFlowTest.swipeRevealsActionsWithoutMutatingUntilConfirmation` adds automated gesture/undo coverage. It has not been run on the phone after M3 changes.

## Isolated performance tooling

`:baselineprofile` is a `com.android.test` module. Its target APK uses application ID `com.example.todo.benchmark`, so fixture creation and benchmark restarts do not operate on the user's normal `com.example.todo` database. A seed Activity exists only in performance build variants and creates 40 synthetic rows. Both performance APKs use the development signing key; they are not distribution releases.

- `profile` target: non-debuggable, unminified, profileable; produces unobfuscated rules suitable for release R8 processing.
- `benchmark` target: non-debuggable, R8/shrunk, profileable; measures the optimized code with an installed profile.
- Startup collection is separate from interaction collection. The shared journey covers startup, sheet, list scrolling and detail.
- `CompilationMode.Partial(...Require)` intentionally fails if profile installation is missing. No fallback result is presented as profile-enabled performance.

With the phone available and device work authorized, use the existing SSH ADB tunnel:

```bash
bash scripts/device-performance.sh collect DEVICE_SERIAL
bash scripts/device-performance.sh benchmark DEVICE_SERIAL
```

The explicit script builds/installs only performance variants, runs the selected class, retains logs/traces under ignored `.tools/performance/`, and merges measured baseline/startup files into `app/src/main/` for AGP packaging. It is never invoked by ordinary builds. Version 0.4.4 includes measured app baseline/startup profiles; no hand-written rules stand in for collected paths. Because these tests use the custom forwarded ADB server, collection is explicit rather than automatically attached to a Gradle release build. Commit measured profiles after reviewing them. Record the APK revision and device build alongside every benchmark run.

Sources: [shared transitions and predictive back](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation), [profile generation](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

## M5 measurement record

The benchmark script also records the isolated target's foreground idle PSS/RSS with `dumpsys meminfo`: launch, wait 30 seconds, then take three samples 10 seconds apart. It stores model, OS build and source revision next to the samples. Version 0.4.4 runs this workflow on the isolated emulator; hardware measurements remain a separate explicit device run.

| Measure | Target / definition | Current evidence |
| --- | --- | --- |
| Cold start | Spec target <500 ms; report startup median and p95 with device/compilation mode | 0.4.4 emulator: TTID median 826.66ms / P95 978.17ms; hardware pending |
| Interaction | Spec dropped-frame fraction ≤10%; inspect actual frame deadlines/traces, not just average duration | 0.4.4 emulator: 5 full-journey iterations recorded; scrolling-only hardware acceptance pending |
| Idle memory | Foreground idle PSS/RSS, three samples after 30 s settling | 0.4.4 emulator median PSS 57.84MiB / RSS 183.54MiB; hardware pending |
| Release APK | Record size without a hard acceptance threshold | See release artifact record in `implementation-status.md` |
| App baseline/startup profile | Collect actual executed paths and rebuild | Collected on API 36 emulator in 0.4.4; see the record below |

Host compilation and the APK's library-contributed profiles do not establish the app's baseline profile or performance acceptance.

## 0.4.1 focused emulator comparison

`DetailTransitionBenchmark` measures only repeated list/detail navigation with `CompilationMode.Full()`. It is separate from the profile-required full journey above. Both runner variants now explicitly use the Debug signing configuration; their previous assemble-only checks did not prove installability. Fixture preparation restarts only `.benchmark` and selects a visible synthetic row without assuming insertion order.

The API 36 SwiftShader emulator A/B comparison, actual frame metrics, excluded setup failures and rendering samples are recorded in [ui-fixes-0.4.1.md](ui-fixes-0.4.1.md). This is engineering evidence for this transition change, not phone frame-rate acceptance or a generated Baseline Profile.


## 0.4.4 app profiles and continued performance work

Startup and interaction collections both passed on the isolated API 36 emulator. The committed baseline contains 24,666 rules (1,583 app rules); startup contains 19,335 (1,026 app rules). Dependencies' executed code is retained; synthetic benchmark fixture rules are excluded. Sources are in `app/src/main/baseline-prof.txt` and `startup-prof.txt`.

Reproduce without using the phone tunnel:

```bash
bash scripts/device-performance.sh collect 127.0.0.1:5581 --emulator
bash scripts/device-performance.sh benchmark 127.0.0.1:5581 --emulator
```

The explicit emulator option checks `ro.kernel.qemu` on ADB 15038 and never falls back to a phone. [The 0.4.4 record](performance-0.4.4.md) contains timing changes, provenance, packaging and measurement evidence. Profile sources contain actual executed paths, not a claim of measured speedup. Older observations above remain historical; all hardware startup/frame/memory acceptance remains pending.
