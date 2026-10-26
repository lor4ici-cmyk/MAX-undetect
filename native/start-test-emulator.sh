#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_AVD_HOME=/workspace/toolchains/avd
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export ANDROID_EMULATOR_HOME=/workspace/toolchains/android-user
export XDG_CACHE_HOME=/workspace/toolchains/xdg-cache
export XDG_CONFIG_HOME=/workspace/toolchains/xdg-config
# The retained MAXund AVD is a disposable account-free Android 13 fixture.
# No reset is performed by this script. Do not put real accounts in this fixture.
exec "$ANDROID_HOME/emulator/emulator" -avd MAXund -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics -skip-adb-auth -accel off -gpu swiftshader_indirect -memory 2048 -cores 2 -ports 5580,5581
