#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/android-env.sh"
java -version
gradle --version
env -u ALL_PROXY -u all_proxy android --no-metrics --version
env -u ALL_PROXY -u all_proxy android --no-metrics --sdk="$ANDROID_HOME" sdk list
adb version
test -f "$ANDROID_HOME/platforms/android-37.0/android.jar"
"$ANDROID_HOME/build-tools/37.0.0/aapt2" version
"$ANDROID_HOME/build-tools/37.0.0/apksigner" version
adb devices -l
