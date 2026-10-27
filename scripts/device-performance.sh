#!/usr/bin/env bash
# Explicit device work only: never called by assemble/test tasks.
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:?Usage: device-performance.sh collect|benchmark DEVICE_SERIAL}"
serial="${2:?Pass the intended device serial}"
source scripts/android-env.sh
adb=(bash scripts/adb-remote.sh -s "$serial")
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
"${adb[@]}" shell mkdir -p "$remote_dir"
"${adb[@]}" shell am instrument -w -r -e class "$test_class" -e additionalTestOutputDir "$remote_dir" \
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
for suffix, target in [('baseline-prof.txt', 'baseline-prof.txt'), ('startup-prof.txt', 'startup-prof.txt')]:
    lines = set()
    for path in root.rglob('*-' + suffix):
        lines.update(line.strip() for line in path.read_text().splitlines()
                     if line.strip() and not line.lstrip().startswith('#') and 'Lcom/example/todo/benchmark/' not in line)
    if not any('Lcom/example/todo/' in line for line in lines):
        raise SystemExit('Missing measured app rules for ' + suffix + '; nothing fabricated.')
    Path('app/src/main', target).write_text('\n'.join(sorted(lines)) + '\n')
print('Measured profiles copied to app/src/main. Rebuild before benchmarking.')
PY
fi
echo "Evidence: $local_dir"
