package cn.logicliu.filesafe.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity

enum class FileOperation {
    RENAME,
    DELETE,
    MOVE,
    RESTORE,
    PERMANENT_DELETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOperationDialog(
    file: FileItemEntity?,
    folders: List<FolderEntity>,
    operation: FileOperation,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var newName by remember { mutableStateOf(file?.name ?: "") }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    when (operation) {
        FileOperation.RENAME -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("重命名文件") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新文件名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirm(newName) },
                        enabled = newName.isNotBlank() && newName != file?.name
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("删除文件") },
                text = { Text("确定要将 \"${file?.name}\" 移到回收站吗？") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.PERMANENT_DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("永久删除") },
                text = { Text("确定要永久删除 \"${file?.name}\" 吗？此操作不可恢复！") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.RESTORE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("恢复文件") },
                text = { Text("确定要恢复 \"${file?.name}\" 吗？") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("恢复")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.MOVE -> {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "移动到",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ListItem(
                        headlineContent = { Text("根目录") },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        modifier = Modifier.selectable(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null },
                            role = Role.RadioButton
                        )
                    )

                    folders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            modifier = Modifier.selectable(
                                selected = selectedFolderId == folder.id,
                                onClick = { selectedFolderId = folder.id },
                                role = Role.RadioButton
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { onConfirm(selectedFolderId?.toString()) }
                        ) {
                            Text("移动")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOperationDialog(
    folder: FolderEntity?,
    folders: List<FolderEntity>,
    operation: FileOperation,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var newName by remember { mutableStateOf(folder?.name ?: "") }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    when (operation) {
        FileOperation.RENAME -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("重命名文件夹") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新文件夹名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirm(newName) },
                        enabled = newName.isNotBlank() && newName != folder?.name
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("删除文件夹") },
                text = { Text("确定要将 \"${folder?.name}\" 移到回收站吗？") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.PERMANENT_DELETE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("永久删除") },
                text = { Text("确定要永久删除 \"${folder?.name}\" 吗？此操作不可恢复！") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.RESTORE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("恢复文件夹") },
                text = { Text("确定要恢复 \"${folder?.name}\" 吗？") },
                confirmButton = {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("恢复")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            )
        }

        FileOperation.MOVE -> {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "移动到",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ListItem(
                        headlineContent = { Text("根目录") },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        modifier = Modifier.selectable(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null },
                            role = Role.RadioButton
                        )
                    )

                    folders.forEach { f ->
                        if (f.id != folder?.id) {
                            ListItem(
                                headlineContent = { Text(f.name) },
                                leadingContent = {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                },
                                modifier = Modifier.selectable(
                                    selected = selectedFolderId == f.id,
                                    onClick = { selectedFolderId = f.id },
                                    role = Role.RadioButton
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { onConfirm(selectedFolderId?.toString()) }
                        ) {
                            Text("移动")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("文件夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
