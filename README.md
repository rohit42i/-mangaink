# Manga Inker • Stage 1

Local-only Android touch-path replay utility. It sends a fixed S-curve through Android AccessibilityService so you can test synthetic touch response in ibisPaint X.

Stage 1 only: no sketch import, image processing, OpenCV, AI, calibration, path tracing, screenshots or artwork storage.

## Permissions and privacy
- No INTERNET permission, analytics, accounts, cloud, uploads, screenshots or image storage.
- SYSTEM_ALERT_WINDOW is only for floating controls.
- Accessibility has canPerformGestures=true and canRetrieveWindowContent=false.
- No MediaProjection or storage permission.

## Install on Android 13+
1. Download the manga-inker-stage1-debug-apk artifact from a successful GitHub Actions run and extract the ZIP.
2. Install app-debug.apk.
3. If Android blocks Accessibility with “App was denied access”, open Settings → Apps → Manga Inker → three-dot menu → Allow restricted settings (if shown). Only do this for an APK you trust.
4. In Manga Inker, open Accessibility settings and enable Manga Inker gesture access.
5. Return and open Overlay settings. Allow Display over other apps.
6. Open ibisPaint X and select a brush.
7. In Manga Inker, tap Test stroke to show the floating panel. Switch back to ibisPaint and tap Start on the panel.

Overlay and Accessibility are separate permissions.

## Stop behavior
Android's public dispatchGesture API has no direct cancellation handle for an already-dispatched gesture. Stop cancels the player coroutine immediately and prevents later segments. A segment already accepted by Android may finish, with a maximum requested duration of 500 ms. We do not send a second touch to interrupt it because that could leave an unwanted mark.

## Build
GitHub Actions builds app-debug.apk on pushes to main and uploads manga-inker-stage1-debug-apk. The APK uses Android Gradle Plugin's standard debug signing.
