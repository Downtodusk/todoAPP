#!/usr/bin/env python3
"""Read the opt-in fixture without starting Todo (which would perform catch-up).

Only this debug application's database is copied into ignored local test artifacts.
No notification contents or databases belonging to other applications are read.
"""
import argparse
import io
import json
from pathlib import Path
import sqlite3
import subprocess
import tarfile

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
adb = ["bash", str(root / "scripts/adb-remote.sh"), "-s", args.serial]

def run(*command, check=True):
    return subprocess.run(adb + list(command), capture_output=True, check=check, timeout=20)

fixture = json.loads(run("exec-out", "run-as", "com.example.todo", "cat", "cache/reminder-device-scenario.json").stdout)
archive = run("exec-out", "run-as", "com.example.todo", "tar", "-cf", "-", "-C", "databases", "todo.db", "todo.db-wal", check=False)
folder = root / ".tools/downloads/device-scenario-snapshot"
folder.mkdir(parents=True, exist_ok=True)
for suffix in ["", "-wal", "-shm"]:
    (folder / ("todo.db" + suffix)).unlink(missing_ok=True)
with tarfile.open(fileobj=io.BytesIO(archive.stdout), mode="r:") as tar:
    for name in ["todo.db", "todo.db-wal"]:
        try:
            member = tar.getmember(name)
        except KeyError:
            if name == "todo.db":
                raise RuntimeError("No app database in snapshot")
            continue
        if not member.isfile():
            raise RuntimeError("Expected a regular database file")
        (folder / name).write_bytes(tar.extractfile(member).read())
with sqlite3.connect(f"file:{folder / 'todo.db'}?mode=ro", uri=True) as db:
    row = db.execute("SELECT triggerAt, fired FROM reminder WHERE id = ? AND todoId = ?",
                     (fixture["reminderId"], fixture["todoId"])).fetchone()
result = {
    "fixture": fixture,
    "deviceEpochSeconds": int(run("shell", "date", "+%s").stdout),
    "appPid": run("shell", "pidof", "com.example.todo", check=False).stdout.decode().strip() or None,
    "reminder": None if row is None else {"triggerAt": row[0], "fired": bool(row[1])},
    "method": "Read-only DB/WAL snapshot; no application launch or catch-up initiated",
}
print(json.dumps(result, ensure_ascii=False, indent=2))
