package cn.logicliu.filesafe.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import cn.logicliu.filesafe.ui.viewmodel.ForgotPasswordState
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.security.SecuritySettingsManager
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onPasswordReset: () -> Unit
) {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager(context) }
    val securityQuestionManager = remember { SecurityQuestionManager(context) }
    val securitySettingsManager = remember { SecuritySettingsManager(context) }
    val viewModel = remember {
        AuthViewModel(passwordManager, securityQuestionManager, securitySettingsManager)
    }

    val forgotPasswordState by viewModel.forgotPasswordState.collectAsState()
    val securityQuestions by viewModel.securityQuestions.collectAsState()

    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadSecurityQuestions()
    }

    LaunchedEffect(forgotPasswordState) {
        when (forgotPasswordState) {
            is ForgotPasswordState.PasswordResetSuccess -> {
                onPasswordReset()
            }
            is ForgotPasswordState.Error -> {
                snackbarHostState.showSnackbar((forgotPasswordState as ForgotPasswordState.Error).message)
                viewModel.resetForgotPasswordState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("忘记密码") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (forgotPasswordState) {
                is ForgotPasswordState.EnteringQuestion, is ForgotPasswordState.AnswerResult -> {
                    if (securityQuestions.isEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "您尚未设置密保问题",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "请联系应用管理员或重装应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val state = forgotPasswordState
                        val correctCount = if (state is ForgotPasswordState.AnswerResult) state.correctCount else 0

                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "验证密保问题",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "请回答 ${correctCount + 1}/${securityQuestions.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = securityQuestions.getOrNull(currentQuestionIndex)?.question ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("请输入答案") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.verifySecurityAnswer(currentQuestionIndex, answer)
                                if (forgotPasswordState is ForgotPasswordState.AnswerResult) {
                                    val result = forgotPasswordState as ForgotPasswordState.AnswerResult
                                    if (result.correctCount + 1 > currentQuestionIndex) {
                                        currentQuestionIndex++
                                        answer = ""
                                    }
                                } else {
                                    currentQuestionIndex++
                                    answer = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = answer.isNotBlank()
                        ) {
                            Text("验证答案")
                        }
                    }
                }

                is ForgotPasswordState.Verified -> {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "验证成功",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "请设置您的新密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val passwordError = when {
                        newPassword.isNotEmpty() && newPassword.length < 6 -> "密码长度至少6位"
                        confirmPassword.isNotEmpty() && newPassword != confirmPassword -> "两次密码不一致"
                        else -> null
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
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
                        isError = passwordError != null && newPassword.isNotEmpty(),
                        supportingText = {
                            if (passwordError != null && newPassword.isNotEmpty()) {
                                Text(passwordError)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordError != null && confirmPassword.isNotEmpty(),
                        supportingText = {
                            if (passwordError != null && confirmPassword.isNotEmpty()) {
                                Text(passwordError)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.resetPassword(newPassword) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newPassword.isNotBlank() &&
                                confirmPassword.isNotBlank() &&
                                newPassword.length >= 6 &&
                                newPassword == confirmPassword
                    ) {
                        Text("重置密码")
                    }
                }

                is ForgotPasswordState.PasswordResetSuccess -> {
                    CircularProgressIndicator()
                }

                is ForgotPasswordState.Error -> {
                    Text(
                        text = (forgotPasswordState as ForgotPasswordState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
