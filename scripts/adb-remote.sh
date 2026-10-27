#!/usr/bin/env bash
# Connect to a Windows USB device through an SSH reverse forward. Never starts a
# new adb server on the forwarded port or changes the normal Linux adb server.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/android-env.sh"
TODO_ADB_PORT="${TODO_ADB_PORT:-15037}"
if ! [[ "$TODO_ADB_PORT" =~ ^[0-9]+$ ]] || (( TODO_ADB_PORT < 1024 || TODO_ADB_PORT > 65535 )); then
    echo "TODO_ADB_PORT must be a port between 1024 and 65535" >&2
    exit 2
fi
if ! python3 - "$TODO_ADB_PORT" <<'PY'
import socket
import sys

def exact(sock, size):
    result = b''
    while len(result) < size:
        chunk = sock.recv(size - len(result))
        if not chunk:
            raise OSError('ADB server closed the connection')
        result += chunk
    return result

try:
    with socket.create_connection(('127.0.0.1', int(sys.argv[1])), timeout=3) as sock:
        request = b'host:version'
        sock.sendall(f'{len(request):04x}'.encode() + request)
        if exact(sock, 4) != b'OKAY':
            raise OSError('ADB server rejected the version probe')
        size = int(exact(sock, 4), 16)
        if size > 16 or int(exact(sock, size), 16) != 41:
            raise OSError('Use compatible Platform Tools with ADB protocol 1.0.41')
except (OSError, ValueError) as error:
    print(f'ADB tunnel unavailable: {error}', file=sys.stderr)
    sys.exit(1)
PY
then
    echo "Check the SSH tunnel on 127.0.0.1:$TODO_ADB_PORT. See docs/remote-device-testing.md." >&2
    exit 1
fi
exec adb -H 127.0.0.1 -P "$TODO_ADB_PORT" "$@"
