package cn.logicliu.filesafe.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import cn.logicliu.filesafe.ui.viewmodel.LoginState

@Composable
fun LoginScreen(
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager(context) }
    val securityQuestionManager = remember { SecurityQuestionManager(context) }
    val securitySettingsManager = remember { SecuritySettingsManager(context) }
    val viewModel = remember {
        AuthViewModel(passwordManager, securityQuestionManager, securitySettingsManager)
    }

    val loginState by viewModel.loginState.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = context as? FragmentActivity

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Error) {
            snackbarHostState.showSnackbar((loginState as LoginState.Error).message)
            viewModel.resetLoginState()
        }
    }

    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && activity != null) {
            val biometricHelper = BiometricHelper(context)
            if (biometricHelper.canAuthenticate() == BiometricHelper.BiometricStatus.AVAILABLE) {
                biometricHelper.authenticate(
                    activity = activity,
                    onSuccess = { viewModel.onBiometricSuccess() },
                    onError = { error -> viewModel.onBiometricError(error) },
                    onFailed = { }
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "文件保险箱",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请输入密码解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.verifyPassword(password) }
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is LoginState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.verifyPassword(password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotBlank() && loginState !is LoginState.Loading
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("解锁")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isBiometricEnabled) {
                OutlinedButton(
                    onClick = {
                        activity?.let { fragmentActivity ->
                            val biometricHelper = BiometricHelper(context)
                            biometricHelper.authenticate(
                                activity = fragmentActivity,
                                onSuccess = { viewModel.onBiometricSuccess() },
                                onError = { error -> viewModel.onBiometricError(error) },
                                onFailed = { }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("使用指纹解锁")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            TextButton(onClick = onNavigateToForgotPassword) {
                Text("忘记密码？")
            }
        }
    }
}
