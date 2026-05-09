package cn.logicliu.filesafe.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestion
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.security.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(
    private val passwordManager: PasswordManager,
    private val securityQuestionManager: SecurityQuestionManager,
    private val securitySettingsManager: SecuritySettingsManager
) : ViewModel() {

    val isPasswordSet: StateFlow<Boolean> = passwordManager.isPasswordSet
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isUnlocked: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isBiometricEnabled: StateFlow<Boolean> = securitySettingsManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoLockTime: StateFlow<Long> = securitySettingsManager.autoLockTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, SecuritySettingsManager.DEFAULT_AUTO_LOCK_TIME)

    val screenshotEnabled: StateFlow<Boolean> = securitySettingsManager.screenshotEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val screenOffLockEnabled: StateFlow<Boolean> = securitySettingsManager.screenOffLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<ThemeMode> = securitySettingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.LIGHT)

    val isSecurityQuestionsSet: StateFlow<Boolean> = securityQuestionManager.securityQuestionsSet
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _forgotPasswordState = MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.EnteringQuestion)
    val forgotPasswordState: StateFlow<ForgotPasswordState> = _forgotPasswordState.asStateFlow()

    private val _securityQuestions = MutableStateFlow<List<SecurityQuestion>>(emptyList())
    val securityQuestions: StateFlow<List<SecurityQuestion>> = _securityQuestions.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            val isSet = isPasswordSet.value
            _authState.value = if (isSet) AuthState.NeedsLogin else AuthState.NeedsSetup
        }
    }

    fun setPasswordSet(set: Boolean) {
        if (set) {
            _authState.value = AuthState.NeedsSetupSecurityQuestions
        }
    }

    fun unlock() {
        isUnlocked.value = true
        _authState.value = AuthState.Authenticated
    }

    fun lock() {
        isUnlocked.value = false
        _authState.value = AuthState.NeedsLogin
        _loginState.value = LoginState.Idle
    }

    fun setupPassword(password: String) {
        viewModelScope.launch {
            try {
                passwordManager.setPassword(password)
                passwordManager.setFirstLaunchComplete()
                _authState.value = AuthState.NeedsSetupSecurityQuestions
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("设置密码失败: ${e.message}")
            }
        }
    }

    fun verifyPassword(password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val isValid = passwordManager.verifyPassword(password)
                if (isValid) {
                    _loginState.value = LoginState.Success
                    _authState.value = AuthState.Authenticated
                    isUnlocked.value = true
                } else {
                    _loginState.value = LoginState.Error("密码错误")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("验证密码失败: ${e.message}")
            }
        }
    }

    fun onBiometricSuccess() {
        _loginState.value = LoginState.Success
        _authState.value = AuthState.Authenticated
        isUnlocked.value = true
    }

    fun onBiometricError(error: String) {
        _loginState.value = LoginState.Error(error)
    }

    fun loadSecurityQuestions() {
        viewModelScope.launch {
            val questions = securityQuestionManager.getSecurityQuestions()
            _securityQuestions.value = questions
        }
    }

    fun verifySecurityAnswer(questionIndex: Int, answer: String) {
        viewModelScope.launch {
            val isValid = securityQuestionManager.verifyAnswer(questionIndex, answer)
            if (isValid) {
                val currentState = _forgotPasswordState.value
                if (currentState is ForgotPasswordState.AnswerResult) {
                    val newCorrectCount = currentState.correctCount + 1
                    if (newCorrectCount >= 2) {
                        _forgotPasswordState.value = ForgotPasswordState.Verified
                    } else {
                        _forgotPasswordState.value = ForgotPasswordState.AnswerResult(newCorrectCount, currentState.totalQuestions)
                    }
                } else {
                    _forgotPasswordState.value = ForgotPasswordState.AnswerResult(1, _securityQuestions.value.size)
                }
            } else {
                _forgotPasswordState.value = ForgotPasswordState.Error("答案错误")
            }
        }
    }

    fun resetPassword(newPassword: String) {
        viewModelScope.launch {
            try {
                passwordManager.setPassword(newPassword)
                _forgotPasswordState.value = ForgotPasswordState.PasswordResetSuccess
            } catch (e: Exception) {
                _forgotPasswordState.value = ForgotPasswordState.Error("重置密码失败: ${e.message}")
            }
        }
    }

    fun saveSecurityQuestions(questions: List<SecurityQuestion>) {
        viewModelScope.launch {
            try {
                securityQuestionManager.saveSecurityQuestions(questions)
                _authState.value = AuthState.Authenticated
                isUnlocked.value = true
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("保存密保问题失败: ${e.message}")
            }
        }
    }

    fun setAutoLockTime(timeMillis: Long) {
        viewModelScope.launch {
            securitySettingsManager.setAutoLockTime(timeMillis)
        }
    }

    fun setScreenshotEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securitySettingsManager.setScreenshotEnabled(enabled)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securitySettingsManager.setBiometricEnabled(enabled)
        }
    }

    fun setScreenOffLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securitySettingsManager.setScreenOffLockEnabled(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            securitySettingsManager.setThemeMode(mode)
        }
    }

    fun resetForgotPasswordState() {
        _forgotPasswordState.value = ForgotPasswordState.EnteringQuestion
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    fun lockApp() {
        isUnlocked.value = false
        _authState.value = AuthState.NeedsLogin
        _loginState.value = LoginState.Idle
    }
}

sealed class AuthState {
    data object Loading : AuthState()
    data object NeedsSetup : AuthState()
    data object NeedsSetupSecurityQuestions : AuthState()
    data object NeedsLogin : AuthState()
    data object Authenticated : AuthState()
}

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class ForgotPasswordState {
    data object EnteringQuestion : ForgotPasswordState()
    data class AnswerResult(val correctCount: Int, val totalQuestions: Int) : ForgotPasswordState()
    data object Verified : ForgotPasswordState()
    data object PasswordResetSuccess : ForgotPasswordState()
    data class Error(val message: String) : ForgotPasswordState()
}
