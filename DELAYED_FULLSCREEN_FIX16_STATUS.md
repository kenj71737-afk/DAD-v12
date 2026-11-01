# ALAD FIX16 – selected-app video capture and delayed fullscreen preview

## Added in this source iteration

- Android 14+ capture consent now requests the system's **user-selected app/window** flow (`MediaProjectionConfig.createConfigForUserChoice`). On Android 10–13 it falls back to the standard full-display capture consent.
- `DelayedScreenVideoCapture` creates a `VirtualDisplay` and records H.264 MP4 **video-only** segments using `MediaRecorder` (2-second segments, half-display resolution, bounded rolling retention).
- `DelayedVideoSegmentStore` indexes finalized segments by `elapsedRealtime` timestamps and removes old files.
- `DelayedFullscreenActivity` is an immersive landscape full-screen preview that plays only finalized segments once their end time is at least the configured delay behind the live capture clock.
- The foreground notification gains a `Video trễ toàn màn hình` action.
- Existing AudioPlaybackCapture/WebSocket path remains independent and continues sending live PCM to Gemini; video capture does not sit in front of that path.

## Important limitations – not a finished synchronized delayed player

- This iteration records **video only**. It does not yet mux delayed original audio into the MP4 or play a delayed audio track. Therefore the fullscreen preview is currently silent; the source audio capture continues live to Gemini. Do not describe this as synchronized picture+original-audio playback.
- The delayed preview currently uses short MP4 segments. MediaRecorder/VirtualDisplay behavior, encoder support, selected-app capture and rotation stability must be tested on the target vivo device.
- Some apps/content may be protected (`FLAG_SECURE`/DRM), resulting in black or unavailable captured frames.
- Android 10–13 cannot request the Android 14 single-app chooser through this API; it uses whole-display capture.
- AudioPlaybackCapture is subject to source-app capture policy and may return silence. This implementation does not bypass DRM or capture restrictions.
- No APK build or physical-device test has been completed in this environment. A successful ZIP check is not a successful Android build.

## Next engineering step

Implement a timestamped PCM ring buffer fed by the existing AudioRecord capture callback, then encode/mux AAC with the corresponding video timestamps (or use a MediaCodec audio/video muxer) and have the fullscreen player play synchronized A/V. Only after testing segment boundary continuity, A/V drift, encoder compatibility, memory use and thermal behavior on the vivo phone should this be called a working delayed player.
