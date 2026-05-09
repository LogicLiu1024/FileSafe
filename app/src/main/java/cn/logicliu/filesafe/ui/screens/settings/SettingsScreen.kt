package cn.logicliu.filesafe.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.security.EncryptionAlgorithmType
import cn.logicliu.filesafe.security.EncryptionMode
import cn.logicliu.filesafe.security.ThemeMode
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSecuritySettings: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val themeMode by authViewModel.themeMode.collectAsState()
    val encryptionMode by authViewModel.encryptionMode.collectAsState()
    val encryptionAlgorithm by authViewModel.encryptionAlgorithm.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEncryptionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Text(
                        text = "外观",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = "深色模式",
                        subtitle = when (themeMode) {
                            ThemeMode.LIGHT -> "浅色模式"
                            ThemeMode.DARK -> "深色模式"
                            ThemeMode.SYSTEM -> "跟随系统"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Text(
                        text = "安全与隐私",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    SettingsItem(
                        icon = Icons.Default.EnhancedEncryption,
                        title = "加密方式",
                        subtitle = when (encryptionMode) {
                            EncryptionMode.HIDE -> "隐藏模式（仅重命名）"
                            EncryptionMode.ENCRYPT -> when (encryptionAlgorithm) {
                                EncryptionAlgorithmType.AES_256_GCM -> "加密模式（AES-256-GCM）"
                                EncryptionAlgorithmType.XCHACHA20_POLY1305 -> "加密模式（XChaCha20-Poly1305）"
                            }
                        },
                        onClick = { showEncryptionDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "安全设置",
                        subtitle = "自动锁定、防截屏、指纹解锁",
                        onClick = onNavigateToSecuritySettings
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Text(
                        text = "数据管理",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = "备份与恢复",
                        subtitle = "导出和导入您的数据",
                        onClick = onNavigateToBackup
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Text(
                        text = "其他",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "关于",
                        subtitle = "应用版本和相关信息",
                        onClick = onNavigateToAbout
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onModeSelected = { mode ->
                authViewModel.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showEncryptionDialog) {
        EncryptionModeDialog(
            currentMode = encryptionMode,
            currentAlgorithm = encryptionAlgorithm,
            onDismiss = { showEncryptionDialog = false },
            onModeSelected = { mode, algorithm ->
                authViewModel.setEncryptionMode(mode)
                if (mode == EncryptionMode.ENCRYPT && algorithm != null) {
                    authViewModel.setEncryptionAlgorithm(algorithm)
                }
                showEncryptionDialog = false
            }
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onModeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题模式") },
        text = {
            Column {
                ThemeModeOption(
                    mode = ThemeMode.LIGHT,
                    label = "浅色模式",
                    isSelected = currentMode == ThemeMode.LIGHT,
                    onClick = { onModeSelected(ThemeMode.LIGHT) }
                )
                
                ThemeModeOption(
                    mode = ThemeMode.DARK,
                    label = "深色模式",
                    isSelected = currentMode == ThemeMode.DARK,
                    onClick = { onModeSelected(ThemeMode.DARK) }
                )
                
                ThemeModeOption(
                    mode = ThemeMode.SYSTEM,
                    label = "跟随系统",
                    isSelected = currentMode == ThemeMode.SYSTEM,
                    onClick = { onModeSelected(ThemeMode.SYSTEM) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EncryptionModeDialog(
    currentMode: EncryptionMode,
    currentAlgorithm: EncryptionAlgorithmType,
    onDismiss: () -> Unit,
    onModeSelected: (EncryptionMode, EncryptionAlgorithmType?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedAlgorithm by remember { mutableStateOf(currentAlgorithm) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择加密方式") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            selectedMode = EncryptionMode.HIDE
                            onModeSelected(EncryptionMode.HIDE, null)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == EncryptionMode.HIDE,
                        onClick = { 
                            selectedMode = EncryptionMode.HIDE
                            onModeSelected(EncryptionMode.HIDE, null)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "隐藏模式",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "仅重命名文件，不进行加密处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "加密模式",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Text(
                    text = "使用加密算法保护文件内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                EncryptionAlgorithmOption(
                    algorithm = EncryptionAlgorithmType.AES_256_GCM,
                    selectedAlgorithm = selectedAlgorithm,
                    label = "AES-256-GCM",
                    description = "标准加密算法，兼容性最好",
                    onClick = {
                        selectedAlgorithm = EncryptionAlgorithmType.AES_256_GCM
                        selectedMode = EncryptionMode.ENCRYPT
                        onModeSelected(EncryptionMode.ENCRYPT, EncryptionAlgorithmType.AES_256_GCM)
                    }
                )
                
                EncryptionAlgorithmOption(
                    algorithm = EncryptionAlgorithmType.XCHACHA20_POLY1305,
                    selectedAlgorithm = selectedAlgorithm,
                    label = "XChaCha20-Poly1305",
                    description = "高性能加密算法，适合大文件",
                    onClick = {
                        selectedAlgorithm = EncryptionAlgorithmType.XCHACHA20_POLY1305
                        selectedMode = EncryptionMode.ENCRYPT
                        onModeSelected(EncryptionMode.ENCRYPT, EncryptionAlgorithmType.XCHACHA20_POLY1305)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun EncryptionAlgorithmOption(
    algorithm: EncryptionAlgorithmType,
    selectedAlgorithm: EncryptionAlgorithmType,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedAlgorithm == algorithm,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
