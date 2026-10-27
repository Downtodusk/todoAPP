# Dependency decisions

Production remains one `app` module. Versions are pinned; no dynamic dependency versions.

| Dependency | Purpose |
| --- | --- |
| AGP 9.4.1, Kotlin/Compose compiler 2.4.21, Gradle 9.6.0 | Reuse the verified Android 17 toolchain described in development-environment.md. |
| Compose BOM 2026.09.00, Material 3, Animation | Native UI, accessible controls, navigation and item transitions; no bitmap or font bundle. |
| Activity Compose 1.13.0 | Single activity, edge-to-edge, platform back integration. |
| Lifecycle runtime/viewmodel Compose 2.10.0 | StateFlow collection tied to the visible lifecycle and retained ViewModels. |
| Navigation Compose 2.9.5 | Screen back stack and predictive back infrastructure. |
| Room 2.8.5 + KSP 2.3.10 | Transactional local storage and compile-time DAO generation. KSP 2.3.10 includes Kotlin 2.4 module-name fixes. |
| DataStore Preferences 1.2.0 | Persist user settings with transactional updates. |
| JUnit 4, coroutines-test, AndroidX Test, Room testing, Compose UI Test | Test-only dependencies for calendar logic, database integrity and UI behavior. |

Release references: [Room](https://developer.android.com/jetpack/androidx/releases/room), [KSP](https://github.com/google/ksp/releases), [Navigation](https://developer.android.com/jetpack/androidx/releases/navigation). No Hilt, Retrofit, Gson, Moshi, extended icon bundle, Firebase or GMS was added.
