# Reminder compatibility decision — optional system alarm mode adopted

## Observed problem

On the user's OPPO PLJ110 / Android 17, the specified `setExactAndAllowWhileIdle` path has permission and successfully registers an alarm. After configuring Android battery exemption, OEM background allowance and autostart, the receiver can start from a dead process while the phone is locked, and the notification is visible/audible. However, a 60-second test was delivered 44.222 seconds late. The system's alarm dump shows a nonzero dispatch window despite the app using the exact-alarm API. This does not satisfy punctuality acceptance.

Evidence and unsuccessful tests remain in `reminder-test-matrix.md`; ordinary mode retains this limitation on the tested device.

## Comparison and approved implementation

The user authorized an isolated comparison with Android `AlarmManager.setAlarmClock`. With the phone locked and the app process terminated normally, the alarm requested at `20:37:53.605` was sent at `20:37:53.606` (+1 ms), and the notification record was created at `20:37:54.007` (+402 ms). The user confirmed visible/audible receipt. This is one device observation, not a general timing guarantee.

The user then explicitly approved adding “系统闹钟提醒” as an optional production mode and enabling it on this phone. New installations default to ordinary reminders. Settings → Reminder self-check exposes both modes and explains the upcoming-alarm indicator and possible extra resource use. DataStore persists the choice; changing it reschedules pending reminders. Cold start and boot use the same setting.

The approved change is reflected in specification §2/§5.5/§6.2 and `decisions.md`. Both paths share reminder identity, delivery, actions and recovery. No permanent foreground service, push SDK or backend was added. The temporary debug-only experiment factory was removed.

The final production implementation passed 19 instrumented tests on this phone, including mode selection/persistence, real alarm/notification actions and two-notification detail routing. Host verification passed 24 JVM tests, Lint, debug/test APK builds and the unsigned R8 release build. Recents removal also passed a separate locked-device observation. Reboot recovery failed because the OEM deferred the boot broadcast; its fix is deferred by the user. Doze, battery saver and timezone device tests were skipped by user choice. These gaps still prevent full M2 acceptance.

References: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [AlarmManager API](https://developer.android.com/reference/android/app/AlarmManager).
