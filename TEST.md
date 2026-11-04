# Stage 1 manual device test

Target: OPPO F29 5G, Android 16 / ColorOS.

## Setup
- [ ] Install the debug APK.
- [ ] If Android shows “App was denied access”, open App info → three-dot menu → Allow restricted settings (only for the trusted APK).
- [ ] Open Manga Inker → Accessibility settings → enable Manga Inker gesture access.
- [ ] Return and verify Accessibility says Enabled.
- [ ] Open Overlay settings → allow Display over other apps.
- [ ] Return and verify Overlay says Enabled.
- [ ] Open ibisPaint X, open a blank canvas and select a brush.

## Test
- [ ] In Manga Inker, tap Test stroke to open the floating panel (or open the panel directly).
- [ ] Switch to ibisPaint X and tap Start on the floating panel.
- [ ] Check whether an S-curve appears near screen centre.
- [ ] Check whether it is approximately 400 px tall.
- [ ] Undo the mark in ibisPaint.
- [ ] Test Pause, Resume and Stop in the floating panel.
- [ ] Verify RUNNING and PAUSED indicators.
- [ ] Verify Stop prevents later segments from starting.

## Record
- Date/time:
- Android / ColorOS build:
- ibisPaint X version:
- Brush:
- Did the curve appear? Yes / No
- Was it centred and approximately 400 px tall? Yes / No
- MI_GESTURE result:
- Pause/resume/stop results:
- Warnings or errors:

A MI_GESTURE “completed” log means Android completed the synthetic gesture. It does not prove ibisPaint rendered a visible line. Only the on-device check confirms that.
