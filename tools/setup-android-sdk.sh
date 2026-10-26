#!/usr/bin/env bash
# Installs a headless Android build toolchain (JDK 21, Gradle, Android SDK)
# under ~/tools and ~/android-sdk. Safe to re-run: skips what is already there.
#
#   bash tools/setup-android-sdk.sh
#
# Afterwards:
#   echo "sdk.dir=$HOME/android-sdk" > local.properties
#   JAVA_HOME=$HOME/tools/jdk-21 PATH=$HOME/tools/gradle/bin:$PATH ./gradlew assembleDebug
set -euo pipefail

TOOLS="$HOME/tools"
SDK="$HOME/android-sdk"
CMDTOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"

mkdir -p "$TOOLS" "$SDK"

if ! ls -d "$TOOLS"/jdk-21* >/dev/null 2>&1; then
  echo "==> JDK 21 (Temurin)"
  curl -fL -o "$TOOLS/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf "$TOOLS/jdk.tar.gz" -C "$TOOLS"
  rm -f "$TOOLS/jdk.tar.gz"
fi
JDK_DIR="$(ls -d "$TOOLS"/jdk-21* | head -1)"
echo "JDK: $JDK_DIR"

if [ ! -x "$TOOLS/gradle-8.14.3/bin/gradle" ]; then
  echo "==> Gradle 8.14.3"
  curl -fL -o "$TOOLS/gradle.zip" "https://services.gradle.org/distributions/gradle-8.14.3-bin.zip"
  unzip -q -o "$TOOLS/gradle.zip" -d "$TOOLS"
  rm -f "$TOOLS/gradle.zip"
fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Android command-line tools"
  curl -fL -o "$TOOLS/cmdtools.zip" "https://dl.google.com/android/repository/$CMDTOOLS_ZIP"
  rm -rf "$TOOLS/cmdtools-tmp"
  unzip -q -o "$TOOLS/cmdtools.zip" -d "$TOOLS/cmdtools-tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$TOOLS/cmdtools-tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TOOLS/cmdtools-tmp" "$TOOLS/cmdtools.zip"
fi

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JAVA_HOME/bin:$TOOLS/gradle-8.14.3/bin:$SDK/cmdline-tools/latest/bin:$PATH"

echo "==> SDK licences"
# `yes |` déclenche SIGPIPE avec pipefail : on tolère explicitement la sortie.
yes | sdkmanager --sdk_root="$SDK" --licenses >/dev/null || true

echo "==> SDK packages"
sdkmanager --sdk_root="$SDK" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "==> done"
java -version 2>&1 | head -2
gradle --version | head -4
