# Manga Inker

Manga Inker is an Android tool that scans a complete manga/comic page, extracts outline centre-lines locally and traces those paths in ibisPaint X using Android Accessibility gestures.

## Page workflow

1. **Import:** choose a PNG, JPEG or WebP using Android's document picker.
2. **Scan full page:** the app downsamples the longest side to at most 1,400 px, converts it to grayscale, applies Otsu thresholding, removes small isolated components, skeletonizes the outlines, and traces open lines, branches and loops.
3. **Select area (optional):** tap Select scan area and drag a rectangle over the page preview. Scan selected area processes only that crop. Full page resets the selection.
4. **Review:** the blue centre-line paths appear over the source image. Adjust threshold, speck removal, smoothing and detail preservation, then scan again.
5. **Calibrate:** open the floating controls above ibisPaint and drag markers 1–4 to the visible canvas corners. Four-corner projective mapping accounts for perspective.
6. **Test:** run one alignment stroke, check that ibisPaint visibly rendered it, undo it in ibisPaint, then confirm the result in Manga Inker.
7. **Trace:** the full page or selected crop can then be traced with the selected ibisPaint brush. Pause/resume works between accepted gesture segments. Stop cancels all queued segments and closes the dispatch gate.

## Floating panel

The compact bubble can be dragged and snapped to the screen edge. Expand it for Test/Start, Pause/Resume, Stop, and More. More includes Select scan area, Scan selected area, Scan full page, Calibrate page corners, and Clear page session. Minimize collapses the panel without ending the session. The running border and current path progress remain visible. Holding volume-down for about 1.5 seconds is an extra stop action.

The overlay runs as a user-started foreground service with a persistent notification. If ibisPaint leaves the foreground or Accessibility disconnects, the active trace stops rather than retrying blindly.

## GitHub auto-updates

After the first installation, Manga Inker checks its private GitHub release channel when the app opens. When a newer build is published, it downloads the signed APK, verifies the SHA-256 checksum, and shows **Install update** in Settings. Android then displays its own install confirmation. Android does not permit a normal app to silently replace itself.

The repository is private, so setup needs a fine-grained GitHub token once:

1. In GitHub, open **Settings → Developer settings → Personal access tokens → Fine-grained tokens** and create a token.
2. Choose resource owner **rohit42i** and repository access limited to **-mangaink**.
3. Grant **Contents: Read-only**. Do not grant write access.
4. Paste the token into **Manga Inker → Settings → GitHub app updates** and tap **Save token and check**.

The token is encrypted with Android Keystore on the device. It is not included in the APK or uploaded by the app. The update workflow publishes a new signed release whenever code is pushed to `feature/full-page-outline-repair`. The same signing identity is used for each release so Android can install an update over the existing app.

**One-time migration:** builds from the earlier Stage 1 workflow used an ephemeral debug signing key. They cannot be upgraded in place by the new release-signed app. Uninstall the old Stage 1 APK once, install the first `app-release.apk` from the private `mobile-latest` GitHub release, then configure the token. After that, future builds update in-app without manually downloading each APK. Uninstalling removes the old app's local session and update token.

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
- No broad storage permission or MediaProjection permission is used.
- Update credentials are encrypted using Android Keystore. Debug events are held in memory and logcat under `MI_GESTURE`.

## Device test status

Algorithm tests and CI are run by GitHub Actions. Real touch rendering, canvas alignment, ColorOS background behaviour, and long-session pause/stop reliability still need to be verified on the OPPO F29 5G. See `TEST.md`; device results must not be marked passed until physically checked.
