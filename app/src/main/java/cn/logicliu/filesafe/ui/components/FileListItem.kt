package cn.logicliu.filesafe.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.ui.viewmodel.FileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItemEntity,
    isGridView: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val icon = getFileIcon(file.name)
    val category = getFileCategory(file.name)

    if (isGridView) {
        Card(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    modifier = Modifier.size(48.dp),
                    tint = getFileIconTint(category)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    modifier = Modifier.size(40.dp),
                    tint = getFileIconTint(category)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormat.format(Date(file.modifiedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension in FileViewModel.IMAGE_EXTENSIONS -> Icons.Default.Image
        extension in FileViewModel.VIDEO_EXTENSIONS -> Icons.Default.VideoFile
        extension in setOf("pdf") -> Icons.Default.PictureAsPdf
        extension in setOf("doc", "docx") -> Icons.Default.Description
        extension in setOf("xls", "xlsx") -> Icons.Default.Description
        extension in setOf("ppt", "pptx") -> Icons.Default.Description
        extension in setOf("txt", "md", "json", "xml", "html", "css", "js") -> Icons.Default.Code
        extension in setOf("mp3", "wav", "aac", "flac", "ogg") -> Icons.Default.AudioFile
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileCategory(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension in FileViewModel.IMAGE_EXTENSIONS -> "图片"
        extension in FileViewModel.VIDEO_EXTENSIONS -> "视频"
        extension in FileViewModel.DOCUMENT_EXTENSIONS -> "文档"
        else -> "其他"
    }
}

@Composable
private fun getFileIconTint(category: String): androidx.compose.ui.graphics.Color {
    return when (category) {
        "图片" -> MaterialTheme.colorScheme.primary
        "视频" -> MaterialTheme.colorScheme.secondary
        "文档" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}
