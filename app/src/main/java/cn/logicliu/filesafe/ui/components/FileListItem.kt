package cn.logicliu.filesafe.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.service.ThumbnailManager
import cn.logicliu.filesafe.ui.viewmodel.FileViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItemEntity,
    isGridView: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val icon = getFileIcon(file.name)
    val category = getFileCategory(file.name)
    val supportsThumbnail = ThumbnailManager.isThumbnailSupported(file.name)
    var thumbnailUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(file.id) {
        if (supportsThumbnail) {
            if (!ThumbnailManager.hasThumbnail(context, file.id)) {
                ThumbnailManager.ensureThumbnail(context, file)
            }
            if (ThumbnailManager.hasThumbnail(context, file.id)) {
                thumbnailUri = Uri.fromFile(ThumbnailManager.getThumbnailFile(context, file.id))
            }
        }
    }

    if (isGridView) {
        Card(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            }
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSelectionMode) {
                        SelectionIndicator(isSelected = isSelected)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (thumbnailUri != null) {
                        AsyncImage(
                            model = thumbnailUri,
                            contentDescription = category,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = category,
                            modifier = Modifier.size(48.dp),
                            tint = getFileIconTint(category)
                        )
                    }
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
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    SelectionIndicator(isSelected = isSelected)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (thumbnailUri != null) {
                    AsyncImage(
                        model = thumbnailUri,
                        contentDescription = category,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = category,
                        modifier = Modifier.size(40.dp),
                        tint = getFileIconTint(category)
                    )
                }
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
private fun SelectionIndicator(isSelected: Boolean) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
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