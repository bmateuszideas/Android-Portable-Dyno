# Android Road Dyno v0.1

Android GNSS logger for testing the real speed sample stream on a Samsung Galaxy S25. It uses `LocationManager.GPS_PROVIDER`, `Location.speed`, and `Location.elapsedRealtimeNanos`.

## Build

Open this directory in Android Studio with JDK 17, Android SDK Platform 36, and Gradle 8.13. Sync the project, then choose **Build → Build APK(s)**. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The included GitHub Actions workflow can also build the APK: place the project in a GitHub repository, run **Android build** from the Actions tab, and download the `AndroidRoadDyno-debug` artifact. No Gradle wrapper is bundled; Android Studio must use installed Gradle 8.13, or the workflow will supply it.

## Use

1. Install the APK on Android 12 or later and grant **precise location**. On Android 13+, allow notifications to see the recording notification.
2. Enable device Location Services and open the app outside with a clear view of the sky.
3. Tap **START**. The service displays speed, reported speed accuracy, real sampling rate, timestamp statistics, and satellite count.
4. The recording continues after the screen is turned off. Stop it from the app or the persistent notification.
5. Open **SESSIONS** to inspect a report, export the raw CSV, or replay the exact stored sequence at real time or maximum speed.

The CSV contains received order (`sample_index`), GNSS fix time (`timestamp_ns`), callback arrival time, provider, raw speed, optional accuracy and coordinates, and anomaly flags. Anomalies are retained. Room commits batches of up to 50 samples, or flushes after 250 ms. An unexpected process death can lose the most recent in-memory batch; saved batches remain available with session status `INTERRUPTED` after the app next opens.

## Verification

Run `gradle :app:testDebugUnitTest :app:assembleDebug` with Gradle 8.13 and the Android SDK. The unit tests cover sampling frequency and replay ordering. Complete acceptance still requires the four physical tests on the Galaxy S25 described in the v0.1 plan.

See `docs/ACCEPTANCE_V0.1.md` for the PRD acceptance checklist and S25 field protocol.
