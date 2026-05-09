package cn.logicliu.filesafe.ui.screens.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.logicliu.filesafe.ui.screens.player.VideoPlayerScreen
import cn.logicliu.filesafe.ui.screens.player.isVideoFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    file: File,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val fileName = file.name

    val isVideo = isVideoFile(fileName)
    val isImage = isImageFile(fileName)
    val isPdf = fileName.lowercase().endsWith(".pdf")

    when {
        isVideo -> {
            VideoPlayerScreen(
                videoUri = Uri.fromFile(file).toString(),
                videoName = fileName,
                onNavigateBack = onNavigateBack
            )
        }
        isImage -> {
            ImageViewerScreen(
                imageFile = file,
                onNavigateBack = onNavigateBack
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(fileName) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("无法直接预览此文件类型，请使用系统应用打开")
                    androidx.compose.material3.Button(
                        onClick = { openFileWithSystemApp(context, file) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("打开")
                    }
                }
            }
        }
    }
}

private fun isImageFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
}

private fun openFileWithSystemApp(context: Context, file: File) {
    val uri = Uri.fromFile(file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, getMimeType(file.name))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "*/*"
    }
}
