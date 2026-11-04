# Stage 2 plan

Planning notes only. No Stage 2 code or dependencies are included in the Stage 1 build.

## Image import

- Use the Android system photo picker or Storage Access Framework document picker.
- Retain the returned content URI only for the active session.
- Do not request broad storage permissions or copy the imported artwork into app storage.

## Preprocessing

1. Decode the selected image from its content URI.
2. Convert the working image to grayscale.
3. Apply adaptive thresholding to separate line art from the page.
4. Use morphological cleanup to reduce isolated specks and close small gaps.
5. Keep preprocessing results in memory and release image buffers when the session ends.

## Centerline extraction

- Skeletonize the thresholded line art to one-pixel centerlines.
- Detect endpoints, junctions and connected components.
- Preserve component identity so paths can be inspected before any gesture is sent.

## Graph tracing and path preparation

- Represent centerlines as a graph of pixels, endpoints and junctions.
- Trace connected branches and order candidate paths.
- Apply Douglas-Peucker simplification to reduce redundant points.
- Support Catmull-Rom or cubic Bezier smoothing while keeping the path near the source centerline.
- Suppress duplicate or nearly coincident strokes.
- Enforce a configurable stroke-count limit before playback.

## Coordinate mapping and preview

- Map image bounds to a user-selected screen region.
- Lock aspect ratio by default and make any non-uniform mapping explicit.
- Show a preview outline of the target region before drawing.
- Keep the preview separate from the gesture dispatcher so no marks are sent until the user starts a confirmed run.

## Acceptance checks

- Test with a small set of sample line-art images.
- Compare extracted centerlines and ordered paths against the source images.
- Record false branches, lost junctions, duplicate paths and mapping errors.
- Do not proceed until the previous stage has a passing device test recorded in `TEST.md`.
