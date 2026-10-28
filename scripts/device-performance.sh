#!/usr/bin/env bash
# Explicit device work only: never called by assemble/test tasks.
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:?Usage: device-performance.sh collect|benchmark DEVICE_SERIAL [--emulator]}"
serial="${2:?Pass the intended device serial}"
source scripts/android-env.sh
adb=(bash scripts/adb-remote.sh -s "$serial")
instrument_args=()
environment=hardware
if [[ "${3:-}" == --emulator ]]; then
  # Separate server; never discover or fall back to the user's forwarded phone.
  adb=(adb -H 127.0.0.1 -P 15038 -s "$serial")
  [[ "$("${adb[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
    echo 'Expected an explicitly selected emulator on isolated ADB port 15038.' >&2; exit 2;
  }
  instrument_args=(-e androidx.benchmark.suppressErrors EMULATOR)
  environment=emulator
elif [[ -n "${3:-}" ]]; then
  echo 'Unknown transport option; use --emulator or omit for the authorized phone.' >&2; exit 2
fi
case "$mode" in
  collect) variant=profile; task=Profile; test_class=com.example.todo.baselineprofile.BaselineProfileGenerator ;;
  benchmark)
    test -s app/src/main/baseline-prof.txt || { echo 'Generate a baseline profile first.' >&2; exit 1; }
    variant=benchmark; task=Benchmark; test_class=com.example.todo.baselineprofile.TodoMacrobenchmark ;;
  *) echo 'Expected collect or benchmark.' >&2; exit 2 ;;
esac
./gradlew ":app:assemble$task" ":baselineprofile:assemble$task" --console=plain
"${adb[@]}" install -r "app/build/outputs/apk/$variant/app-$variant.apk"
"${adb[@]}" install -r -t "baselineprofile/build/outputs/apk/$variant/baselineprofile-$variant.apk"
result_name="$(date -u +%Y%m%dT%H%M%SZ)-$mode"
local_dir=".tools/performance/$result_name"
remote_dir="/sdcard/Android/media/com.example.todo.baselineprofile/$result_name"
mkdir -p "$local_dir"
git rev-parse HEAD > "$local_dir/source-commit.txt"
git status --porcelain > "$local_dir/source-working-tree.txt"
printf '%s\n' "$environment" > "$local_dir/environment.txt"
sha256sum "app/build/outputs/apk/$variant/app-$variant.apk" > "$local_dir/measured-apk.sha256"
"${adb[@]}" shell getprop ro.product.model > "$local_dir/device-model.txt"
"${adb[@]}" shell getprop ro.build.fingerprint > "$local_dir/device-build.txt"
"${adb[@]}" shell mkdir -p "$remote_dir"
"${adb[@]}" shell am instrument -w -r "${instrument_args[@]}" -e class "$test_class" -e additionalTestOutputDir "$remote_dir" \
  com.example.todo.baselineprofile/androidx.test.runner.AndroidJUnitRunner | tee "$local_dir/instrumentation.log"
python3 - "$local_dir/instrumentation.log" <<'PY'
from pathlib import Path
import re, sys
text = Path(sys.argv[1]).read_text()
if not re.search(r'OK \(\d+ tests?\)', text) or 'FAILURES!!!' in text:
    raise SystemExit('Instrumentation did not report success; retain its log and investigate.')
PY
"${adb[@]}" pull "$remote_dir" "$local_dir/outputs"
if [[ "$mode" == collect ]]; then
  python3 - "$local_dir" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
profiles = {}
for suffix, target in [('baseline-prof.txt', 'baseline-prof.txt'), ('startup-prof.txt', 'startup-prof.txt')]:
    lines = set()
    for path in root.rglob('*-' + suffix):
        lines.update(line.strip() for line in path.read_text().splitlines()
                     if line.strip() and not line.lstrip().startswith('#') and 'Lcom/example/todo/benchmark/' not in line)
    if not any('Lcom/example/todo/' in line for line in lines):
        raise SystemExit('Missing measured app rules for ' + suffix + '; nothing fabricated.')
    profiles[target] = '\n'.join(sorted(lines)) + '\n'
for target, content in profiles.items():
    Path('app/src/main', target).write_text(content)
print('Measured profiles copied to app/src/main. Rebuild before benchmarking.')
PY
fi
if [[ "$mode" == benchmark ]]; then
  # Foreground idle memory of the isolated fixture app; never the user's app/data.
  "${adb[@]}" shell getprop ro.product.model > "$local_dir/device-model.txt"
  "${adb[@]}" shell getprop ro.build.fingerprint > "$local_dir/device-build.txt"
  git rev-parse HEAD > "$local_dir/source-commit.txt"
  "${adb[@]}" shell am start -W -n com.example.todo.benchmark/com.example.todo.MainActivity > "$local_dir/idle-launch.txt"
  sleep 30
  for sample in 1 2 3; do
    "${adb[@]}" shell dumpsys meminfo com.example.todo.benchmark > "$local_dir/memory-$sample.txt"
    if [[ "$sample" != 3 ]]; then sleep 10; fi
  done
fi
echo "Evidence: $local_dir"
