# TVB-inspired Vietnamese voice notes (FIX12)

This project uses newly synthesized Android Vietnamese TTS voices with conservative pitch/rate shaping. It does **not** clone or reproduce the identity of any TVB dubbing actor.

## Current behavior
- Gemini text is expected to contain speaker markers: `[[TVB:speaker_id:M]]` or `[[TVB:speaker_id:F]]`.
- The streaming parser buffers partial WebSocket text until a complete speaker marker and phrase are available.
- Each speaker ID is retained in the voice director's speaker maps for the current session.
- Male/female profiles adjust Android TTS pitch and speech rate before the utterance is queued.
- Vietnamese speech is synthesized locally by the Android TTS engine; installed Vietnamese voice data and quality vary by device.

## Important limitations
- Gender and speaker identity are estimates from Gemini's audio understanding, not guaranteed diarization.
- Android TTS pitch/rate changes only approximate a TVB dubbing style. They do not create actor-like timbre, expressive acting, or studio-quality voice cloning.
- For higher quality, integrate a licensed Vietnamese neural TTS model/provider that supports speaker IDs, emotion/style controls, and streaming or short-phrase synthesis. Keep the same `Segment(speakerId, gender, text)` interface so the provider can replace Android TTS later.
- If a speaker marker is missing or malformed, the current tagged-text path will not synthesize that segment; do not silently treat untagged text as a correctly identified speaker.

## Device validation required
1. Confirm the vivo device has a Vietnamese TTS voice installed and selected.
2. Play alternating male/female tagged phrases and check that pitch/rate are applied per phrase.
3. Test at least three speaker IDs and verify each ID remains stable when the same character speaks again.
4. Verify the TTS queue does not overlap and that subtitle timing remains aligned with delayed original audio/video.
