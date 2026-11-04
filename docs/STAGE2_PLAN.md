# Full-page and selected-area scanning

The repair branch contains the following implementation:

## Import and image lifetime
- Android document picker accepts supported image types.
- Bitmap decoding is local and downsamples the longest side to at most 1,400 px.
- Transparent pixels composite onto white for grayscale conversion.
- Image and paths remain in the active app session only.

## Scan modes
- Full-page scan uses the entire imported image.
- Selected-area scan uses a drag rectangle drawn directly on the image preview.
- The chosen crop is processed independently; preview paths are positioned back over the original full-page bitmap.
- The selected crop is calibrated to the corresponding visible canvas area. Re-scan invalidates prior calibration and test confirmation.

## Outline extraction
- Otsu global threshold with offset control.
- Eight-connected component filtering for isolated specks.
- Zhang-Suen skeletonization with bounded iterations.
- Pixel-neighbour graph tracing for open paths, branches and closed loops.
- Ramer-Douglas-Peucker simplification.
- Catmull-Rom smoothing blended toward source geometry.
- Preview over the source page, with scan metrics.

## Calibration and playback
- Four draggable corners are validated as a non-degenerate convex quadrilateral.
- A projective transform maps normalized scan coordinates to the calibrated ibisPaint region.
- The user must verify and undo a test stroke before full-page or selected-area tracing.
- Long paths are split by arc length into contiguous pieces of at most 500 ms.
- Pause takes effect between accepted gesture segments.
- Stop closes the dispatch gate immediately and cancels queued segments. An already accepted Android gesture may finish.
- Leaving ibisPaint or disconnecting Accessibility stops the run.

## GitHub updates
- Pushes to the repair branch build a signed release APK with a stable signing identity.
- Workflow updates the private `mobile-latest` GitHub release and its SHA-256 manifest.
- The app checks the private release using a user-provided fine-grained Contents: Read token encrypted with Android Keystore.
- The app downloads and checksum-verifies a newer APK, then opens Android's package installer. Android approval is still required.
- The repository must remain private because the update signing key is stored in its CI source. Do not publish the repository or keystore.

## Device verification
Automated tests/builds do not verify ColorOS service retention, canvas calibration or visible ibisPaint marks. See TEST.md; no physical-device result is claimed until completed.
