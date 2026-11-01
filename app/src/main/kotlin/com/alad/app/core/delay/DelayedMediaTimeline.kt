package com.alad.app.core.delay

import java.util.ArrayDeque

/**
 * Timestamp-based bounded buffer shared by the delayed video and delayed-audio paths.
 * Source audio must be forwarded to Gemini BEFORE it is added to this timeline.
 * Timestamps are monotonic elapsed-realtime milliseconds, not wall-clock time.
 *
 * This class is deliberately codec-agnostic: video frames should be encoded access units
 * (or short independently decodable segments), not raw full-resolution Bitmaps.
 */
class DelayedMediaTimeline(
    private val windowMs: Long = 10_000L,
    private val maxBytes: Long = 48L * 1024L * 1024L
) {
    data class Packet(
        val timestampMs: Long,
        val mimeType: String,
        val payload: ByteArray,
        val isKeyFrame: Boolean = false
    )

    private val packets = ArrayDeque<Packet>()
    private var retainedBytes = 0L
    private var lastTimestampMs = Long.MIN_VALUE

    @Synchronized
    fun offer(packet: Packet): Boolean {
        if (packet.payload.isEmpty() || packet.timestampMs < lastTimestampMs) return false
        if (packet.payload.size.toLong() > maxBytes) return false
        packets.addLast(packet.copy(payload = packet.payload.copyOf()))
        retainedBytes += packet.payload.size
        lastTimestampMs = packet.timestampMs
        evict(packet.timestampMs)
        return true
    }

    /** Returns the newest packet at or before sourceNow-delay, without consuming it. */
    @Synchronized
    fun peekDue(sourceNowMs: Long, delayMs: Long, mimeType: String? = null): Packet? {
        val target = sourceNowMs - delayMs.coerceAtLeast(0L)
        return packets.asSequence()
            .filter { it.timestampMs <= target && (mimeType == null || it.mimeType == mimeType) }
            .lastOrNull()
    }

    /** Removes and returns packets up to the delayed presentation timestamp. */
    @Synchronized
    fun drainDue(sourceNowMs: Long, delayMs: Long, mimeType: String? = null): List<Packet> {
        val target = sourceNowMs - delayMs.coerceAtLeast(0L)
        val out = ArrayList<Packet>()
        val keep = ArrayDeque<Packet>()
        while (packets.isNotEmpty()) {
            val packet = packets.removeFirst()
            if (packet.timestampMs <= target && (mimeType == null || packet.mimeType == mimeType)) {
                out += packet
                retainedBytes -= packet.payload.size
            } else {
                keep.addLast(packet)
            }
        }
        packets.addAll(keep)
        return out
    }

    @Synchronized
    fun clear() {
        packets.clear()
        retainedBytes = 0L
        lastTimestampMs = Long.MIN_VALUE
    }

    @Synchronized
    fun stats(): Stats = Stats(packets.size, retainedBytes, packets.firstOrNull()?.timestampMs, packets.lastOrNull()?.timestampMs)

    data class Stats(val packetCount: Int, val bytes: Long, val oldestTimestampMs: Long?, val newestTimestampMs: Long?)

    private fun evict(newestMs: Long) {
        val cutoff = newestMs - windowMs
        while (packets.isNotEmpty() &&
            (packets.first().timestampMs < cutoff || retainedBytes > maxBytes)) {
            retainedBytes -= packets.removeFirst().payload.size
        }
    }
}
