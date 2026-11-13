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

Tables use USER scope for App User and GLOBAL scope for App Administrator. App User has CRUD access within its own USER-scoped records.

## Build a Catalyst upload package

Every successful GitHub Actions build creates an artifact named MangaInker-Catalyst-Web. Download its mangainker-catalyst-web.zip file and upload that ZIP in Catalyst Console → Web Client Hosting. The ZIP root contains index.html, built assets, and client-package.json as required by Catalyst Web Client Hosting.

## Stratus setup required after hosting

In Stratus → 1manga:
1. Confirm the authenticated bucket rules allow App User GetObject, PutObject, and DeleteObject.
2. Add the generated Web Client Hosting URL under Bucket CORS with GET, PUT, and DELETE methods.

The Catalyst connector used for this migration does not expose these bucket-permission/CORS controls or a web-client upload action. Cloud project saving reports Catalyst permission/CORS failures clearly rather than silently pretending a file was saved.

## Privacy

Image processing runs in the browser. Nothing is uploaded until the signed-in user chooses Save project. Saved originals and SVG outputs are stored in the authenticated 1manga bucket, with project metadata stored in Catalyst Data Store.
