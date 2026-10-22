# Tip Calculator (Android)

A tiny Android app: enter a bill, pick a tip percentage and how many people are splitting it.

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow, which builds the app and publishes
`TipCalculator.apk` on the repository's **Releases** page (and as a workflow artifact).
Download it on your phone and open it to install (allow "install unknown apps" when asked).

## Building locally

Requires the Android SDK (set `ANDROID_HOME`) and JDK 17+:

```sh
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```
