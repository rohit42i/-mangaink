# MangaInk master requirements and delivery checklist

This is the product acceptance checklist for the Android MangaInk / Manga Inker app. The priority is a dependable page-to-ink workflow on the user's OPPO F29 5G, not a collection of buttons that only look complete.

## Non-negotiable product requirements

- Keep artwork, decoded pages, scan data, and generated paths on-device. Never upload artwork, screenshots, or path geometry.
- Preserve the source page, original proportions, fine details, and existing linework. Do not silently crop, stretch, invent, or erase artwork.
- Process a complete manga page, including every panel. A seven-panel page is a required real-world test case.
- Produce smooth, accurate, non-duplicated paths. Avoid retracing, overlaps, accidental joins between unrelated strokes, and paths escaping panel/page boundaries.
- Do not claim a gesture succeeded merely because Android accepted it. Require a visible test stroke and user confirmation before a full trace.
- Make every action observable: progress, current state, useful error text, and a recovery path. No silent failures or dead controls.
- The app must be install-once and update in-app from the private GitHub release channel. Do not make the user repeatedly download APK versions. Android's own install confirmation remains mandatory.

## A. Professional app experience

- Replace the rough/placeholder feeling with a polished, consistent, compact Android UI.
- Use clear hierarchy: page workspace first, scan/preview next, trace controls next, settings and diagnostics separately.
- Show page dimensions, scan state, path count, processing metrics, calibration state, Accessibility connection, foreground target, and trace progress.
- Disable actions only when they cannot work, and explain why.
- Provide useful empty, loading, success, warning, and error states.
- Keep settings understandable and avoid cluttering the main workflow.

**Current evidence:** a Compose Home/Settings screen, preview, metrics, scan settings, setup rows and update UI exist on the repair branch. Visual quality and complete interaction flow still need review on-device.

## B. Import and page workspace

- Import PNG, JPEG and WebP through Android's document picker.
- Display the full page at its original aspect ratio.
- Support full-page scanning as the default, with optional drag-selected-area scanning.
- Make crop coordinates map back to the exact original page coordinates.
- Provide replace page, clear session, rescan, and recoverable import errors.
- Keep the current page session in memory only.

**Current evidence:** document-picker import, downsampling, full-page preview, selected-area scan and clear-session code exist. OPPO device behaviour is unverified.

## C. Full-page line analysis and inking

- Scan every panel and all visible linework on the page, including small details.
- Separate independent strokes, endpoints, branches and closed loops.
- Preserve line direction and local geometry; smooth without rounding away corners or facial details.
- Remove isolated specks without deleting small intentional marks.
- Prevent duplicate paths, repeated edges, false bridges and unwanted connections.
- Keep all paths within the selected scan region and source page.
- Show a preview over the source page before sending any gesture.
- Expose threshold, speck filtering, smoothing and detail-preservation controls with safe defaults.
- Provide a useful scan report and clear explanation when no usable paths are found.

**Current evidence:** grayscale/Otsu thresholding, connected-component speck filtering, Zhang-Suen thinning, graph path tracing, simplification, smoothing and path preview exist. Current scanner is classical image processing, not AI. Dense hatching, faint pencil and intersecting outlines remain known risk cases. Panel-aware grouping and overlap/duplicate validation need dedicated tests.

## D. Floating controls (autoclicker-style interaction)

- User-started floating bubble that can be dragged and snapped to screen edges.
- Compact/minimized mode that does not stop a scan or trace.
- Expand/reopen controls without losing the current page or trace state.
- Prominent Start/Test, Pause/Resume and Stop actions with unmistakable states.
- More menu for scan full page, scan selected area, select scan area, calibration, clear session and settings/help.
- Explicit Turn overlay off / close action, distinct from Minimize.
- Show scan/trace progress and current status in the overlay.
- Stop must close the dispatch gate immediately and cancel queued work.
- Handle Android Accessibility disconnect, target-app changes and foreground-service loss safely.
- Long-press volume-down emergency stop.
- Never resume blindly after a lost service or changed canvas.

**Current evidence:** floating bubble, drag/snap, expand/minimize, More menu, calibration, selected-area/full-page scan actions, Start/Pause/Resume/Stop, foreground notification, volume-down stop and service-loss stop are present in code. Physical OPPO/ColorOS gesture testing is still required. Verify the explicit overlay-off behaviour and every menu action.

## E. ibisPaint X integration and safety

- Work with ibisPaint X using Android Accessibility gestures; do not inspect or upload canvas content.
- Four-corner calibration for the visible page, including perspective mapping.
- Reject invalid/collapsed calibration points.
- Require a visible alignment test stroke, manual check, undo, and explicit confirmation.
- Match imported page, canvas size, zoom, rotation and position; require recalibration after changes.
- Trace in bounded gesture segments; report progress and segment failures.
- Pause between accepted segments; Stop blocks all later dispatches.
- Stop if ibisPaint is no longer foreground or Accessibility disconnects.
- Do not claim pressure/tilt simulation; Android touch gestures cannot reproduce stylus pressure/tilt.

**Current evidence:** four-corner projective calibration, test-stroke confirmation, segmented gesture playback, pause/stop dispatch gate and foreground/service-loss handling are implemented. Visible rendering and alignment are not yet verified on the phone.

## F. Manga and comic creation studio roadmap

These are the broader "ultimate manga/comic tool" functions requested, beyond the automatic inking engine. They must be real working tools, not decorative controls.

1. Page/project management: create/open projects, page list, reorder/duplicate pages, page dimensions, safe session recovery.
2. Panel tools: add/edit/resize/reorder panel frames, gutters, alignment guides, and panel-aware linework protection.
3. Inking workspace: brush/line presets, undo/redo where supported, layer-aware workflow guidance, preview and export.
4. Lettering: speech/thought bubbles, tails, editable text, font sizing, alignment and safe margins.
5. Effects: screentones, gradients, speed lines, hatching and reusable texture presets.
6. Sound effects: editable SFX text with rotation, outline, fill and placement controls.
7. Export: high-resolution PNG page export, consistent page dimensions, and project/page naming.
8. Usability: tooltips, shortcuts/gestures where Android permits, onboarding, settings, and diagnostics.

**Implemented in this branch:** private local project create/open/delete; single-page project storage; drag-to-create panel frames; dotted screentone regions; editable dialogue bubbles and SFX text; PNG composition and Android Save As export. Physical export and reopen tests are still pending.

**Still outstanding:** multi-page sequencing and reordering within a single project, duplicate pages, panel resizing/reordering after creation, richer balloon tails and text wrapping, brush presets, editable ink layers, undo/redo history beyond the most recent added element, gradients, speed lines, hatching presets and texture libraries. Do not mark the complete studio roadmap finished until those tools are implemented and tested.

## G. GitHub updates and release

- Private repository and one stable release signing identity.
- First install only once from the signed release artifact.
- App checks the private `mobile-latest` release, downloads APK, verifies SHA-256, and opens Android's package installer.
- Store a user-provided read-only GitHub token encrypted with Android Keystore.
- Never embed a GitHub token or expose the signing key in source.
- Publish releases from the trusted main-branch workflow only.
- Provide clear update states and errors; preserve the installed app and local settings across upgrades.

**Current evidence:** in-app update manager, encrypted token storage, checksum verification and installer handoff exist in source. The app now retains the established `com.rohit.mangainker` application ID, and the release manifest uses the same ID. Release publishing is configured for main after PR #5 is merged. The first-install signing migration and in-app update path are not device-tested.

**Release security blocker:** the current branch includes a release keystore file under `.ci/` and its passwords are written in the Gradle signing configuration. The repository is private, but this is not a production-grade secret-management setup. Before public distribution or granting repository access to anyone else, move the signing material into GitHub Actions secrets and rotate the key if its access scope is no longer trusted. The available GitHub connection cannot create repository Actions secrets, so this remains an owner-side configuration step.

## H. Required verification before calling it complete

- Unit tests for open paths, branches, loops, intersections, tiny details, dense hatching, disconnected specks, blank pages, and multi-panel pages.
- Regression test using a full seven-panel page: compare preview to source and confirm no panel-boundary escapes, duplicate/retraced paths or destructive smoothing.
- Android lint, debug build, signed release build, manifest checks and all unit tests green.
- OPPO F29 5G / current ColorOS physical tests for permissions, overlay lifecycle, drag/snap, scan, calibration, visible test stroke, full trace, pause/resume/stop, volume stop, foreground loss and update installation.
- Record actual device results in TEST.md. Never mark a physical test passed based only on CI or a dispatch callback.

## Delivery order

1. Audit current code and remove/resolve duplicate or conflicting implementations.
2. Make the core page scan and path quality measurable with regression tests.
3. Finish and polish the floating controls and permission/error handling.
4. Redesign the main workspace around the actual page-to-ink workflow.
5. Complete the seven-panel end-to-end test and OPPO device test record.
6. Implement the broader comic-studio roadmap in coherent modules.
7. Verify signed release/update flow and only then merge/release.

## Recent implementation changes

- Added an explicit stop action for active image import and full-page/selected-area scans.
- Added scan cancellation checkpoints to bitmap grayscale conversion, threshold classification, connected-component filtering, skeletonization, graph tracing, simplification and smoothing.
- Added stage-based and per-path progress reporting to the main page workspace and floating overlay.
- Made the floating Stop action immediate rather than putting a confirmation dialog in front of the stop gate.
- Turning off the floating overlay now also cancels active scanning and tracing.
- Added regression coverage for a seven-panel page and for cancellation during outline extraction.
- Added a local Comic Studio tab with project create/open/delete, panel and screentone placement, editable dialogue/SFX elements, and PNG export.
- Restricted GitHub Contents write permission to the main-only release publishing job; ordinary verification builds use read-only repository permission.
- Restored the existing Android application ID `com.rohit.mangainker` and aligned the release update manifest.

These are source changes, not proof of device behaviour. The CI job still needs to report green, and the OPPO F29 checklist must be completed physically.

Status on this branch: core page-to-ink implementation improved; comic-studio roadmap and physical device verification remain in progress.