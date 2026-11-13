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
- Original and inked preview, zoom, dark/light theme
- SVG and PNG export
- Local processing; source images are uploaded to Catalyst only when the user explicitly saves a project

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
