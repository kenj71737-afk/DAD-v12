# ALAD-Mobile FIX17 — delayed original audio branch

## What changed
- Added `DelayedOriginalAudioPlayer`, an AudioTrack streaming player for captured
  16 kHz mono PCM16 source audio.
- In the Android AudioPlaybackCapture fallback path, the service sends each PCM
  chunk to Gemini immediately, then queues a copy for playback after 5 seconds.
- The WebAudio bridge path is not queued here because its browser-side DelayNode
  already handles delayed original playback; queueing it again would duplicate audio.
- Full-screen delayed player status now describes delayed picture + original audio.

## Important limitations
- This is a delayed audio *copy*. Android does not provide a supported per-app
  mute API for another app such as Chrome. Chrome's original live sound may still
  be audible alongside the delayed copy. The app must not claim that it has
  replaced/muted Chrome audio.
- Audio timing currently uses `elapsedRealtime()` at PCM callback arrival, not
  AudioRecord hardware timestamps. It is a coarse 5-second delay, not sample-accurate
  A/V synchronization.
- Existing video capture uses short video-only MP4 segments. It does not mux this
  PCM into those MP4 files. AudioTrack playback runs separately and may drift or
  have small transition offsets relative to VideoView segment playback.
- MediaProjection capture may include the whole display depending on Android/OEM
  behavior; selecting only Chrome is not guaranteed by this code.
- Playback capture can be blocked by app policy, DRM, or protected content.
- WebAudio bridge mode needs its own verified end-to-end delayed audio path; this
  patch deliberately avoids double-queuing it.

## Verification
- Source/ZIP structure checked only.
- Android Gradle build, emulator playback, and vivo device playback were not run.
- This is not a verified APK.
