#!/usr/bin/env bash
# Source this file: source scripts/android-env.sh
TODO_PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$TODO_PROJECT_ROOT/.tools/jdk"
export ANDROID_HOME="$TODO_PROJECT_ROOT/.tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$TODO_PROJECT_ROOT/.tools/android-user"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
export GRADLE_USER_HOME="$TODO_PROJECT_ROOT/.tools/gradle-user"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$TODO_PROJECT_ROOT/.tools/gradle-9.6.0/bin:$PATH"
unset TODO_PROJECT_ROOT
