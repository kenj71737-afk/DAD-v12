# FIX13 — on-device Neural TTS preparation

## What is implemented in this source update
- Adds `NeuralTtsModelManager`, which checks for a complete model pack in app-private storage.
- Prevents the app from treating an absent/partial ONNX model as a working Neural TTS engine.
- Keeps the existing `TvbVoiceDirector` and Android TTS fallback path unchanged.
- Defines a model-pack layout suitable for a later Sherpa-ONNX adapter.

## Model pack layout
`files/neural_tts/vi_vn/<voiceId>/`
- `model.onnx` — acoustic/TTS model (architecture-specific)
- `tokens.txt` — tokenizer/vocabulary for that exact model
- `model.json` — metadata including architecture, sample rate, speaker IDs, license and model source
- optional `lexicon.txt`, `voices.bin` only if required by that model

Do not mix files from different model releases. A generic ONNX file is not enough: the runtime adapter must match the model architecture and its input/output tensor contract.

## Important status
This is **not yet an active Neural TTS engine**. No verified Vietnamese model pack has been bundled or installed, and this update does not add an unverified Maven dependency. The current speech output remains Android TTS until a compatible Vietnamese model and its license are verified and the Sherpa-ONNX adapter is implemented and tested.

## Next required engineering steps
1. Select a Vietnamese model whose license permits redistribution/use in this app.
2. Verify its architecture is supported by the chosen Sherpa-ONNX Android runtime version.
3. Implement the matching native Kotlin wrapper and audio synthesis path.
4. Add model download/import with checksum verification and storage-space checks.
5. Test male/female speaker IDs and emotion/style controls. If the model has no emotion conditioning, do not claim real emotion control; use prosody adjustments only as a fallback.
6. Route generated PCM into `AudioPlayerManager` using the delayed media timeline.
