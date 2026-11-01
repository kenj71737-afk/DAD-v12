package com.alad.app.core.delay

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File

/** Captures the user-selected MediaProjection surface as short H.264/MP4 video-only segments.
 * Audio is intentionally not recorded here: AudioPlaybackCapture remains a separate live
 * branch to Gemini. A synchronized delayed audio renderer/mux path is a separate next stage.
 */
class DelayedScreenVideoCapture(private val context: Context) {
    companion object { private const val TAG = "ALADVideoCapture"; private const val SEGMENT_MS = 2000L }
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var running = false
    private var segmentStart = 0L
    private var outputDir: File? = null
    private val rotate = object : Runnable { override fun run() { if (!running) return; rotateSegment(); handler.postDelayed(this, SEGMENT_MS) } }

    fun start(mediaProjection: MediaProjection): Boolean {
        if (running) return true
        projection = mediaProjection
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = (metrics.widthPixels / 2).coerceAtLeast(640).let { it - it % 2 }
        val height = (metrics.heightPixels / 2).coerceAtLeast(360).let { it - it % 2 }
        outputDir = File(context.cacheDir, "delayed_video").apply { mkdirs() }
        return try {
            val first = createRecorder(width, height)
            display = mediaProjection.createVirtualDisplay("ALAD-Delayed-Video", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, first.surface, null, handler)
            recorder = first
            segmentStart = SystemClock.elapsedRealtime()
            running = true
            handler.postDelayed(rotate, SEGMENT_MS)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start video capture", t)
            runCatching { recorder?.release() }; recorder = null
            runCatching { display?.release() }; display = null
            false
        }
    }

    private fun createRecorder(width: Int, height: Int): MediaRecorder {
        val out = File(outputDir, "seg_${SystemClock.elapsedRealtime()}.mp4")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setOutputFile(out.absolutePath)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(width, height)
        r.setVideoFrameRate(24)
        r.setVideoEncodingBitRate(2_000_000)
        r.setOnErrorListener { _, what, extra -> Log.e(TAG, "MediaRecorder error $what/$extra") }
        r.prepare(); r.start()
        pendingFile = out
        return r
    }

    private var pendingFile: File? = null
    private fun rotateSegment() {
        val old = recorder ?: return
        val end = SystemClock.elapsedRealtime()
        val file = pendingFile
        runCatching { old.stop() }.onFailure { Log.w(TAG, "Discarding incomplete segment", it); file?.delete() }
        runCatching { old.reset(); old.release() }
        recorder = null
        if (file != null && file.exists() && file.length() > 4096L) {
            DelayedVideoSegmentStore.add(DelayedVideoSegmentStore.Segment(file, segmentStart, end))
        }
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val w = (metrics.widthPixels / 2).coerceAtLeast(640).let { it - it % 2 }
            val h = (metrics.heightPixels / 2).coerceAtLeast(360).let { it - it % 2 }
            val next = createRecorder(w, h)
            display?.setSurface(next.surface)
            recorder = next
            segmentStart = end
        } catch (t: Throwable) {
            Log.e(TAG, "Video segment rotation failed", t)
            running = false
            runCatching { display?.setSurface(null) }
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(rotate)
        val end = SystemClock.elapsedRealtime()
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
            pendingFile?.let { if (it.exists() && it.length() > 4096L) DelayedVideoSegmentStore.add(DelayedVideoSegmentStore.Segment(it, segmentStart, end)) else it.delete() }
        }
        recorder = null; pendingFile = null
        runCatching { display?.setSurface(null); display?.release() }
        display = null; projection = null
    }
}
