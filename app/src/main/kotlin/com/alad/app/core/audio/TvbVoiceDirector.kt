package com.alad.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * Ordered Vietnamese voice director for Gemini speaker-tagged text.
 *
 * NOTE: This is currently an Android TTS fallback, not Neural TTS and not a
 * recreation of any particular actor. Each utterance is synthesized serially
 * so changing the male/female profile cannot overtake a queued utterance.
 */
class TvbVoiceDirector(context: Context) : TextToSpeech.OnInitListener {
    enum class Gender { MALE, FEMALE }
    data class Segment(val speakerId: String, val gender: Gender, val text: String)

    companion object {
        private const val TAG = "TvbVoiceDirector"
        private val MARKER = Regex("\\[\\[TVB:([A-Za-z0-9_-]{1,32}):(M|F)\\]\\]", RegexOption.IGNORE_CASE)

        fun parseTaggedSegment(raw: String): Segment? {
            val match = MARKER.find(raw.trim()) ?: return null
            val body = raw.trim().substring(match.range.last + 1).trim()
            if (body.isBlank()) return null
            val gender = if (match.groupValues[2].equals("M", true)) Gender.MALE else Gender.FEMALE
            return Segment(match.groupValues[1], gender, body)
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Segment>()
    private val streamLock = Any()
    private val queueLock = Any()
    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    private var speaking = false
    private val streamBuffer = StringBuilder()
    private var activeSpeakerId: String? = null
    private var activeGender: Gender? = null
    private val streamMarker = MARKER

    init { engine = TextToSpeech(appContext, this) }

    /** Buffers fragmented Gemini deltas; markers may span WebSocket events. */
    fun consumeTextDelta(delta: String): List<Segment> = synchronized(streamLock) {
        if (delta.isNotEmpty()) streamBuffer.append(delta)
        val emitted = mutableListOf<Segment>()
        while (true) {
            val snapshot = streamBuffer.toString()
            val marker = streamMarker.find(snapshot)
            if (marker == null) {
                // Preserve a possible partial marker at the end of the buffer.
                if (streamBuffer.length > 4096) {
                    val keep = minOf(128, streamBuffer.length)
                    streamBuffer.delete(0, streamBuffer.length - keep)
                }
                break
            }
            val previousSpeaker = activeSpeakerId
            val previousGender = activeGender
            if (previousSpeaker != null && previousGender != null) {
                val body = snapshot.substring(0, marker.range.first).trim()
                if (body.isNotEmpty()) emitted += Segment(previousSpeaker, previousGender, body)
            }
            streamBuffer.delete(0, marker.range.last + 1)
            activeSpeakerId = marker.groupValues[1]
            activeGender = if (marker.groupValues[2].equals("M", true)) Gender.MALE else Gender.FEMALE
        }
        emitted
    }

    /** Emits the final phrase at Gemini turnComplete. */
    fun flushTextStream(): List<Segment> = synchronized(streamLock) {
        val body = streamBuffer.toString().trim()
        val id = activeSpeakerId
        val gender = activeGender
        streamBuffer.setLength(0)
        activeSpeakerId = null
        activeGender = null
        if (body.isNotEmpty() && id != null && gender != null) listOf(Segment(id, gender, body)) else emptyList()
    }

    fun resetTextStream() = synchronized(streamLock) {
        streamBuffer.setLength(0)
        activeSpeakerId = null
        activeGender = null
    }

    override fun onInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Android TTS initialization failed: $status")
            return
        }
        val result = tts.setLanguage(Locale("vi", "VN"))
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = mainHandler.post { finishCurrentUtterance() }.let { Unit }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = mainHandler.post { finishCurrentUtterance() }.let { Unit }
            override fun onError(utteranceId: String?, errorCode: Int) = mainHandler.post { finishCurrentUtterance() }.let { Unit }
        })
        Log.i(TAG, "Vietnamese Android TTS ready=$ready")
        if (ready) mainHandler.post { startNextUtterance() }
    }

    /** Accepts a tagged line and queues it in arrival order. */
    fun speakTagged(raw: String): Boolean {
        val segment = parseTaggedSegment(raw) ?: return false
        return speakSegment(segment)
    }

    fun speakSegment(segment: Segment): Boolean {
        if (!ready || closed || segment.text.isBlank()) return false
        synchronized(queueLock) { queue.addLast(segment) }
        mainHandler.post { startNextUtterance() }
        return true
    }

    private fun startNextUtterance() {
        if (!ready || closed || speaking) return
        val next = synchronized(queueLock) { if (queue.isEmpty()) null else queue.removeFirst() } ?: return
        val tts = engine ?: return
        speaking = true
        // These settings are applied only when this utterance becomes active.
        // QUEUE_FLUSH prevents a previous speaker's settings from affecting it.
        val pitch = if (next.gender == Gender.MALE) 0.88f else 1.07f
        val rate = if (next.gender == Gender.MALE) 0.92f else 0.97f
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
        val utteranceId = "alad-${next.speakerId}-${UUID.randomUUID()}"
        val result = tts.speak(next.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS rejected utterance for speaker=${next.speakerId}")
            speaking = false
            mainHandler.post { startNextUtterance() }
        }
    }

    private fun finishCurrentUtterance() {
        speaking = false
        startNextUtterance()
    }

    fun stop() {
        mainHandler.post {
            synchronized(queueLock) { queue.clear() }
            speaking = false
            engine?.stop()
        }
    }

    fun shutdown() {
        closed = true
        ready = false
        mainHandler.post {
            synchronized(queueLock) { queue.clear() }
            speaking = false
            engine?.stop()
            engine?.shutdown()
            engine = null
        }
        resetTextStream()
    }
}
