# ALAD FIX15 — Delayed full-screen playback foundation

## User-selected behavior
- YouTube/Facebook remains open in Chrome.
- Gemini receives source audio immediately (no delay buffer in the Gemini path).
- ALAD should show a full-screen delayed replay, initially 5 seconds behind.
- Video and replay audio must share one monotonic timestamp timeline.

## Added in this source revision
- `core/delay/DelayedMediaTimeline.kt`: bounded, timestamp-based packet buffer with a 10-second default retention window and 48 MiB default cap. It supports delayed packet lookup/drain and rejects out-of-order timestamps.
- The class is codec-agnostic and intended for encoded video access units/segments and PCM/audio packets.

## Not implemented / not verified
This is a foundation, NOT a completed delayed player. It does not yet:
- request an app-window capture session for Chrome;
- create a MediaProjection VirtualDisplay / MediaCodec encoder;
- capture and timestamp Chrome audio/video;
- mux/decode/replay video in a full-screen Activity;
- delay original PCM and translated voice on the same presentation clock;
- mute Chrome's original output without affecting other media apps.

Android 14+ may offer app-window sharing through the system MediaProjection picker, depending on OS/vendor implementation and user selection. Older Android versions generally capture the display. DRM/secure surfaces can appear black. A MediaProjection capture is user-consented and must remain foreground-service controlled.

## Critical routing invariant
The live PCM callback must call the Gemini WebSocket send function immediately. Only after that send may a copy be timestamped and inserted into `DelayedMediaTimeline` for local delayed playback. Never feed delayed packets back into Gemini.

## Build / device status
- Source ZIP integrity checked after packaging.
- Android build not run in this environment; dependency resolution/Gradle network access has previously failed.
- No APK or vivo device playback test is claimed.
