package cn.logicliu.filesafe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOperationMenu(
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "文件操作",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OperationItem(
                icon = Icons.Default.Visibility,
                text = "查看",
                onClick = {
                    onView()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.ContentCopy,
                text = "复制",
                onClick = {
                    onCopy()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.DriveFileMove,
                text = "移动",
                onClick = {
                    onMove()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.Edit,
                text = "重命名",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.GetApp,
                text = "导出",
                onClick = {
                    onExport()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.Delete,
                text = "删除",
                onClick = {
                    onDelete()
                    onDismiss()
                },
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOperationMenu(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "文件夹操作",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OperationItem(
                icon = Icons.Default.ContentCopy,
                text = "复制",
                onClick = {
                    onCopy()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.DriveFileMove,
                text = "移动",
                onClick = {
                    onMove()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.Edit,
                text = "重命名",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OperationItem(
                icon = Icons.Default.Delete,
                text = "删除",
                onClick = {
                    onDelete()
                    onDismiss()
                },
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun OperationItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val textColor = if (isDestructive) {
        androidx.compose.material3.MaterialTheme.colorScheme.error
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    }

    val iconColor = if (isDestructive) {
        androidx.compose.material3.MaterialTheme.colorScheme.error
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}
