# ALAD-Mobile FIX18 — Chrome audio duck experiment

## Added
- Adds a persistent-notification action: "Thử giảm tiếng Chrome".
- On explicit tap, requests Android `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
- Keeps the existing AudioPlaybackCapture callback ordering: send captured PCM to
  Gemini immediately, then enqueue its copy for delayed playback.
- Abandons the focus request when the dubbing service stops or focus is lost.

## What this does NOT guarantee
- Audio Focus is cooperative. Chrome may duck, pause, ignore the request, or behave
  differently by version/OEM. Android has no public API for ALAD to force-mute only
  Chrome while guaranteeing capture continues.
- If Chrome ducks, the captured PCM can also become quieter. Gemini may therefore
  receive lower-level audio. This action is deliberately manual/experimental.
- It does not change system-wide media volume and does not claim to mute Chrome.
- WebAudio-originated PCM is not routed through the delayed AudioTrack branch here;
  the BrowserAudioBridge may already own a WebAudio DelayNode path. Avoid enabling
  both delay paths simultaneously.
- The delayed audio copy is not yet muxed into the delayed video MP4 segments.
  Exact A/V sync and voiceover mix still require a shared timestamped media pipeline.

## User test
1. Start ALAD capture while YouTube plays in Chrome.
2. Confirm the ALAD audio meter is active.
3. Tap "Thử giảm tiếng Chrome" in the persistent ALAD notification.
4. Listen: does Chrome become quieter, pause, or remain unchanged?
5. Check whether the ALAD audio meter / Gemini transcription becomes quieter or stops.
6. If capture is damaged, stop ALAD and restart; service stop abandons Audio Focus.

## Verification status
- ZIP CRC checked.
- Android Gradle build and physical vivo test not run.
- This is source code for a controlled experiment, not a verified mute feature or APK.
