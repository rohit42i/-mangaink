# Manga Inker

Manga Inker is a local-only Android app for testing synthetic touch gestures in ibisPaint X.

## Stage 1 only

This build contains the permission checklist, floating control panel, fixed 400 px S-curve test and gesture result logging. It does not contain image import, image processing, path extraction, screenshots or artwork storage.

## Privacy and permissions

- No INTERNET permission, accounts, analytics, cloud requests or uploads.
- No storage permissions and no MediaProjection.
- No images, screenshots or drawing paths are written to disk.
- Debug events are sent to Android logcat with the tag `MI_GESTURE` and held in memory for the in-app viewer.
- Accessibility uses `canPerformGestures=true` and `canRetrieveWindowContent=false`.
- Display over other apps is used only for the floating panel and running indicator.

Privacy statement: **No network. No storage. Nothing leaves the device.**

## Install the debug APK

1. Open a successful GitHub Actions run and download the `manga-inker-stage1-debug-apk` artifact. GitHub downloads artifacts as ZIP files.
2. Extract the ZIP and install `app-debug.apk`.
3. On Android 13 or newer, if Android blocks the accessibility service, open App info for Manga Inker, tap the three-dot menu, then choose **Allow restricted settings**. This option may only appear for sideloaded apps. Enable it only for an APK you trust.
4. Open Manga Inker and tap Settings beside **Accessibility service**.
5. In ColorOS, open Accessibility, find Manga Inker under downloaded or installed apps/services, then turn it on. Menu names vary by ColorOS release.
6. Return to Manga Inker and allow **Display over other apps**.
7. Open ibisPaint X, open a canvas and select the brush you want to test.
8. Return to Manga Inker, tap **Test stroke**, switch back to ibisPaint X, then tap **Start** on the floating panel.
9. Answer whether a visible line appeared when the result dialog is shown.

Accessibility and overlay are separate permissions. The restricted-settings row is a manual confirmation because Android does not expose a reliable public API to read that toggle.

## Floating controls and safety

- Start dispatches only while ibisPaint is the foreground app.
- Pause waits for the currently accepted segment to complete, then pauses before the next segment.
- Stop cancels the playback coroutine, clears queued segments and closes the dispatch gate immediately. An Android gesture already accepted may finish, for at most 500 ms.
- Hold the device volume-down key for about 1.5 seconds to stop a running session.
- If Accessibility disconnects or ibisPaint leaves the foreground during a run, the session stops and shows a recovery message. It does not retry automatically.
- While running, a thin red border stays inset around the screen.

## Build

GitHub Actions runs unit tests, builds the debug APK and uploads the `manga-inker-stage1-debug-apk` artifact on pushes to `main` and pull requests targeting `main`.

Toolchain: Android Gradle Plugin 9.5.0, Kotlin Compose compiler plugin 2.3.21, Gradle 9.6.0, JDK 17, compileSdk 37, targetSdk 36, minSdk 26.
