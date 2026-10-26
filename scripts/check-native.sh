#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=/workspace/toolchains/jdk-17.0.21+9
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
export PATH="$JAVA_HOME/bin:$PATH"
python3 native/build.py
python3 native/audit.py
/workspace/toolchains/gradle-8.11.1/bin/gradle --no-daemon --max-workers=2 :app:testDebugUnitTest --tests app.maxund.PrivacyLifecycleTest --tests app.maxund.privacy.UndetectTest --tests UndetectBridgeTest
