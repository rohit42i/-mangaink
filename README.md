# Manga Inker

Manga Inker is an Android tool that scans a complete manga/comic page, extracts outline centre-lines locally and traces those paths in ibisPaint X using Android Accessibility gestures.

## Page workflow

1. **Capture ibisPaint:** from Home, tap **Capture ibisPaint screen** and approve Android's screen-capture prompt. Approval is required for every capture session; Manga Inker cannot silently read another app.
2. **Open the artwork:** after approval, Manga Inker starts ibisPaint X. A persistent **Recording screen** notification stays visible while the capture service is active. The app waits until Accessibility reports ibisPaint in the foreground, then captures one frame and stops the projection.
3. **Detect the canvas:** the frame is downsampled for local page-bound detection. A clear large light canvas is cropped automatically. If the page is ambiguous (for example, a dark canvas or a page blended into the app background), Manga Inker keeps the full frame and asks you to select the artwork area manually.
4. **Import alternative:** choose a PNG, JPEG or WebP using Android's document picker when you already have a clean page image.
5. **Scan:** the processing copy is capped at 1,400 px on its longest side, converted to grayscale, thresholded with Otsu, filtered for small components, skeletonized, and traced into open lines, branches and loops.
6. **Select area:** tap **Select scan area** and drag a rectangle over the page preview. Scan selected area processes only that crop. Full page resets the selection.
7. **Review:** blue centre-line paths appear over the source image. Adjust threshold, speck removal, smoothing and detail preservation, then scan again.
8. **Calibrate:** open the floating controls above ibisPaint and drag markers 1–4 to the visible canvas corners. Four-corner projective mapping accounts for perspective.
9. **Test and trace:** run one alignment stroke, verify that ibisPaint visibly rendered it, undo it in ibisPaint, then confirm the result in Manga Inker. The app traces with the selected ibisPaint brush.

Gestures are finger-style touch input, not stylus input. Set brush size in ibisPaint before tracing; Accessibility cannot simulate stylus pressure or tilt. Pause takes effect after the active gesture segment. Stop prevents later queued segments from being dispatched.

## Floating panel

The compact bubble can be dragged and snapped to the screen edge. Expand it for Test/Start, Pause/Resume, immediate Stop, and More. Turning the overlay off also stops an active trace or scan so work cannot continue invisibly without controls. More includes Select scan area, Scan selected area, Scan full page, Stop scanning, Calibrate page corners, and Clear page session. During a scan, the panel shows the current processing stage and progress. Stop scanning cancels the active import/scan without clearing the already loaded page. Minimize collapses the panel without ending the session. The running border and current path progress remain visible. Holding volume-down for about 1.5 seconds is an extra stop action.

The overlay runs as a user-started foreground service with a persistent notification. If ibisPaint leaves the foreground, tracing stops for safety. If Accessibility disconnects during a page trace, Manga Inker pauses, remembers the last fully completed path, and resumes from the next path after the service is re-enabled and ibisPaint is foreground again. An interrupted test stroke must be run again.

## Comic Studio

1. Import a page from Home.
2. Open **Studio** and create a local project from the imported page.
3. Choose **Panel**, **Tone**, **Gradient**, **Speed lines**, **Bubble** or **SFX**, then drag on the page to place that element.
4. Dialogue and SFX text can be edited in the text dialog. Project changes are saved in the app's private storage.
5. Return to the project list to reopen or delete saved projects.
6. Choose **Export finished page as PNG** and select a save location in Android's file picker.

The studio currently exports the page bitmap used by the inking workspace (the scanner's processing copy is capped at 1,400 px on its longest side). It does not yet replace a full desktop publishing or multi-layer drawing editor. Advanced multi-page sequencing, panel reordering, richer bubble tails/text wrapping, brush/layer editing, speed lines and gradients remain planned.

## GitHub auto-updates

After the first installation, Manga Inker checks its private GitHub release channel when the app opens. When a newer build is published, it downloads the signed APK, verifies the SHA-256 checksum, and shows **Install update** in Settings. Android then displays its own install confirmation. Android does not permit a normal app to silently replace itself.

The repository is private, so setup needs a fine-grained GitHub token once:

1. In GitHub, open **Settings → Developer settings → Personal access tokens → Fine-grained tokens** and create a token.
2. Choose resource owner **rohit42i** and repository access limited to **-mangaink**.
3. Grant **Contents: Read-only**. Do not grant write access.
4. Paste the token into **Manga Inker → Settings → GitHub app updates** and tap **Save token and check**.

The token is encrypted with Android Keystore on the device. It is not included in the APK or uploaded by the app. Feature-branch builds upload one signed first-install APK artifact. The private `mobile-latest` release is published only from `main` after PR #5 is merged. Later pushes to `main` publish new releases automatically. This keeps the release token read/write scope out of the feature-branch workflow. The same signing identity is used for each release so Android can install an update over the existing app.

**One-time migration:** builds from the earlier Stage 1 workflow used an ephemeral debug signing key. They cannot be upgraded in place by the new release-signed app. The new release keeps the established `com.rohit.mangainker` Android application ID, but uses the dedicated release signing key. Uninstall the old Stage 1 APK once because Android will reject an update signed with a different key, then install `app-release.apk` from the `manga-inker-install-once-release-apk` artifact in the successful feature-branch Actions run. Once PR #5 is merged, `main` publishes `mobile-latest`; open Manga Inker and add the read-only token. After that, later `main` builds are checked and installed from inside the app, with Android's confirmation prompt. Uninstalling removes the app's local session and update token.

## Important limitations

- Android reports accepted/completed gestures, not whether ibisPaint visibly rendered a mark. Always check the test stroke.
- Simulated touch does not reproduce stylus pressure or tilt. ibisPaint uses the selected brush's touch behaviour.
- This is classical image processing, not AI. Dense cross-hatching, faint pencil, textured paper and intersecting outlines can create extra or missing paths. Review the preview.
- The scanner caps the longest image side at 1,400 px to manage memory; fine details may be lost.
- Calibration must match the same ibisPaint page, zoom, rotation and position as the imported page. Recalibrate after changing the canvas.
- Accessibility is configured with `canPerformGestures=true` and `canRetrieveWindowContent=false`. The app does not inspect ibisPaint's canvas; the four corners are set manually.
- An Android gesture already accepted can finish for up to 500 ms after Stop. No later segments are sent.
- Test on a duplicate canvas before using important artwork.

## Permissions and privacy

- Artwork import and outline extraction are local. No artwork, screenshots or path geometry are uploaded or written to persistent app storage.
- Network access is used only for checking and downloading the signed private GitHub app update. The app does not send artwork to GitHub.
- The app requests overlay access for the floating panel, Accessibility gesture access for tracing, and permission to request APK installation for updates.
- Screen capture uses Android MediaProjection and its required foreground-service permission. The user must approve each session. A persistent notification and Android's screen-sharing indicator are shown while it is active. Only one ibisPaint frame is captured per request.
- Captured frames and imported artwork stay in memory for the session. They are not written to persistent app storage or uploaded. Clearing/replacing the page or ending the app releases the temporary bitmap.
- Update credentials are encrypted using Android Keystore. Debug events are held in memory and logcat under `MI_GESTURE`.

## Device test status

Algorithm tests and CI are run by GitHub Actions. Real touch rendering, canvas alignment, ColorOS background behaviour, and long-session pause/stop reliability still need to be verified on the OPPO F29 5G. See `TEST.md`; device results must not be marked passed until physically checked.
