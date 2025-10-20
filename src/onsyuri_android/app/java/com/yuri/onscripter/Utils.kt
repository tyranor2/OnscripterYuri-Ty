package com.yuri.onscripter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

private const val TAG = "Utils"

const val CODE_RW_PERMISSION = 0
const val CODE_OPEN_DIR = 1

fun Context.Toast(text: String, isLong: Boolean = false) {
    Toast.makeText(this.applicationContext, text, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun hasRWPermission(context: Context): Boolean {
    val read = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    val write = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    return read && write
}

/**
 * 请求储存权限(Android 6-10)/所有文件访问权限(Android 11+)
 */
fun requestAllFilesPermission(context: Context, launcher: ActivityResultLauncher<Intent>, onGrant: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:" + context.packageName).toUri()
                launcher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                launcher.launch(intent)
            }
        } else {
            onGrant()
            Log.d(TAG, "requestAllFilesPermission: 已拥有所有文件访问权限")
        }
    } else {
        // Android 10及以下用普通方式
        if (!hasRWPermission(context)) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                CODE_RW_PERMISSION
            )
        } else {
            Log.d(TAG, "requestAllFilesPermission: 已拥有文件读写权限")
            onGrant()
        }
    }
}

/**
 * 打开Uri所在路径(Android 8以上可直达，以下只能手动定位过去)
 */
fun Uri.openDirectory(activity: Activity) {
    val uri = this
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        }*/
    }
    activity.startActivityForResult(intent, CODE_OPEN_DIR)
}

/**
 * Uri是否已授权
 */
fun Uri.isPersisted(context: Context): Boolean {
    val perms = context.contentResolver.persistedUriPermissions
    Log.d(TAG, "isPersisted: ${perms.map { it.uri.toString() }.joinToString { "\n" }}")
    return perms.any { it.uri == this && it.isReadPermission }
}

/**
 * 储存已授权Uri
 */
fun Uri.persist(context: Context) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    Log.d(TAG, "persist: ${this}")
    context.contentResolver.takePersistableUriPermission(this, flags)
}

fun String.openUrl(context: Context) = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, this.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}


fun File.toUri(context: Context) = FileProvider.getUriForFile(
    context,
    context.packageName + ".file_provider",
    this
)
fun Uri.exists(context: Context) = DocumentFile.fromSingleUri(context, this)?.exists() == true
fun Uri.treeDocumentFile(context: Context) = DocumentFile.fromTreeUri(context, this)


/**
 * 重建为有效的DocumentFile
 */
fun DocumentFile.rebuild(context: Context, dropLast: Boolean = true): DocumentFile? {
    val rootDoc = getRootDoc(context)
    // Log.d(TAG, "rebuild: uri.path: ${uri.path}")
    // Log.d(TAG, "rebuild: rootDoc.path: ${rootDoc.uri.path}")
    val nodeList = uri.path
        ?.replace(rootDoc.uri.path.orEmpty(), "")
        ?.split("/")
        ?.filter { it.isNotBlank() }
        ?.let {
            if (dropLast) it.dropLast(1) else it
        }
    var newDoc: DocumentFile? = rootDoc
    // Log.d(TAG, "rebuild: $nodeList")
    nodeList?.forEach {
        newDoc = newDoc?.findFileIgnoreCase(it)
    }
    return newDoc
}

fun DocumentFile.findFileIgnoreCase(displayName: String): DocumentFile? {
    return runCatching {
        for (doc in listFiles()) {
            if (doc.name != null && displayName.compareTo(doc.name!!, true) == 0) {
                return doc
            }
        }
        null
    }.getOrNull()
}

/**
 * 获取根Doc(授权时选择的文件夹)
 */
fun DocumentFile.getRootDoc(context: Context): DocumentFile {
    val documentUri = this.uri
    val treeId = DocumentsContract.getTreeDocumentId(documentUri)
    val treeUri = DocumentsContract.buildTreeDocumentUri(
        documentUri.authority!!,
        treeId
    )
    return DocumentFile.fromTreeUri(context, treeUri)!!
}

val Context.packageInfo get() = packageManager.getPackageInfo(this.packageName, 0)
val Context.versionName get() = packageInfo.versionName
val Context.versionCode get() = packageInfo.versionCode

val TextLinkStyles.Default get() = TextLinkStyles(
    style = SpanStyle(
        color = Color(0xFF1E88E5), // 普通状态蓝色
        textDecoration = TextDecoration.Underline
    ),
    pressedStyle = SpanStyle(
        color = Color(0xFF1565C0), // 按下时深蓝色
        textDecoration = TextDecoration.Underline
    ),
    hoveredStyle = SpanStyle(
        color = Color(0xFF42A5F5), // 悬停亮蓝色
        textDecoration = TextDecoration.Underline
    ),
    focusedStyle = SpanStyle(
        color = Color(0xFF64B5F6), // 聚焦时浅蓝色
        textDecoration = TextDecoration.Underline
    )
)

