#!/usr/bin/env bash
# Local build and packaging only; never installs on or contacts a device.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh
./gradlew :app:assembleDebug :app:assembleRelease --console=plain
mkdir -p dist
version="$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path('app/build/outputs/apk/release/output-metadata.json').read_text())['elements'][0]['versionName'])
PY
)"
for variant in debug release; do
  input="app/build/outputs/apk/$variant/app-$variant.apk"
  output="dist/todo-$version-$variant.apk"
  "$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --verbose --print-certs "$input" > "dist/$variant-signature.txt"
  cp "$input" "$output"
done
mkdir -p "dist/mapping-$version"
cp app/build/outputs/mapping/release/mapping.txt "dist/mapping-$version/mapping.txt"
python3 - "$version" <<'PY'
import hashlib, json, subprocess, sys
from pathlib import Path
version = sys.argv[1]
files = [Path(f'dist/todo-{version}-{variant}.apk') for variant in ['debug', 'release']]
checksums = []
for path in files:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    checksums.append(f'{digest}  {path.name}')
    print(f'{path}: {path.stat().st_size:,} bytes ({path.stat().st_size / 1048576:.2f} MiB)')
Path('dist/SHA256SUMS').write_text('\n'.join(checksums) + '\n')
Path('dist/build-info.json').write_text(json.dumps({
    'version': version,
    'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'workingTreeModified': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
    'deviceAcceptanceComplete': False,
}, indent=2) + '\n')
PY
