package com.alad.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView
import com.alad.app.core.delay.DelayedVideoSegmentStore

/** Full-screen delayed video-only preview. Original source audio continues to Gemini live. */
class DelayedFullscreenActivity : Activity() {
    private lateinit var video: VideoView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastEndMs = 0L
    private var segmentInProgress = false
    private val poll = object : Runnable { override fun run() { playNextIfReady(); handler.postDelayed(this, 250L) } }
    private val delayMs by lazy { intent.getLongExtra(EXTRA_DELAY_MS, 5000L).coerceIn(1000L, 10000L) }
    companion object { const val EXTRA_DELAY_MS = "extra_delay_ms" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        video = VideoView(this)
        status = TextView(this).apply { text = "ALAD đang chờ video trễ…"; setTextColor(0xffffffff.toInt()); textSize = 15f; setPadding(24, 24, 24, 24) }
        val root = FrameLayout(this).apply { setBackgroundColor(0xff000000.toInt()); addView(video, FrameLayout.LayoutParams(-1, -1)); addView(status, FrameLayout.LayoutParams(-1, -2)) }
        setContentView(root)
        handler.post(poll)
    }

    private fun playNextIfReady() {
        if (segmentInProgress) return
        val segment = DelayedVideoSegmentStore.nextPlayable(SystemClock.elapsedRealtime(), delayMs, lastEndMs) ?: return
        lastEndMs = segment.endElapsedMs
        segmentInProgress = true
        status.text = "Hình và tiếng gốc đang phát trễ ${(delayMs / 1000.0)} giây"
        video.setVideoPath(segment.file.absolutePath)
        video.setOnPreparedListener { mp -> mp.isLooping = false; video.start() }
        video.setOnCompletionListener {
            segmentInProgress = false
            status.text = "Đang chờ đoạn tiếp theo…"
            playNextIfReady()
        }
        video.setOnErrorListener { _, what, extra ->
            segmentInProgress = false
            status.text = "Không phát được đoạn video ($what/$extra)"
            true
        }
    }

    override fun onDestroy() { handler.removeCallbacks(poll); runCatching { video.stopPlayback() }; super.onDestroy() }
}
