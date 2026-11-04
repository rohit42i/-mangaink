# Manga Inker

Manga Inker is an Android-first inking assistant for a complete outlined manga/comic page. The current build reconstructs dark outline pixels into smooth centerline paths and sends those paths into another drawing app through user-enabled Android accessibility gestures. The target drawing app is ibisPaint X.

## Current build: 0.2.0-alpha

Implemented in this project:

- Import one complete outlined page from the Android document picker (PNG, JPEG, WebP).
- Decode and downsample the page locally to a maximum 1,400-pixel side.
- Otsu thresholding with a manual threshold adjustment.
- Connected-component speck removal.
- Zhang-Suen skeletonization to reconstruct line centerlines.
- Path tracing for open lines, closed panel borders, and branches.
- Ramer-Douglas-Peucker simplification and adjustable Catmull-Rom smoothing.
- Detail-preservation and drawing-speed controls.
- Page preview with reconstructed paths overlaid.
- Four-corner draggable calibration with a projective transform for zoomed, rotated, or skewed page placement.
- Floating controls for a test curve, start/resume, pause-after-current-stroke, stop-after-current-stroke, and session progress.
- A required test-curve confirmation gate: full-page drawing stays locked until the user sees the curve in ibisPaint, manually undoes it, and confirms.
- Local-only session data; no Internet permission, account, server, artwork upload, or cloud image processing.

## Important: what this build does not claim

- Android reports whether it accepted/completed a dispatched gesture, not whether ibisPaint visibly rendered it. The user must verify the test curve.
- Accessibility gestures emulate touch. Pressure, tilt, stylus behavior, and brush dynamics are not reproduced. ibisPaint uses the currently selected brush and its fixed touch behavior.
- No AI model is used in this version. The first path-reconstruction stage uses classical image processing. Neural models should only be introduced after real sample pages reveal a specific problem they can solve.
- The app has not yet been verified on the OPPO F29 5G. ColorOS may restrict background accessibility/overlay services.

## Recommended first device test

1. Install the APK and open Manga Inker.
2. Enable **Manga Inker drawing gestures** in Android Accessibility settings.
3. Allow **Display over other apps**.
4. Select the exact full-page outlined image you have placed in ibisPaint. Keep the target canvas visible and do not change zoom/rotation after calibration.
5. Build paths and inspect the preview. Adjust threshold, speck removal, smoothing, and detail preservation before proceeding.
6. Open the floating controls in ibisPaint. Drag markers 1–4 to the four corners of the page and save.
7. Tap **Test one curve**. Check that the curve appears in the intended layer and at the correct location.
8. Undo the test curve inside ibisPaint, then tap **I can see it and undid it**. Full-page inking is blocked until this confirmation.
9. Save a copy of your ibisPaint project before starting a full page. Test on a duplicate page first.
10. Start inking. Pause and stop controls take effect after the active stroke so a gesture is not cut mid-line.

## Build with GitHub Actions

The workflow at `.github/workflows/android.yml` runs unit tests, Android lint, and assembles a debug APK on pushes to `main` or manual dispatch. Download `manga-inker-debug-apk` from the successful workflow run's **Artifacts** section.

Build requirements: JDK 17, Android SDK platform 35, Build Tools 35.0.0, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21.

## Next development stages

1. Device proof: verify that the test curve is drawn by ibisPaint on the intended current layer.
2. Calibration proof: repeat the test at different canvas zoom and rotation settings.
3. Geometry quality: compare path previews against real manga outlines, especially hair, faces, cross-hatching, and overlapping strokes.
4. Long-session reliability: test pause, resume, interruption, rejected gestures, and ColorOS battery restrictions.
5. Advanced tracing: only then evaluate optional on-device ML for local problems that the classical pipeline cannot solve.

Do not use a valuable original page for early testing. Synthetic touch input can produce many separate undo entries; keep a backup.
