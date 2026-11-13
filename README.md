# MangaInker Web

MangaInker is migrating from a native Android utility to a browser-based manga line-art studio.

## Stack
- React 18 + Vite
- Browser Canvas API for local image processing
- SVG export for editable vector line work
- Zoho Catalyst planned for authentication, application data, serverless APIs and protected object storage
- GitHub is the source of truth for all application code

## Development
```bash
npm install
npm run dev
npm run build
```

## Current browser capabilities
- Import browser-supported image formats up to 25 MB
- Local grayscale thresholding and Otsu threshold helper
- Zhang-Suen skeleton thinning and SVG path reconstruction
- Speck cleanup, threshold and stroke-width controls
- SVG and PNG export
- Responsive workspace and dark/light theme

## Privacy
Image processing currently runs entirely in the browser. Images are not uploaded to a server.

## Migration notes
This branch is the first browser migration slice. Android accessibility automation cannot run from a website; the browser workflow exports artwork for use in drawing applications. Catalyst backend integration and deployment are separate remaining steps.
