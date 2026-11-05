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
- [ ] Start the floating panel and verify its persistent notification.
- [ ] Drag the compact bubble; verify it snaps to either edge.
- [ ] Expand, minimize and reopen the panel.
- [ ] Verify More menu actions are available and enabled only when applicable.
- [ ] Start a full-page scan from More and verify stage text and progress update in the overlay.
- [ ] Tap Stop scanning during a scan and verify it cancels without clearing the imported page.
- [ ] Turn the overlay off during a scan and verify the operation is cancelled.

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
- [ ] Interrupt/disable Accessibility and verify recovery state.
- [ ] Verify running border appears only during active trace.

## Comic Studio projects and export

- [ ] Import a page from Home and create a project in Studio.
- [ ] Confirm the project appears in the saved project list after leaving and reopening Studio.
- [ ] Add at least two panel frames and verify they remain aligned to the page.
- [ ] Add a dotted screentone area and verify the preview and exported result.
- [ ] Add a grayscale gradient and verify the preview and exported result.
- [ ] Add speed lines and verify the preview and exported result.
- [ ] Add a dialogue bubble, edit its text, save and reopen the project.
- [ ] Add a sound effect, edit its text, save and reopen the project.
- [ ] Undo the most recent element and verify it is removed.
- [ ] Export the finished page as PNG using Android Save As.
- [ ] Open the saved PNG in an image viewer and verify the source page and added elements are present.
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
