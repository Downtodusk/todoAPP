# Windows USB device → Linux build host

The phone stays connected to Windows. Only the ADB server's loopback port is forwarded over the user's existing SSH connection. Do not expose ADB on a public interface, enable `adb -a`, or change sshd's GatewayPorts for this workflow.

## On Windows (PowerShell)

1. Download [official Platform Tools](https://developer.android.com/tools/releases/platform-tools), extract, and enter the directory with `adb.exe`.
2. Enable USB debugging on the phone, connect by USB and run:

   ```powershell
   .\adb.exe start-server
   .\adb.exe devices -l
   ```

   Accept the authorization prompt on the phone. Its state must be `device`, not `unauthorized`.
3. Start the tunnel, substituting your usual SSH destination and retaining any `-p`, `-i` or `-J` options you normally need:

   ```powershell
   ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -R 127.0.0.1:15037:127.0.0.1:5037 YOUR_LINUX_SSH_TARGET
   ```

   Keep this window open. A successful connection normally prints nothing. `Ctrl+C` closes only this tunnel. Linux host for this workspace: `njucs`; use the actual hostname/alias from your Windows SSH configuration.

## On Linux

```bash
bash scripts/adb-remote.sh devices -l
bash scripts/adb-remote.sh -s DEVICE_SERIAL shell getprop ro.product.model
bash scripts/adb-remote.sh -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
bash scripts/adb-remote.sh -s DEVICE_SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
bash scripts/adb-remote.sh -s DEVICE_SERIAL shell am instrument -w -r com.example.todo.test/androidx.test.runner.AndroidJUnitRunner
```

Always select the intended device when more than one is attached. The build runs locally with Gradle; instrumented execution is invoked through the forwarded ADB server so it does not depend on Gradle detecting a non-default server port. Do not use `kill-server` against the remote server.

Expected test count and actual results are recorded in implementation-status.md after execution. Device tests create and delete only their own uniquely named UI fixture; Room tests use temporary in-memory databases. Do not clear app data or uninstall to fix a test without considering user-created data.

## Background reminder scenarios

`DeviceScenarioFixtureTest` is opt-in and skipped during normal runs. Pass `-e todoScenarioAction prepare` (or `cleanup`), `-e todoScenarioSeconds 90`, and a short `-e todoScenarioLabel` with the class selector. This only creates a fixture; it does not validate delivery.

Preparation uses the currently saved reminder mode unless `-e todoScenarioMode exact_idle` or `alarm_clock` is specified; cleanup restores the previous mode and removes only the fixture. The explicit `configure` action plus `todoScenarioMode` changes the persisted production setting without creating a fixture. Use it only when the user has authorized that setting change. It does not replace the required normal app launch after instrumentation.

**Instrumentation termination force-stops the target and clears its alarms on the tested OPPO.** After preparation has finished, normally launch `com.example.todo/.MainActivity` before the deadline and verify the pending system alarm. Only then begin lock-screen/background observation. Do not start another instrumentation test or activity during observation, as that would clear alarms or initiate cold-start catch-up.

Read the fixture and fired flag without launching the app:

```bash
python3 scripts/read-device-reminder-state.py --serial DEVICE_SERIAL
```

The helper copies only this debug app's database/WAL into ignored `.tools/downloads/device-scenario-snapshot/` and reports the selected fixture. A `fired` flag establishes that the app's notification call succeeded; visible/audible delivery and its timing still require system or user evidence. Clean up the fixture after recording the outcome.

Troubleshooting: missing Windows device → check cable/USB mode/debug authorization; empty Linux list with working Windows list → check the SSH target and tunnel; forwarding refused → check SSH `AllowTcpForwarding` policy with the host administrator. Do not weaken host policy automatically.

References: [ADB server host/port options](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md), [OpenSSH -R](https://man.openbsd.org/ssh).
