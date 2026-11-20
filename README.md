# Tomato Wear (Unofficial)

**Standalone Wear OS Pomodoro app**, adapted from the design and core workflow of [Tomato](https://github.com/nsh07/Tomato) by Nishant Mishra. This is a new community adaptation, **not an official Tomato release** and not affiliated with the original author.

## Implemented

- Wear OS-specific, round-friendly Jetpack Compose interface, light and dark theme
- Focus / short break / long break intervals, start, pause, resume, skip, and reset
- Configurable focus, break lengths (1–180 minutes), number of focus rounds, vibration, sound, and optional next-interval auto-start
- Persistent countdown across activity closure and app process termination
- AlarmManager completion wakeups with vibration and a notification; exact when the user grants **Alarms & reminders** access
- Ongoing countdown notification; supports opening the timer from the notification
- Local record of actual focused and break time; session counts; daily and 7-day bars, 30-day totals, per-topic breakdown
- Default and custom focus topics stored locally
- Automatic alarm rescheduling after boot or system clock change
- Standalone: does not require a paired phone, Internet connection, account, or cloud service

## Build (Android Studio)

1. Open this folder in a recent Android Studio version that supports **Android Gradle Plugin 9.4.0**, **Kotlin 2.4.20**, and the **Android API 37 SDK**. Use **JDK 17**. Install Android API 37 under SDK Manager if required.
2. Sync Gradle. Build with **Build > Build APK(s)** or via `gradle :app:assembleDebug` with Gradle 9.7.1 (see wrapper note below).
3. Install `app/build/outputs/apk/debug/app-debug.apk` on a connected Wear OS emulator/watch (Android Studio or `adb install -r ...`). This package uses `dev.tomatowear.app` and can coexist with the original Tomato phone app.
4. On Android 13+ watches, allow **Notifications** for completion and countdown notifications. On Android 12+ watches allow **Alarms & reminders** from the Settings screen for timely timer completion during Doze.

### GitHub Actions debug APK

If you upload this project into a repository on your own GitHub account, the included `.github/workflows/wear-debug-apk.yml` runs tests and builds a **debug APK** on each push to `main` or `master` (and when manually dispatched). In Actions, open **Build Tomato Wear debug APK** and retrieve the `tomato-wear-debug-apk` artifact. The workflow has **not** run in this environment.

### Gradle CLI note

The `gradle/wrapper/gradle-wrapper.properties` records the expected Gradle distribution, but a binary `gradle-wrapper.jar` is not included. To use CLI builds, install **Gradle 9.7.1** and run `gradle :app:assembleDebug`, or in Android Studio use the IDE-provided Gradle integration / regenerate a standard wrapper using `gradle wrapper --gradle-version 9.7.1` (a `setup-wrapper.sh` convenience script is included).

## Behavior and limitations

- Starting a timer works without notification permission, but visible alerts require it.
- If Android denies precise alarms, the app schedules an inexact wakeup; the displayed timer is based on real time but the end notification may be delayed by Doze. Allow **Alarms & reminders** for accuracy.
- A completed focus interval is added to the completed-session count. Elapsed focus/break time is recorded on pause, skip, reset, and completion (rather than discarded).
- When an interval finishes, the next one is queued paused by default; Settings can turn on auto-start.
- Time statistics are assigned to the local date on which a segment is committed. Crossing midnight can assign a segment to the later date.
- This version is an **independent watch data store**. It **does not synchronize with the original Android/desktop Tomato database**, and does not implement the original app's annual heatmaps, all proprietary paid options, backup/restore, phone widgets, or phone Now Bar. It's not feature-for-feature parity with the upstream app.
- This is source code, **not an APK built and device-tested**. Full Android compilation and physical-device testing should be completed before treating it as production-ready.

## Tests

`gradle :app:testDebugUnitTest` (JVM tests for interval selection, countdown, and time formatting). Also test on both round and square watches, with the app backgrounded or force-stopped, and with precise alarms allowed and denied. Note that Android prevents broadcast execution for apps manually **force-stopped** in system settings until reopened.

## License and attribution

Tomato original: Copyright (c) 2025–2026 Nishant Mishra. Original source: <https://github.com/nsh07/Tomato>.

This adaptation: 2026 independent Wear OS implementation with modified design and workflow, published under **GNU GPL version 3 or later** to respect the terms of the original Tomato project. See `LICENSE`. Include the same source and license when distributing modified copies, and mark further changes.
