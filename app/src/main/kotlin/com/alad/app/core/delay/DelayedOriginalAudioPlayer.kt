package com.alad.app.core.delay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays a second copy of captured source PCM after the same delay used by the
 * delayed screen-video segments. The caller MUST forward PCM to Gemini first,
 * then call enqueue(). Uses USAGE_ASSISTANT so AudioPlaybackCapture's MEDIA /
 * GAME / UNKNOWN filter does not recapture this delayed monitor and create a loop.
 *
 * Timing is based on elapsedRealtime at callback arrival. For sample-accurate A/V
 * sync, capture timestamps must later be taken from AudioRecord.getTimestamp()
 * and video segment PTS must be mapped to the same clock.
 */
class DelayedOriginalAudioPlayer(
    private val delayMs: Long = 5_000L,
    private val maxQueuedBytes: Int = 16_000 * 2 * 10
) {
    companion object {
        private const val TAG = "ALADDelayedAudio"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private data class Chunk(
        val dueAtMs: Long,
        val sequence: Long,
        val pcm: ByteArray
    ) : Comparable<Chunk> {
        override fun compareTo(other: Chunk): Int =
            compareValuesBy(this, other, Chunk::dueAtMs, Chunk::sequence)
    }

    private val queue = PriorityBlockingQueue<Chunk>()
    private val running = AtomicBoolean(false)
    @Volatile private var queuedBytes = 0
    private var sequence = 0L
    @Volatile private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running.get()) return
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
        if (minBuffer <= 0) {
            Log.e(TAG, "Invalid AudioTrack min buffer: $minBuffer")
            return
        }
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_MASK)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer * 2, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed")
            audioTrack.release()
            return
        }
        track = audioTrack
        running.set(true)
        audioTrack.play()
        worker = Thread({ playbackLoop() }, "ALAD-Delayed-Original-Audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Enqueue only AFTER the same PCM block has been sent to Gemini. */
    @Synchronized
    fun enqueue(pcm: ByteArray, capturedAtElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (!running.get() || pcm.isEmpty()) return
        val copy = pcm.copyOf()
        // Keep memory bounded; drop oldest pending chunks if the consumer falls behind.
        while (queuedBytes + copy.size > maxQueuedBytes) {
            val dropped = queue.poll() ?: break
            queuedBytes -= dropped.pcm.size
        }
        queue.offer(Chunk(capturedAtElapsedMs + delayMs, sequence++, copy))
        queuedBytes += copy.size
    }

    private fun playbackLoop() {
        while (running.get()) {
            try {
                val chunk = queue.peek()
                if (chunk == null) {
                    Thread.sleep(5)
                    continue
                }
                val waitMs = chunk.dueAtMs - SystemClock.elapsedRealtime()
                if (waitMs > 0) {
                    Thread.sleep(minOf(waitMs, 20L))
                    continue
                }
                val ready = queue.poll() ?: continue
                synchronized(this) { queuedBytes = (queuedBytes - ready.pcm.size).coerceAtLeast(0) }
                val audioTrack = track ?: break
                var offset = 0
                while (offset < ready.pcm.size && running.get()) {
                    val written = audioTrack.write(
                        ready.pcm, offset, ready.pcm.size - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written <= 0) {
                        Log.w(TAG, "AudioTrack write failed: $written")
                        break
                    }
                    offset += written
                }
            } catch (interrupted: InterruptedException) {
                break
            } catch (t: Throwable) {
                Log.e(TAG, "Delayed audio playback failed", t)
            }
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
        queue.clear()
        queuedBytes = 0
        track?.let { audio ->
            runCatching { audio.pause(); audio.flush(); audio.stop() }
            runCatching { audio.release() }
        }
        track = null
    }
}
