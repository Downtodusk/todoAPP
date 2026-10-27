# Dependency decisions

Production remains one `app` module. Versions are pinned; no dynamic dependency versions.

| Dependency | Purpose |
| --- | --- |
| AGP 9.4.1, Kotlin/Compose compiler 2.4.21, Gradle 9.6.0 | Reuse the verified Android 17 toolchain described in development-environment.md. |
| Compose BOM 2026.09.00, Material 3, Animation | Native UI, accessible controls, navigation and item transitions; no bitmap UI bundle. |
| Activity Compose 1.13.0 | Single activity, edge-to-edge, platform back integration. |
| Lifecycle runtime/viewmodel Compose 2.10.0 | StateFlow collection tied to the visible lifecycle and retained ViewModels. |
| Navigation Compose 2.9.5 | Screen back stack and predictive back infrastructure. |
| Room 2.8.5 + KSP 2.3.10 | Transactional local storage and compile-time DAO generation. KSP 2.3.10 includes Kotlin 2.4 module-name fixes. |
| DataStore Preferences 1.2.0 | Persist user settings with transactional updates. |
| JUnit 4, coroutines-test, AndroidX Test, Room testing, Compose UI Test | Test-only dependencies for calendar logic, database integrity and UI behavior. |
| Espresso 3.7.0 (explicit test-only pin) | Compose transitively selected 3.5.0, which crashes before UI execution on Android 17 because InputManager.getInstance was removed. 3.7.0 uses getSystemService; verified against the official AndroidX Test release notes. |
| Benchmark Macro JUnit4 1.5.0 and UiAutomator 2.4.0 | Isolated performance test module only; baseline collection and release-like startup/frame measurement. Not packaged into the normal app. |

Release references: [Room](https://developer.android.com/jetpack/androidx/releases/room), [KSP](https://github.com/google/ksp/releases), [Navigation](https://developer.android.com/jetpack/androidx/releases/navigation). No Hilt, Retrofit, Gson, Moshi, extended icon bundle, Firebase or GMS was added.

M4 adds OkHttp **5.5.1** for cancellable direct HTTPS calls and MockWebServer **5.5.1** for local-only HTTP tests. It adds Kotlin serialization compiler plugin **2.4.21** and JSON runtime **1.11.0** for typed responses without reflection. Key encryption uses platform Android Keystore, with no additional security SDK. References: [OkHttp changelog](https://github.com/lysine-dev/okhttp/blob/main/CHANGELOG.md), [Kotlin serialization](https://kotlinlang.org/docs/serialization-get-started.html).

## 0.3.0 offline fonts

The user approved open alternatives to Claude's typography, replacing the earlier system-font-only constraint. Unmodified regular fonts from Adobe's [Source Serif](https://github.com/adobe-fonts/source-serif), [Source Sans](https://github.com/adobe-fonts/source-sans), and [Source Han Sans](https://github.com/adobe-fonts/source-han-sans) are embedded in `res/font`. All three SIL OFL 1.1 license notices ship in `assets/licenses`. Pinned source revisions, download URLs, sizes and SHA-256 hashes are recorded in [font-sources.json](../design/font-sources.json). No runtime font download or new production library is required. Full Chinese coverage increases APK size; no font files were subsetted or renamed internally.

API 29+ uses the public Android custom font fallback API to combine Latin and Chinese; API 26–28 uses the bundled Source Han Sans face and its own Latin glyphs. Those older versions do not reproduce the serif Latin headings and have not received runtime verification in this round.
