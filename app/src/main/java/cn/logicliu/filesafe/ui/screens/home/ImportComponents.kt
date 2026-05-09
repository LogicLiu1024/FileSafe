package cn.logicliu.filesafe.ui.screens.home

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SnippetFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class FolderImportFile(
    val name: String,
    val uri: Uri,
    val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionsSheet(
    onDismiss: () -> Unit,
    onSelectFiles: () -> Unit,
    onImportFromFolder: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "导入文件",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text("选择文件") },
                supportingContent = { Text("从文件管理器选择一个或多个文件") },
                leadingContent = {
                    Icon(Icons.Default.Description, contentDescription = null)
                },
                modifier = Modifier.selectable(
                    selected = false,
                    onClick = onSelectFiles,
                    role = Role.Button
                )
            )

            ListItem(
                headlineContent = { Text("从文件夹导入") },
                supportingContent = { Text("选择文件夹后可全选文件批量导入") },
                leadingContent = {
                    Icon(Icons.Default.SnippetFolder, contentDescription = null)
                },
                modifier = Modifier.selectable(
                    selected = false,
                    onClick = onImportFromFolder,
                    role = Role.Button
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FolderImportDialog(
    files: List<FolderImportFile>,
    selectedUris: Set<Uri>,
    onSelectionChange: (Set<Uri>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val allSelected = selectedUris.size == files.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择要导入的文件", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onSelectionChange(if (allSelected) emptySet() else files.map { it.uri }.toSet())
                    }
                ) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "已选 ${selectedUris.size}/${files.size} 个文件",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(files, key = { it.uri.toString() }) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = file.uri in selectedUris,
                                    onClick = {
                                        onSelectionChange(
                                            if (file.uri in selectedUris) selectedUris - file.uri
                                            else selectedUris + file.uri
                                        )
                                    },
                                    role = Role.Checkbox
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = file.uri in selectedUris,
                                onCheckedChange = {
                                    onSelectionChange(
                                        if (file.uri in selectedUris) selectedUris - file.uri
                                        else selectedUris + file.uri
                                    )
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedUris.isNotEmpty()
            ) {
                Text("导入 (${selectedUris.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
        else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
    }
}
