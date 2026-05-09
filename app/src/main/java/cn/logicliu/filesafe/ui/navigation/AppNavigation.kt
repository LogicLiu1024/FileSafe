package cn.logicliu.filesafe.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.logicliu.filesafe.ui.screens.auth.ForgotPasswordScreen
import cn.logicliu.filesafe.ui.screens.auth.LoginScreen
import cn.logicliu.filesafe.ui.screens.auth.SetupPasswordScreen
import cn.logicliu.filesafe.ui.screens.auth.SetupSecurityQuestionsScreen
import cn.logicliu.filesafe.ui.screens.home.FolderScreen
import cn.logicliu.filesafe.ui.screens.home.HomeScreen
import cn.logicliu.filesafe.ui.screens.home.TrashScreen
import cn.logicliu.filesafe.ui.screens.player.VideoPlayerScreen
import cn.logicliu.filesafe.ui.screens.viewer.FileViewerScreen
import cn.logicliu.filesafe.ui.screens.settings.AboutScreen
import cn.logicliu.filesafe.ui.screens.settings.BackupScreen
import cn.logicliu.filesafe.ui.screens.settings.SecuritySettingsScreen
import cn.logicliu.filesafe.ui.screens.settings.SettingsScreen
import cn.logicliu.filesafe.ui.viewmodel.AuthState
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object SetupPassword : Screen("setup_password")
    data object SetupSecurityQuestions : Screen("setup_security_questions")
    data object ForgotPassword : Screen("forgot_password")
    data object Home : Screen("home")
    data object Folder : Screen("folder/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, folderName: String): String {
            val encodedName = URLEncoder.encode(folderName, StandardCharsets.UTF_8.toString())
            return "folder/$folderId/$encodedName"
        }
    }
    data object Trash : Screen("trash")
    data object Settings : Screen("settings")
    data object SecuritySettings : Screen("security_settings")
    data object Backup : Screen("backup")
    data object About : Screen("about")
    data object VideoPlayer : Screen("video_player/{videoUri}/{videoName}") {
        fun createRoute(videoUri: String, videoName: String): String {
            val encodedUri = URLEncoder.encode(videoUri, StandardCharsets.UTF_8.toString())
            val encodedName = URLEncoder.encode(videoName, StandardCharsets.UTF_8.toString())
            return "video_player/$encodedUri/$encodedName"
        }
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    val startDestination = when (authState) {
        AuthState.Loading -> Screen.Login.route
        AuthState.NeedsSetup -> Screen.SetupPassword.route
        AuthState.NeedsSetupSecurityQuestions -> Screen.SetupSecurityQuestions.route
        AuthState.NeedsLogin -> Screen.Login.route
        AuthState.Authenticated -> Screen.Home.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SetupPassword.route) {
            SetupPasswordScreen(
                onPasswordSet = {
                    navController.navigate(Screen.SetupSecurityQuestions.route) {
                        popUpTo(Screen.SetupPassword.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SetupSecurityQuestions.route) {
            SetupSecurityQuestionsScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SetupSecurityQuestions.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPasswordReset = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToTrash = {
                    navController.navigate(Screen.Trash.route)
                },
                onNavigateToFolder = { folderId, folderName ->
                    navController.navigate(Screen.Folder.createRoute(folderId, folderName))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Folder.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
            val folderName = backStackEntry.arguments?.getString("folderName")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: ""

            FolderScreen(
                folderId = folderId,
                folderName = folderName,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSubFolder = { subFolderId, subFolderName ->
                    navController.navigate(Screen.Folder.createRoute(subFolderId, subFolderName))
                }
            )
        }

        composable(Screen.Trash.route) {
            TrashScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSecuritySettings = {
                    navController.navigate(Screen.SecuritySettings.route)
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.Backup.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        composable(Screen.SecuritySettings.route) {
            SecuritySettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Backup.route) {
            BackupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType },
                navArgument("videoName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: ""
            val videoName = backStackEntry.arguments?.getString("videoName")?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } ?: ""

            VideoPlayerScreen(
                videoUri = videoUri,
                videoName = videoName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
