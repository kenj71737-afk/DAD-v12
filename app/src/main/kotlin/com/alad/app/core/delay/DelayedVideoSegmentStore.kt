package com.alad.app.core.delay

import java.io.File
import java.util.ArrayDeque

/** In-process index of finalized video-only MP4 segments captured from the selected app/window. */
object DelayedVideoSegmentStore {
    data class Segment(val file: File, val startElapsedMs: Long, val endElapsedMs: Long)
    private val segments = ArrayDeque<Segment>()
    @Synchronized fun add(segment: Segment, maxSegments: Int = 20) {
        segments.addLast(segment)
        while (segments.size > maxSegments) {
            val old = segments.removeFirst()
            runCatching { old.file.delete() }
        }
    }
    @Synchronized fun nextPlayable(nowElapsedMs: Long, delayMs: Long, afterEndMs: Long): Segment? =
        segments.firstOrNull { it.endElapsedMs <= nowElapsedMs - delayMs && it.endElapsedMs > afterEndMs && it.file.exists() && it.file.length() > 4096L }
    @Synchronized fun latestEndMs(): Long = segments.lastOrNull()?.endElapsedMs ?: 0L
    @Synchronized fun clear() { segments.forEach { runCatching { it.file.delete() } }; segments.clear() }
}
