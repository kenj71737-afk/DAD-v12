# FIX19 - Fullscreen button on ALAD subtitle overlay

## Change
- Added a dedicated fullscreen icon button to the `ALAD SUBTITLES` floating panel header, between the drag handle and close button.
- Tapping the icon launches `DelayedFullscreenActivity` directly from the visible overlay, with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP` and the currently selected delay value.
- Existing notification action `Video trễ toàn màn hình` remains available as a fallback.

## User flow
1. Start ALAD and play the source video in Chrome.
2. When the `ALAD SUBTITLES` panel appears, tap the new expand/fullscreen icon in its header.
3. ALAD requests to open the delayed fullscreen player; Android may still apply OEM/background-activity restrictions, so the notification action remains a fallback.

## Important limitations
- This change adds the missing visible control; it does not make Chrome's video appear inside ALAD.
- The existing `DelayedFullscreenActivity` currently plays captured video segments only. The source audio pipeline is separate, and exact audio/video synchronization is not yet implemented.
- Android/OEM policy can block an activity launch from an overlay on some devices. If that happens, use the foreground-service notification action.
- Build verification was attempted with `bash gradlew :app:compileDebugKotlin --no-daemon` but could not run because this environment cannot resolve `services.gradle.org` (UnknownHostException). No APK was built or device-tested.
