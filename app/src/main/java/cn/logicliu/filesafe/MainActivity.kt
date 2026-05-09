package cn.logicliu.filesafe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cn.logicliu.filesafe.ui.navigation.AppNavigation
import cn.logicliu.filesafe.ui.screens.auth.LoginScreen
import cn.logicliu.filesafe.ui.screens.auth.SetupPasswordScreen
import cn.logicliu.filesafe.ui.theme.FileSafeTheme
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private lateinit var authViewModel: AuthViewModel
    
    // 最后活动时间
    private var lastActiveTime: Long = System.currentTimeMillis()
    
    // 屏幕关闭广播接收器
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // 屏幕关闭时，检查设置后锁定
                if (authViewModel.screenOffLockEnabled.value) {
                    authViewModel.lockApp()
                }
            }
        }
    }

    // 广播接收器是否已注册
    private var isReceiverRegistered = false

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
                    // 处理防截屏设置
                    val screenshotEnabled by authViewModel.screenshotEnabled.collectAsState()
                    LaunchedEffect(screenshotEnabled) {
                        updateScreenshotFlag(screenshotEnabled)
                    }
                    
                    // 处理息屏锁定设置
                    val screenOffLockEnabled by authViewModel.screenOffLockEnabled.collectAsState()
                    LaunchedEffect(screenOffLockEnabled) {
                        updateScreenOffReceiver(screenOffLockEnabled)
                    }

                    val isPasswordSet by authViewModel.isPasswordSet.collectAsState()
                    val isUnlocked by authViewModel.isUnlocked.collectAsState()
                    val autoLockTime by authViewModel.autoLockTime.collectAsState()

                    // 处理自动锁定逻辑
                    LaunchedEffect(isUnlocked, autoLockTime) {
                        if (isUnlocked) {
                            // 重置最后活动时间
                            lastActiveTime = System.currentTimeMillis()
                        }
                    }

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

        // 添加生命周期观察者
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                checkAutoLock()
            }

            override fun onPause(owner: LifecycleOwner) {
                lastActiveTime = System.currentTimeMillis()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
        }
    }

    // 动态更新屏幕关闭广播接收器
    private fun updateScreenOffReceiver(enable: Boolean) {
        if (enable && !isReceiverRegistered) {
            // 启用息屏锁定，注册广播
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenOffReceiver, filter)
            isReceiverRegistered = true
        } else if (!enable && isReceiverRegistered) {
            // 禁用息屏锁定，注销广播
            unregisterReceiver(screenOffReceiver)
            isReceiverRegistered = false
        }
    }

    // 更新防截屏标志
    private fun updateScreenshotFlag(enableSecure: Boolean) {
        if (enableSecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // 检查是否需要自动锁定
    private fun checkAutoLock() {
        val autoLockMillis = authViewModel.autoLockTime.value
        if (autoLockMillis > 0) { // 0表示立即锁定
            val timeSinceLastActive = System.currentTimeMillis() - lastActiveTime
            if (timeSinceLastActive >= autoLockMillis) {
                authViewModel.lockApp()
            }
        } else if (authViewModel.isUnlocked.value) {
            // 设置为立即锁定时，回到前台立即锁定
            authViewModel.lockApp()
        }
    }
}
