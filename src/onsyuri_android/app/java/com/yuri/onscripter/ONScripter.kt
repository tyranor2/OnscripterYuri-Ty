package com.yuri.onscripter

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import org.libsdl.app.SDLActivity
import java.io.File
import java.nio.charset.StandardCharsets

class ONScripter : SDLActivity() {
    private var m_onsargs: ArrayList<String?>? = null

    // use this to judge whether to use saf
    private var m_onsbase: DocumentFile? = null
    private var m_ignorecutout = false

    companion object {
        private const val TAG = "ONScripter"

        // yuri版本(assets内的文件夹路径)
        const val yuriVersion = "Yuri_0.7.6beta1"
    }

    // for onsyuri c code
    private external fun nativeInitJavaCallbacks(): Int

    fun getFD(pathbyte: ByteArray?, mode: Int): Int {
        if (m_onsbase == null) return -1
        // Log.d(TAG, "getFD: " + new String(pathbyte, StandardCharsets.UTF_8) + " mode:" + mode);
        val path = kotlin.text.String(pathbyte!!, StandardCharsets.UTF_8)
        val safmode = if (mode == 0) "r" else "w"
        val fd = SafFile.getFdSaf(this, m_onsbase!!, path, safmode)
        // Log.d(TAG, "getFD: fd=" + fd);
        // Log.i("## onsyuri_android", String.format("getFD path=%s, mode=%d, fd=%d", new String(path), mode, fd));
        return fd
    }

    fun mkdir(pathbyte: ByteArray?): Int {
        if (m_onsbase == null) return -1
        val path = kotlin.text.String(pathbyte!!, StandardCharsets.UTF_8)
        val doc = SafFile.mkdirsSaf(m_onsbase!!, path)
        if (doc == null) return -1
        else return 0
    }

    fun playVideo(uri: Uri) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("video_uri", uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    fun playVideo(pathbyte: ByteArray?) {
        val path = String(pathbyte!!, StandardCharsets.UTF_8).replace('\\', '/')
        Log.i("## onsyuri_android", "playVideo: $path")
        
        try {
            val uri = if (m_onsbase == null) {
                // 使用普通文件系统访问
                resolveVideoFileUri(path)
            } else {
                // 使用 SAF (Storage Access Framework)
                resolveVideoDocumentUri(path)
            }
            
            if (uri != null) {
                Log.i("## onsyuri_android", "playVideo uri: $uri")
                playVideo(uri)
            } else {
                Log.w("## onsyuri_android", "playVideo: video file not found for path: $path")
            }
        } catch (e: Exception) {
            Log.e("## onsyuri_android", "playVideo error: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }
    
    /**
     * 从普通文件系统解析视频文件URI
     */
    private fun resolveVideoFileUri(path: String): Uri? {
        var file = File(path)
        
        // 如果原始文件不存在,尝试查找 .mp4 格式
        if (!file.exists()) {
            val mp4File = File(file.parentFile, file.nameWithoutExtension + ".mp4")
            if (!mp4File.exists()) {
                Log.i("## onsyuri_android", "video file not found: ${file.name} or ${mp4File.name}")
                return null
            }
            file = mp4File
        }
        
        return FileProvider.getUriForFile(this, "$packageName.file_provider", file)
    }
    
    /**
     * 从 DocumentFile 解析视频文件URI (SAF)
     */
    private fun resolveVideoDocumentUri(path: String): Uri? {
        val paths = path.trim { it == '/' }.split("/")
        
        // 先尝试查找原始文件
        var doc = findDocumentByPath(paths)
        
        // 如果找不到,尝试查找 .mp4 格式
        if (doc == null) {
            Log.i("## onsyuri_android", "original video not found, looking for mp4 format...")
            val mp4Paths = paths.toMutableList().apply {
                this[size - 1] = File(this[size - 1]).nameWithoutExtension + ".mp4"
            }
            doc = findDocumentByPath(mp4Paths)
        }
        
        if (doc == null) {
            Log.i("## onsyuri_android", "video not found in document tree: $path")
            return null
        }
        
        return doc.uri
    }

    /**
     * 根据路径列表查找 DocumentFile
     */
    private fun findDocumentByPath(paths: List<String>): DocumentFile? {
        var doc: DocumentFile? = m_onsbase
        for (segment in paths) {
            doc = doc?.findFileIgnoreCase(segment)
            if (doc == null) break
        }
        return doc
    }

    override fun getLibraries(): Array<String?> {
        return arrayOf(
            "lua",
            "jpeg",
            "bz2",
            "SDL2",
            "SDL2_image",
            "SDL2_mixer",
            "SDL2_ttf",
            "onsyuri",
        )
    }

    /**
     * 使用dyso加载动态库
     */
    override fun loadLibraries() {
        for (lib in libraries) {
            System.loadLibrary(lib!!)
        }
        runCatching {
            System.loadLibrary("ONSPatch")
        }
    }

    protected override fun getArguments(): Array<String?> {
        return m_onsargs!!.toTypedArray()
    }

    // override activity functions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        m_onsargs = intent.getStringArrayListExtra(MainActivity.SHAREDPREF_GAMECONFIG)
        m_ignorecutout = intent.getBooleanExtra("ignorecutout", true)
        val uristr = intent.getStringExtra(MainActivity.SHAREDPREF_GAMEURI)
        // Log.d(TAG, "onCreate: uristr=" + uristr);
        if (uristr != null) {
            val uri = uristr.toUri()
            m_onsbase = DocumentFile.fromTreeUri(this, uri)
        } else {
            m_onsbase = null
        }

        nativeInitJavaCallbacks()
        this.fullscreen()
    }

    @SuppressLint("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed: 手动ESC")
        onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE)
        onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE)
    }

    override fun onResume() {
        super.onResume()
        this.fullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) this.fullscreen()
    }

    private fun fullscreen() {
        // 忽略刘海（cutout）设置
        if (m_ignorecutout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

        // hide title for SDL
        supportActionBar?.hide()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    /**
     * 重新启用返回事件
     */
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                try {
                    onBackPressed()
                } catch (ignored: Exception) {
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

