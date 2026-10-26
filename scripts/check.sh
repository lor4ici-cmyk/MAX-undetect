#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/workspace/toolchains/jdk-17.0.21+9
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
export PATH="$JAVA_HOME/bin:$PATH"
/workspace/toolchains/gradle-8.11.1/bin/gradle --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
apk=app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$apk"
permissions=$("$ANDROID_HOME/build-tools/35.0.0/aapt2" dump permissions "$apk")
if [[ $(printf '%s\n' "$permissions" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p") != android.permission.INTERNET ]]; then
    printf '%s\n' "$permissions" >&2
    exit 1
fi
printf 'APK signature verified; only INTERNET permission declared.\n'
sha256sum "$apk"
