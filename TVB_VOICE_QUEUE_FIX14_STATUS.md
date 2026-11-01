# ALAD-Mobile FIX14 - ordered TVB-style voice queue

## Changes in this revision
- Reworked `TvbVoiceDirector` to buffer Gemini speaker markers split across WebSocket deltas.
- Added a single-utterance queue. A new line starts only after the previous utterance completes or errors.
- Applies the selected male/female Android TTS pitch and rate only when that line becomes active, avoiding rapid setting changes while multiple lines are queued.
- Clears queued lines on stop and releases the Android TTS engine on shutdown.

## Important limitations
- This is still the Android system TTS fallback. Pitch/rate differences are not Neural TTS, genuine emotion control, or a clone of a TVB actor.
- The FIX13 `NeuralTtsModelManager` only validates a local model-pack folder. No Sherpa-ONNX runtime or verified Vietnamese ONNX model is bundled or connected to playback in FIX14.
- The current environment has no installed Gradle executable and no cached Gradle wrapper distribution. The wrapper requests Gradle 9.3.0 from `services.gradle.org`, which was previously unreachable. Therefore this source ZIP has not been compiled, and no APK is claimed.

## Runtime protocol
Gemini text stream format remains:
`[[TVB:character_01:M]]Vietnamese line...`
`[[TVB:character_02:F]]Vietnamese line...`

A marker selects a stable character ID and gender for the following text until the next marker. The model prompt/server must emit these markers correctly for character consistency.
