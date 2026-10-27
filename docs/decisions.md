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
