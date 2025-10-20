package com.yuri.onscripter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yuri.onscripter.MainActivity.SHAREDPREF_GAMECONFIG
import com.yuri.onscripter.MainActivity.SHAREDPREF_GAMEURI
import com.yuri.onscripter.TyActivity.Companion.GITHUB_URL
import com.yuri.onscripter.TyActivity.Companion.TYRANOR_URL
import java.util.ArrayList

class TyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TyActivity"
        const val GITHUB_URL = "https://github.com/tyranor2/OnscripterYuri-Ty"
        const val TYRANOR_URL = "https://github.com/tyranor2/TyranorRelease/releases"
    }

    /**
     * 1. Android 11+ 储存权限回调
     */
    private val requestAllFilePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast("已获取文件访问权限")
                } else {
                    Toast("权限未授予")
                    MaterialAlertDialogBuilder(this)
                        .setTitle("提示")
                        .setMessage("文件访问权限获取失败，将无法读取游戏资源")
                        .setPositiveButton("重试") { _, _ ->
                            onLaunch()
                        }.setNegativeButton("退出") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }

    /**
     * 2. Android 10以下储存权限回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: $requestCode")
        if (requestCode == CODE_RW_PERMISSION) {
            if (hasRWPermission(this)) {
                Toast("已获得文件访问权限")
            }
            else {
                Toast("储存权限未授予")
                MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("文件访问权限获取失败，将无法读取游戏资源")
                    .setPositiveButton("重试") { _, _ ->
                        onLaunch()
                    }.setNegativeButton("退出") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * 3. Uri授权回调
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: $requestCode")
        if (requestCode == CODE_OPEN_DIR) {
            if (resultCode == RESULT_OK) {
                data?.data?.persist(this) // 保存Uri
                onLaunch()
            } else {
                Toast("游戏文件夹权限未授予")
                MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("文件访问权限获取失败，将无法读取游戏资源")
                    .setPositiveButton("重试") { _, _ ->
                        onLaunch()
                    }.setNegativeButton("退出") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onLaunch()

        setContent {
            TyTheme {
                val isShowMainPage by showMainPage
                Log.d(TAG, "onCreate: $isShowMainPage")
                if (isShowMainPage)
                    Main()
                else
                    LoadingProgress()
            }
        }
    }

    /**
     * 请求储存权限
     */
    fun requestPermission(onGrant: () -> Unit) {
        requestAllFilesPermission(this, requestAllFilePermissionLauncher, onGrant)
    }

    /**
     * 请求 Tyranor Uri 权限
     */
    fun requestTyranorPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle("提示")
            .setView(ComposeView(this).apply {
                setContent {
                    TyTheme(
                        Color.Transparent
                    ) {
                        TyGrantHelpPage()
                    }
                }
            })
            .setPositiveButton("授权") { _,_ ->
                tyUri?.openDirectory(this)
            }.setNegativeButton("退出") { _,_ ->
                finish()
            }.setCancelable(false)
            .show()
    }

    private var args: ArrayList<String?>? = null
    private var ignoreCutout: Boolean = true
    private var realUri: Uri? = null
    private var tyUri: Uri? = null
    private val showMainPage = mutableStateOf(false)

    /**
     * 处理启动参数
     */
    private fun onLaunch() {
        runCatching {
            args = intent.getStringArrayListExtra(SHAREDPREF_GAMECONFIG)
            ignoreCutout = intent.getBooleanExtra("ignorecutout", true)
            realUri = intent.getStringExtra(SHAREDPREF_GAMEURI)?.toUri()
            tyUri = realUri // intent.data
            val useUri = tyUri != null
            if (args.isNullOrEmpty()) {
                Toast("请从Tyranor打开游戏")
                showMainPage.value = true
            } else {
                if (useUri) { // use Uri
                    if (!tyUri!!.isPersisted(this)) {
                        Log.d(TAG, "onLaunch: 未授权 $tyUri")
                        requestTyranorPermission()
                    } else { // Uri 已授权
                        Log.d(TAG, "onLaunch: 已授权")
                        val gameUri = tyUri?.treeDocumentFile(this)?.findFile("currentGame")?.uri
                        tyUri = gameUri!!
                        launchGame()
                        showMainPage.value = true
                    }
                } else { // use File
                    requestPermission(
                        onGrant = {
                            launchGame(isUri = false)
                            showMainPage.value = true
                        }
                    )
                }
                Log.d(TAG, "onLaunch: ${args}")
            }
        }.onFailure {
            showMainPage.value = true
            MaterialAlertDialogBuilder(this)
                .setTitle("错误")
                .setMessage("启动时遇到错误，你可以前往github反馈问题\n$it")
                .setPositiveButton("关闭") { _,_ ->
                    finish()
                }.setNegativeButton("反馈") { _,_ ->
                    GITHUB_URL.openUrl(this)
                }.show()
        }
    }

    /**
     * 启动游戏
     */
    fun launchGame(isUri: Boolean = true) {
        Log.d(TAG, "launchGame: $tyUri")
        val intent = Intent(this, ONScripter::class.java).apply {
            putExtra(SHAREDPREF_GAMECONFIG, args)
            putExtra("ignorecutout", ignoreCutout)
            if (isUri) putExtra(SHAREDPREF_GAMEURI, tyUri.toString())
        }
        startActivity(intent)
        finish()
    }
}

val openSrcNotice = """
    <br>
    本软件基于 <a href='https://github.com/YuriSizuku/OnscripterYuri'>https://github.com/YuriSizuku/OnscripterYuri</a> 项目修改，该项目遵循 GNU General Public License v2（GPL v2）协议。<br>
    本修改版本由 <a href='${TYRANOR_URL}'>Tyranor</a> 开发，主要改动包括：<br>
    <br>
    1. 增加了与 Tyranor 模拟器的启动兼容；<br>
    2. 对部分界面和功能进行了改进与调整。<br>
    <br>
    本修改版本的完整源代码可点击下方“源代码”按钮获取：<br>
<br>
    本应用同样遵循 GNU General Public License v2 协议发布。<br>
    有关许可证详情，请参阅：<a href='https://www.gnu.org/licenses/old-licenses/gpl-2.0.html'>GPL v2 License</a>
""".trimIndent()

@Composable
private fun Main(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Scaffold { innerPadding ->
        Column(modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(modifier = Modifier.size(80.dp), painter = painterResource(R.drawable.ons_cute), contentDescription = null)
                Spacer(Modifier.height(8.dp))
                Text("适用于 Tyranor 的 ONS插件", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(context.versionName.toString(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                text = AnnotatedString.fromHtml(openSrcNotice, linkStyles = TextLinkStyles().Default),
                fontSize = 13.sp
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.width(IntrinsicSize.Max)
                ) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, MainActivity::class.java))
                            (context as Activity).finish()
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Text("进入原版主页")
                    }

                    Button (
                        onClick = {
                            GITHUB_URL.openUrl(context)
                        },
                        Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.github),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("源代码")
                    }
                }
            }
        }
    }
}

@Composable
fun TyTheme(
    backgroundColor: Color? = null,
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current as Activity
    LaunchedEffect(Unit) {
        activity.window.navigationBarColor = Color.Transparent.toArgb()
        activity.window.statusBarColor = Color.Transparent.toArgb()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars  = false
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        val bgColor = MaterialTheme.colorScheme.surface
        Surface(
            color = backgroundColor ?: bgColor,
            contentColor = contentColorFor(bgColor)
        ) {
            content()
        }
    }
}

@Composable
fun TyGrantHelpPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text("\n请先授权读取Tyranor文件\n请滑动查看，按照下方图示进行授权\n")
        Text("注意：部分设备可能无法授权，请将游戏移出外部存储卡，使用普通方式运行游戏")
        Text("第一步：")
        Image(painter = painterResource(R.drawable.ty_step1), null)
        Text("第二步：")
        Image(painter = painterResource(R.drawable.ty_step2), null)
        Text("第三步：")
        Image(painter = painterResource(R.drawable.ty_step3), null)
    }
}

@Composable
fun LoadingProgress(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}