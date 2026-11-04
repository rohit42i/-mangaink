# Manga Inker

Manga Inker is an offline Android manga and comic production assistant. Its main job is to reconstruct an existing outlined page into smooth centreline paths and redraw those paths inside ibisPaint X using explicitly user-started Android accessibility gestures. Manga Studio adds practical page-finishing tools.

## Current build: 0.4.0-alpha

### Inking workflow

- Import one complete outlined manga/comic page from the Android document picker.
- Decode artwork locally at up to a 2,000-pixel longest side to keep processing memory bounded.
- Otsu thresholding with a manual threshold adjustment.
- Connected-component speck removal.
- Zhang-Suen skeletonization and centreline path reconstruction.
- Trace open lines, closed panel borders and branches.
- Ramer-Douglas-Peucker simplification and adjustable Catmull-Rom smoothing.
- Detail-preservation and drawing-speed controls.
- Preview extracted paths over the source page.
- Four-corner draggable page calibration with a projective transform for skewed, zoomed or rotated page placement.
- Floating controls for a test curve, start/resume, pause-after-current-stroke, stop-after-current-stroke and progress.
- A required test-curve gate: full-page drawing stays locked until the user sees the test curve in ibisPaint, manually undoes it and confirms.
- Playback pauses when AccessibilityService reports that ibisPaint is no longer the foreground app. Only the official ibisPaint X and ibisPaint Android package IDs are accepted as the drawing target.

### Manga Studio page-finishing tools

- Seven generated panel layouts: single page, two horizontal, two vertical, three strips, four-grid, six-grid and a seven-panel 1+2+4 template. The default **Keep existing panels** option adds no borders and preserves the imported layout.
- Adjustable panel gutters and border thickness.
- Screentone overlays: dots, diagonal lines and crosshatch.
- Apply tones to all panels or an individual panel.
- Place and drag speech bubbles, thought bubbles, narration boxes and sound-effect text.
- Export a finished page as a new PNG through Android's document picker.
- Export never overwrites the imported page or an ibisPaint project.

### Privacy and safety

- No Internet permission, account, cloud processing or artwork upload.
- The app does not retrieve ibisPaint window content or capture the screen.
- The accessibility service reads only the foreground package name from window-state events to ensure gesture playback is directed to ibisPaint; it declares `canRetrieveWindowContent=false`.
- The user manually starts the test and inking session and can pause or stop from the floating panel.
- Imported artwork and extracted paths remain in memory for the active session. Manga Studio exports only when the user explicitly chooses a save destination.

## Important limitations

- This is not an ibisPaint plugin and does not use a public ibisPaint automation API.
- `AccessibilityService.dispatchGesture` emits touch gestures. It does not provide genuine stylus pressure, tilt, pen identity or pressure-sensitive line variation.
- Android's gesture completion callback reports gesture dispatch completion, not proof that ibisPaint visibly rendered the mark. The visual test and manual undo are required.
- A test on the OPPO F29 5G / ColorOS is still required. The foreground-package safety gate, overlay permission and accessibility service must be verified on the actual device.
- The first tracing engine is classical image processing, not a neural model. It reconstructs geometry; it does not identify faces, anatomy or story content.

## First device test

1. Install the debug APK and open Manga Inker.
2. Enable **Manga Inker drawing gestures** in Android Accessibility settings.
3. Allow **Display over other apps**.
4. Import the exact outlined page that corresponds to the page open in ibisPaint.
5. Build paths and inspect the preview. Adjust threshold, speck removal, smoothing and detail preservation.
6. Open the floating controls. Use **Open ibisPaint X** from the panel if needed.
7. In ibisPaint, open the correct canvas, select the intended layer and brush, then drag the four calibration handles to the page corners.
8. Tap **Test one curve**. Check the mark, verify its location and brush, then undo it inside ibisPaint.
9. Tap **I can see it and undid it** to unlock full-page inking.
10. Save a duplicate ibisPaint project before starting. Use the floating pause and stop controls as needed.

The current test is a touch gesture, not a real stylus stroke. Brush width comes from the brush you selected in ibisPaint; pressure and tilt are not emulated. Do not zoom, pan, rotate or change canvas layout while strokes are being sent.

Do not use a valuable original page for early testing. Changing canvas zoom, rotation, toolbars or app layout after calibration invalidates the saved coordinates; recalibrate before continuing.

## Build

GitHub Actions runs unit tests, Android lint and a debug APK build on pushes to `main` and the feature integration branch. Build configuration: JDK 17, Android API 36, Build Tools 36.0.0, Gradle 8.13, Android Gradle Plugin 8.13.2 and Kotlin Gradle Plugin 2.3.10. AndroidX Core 1.17.0 is intentionally pinned because newer Core releases require a newer Android API/AGP combination than this Android 16 build.

Download `manga-inker-debug-apk` from a successful GitHub Actions run's **Artifacts** section.
