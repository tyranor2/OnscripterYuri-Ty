package com.yuri.onscripter

import android.R
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private lateinit var skipButton: ImageView

    private var lastClickTime = 0L
    private val DOUBLE_CLICK_INTERVAL = 300L

    // 用于控制跳过按钮自动隐藏的任务
    private val HIDE_SKIP_DELAY = 3000L  // 3 秒钟

    // 用于 postDelayed 隐藏按钮的 Runnable
    private val hideSkipRunnable = Runnable {
        skipButton.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 根布局
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // PlayerView（用于展示视频画面）
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false  // 关闭默认控制条
        }
        root.addView(playerView)

        // 跳过按钮
        skipButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_media_next)
            visibility = View.GONE
        }
        val skipLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = dpToPx(16)
            topMargin = dpToPx(16)
        }
        skipButton.layoutParams = skipLp
        root.addView(skipButton)

        setContentView(root)

        // 获取视频 URI
        val uri = intent.getParcelableExtra<Uri>("video_uri")
        if (uri == null) {
            Toast.makeText(this, "video uri required", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        initPlayer(uri)

        // 单击逻辑：每次单击都显示跳过按钮，并在 3 秒后隐藏
        playerView.setOnClickListener {
            onSingleClickShowSkipOnly()
        }

        skipButton.setOnClickListener {
            finish()
        }

        fullscreen()
    }

    private fun initPlayer(uri: Uri) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo

        val mediaItem = MediaItem.fromUri(uri)
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    finish()
                }
            }
        })
    }

    private fun onSingleClickShowSkipOnly() {
        // 显示跳过按钮
        skipButton.visibility = View.VISIBLE
        // 先移除之前还没执行的隐藏任务（防止叠加）
        skipButton.removeCallbacks(hideSkipRunnable)
        // 延迟 3 秒后隐藏
        skipButton.postDelayed(hideSkipRunnable, HIDE_SKIP_DELAY)
    }

    // 辅助：把 dp 转成 px
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
        // 取消隐藏任务
        skipButton.removeCallbacks(hideSkipRunnable)
    }

    private fun fullscreen() {
        // 忽略刘海（cutout）设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // 设定不让系统装饰适配窗口
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.let {
            // 隐藏状态栏 + 导航栏
            it.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            // 设置系统条的行为：轻扫时短暂显示
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // hide title
        supportActionBar?.hide()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
