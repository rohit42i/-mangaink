# Stage 1 device test record

Do not mark a test as passed until the visible result has been checked on the phone. An Android `dispatchGesture` callback alone is not proof that ibisPaint rendered a line.

## Device and app details

- Date and time:
- Device: OPPO F29 5G
- Android version:
- ColorOS version:
- Manga Inker version:
- ibisPaint X version:
- Canvas size:
- Selected brush:
- Brush size and opacity:

## Setup checklist

- [ ] Debug APK installed.
- [ ] Restricted settings unlocked where Android 13+ requires it.
- [ ] Accessibility service enabled.
- [ ] Display over other apps enabled.
- [ ] Main screen shows both permissions as Granted.
- [ ] Floating panel appears and can be dragged and snapped to an edge.
- [ ] Collapsed panel shows the state dot and expand handle.
- [ ] Running screen border appears only while a test is active.

## Gesture proof

- [ ] Open ibisPaint X and select the brush recorded above.
- [ ] Tap Test stroke in Manga Inker.
- [ ] Switch to ibisPaint X and tap Start on the floating panel.
- [ ] Observe whether a line appears.
- [ ] Check whether the line is an S-curve and approximately 400 px tall.
- [ ] Answer the in-app result dialog with Yes, No or Partly.
- [ ] Confirm the answer appears in the `MI_GESTURE` log.
- [ ] Undo the test mark in ibisPaint, if one appeared.

## Controls and recovery

- [ ] Pause waits until the active segment completes.
- [ ] Resume continues with the next segment.
- [ ] Stop prevents later segments from being dispatched.
- [ ] Volume-down long press stops a running session.
- [ ] Leaving ibisPaint stops the run.
- [ ] Disabling Accessibility mid-run stops the run and displays a recovery message.
- [ ] Debug log viewer shows segment index, duration and dispatch result.

## Result

- Visible line result: **Not tested**
- Overall Stage 1 result: **Not tested**
- Notes:
- MI_GESTURE log excerpt:
- Unexpected marks or interruptions:
- Retest needed:

Only change the result above after completing the checks on the OPPO F29.
