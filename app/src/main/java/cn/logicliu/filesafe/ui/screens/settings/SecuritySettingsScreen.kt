package cn.logicliu.filesafe.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cn.logicliu.filesafe.security.BiometricHelper
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import cn.logicliu.filesafe.ui.viewmodel.ChangePasswordState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager(context) }
    val securityQuestionManager = remember { SecurityQuestionManager(context) }
    val securitySettingsManager = remember { SecuritySettingsManager(context) }
    val viewModel = remember {
        AuthViewModel(passwordManager, securityQuestionManager, securitySettingsManager)
    }

    val autoLockTime by viewModel.autoLockTime.collectAsState()
    val screenshotEnabled by viewModel.screenshotEnabled.collectAsState()
    val biometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val screenOffLockEnabled by viewModel.screenOffLockEnabled.collectAsState()

    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val changePasswordState by viewModel.changePasswordState.collectAsState()

    LaunchedEffect(changePasswordState) {
        if (changePasswordState is ChangePasswordState.Success) {
            showChangePasswordDialog = false
            viewModel.resetChangePasswordState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全设置") },
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
                        text = "锁定与隐私",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAutoLockDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动锁定",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = getAutoLockText(autoLockTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setScreenshotEnabled(!screenshotEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenshotMonitor,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "防截屏",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "防止其他应用截取屏幕内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = screenshotEnabled,
                            onCheckedChange = { viewModel.setScreenshotEnabled(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setScreenOffLockEnabled(!screenOffLockEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "息屏锁定",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "屏幕关闭时立即锁定应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = screenOffLockEnabled,
                            onCheckedChange = { viewModel.setScreenOffLockEnabled(it) }
                        )
                    }
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
                        text = "生物识别",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    val biometricHelper = remember { BiometricHelper(context) }
                    val biometricStatus = biometricHelper.canAuthenticate()

                    val biometricAvailable = biometricStatus == BiometricHelper.BiometricStatus.AVAILABLE

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (biometricAvailable) {
                                    Modifier.clickable {
                                        if (!biometricEnabled) {
                                            val activity = context as? FragmentActivity
                                            activity?.let { fragmentActivity ->
                                                biometricHelper.authenticate(
                                                    activity = fragmentActivity,
                                                    title = "启用指纹验证",
                                                    subtitle = "验证您的指纹以启用指纹解锁",
                                                    negativeButtonText = "取消",
                                                    onSuccess = { viewModel.setBiometricEnabled(true) },
                                                    onError = { },
                                                    onFailed = { }
                                                )
                                            }
                                        } else {
                                            viewModel.setBiometricEnabled(false)
                                        }
                                    }
                                } else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (biometricAvailable) 
                                MaterialTheme.colorScheme.onSurfaceVariant 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "指纹解锁",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (biometricAvailable)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                text = when (biometricStatus) {
                                    BiometricHelper.BiometricStatus.AVAILABLE -> "使用指纹快速解锁应用"
                                    BiometricHelper.BiometricStatus.NO_HARDWARE -> "您的设备不支持指纹识别"
                                    BiometricHelper.BiometricStatus.NOT_ENROLLED -> "请在系统设置中录入指纹"
                                    BiometricHelper.BiometricStatus.HARDWARE_UNAVAILABLE -> "指纹硬件不可用"
                                    else -> "指纹功能不可用"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (biometricAvailable)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }

                        if (biometricAvailable) {
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val activity = context as? FragmentActivity
                                        activity?.let { fragmentActivity ->
                                            biometricHelper.authenticate(
                                                activity = fragmentActivity,
                                                title = "启用指纹验证",
                                                subtitle = "验证您的指纹以启用指纹解锁",
                                                negativeButtonText = "取消",
                                                onSuccess = { viewModel.setBiometricEnabled(true) },
                                                onError = { },
                                                onFailed = { }
                                            )
                                        }
                                    } else {
                                        viewModel.setBiometricEnabled(false)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "密码管理",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showChangePasswordDialog = true
                                viewModel.resetChangePasswordState()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "修改密码",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "更改您的登录密码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAutoLockDialog) {
        AutoLockTimeDialog(
            currentTime = autoLockTime,
            onDismiss = { showAutoLockDialog = false },
            onTimeSelected = { time ->
                viewModel.setAutoLockTime(time)
                showAutoLockDialog = false
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            state = changePasswordState,
            onDismiss = {
                showChangePasswordDialog = false
                viewModel.resetChangePasswordState()
            },
            onConfirm = { oldPassword, newPassword ->
                viewModel.changePassword(oldPassword, newPassword)
            }
        )
    }
}

@Composable
private fun AutoLockTimeDialog(
    currentTime: Long,
    onDismiss: () -> Unit,
    onTimeSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动锁定时间") },
        text = {
            Column {
                SecuritySettingsManager.AUTO_LOCK_OPTIONS.forEach { (time, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTimeSelected(time) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTime == time,
                            onClick = { onTimeSelected(time) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun getAutoLockText(timeMillis: Long): String {
    return SecuritySettingsManager.AUTO_LOCK_OPTIONS.find { it.first == timeMillis }?.second
        ?: "5分钟"
}

@Composable
private fun ChangePasswordDialog(
    state: ChangePasswordState,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmNewPasswordVisible by remember { mutableStateOf(false) }

    val newPasswordError = when {
        newPassword.isNotEmpty() && newPassword.length < 6 -> "密码长度至少6位"
        confirmNewPassword.isNotEmpty() && newPassword != confirmNewPassword -> "两次密码不一致"
        else -> null
    }

    val isLoading = state is ChangePasswordState.Loading

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("当前密码") },
                    singleLine = true,
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (currentPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    isError = state is ChangePasswordState.Error && currentPassword.isNotEmpty(),
                    supportingText = {
                        if (state is ChangePasswordState.Error && currentPassword.isNotEmpty()) {
                            Text(state.message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (newPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    isError = newPasswordError != null && newPassword.isNotEmpty(),
                    supportingText = {
                        if (newPasswordError != null && newPassword.isNotEmpty()) {
                            Text(newPasswordError)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = { confirmNewPassword = it },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = if (confirmNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmNewPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (confirmNewPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    isError = newPasswordError != null && confirmNewPassword.isNotEmpty(),
                    supportingText = {
                        if (newPasswordError != null && confirmNewPassword.isNotEmpty()) {
                            Text(newPasswordError)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPassword, newPassword) },
                enabled = !isLoading &&
                        currentPassword.isNotBlank() &&
                        newPassword.isNotBlank() &&
                        confirmNewPassword.isNotBlank() &&
                        newPassword.length >= 6 &&
                        newPassword == confirmNewPassword
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("确认")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}
