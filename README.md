# MangaInker Web

MangaInker is a browser-based manga line-art workspace. GitHub is the source of truth for application code. Zoho Catalyst provides authentication, user-scoped Data Store tables, and Stratus object storage.

## Stack

- React 18 + Vite
- Browser Canvas processing for local tracing
- Catalyst Authentication (embedded sign-in)
- Catalyst Data Store for user profiles, projects, processing history, and settings
- Catalyst Stratus bucket 1manga for original pages and SVG results

## Local development

~~~bash
npm install
npm run dev
npm run build
~~~

Local preview keeps image processing available, but cloud login and persistence are enabled only when the app is hosted on a Zoho Catalyst web-client domain.

## Browser tools

- Import PNG, JPG, and WEBP images up to 25 MB
- Grayscale conversion and Otsu auto-threshold helper
- Zhang-Suen skeleton thinning and SVG path reconstruction
- Speck cleanup, threshold and stroke-width controls
- Original, inked and transparent trace-overlay previews with adjustable overlay opacity
- Editable vector paths: draw new strokes, erase traced segments, undo/redo and reset edits
- Zoom controls, dark/light theme, and adjustable output background transparency
- SVG and transparent PNG export (optional white background)
- Local processing; source images are uploaded to Catalyst only when the user explicitly saves a project

## Browser editor

The floating quick-tools button is fixed above the website workspace, can be dragged around the browser viewport, and is excluded from artwork exports. It provides import, preview switching, reprocessing, vector draw/erase, and SVG/PNG export. It is a browser-page control, not an Android system overlay; a website cannot float above ibisPaint or other apps.

The trace overlay places the original sketch under transparent vector ink so the artist can compare alignment. The path editor supports freehand vector additions, removal of the nearest generated segment, undo, redo, and reset. Exported SVG/PNG backgrounds are transparent by default, with an optional white background.

## Catalyst resources

The Development project already contains:

- 1manga Stratus bucket
- MI_UserProfiles
- MI_Projects
- MI_ProcessingJobs
- MI_UserSettings

Tables use USER scope for App User and GLOBAL scope for App Administrator. App User has CRUD access within its own USER-scoped records. Saved Stratus object keys are partitioned by the Data Store row's CREATORID so bucket rules can enforce per-user file isolation.

## Build a Catalyst upload package

Every successful GitHub Actions build creates an artifact named MangaInker-Catalyst-Web. Download its mangainker-catalyst-web.zip file and upload that ZIP in Catalyst Console → Web Client Hosting. The ZIP root contains index.html, built assets, and client-package.json as required by Catalyst Web Client Hosting.

## Required Stratus security setup after hosting

The current authenticated bucket template only grants GetObject by default. Before enabling cloud saves, replace its default permission rule with this user-scoped rule in Stratus → 1manga → Bucket Permissions → Edit Permission:

~~~json
{
  "rules": [
    {
      "rule_id": "MangaInker_User_Private_Files",
      "condition": {
        "user": {
          "auth_type": "authenticated",
          "zuid": "*"
        }
      },
      "allowed_actions": [
        "GetObject",
        "PutObject",
        "DeleteObject"
      ],
      "paths": [
        "1manga::/users/${var:X_ZUID}/*"
      ],
      "effect": "allow"
    }
  ],
  "version": "v1"
}
~~~

This scopes each authenticated user's files to their own Catalyst user ID path. The code uses the Data Store CREATORID value, and Catalyst documents CREATORID as the unique Catalyst account user ID. Do not replace X_ZUID with an asterisk or change the path to the whole bucket.

After the web app has been hosted and Catalyst gives it a URL, open Stratus → 1manga → Configurations → Bucket CORS and add that exact website origin with GET, PUT, and DELETE methods. The URL is generated only after web hosting, so the origin cannot be preconfigured yet.

The Catalyst connector used for this migration does not expose bucket permission/CORS editing or a web-client ZIP upload action. The GitHub Actions artifact is prepared for the console upload step. Cloud project saving reports permission/CORS failures clearly rather than silently pretending a file was saved.

## Privacy

Image processing runs in the browser. Nothing is uploaded until the signed-in user chooses Save project. Saved originals and SVG outputs use a user-specific Stratus path, and project metadata is stored in user-scoped Catalyst Data Store rows.

## Live website

The browser workspace is published at https://rohit42i.github.io/-mangaink/.

The web-migration branch is built and tested by the MangaInker Web CI workflow. After a successful push build, the main-branch Deploy MangaInker Website workflow checks out this branch and publishes the static site to GitHub Pages. The Android application source remains on main and is not replaced by the website source.

GitHub Pages runs the local image-processing and export workspace. Account sign-in and cloud project storage are provided by the Zoho Catalyst-hosted deployment; they are intentionally unavailable on the GitHub Pages origin until that origin is configured and verified for Catalyst authentication and Stratus CORS/permissions.


## On-device workspace

When the site is opened outside Zoho Catalyst, MangaInker keeps project files, editable SVG previews, processing history and studio preferences in the browser's IndexedDB. Projects can be reopened and deleted from the workspace. This storage is private to the current browser/device and does not sync to another device. Sign-in and cross-device cloud storage are available only after the site is deployed and configured in Zoho Catalyst.


## Android system-wide inking

The separate Android app in app/ sends reconstructed outline paths into the drawing app that is currently open, such as ibisPaint. It uses an Android Accessibility Service to dispatch user-started touch strokes and a system overlay for a draggable MI floating icon. This is the part that can appear over other Android apps; a browser website cannot create system-wide overlays.

### How to use it

1. Install the MangaInker-Systemwide-Inking-APK artifact from the Android workflow.
2. Open Manga Inker, choose the same outlined page you want to ink, adjust the reconstruction controls, and build the drawing paths.
3. Enable **Manga Inker drawing gestures** in Android Accessibility settings and allow **Display over other apps**.
4. Open ibisPaint, select the correct canvas, layer, and brush. Return to Manga Inker and tap **Show floating inking icon**.
5. Switch to ibisPaint. Tap the floating **MI** bubble, choose **Calibrate page corners**, and drag markers 1–4 to the exact corners of the page.
6. Save the calibration, run **Test one curve**, verify the mark appears in ibisPaint, undo it there, and then confirm the test in the floating panel.
7. Start inking. The panel minimizes to the floating icon so the page remains clear. Tap the icon to pause or stop when needed.

The tool draws the imported outline geometry; it does not inspect the other app's screen, generate new artwork, reproduce pressure or tilt, or choose the layer/brush for you. Keep ibisPaint on the same canvas and avoid moving or zooming the page after calibration. Android may restrict accessibility gestures in protected apps.

The Android app is built and checked separately from the website by the **Manga Inker Android Inking App** workflow. Its APK artifact is named **MangaInker-Systemwide-Inking-APK**.
