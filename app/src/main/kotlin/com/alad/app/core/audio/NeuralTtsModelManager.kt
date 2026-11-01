package com.alad.app.core.audio

import android.content.Context
import java.io.File

/**
 * Local model-pack validation for an on-device Vietnamese Neural TTS backend.
 *
 * This class deliberately does not claim that a model is installed merely because
 * the app has the Sherpa-ONNX runtime. A compatible Vietnamese model pack must be
 * supplied separately and must match the selected backend's expected files.
 *
 * Expected app-private layout:
 * files/neural_tts/vi_vn/<voiceId>/
 *   model.onnx
 *   tokens.txt
 *   lexicon.txt       (optional; model-dependent)
 *   voices.bin        (optional; multi-speaker model-dependent)
 *   model.json        (metadata written by the model-pack installer)
 */
class NeuralTtsModelManager(context: Context) {
    data class ModelPack(
        val voiceId: String,
        val modelFile: File,
        val tokensFile: File,
        val lexiconFile: File?,
        val voicesFile: File?,
        val metadataFile: File
    )

    sealed class Validation {
        data class Ready(val pack: ModelPack) : Validation()
        data class Missing(val message: String) : Validation()
        data class Invalid(val message: String) : Validation()
    }

    private val modelRoot = File(context.applicationContext.filesDir, "neural_tts/vi_vn")

    fun validate(voiceId: String): Validation {
        if (!voiceId.matches(Regex("[A-Za-z0-9_-]{1,48}"))) {
            return Validation.Invalid("Invalid voice model identifier")
        }
        val dir = File(modelRoot, voiceId)
        if (!dir.exists()) return Validation.Missing("Vietnamese Neural TTS model is not installed")
        val model = File(dir, "model.onnx")
        val tokens = File(dir, "tokens.txt")
        val metadata = File(dir, "model.json")
        if (!model.isFile || model.length() == 0L) {
            return Validation.Invalid("Missing or empty model.onnx")
        }
        if (!tokens.isFile || tokens.length() == 0L) {
            return Validation.Invalid("Missing or empty tokens.txt")
        }
        if (!metadata.isFile || metadata.length() == 0L) {
            return Validation.Invalid("Missing model.json metadata")
        }
        return Validation.Ready(
            ModelPack(
                voiceId = voiceId,
                modelFile = model,
                tokensFile = tokens,
                lexiconFile = File(dir, "lexicon.txt").takeIf { it.isFile },
                voicesFile = File(dir, "voices.bin").takeIf { it.isFile },
                metadataFile = metadata
            )
        )
    }

    fun installedVoiceIds(): List<String> =
        modelRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()
}
