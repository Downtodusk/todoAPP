#!/usr/bin/env python3
"""Create a local release identity once. Never print passwords or overwrite an existing identity."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
properties = root / "keystore.properties"
folder = root / "signing"
key = folder / "todo-release.jks"
if properties.exists() or key.exists():
    raise SystemExit("Existing signing identity/configuration found; refusing to overwrite. Back it up and reuse it.")
keytool = shutil.which("keytool")
if not keytool:
    raise SystemExit("Load scripts/android-env.sh first so keytool is available.")
folder.mkdir(mode=0o700, exist_ok=True)
os.chmod(folder, 0o700)
password = secrets.token_hex(32)
with tempfile.TemporaryDirectory(prefix="todo-signing-") as temporary:
    password_file = Path(temporary) / "password"
    password_file.write_text(password)
    os.chmod(password_file, 0o600)
    subprocess.run([keytool, "-genkeypair", "-keystore", str(key), "-storetype", "JKS",
        "-alias", "todo-release", "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA",
        "-validity", "10000", "-dname", "CN=Todo Personal Release", "-noprompt",
        "-storepass:file", str(password_file), "-keypass:file", str(password_file)], check=True,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
os.chmod(key, 0o600)
# Atomic exclusive creation: a concurrently created config is not replaced.
fd = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as output:
    output.write("storeFile=signing/todo-release.jks\nkeyAlias=todo-release\n")
    output.write(f"storePassword={password}\nkeyPassword={password}\n")
print("Release identity created in ignored signing/ with private keystore.properties.")
print("Back up BOTH in a secure location; all future release updates must reuse this identity.")
