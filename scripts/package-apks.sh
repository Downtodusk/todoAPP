#!/usr/bin/env bash
# Local build and packaging only; never installs on or contacts a device.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh
./gradlew :app:assembleDebug :app:assembleRelease --console=plain "$@"
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
import hashlib, json, subprocess, sys, zipfile
from pathlib import Path
version = sys.argv[1]
profiles = {}
for name in ['baseline-prof.txt', 'startup-prof.txt']:
    path = Path('app/src/main', name)
    content = path.read_bytes()
    if b'Lcom/example/todo/' not in content or b'Lcom/example/todo/benchmark/' in content:
        raise SystemExit('Missing app profile rules or benchmark fixture leaked into ' + name)
    profiles[name] = hashlib.sha256(content).hexdigest()
with zipfile.ZipFile('app/build/outputs/apk/release/app-release.apk') as apk:
    for name, magic in [('baseline.prof', b'pro\x00'), ('baseline.profm', b'prm\x00')]:
        content = apk.read('assets/dexopt/' + name)
        if not content.startswith(magic) or len(content) <= 8:
            raise SystemExit('Invalid packaged ART profile: ' + name)
files = [Path(f'dist/todo-{version}-{variant}.apk') for variant in ['debug', 'release']]
checksums = []
for path in files:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    checksums.append(f'{digest}  {path.name}')
    print(f'{path}: {path.stat().st_size:,} bytes ({path.stat().st_size / 1048576:.2f} MiB)')
Path('dist/SHA256SUMS').write_text('\n'.join(checksums) + '\n')
Path(f'dist/SHA256SUMS-{version}').write_text('\n'.join(checksums) + '\n')
build_info = json.dumps({
    'version': version,
    'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'workingTreeModified': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()),
    'deviceAcceptanceComplete': False,
    'profileSourceSha256': profiles,
}, indent=2) + '\n'
Path('dist/build-info.json').write_text(build_info)
Path(f'dist/build-info-{version}.json').write_text(build_info)
PY
