#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/android-env.sh"
# The launcher chooses the unavailable SOCKS endpoint over the working HTTP proxy.
# Keep HTTP(S)_PROXY intact and change only this child process's environment.
exec env -u ALL_PROXY -u all_proxy android --no-metrics --sdk="$ANDROID_HOME" "$@"
