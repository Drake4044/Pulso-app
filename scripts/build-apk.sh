#!/usr/bin/env bash
# Genera el APK de Pulso.
# 1) Copia la interfaz (app/pulso.html) al proyecto Android como assets/index.html.
# 2) Compila con Gradle. Requiere JDK 17, Android SDK (ANDROID_HOME o android/local.properties) y Gradle 8.
set -euo pipefail
cd "$(dirname "$0")/.."
cp app/pulso.html android/app/src/main/assets/index.html
cd android
gradle assembleRelease
echo "APK: android/app/build/outputs/apk/release/"
