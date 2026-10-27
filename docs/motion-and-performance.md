# M3 motion and performance verification

The user deferred further phone operations on 2026-10-27 and will download APKs for later feedback. Building a test APK is not execution. No M3 device, profile-generation or frame-rate PASS is claimed yet.

## Implemented behavior

- Left swipe completes an open todo; right swipe deletes. Threshold crossing produces haptic feedback; color/icon feedback follows displacement. Both actions use the existing revision-safe repository and five-second undo.
- Checkbox completion draws the check (200 ms), adds the title strike (150 ms), then fades/collapses (150 ms). Database mutation follows feedback; cancellation of feedback must not discard the accepted action. Stable list IDs retain item placement animation.
- Rows use ripple and 0.97 press scale. FAB fades while the quick-add sheet is open; successful save hides the sheet before removing it.
- List/detail containers and title bounds share a transition. Detail snapshots are fetched before starting navigation; separate todo IDs retain separate navigation entries. System predictive back uses Navigation Compose and a seekable scale/fade transition.
- Settings' reduced-motion choice takes priority; automatic mode checks `isLowRamDevice`. Reduced mode disables shared bounds/press scale/collapse and uses fades. Loading placeholders are static in reduced mode; the same shimmer component is available for AI import.

## Device acceptance, all pending

1. Add, cancel and save from the sheet with keyboard visible; verify no jump and no covered controls.
2. Swipe partially and release: no mutation; swipe past threshold: one haptic/action; undo completion/deletion, including a recurring task and a task with reminders.
3. Complete several tasks; check animation order and item placement, repeat with reduced motion on.
4. Enter/return from list and All Todos detail, including long titles and large system text. Test predictive-back completion and cancellation; reopen two different notification details.
5. Repeat light/dark/dynamic colors and narrow layouts. Confirm accessibility checkbox actions and minimum hit targets.
6. Generate a profile, rebuild, then measure cold start and frames. Keep raw results and device settings; evaluate the specified frame budget with actual frame samples, not inferred compilation success.

`TodoFlowTest.swipeCompleteAndDeleteCanBothBeUndone` adds automated gesture/undo coverage. It has not been run on the phone after M3 changes.

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

The explicit script builds/installs only performance variants, runs the selected class, retains logs/traces under ignored `.tools/performance/`, and merges measured baseline/startup files into `app/src/main/` for AGP packaging. It is never invoked by ordinary builds. Profiles are absent until collection actually succeeds; no hand-written rules stand in for a measured profile. Because these tests use the custom forwarded ADB server, collection is explicit rather than automatically attached to a Gradle release build. Commit measured profiles after reviewing them. Record the APK revision and device build alongside every benchmark run.

Sources: [shared transitions and predictive back](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation), [profile generation](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
