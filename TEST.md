# Manga Inker device test record

Do not mark a device test as passed until the visible result has been checked on the OPPO F29. An Android dispatch callback alone does not prove that ibisPaint rendered a mark.

## Device

- Date and time:
- Device: OPPO F29 5G
- Android / ColorOS:
- Manga Inker version:
- ibisPaint X version:
- Canvas size and selected brush:

## First installation and updates

- [ ] Remove the old Stage 1 debug build once (its signing key is different).
- [ ] Install app-release.apk from the manga-inker-install-once-release-apk artifact in the successful feature-branch Actions run (or the mobile-latest release after PR #5 is merged).
- [ ] Create a fine-grained GitHub token for rohit42i/-mangaink with Contents: Read-only.
- [ ] Save the token in Settings → GitHub app updates.
- [ ] After PR #5 is merged, confirm the app can check mobile-latest and verify the APK checksum.
- [ ] Allow Manga Inker to install unknown apps when Android requests it.
- [ ] Confirm Android opens the install confirmation for a newer release.
- [ ] Confirm an update installs over the existing Manga Inker app without uninstalling it.

## Permissions and floating panel

- [ ] On Android 13+, allow restricted settings for this trusted build if requested.
- [ ] Enable Manga Inker in Accessibility settings.
- [ ] Allow Display over other apps.
- [ ] Cold-start the floating panel and verify the Compose overlay opens without a lifecycle-owner crash.
- [ ] Expand, collapse and reopen the floating controls repeatedly; verify the overlay service stays running.
- [ ] Start the floating panel and verify its persistent notification.
- [ ] Drag the compact bubble; verify it stays fully inside the screen and snaps to either edge.
- [ ] Expand a bubble snapped to the right edge; verify the full panel remains visible and aligned to the right edge.
- [ ] Expand a bubble snapped to the left edge; verify the full panel remains visible and aligned to the left edge.
- [ ] Minimize after expanding on either edge; verify the compact bubble returns to that same edge.
- [ ] Restart the floating service and verify its last snapped position is restored.
- [ ] Rotate/change display size and verify the bubble is clamped back onto the visible screen.
- [ ] Expand, minimize and reopen the panel.
- [ ] Verify More menu actions are available and enabled only when applicable.
- [ ] Start a full-page scan from More and verify stage text and progress update in the overlay.
- [ ] While a scan is running, verify the direct Stop scan control cancels it without clearing the imported page.
- [ ] Use the floating service notification's Turn off action; verify scanning/tracing and any active screen capture stop and the overlay disappears.
- [ ] Tap Stop scanning during a scan and verify it cancels without clearing the imported page.
- [ ] Turn the overlay off during a scan and verify the operation is cancelled.
- [ ] While screen capture is waiting for ibisPaint, use the Recording screen notification's Stop capture action and verify capture ends.
- [ ] During captured-frame import, use Stop scanning and verify import cancels and the previous page is not replaced.

## MediaProjection screen capture

- [ ] Open ibisPaint X with a page visible.
- [ ] From Manga Inker Home, tap Capture ibisPaint screen.
- [ ] Approve Android's screen-capture consent prompt for this session.
- [ ] Confirm a persistent Recording screen notification appears.
- [ ] Confirm Manga Inker returns to ibisPaint and waits for it to be foreground.
- [ ] Confirm the floating bubble is hidden during the captured frame so it cannot be included in the artwork.
- [ ] Confirm the Recording screen notification provides a working Stop capture action.
- [ ] Confirm one frame is captured and the recording notification disappears.
- [ ] Confirm a light artwork canvas is auto-detected and cropped without ibisPaint toolbars.
- [ ] Test a dark or ambiguous canvas and verify the full frame is retained for manual selection.
- [ ] Deny the consent prompt and verify the app reports that nothing was captured.
- [ ] Leave ibisPaint closed and verify the 20-second timeout ends capture with a useful message.
- [ ] Confirm no screenshot file is created in shared storage or uploaded.
- [ ] Replace or clear the captured page and verify the previous temporary bitmap is released.

## Import and full-page scan

- [ ] Import a complete PNG, JPEG or WebP page.
- [ ] Confirm the entire source image appears in the preview.
- [ ] Tap Scan full page and verify blue paths appear over the full page.
- [ ] Confirm path count, foreground pixels, centreline pixels and removed specks are sensible.
- [ ] Adjust threshold and speck removal; rebuild.
- [ ] Adjust smoothing and detail preservation; rebuild.
- [ ] Import a blank or unsupported image and verify a useful error is shown.

## Selected-area scan

- [ ] Tap Select scan area.
- [ ] Drag a rectangle over the page preview.
- [ ] Verify the selected rectangle is shown in the original page position.
- [ ] Tap Scan selected area.
- [ ] Verify extracted paths remain in the correct position relative to the source page.
- [ ] Tap Scan full page and verify the selection resets.

## Calibration and test stroke

- [ ] Open the matching source page in ibisPaint X.
- [ ] Open More → Calibrate page corners.
- [ ] Drag markers 1–4 to top-left, top-right, bottom-right and bottom-left of the visible page.
- [ ] Save corners; verify crossed/collapsed corners are rejected.
- [ ] Run the test stroke.
- [ ] Confirm the line appears in the correct canvas region and layer.
- [ ] Undo the test stroke in ibisPaint.
- [ ] Confirm Yes only after verifying it appeared and was undone.

## Trace controls and recovery

- [ ] Start tracing on a duplicate canvas.
- [ ] Verify path progress advances and outlines follow the source.
- [ ] Pause; ensure no new segment starts after the active segment finishes.
- [ ] Resume; ensure tracing continues.
- [ ] Stop; ensure queued paths are not dispatched afterward and the stop action is immediate.
- [ ] Turn the overlay off during tracing and verify tracing is stopped.
- [ ] Test volume-down long-press stop.
- [ ] Leave ibisPaint and verify tracing stops.
- [ ] Interrupt/disable Accessibility mid-trace and verify the handle turns red and the panel shows Reconnect Accessibility.
- [ ] Re-enable Accessibility, return to ibisPaint and verify tracing resumes at the next path after the last fully completed path, without replaying completed paths.
- [ ] Verify running border appears only during active trace.

## Comic Studio projects and export

- [ ] Import a page from Home and create a project in Studio.
- [ ] Confirm the project appears in the saved project list after leaving and reopening Studio.
- [ ] Rename the project and verify the new title persists after reopening.
- [ ] Add a second imported page, rename it, duplicate it, reorder the pages and switch between them.
- [ ] Delete a non-final page and verify the remaining pages and page order are preserved.
- [ ] Verify the app refuses to delete the last remaining page.
- [ ] Add at least two panel frames and verify they remain aligned to the page.
- [ ] Add a dotted screentone area and verify the preview and exported result.
- [ ] Add a grayscale gradient and verify the preview and exported result.
- [ ] Add speed lines and verify the preview and exported result.
- [ ] Add a dialogue bubble, edit its text, wrap long dialogue into multiple lines, change text size and move its tail left/right.
- [ ] Rotate a bubble and verify it looks the same in the preview and PNG export.
- [ ] Add a dialogue bubble, save and reopen the project.
- [ ] Add a sound effect, edit its text, change text size and rotate it; save and reopen the project.
- [ ] Adjust tone and speed-line density and verify the preview and export match.
- [ ] Undo the most recent element and verify it is removed.
- [ ] Export the finished page as PNG using Android Save As.
- [ ] Open the saved PNG in an image viewer and verify the source page and added elements are present.
- [ ] Export the full project as CBZ, open it with a CBZ-compatible reader and verify every page appears in the correct order.
- [ ] Delete a test project and verify it disappears from the project list.

## Results

- Automated CI: record workflow URL and result.
- Import/full-page scan: **Not device tested**
- Selected-area scan: **Not device tested**
- Calibration: **Not device tested**
- Visible test stroke: **Not device tested**
- Full-page and selected-area tracing: **Not device tested**
- Pause/resume/stop: **Not device tested**
- In-app update/install: **Not device tested**
- Notes and MI_GESTURE log excerpt:
- Retest needed:

Only update these results after testing the physical phone.
