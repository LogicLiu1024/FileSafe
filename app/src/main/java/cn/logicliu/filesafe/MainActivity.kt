package cn.logicliu.filesafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import cn.logicliu.filesafe.ui.navigation.AppNavigation
import cn.logicliu.filesafe.ui.screens.auth.LoginScreen
import cn.logicliu.filesafe.ui.screens.auth.SetupPasswordScreen
import cn.logicliu.filesafe.ui.theme.FileSafeTheme
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel

class MainActivity : FragmentActivity() {
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authViewModel = AuthViewModel(
            FileSafeApplication.instance.passwordManager,
            FileSafeApplication.instance.securityQuestionManager,
            FileSafeApplication.instance.securitySettingsManager
        )

        setContent {
            FileSafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isPasswordSet by authViewModel.isPasswordSet.collectAsState()
                    val isUnlocked by authViewModel.isUnlocked.collectAsState()

                    when {
                        !isPasswordSet -> {
                            SetupPasswordScreen(
                                onPasswordSet = {
                                    authViewModel.setPasswordSet(true)
                                }
                            )
                        }
                        !isUnlocked -> {
                            LoginScreen(
                                onNavigateToForgotPassword = { },
                                onLoginSuccess = {
                                    authViewModel.unlock()
                                }
                            )
                        }
                        else -> {
                            AppNavigation(
                                authViewModel = authViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
