# Manga Inker

Android-first inking assistant for a complete outlined manga/comic page. This first working milestone reconstructs dark outline pixels into smooth centerline paths, previews them locally, then sends explicitly user-started touch gestures into a drawing app such as ibisPaint X.

See the project structure and current build status in the source files. Artwork is processed on-device only; no Internet permission, account, upload, or cloud image processing is included.

**Important:** Accessibility gesture completion does not prove that ibisPaint visibly drew the line. The app requires the user to inspect and undo a test curve before unlocking full-page inking. Pressure and tilt are not reproduced.

Early builds must be tested on a duplicate page. The OPPO F29 / ColorOS device behavior has not yet been verified.