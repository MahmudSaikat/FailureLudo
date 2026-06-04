package com.failureludo.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.failureludo.ui.screens.AuthScreen
import com.failureludo.ui.screens.GameBoardScreen
import com.failureludo.ui.screens.GameSetupScreen
import com.failureludo.ui.screens.HistoryScreen
import com.failureludo.ui.screens.HomeScreen
import com.failureludo.ui.screens.OnlineLobbyScreen
import com.failureludo.ui.screens.WaitingRoomScreen
import com.failureludo.ui.screens.WinScreen
import com.failureludo.viewmodel.AuthState
import com.failureludo.viewmodel.AuthViewModel
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.OnlineLobbyViewModel
import com.failureludo.viewmodel.WaitingRoomViewModel

@Composable
fun AppNavigation(navController: NavHostController) {
    val gameViewModel: GameViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val onlineLobbyViewModel: OnlineLobbyViewModel = viewModel()
    val waitingRoomViewModel: WaitingRoomViewModel = viewModel()

    val authState by authViewModel.authState.collectAsState()
    val isSessionRestored by gameViewModel.isSessionRestored.collectAsState()
    val historyRecords by gameViewModel.historyRecords.collectAsState()

    val hasActiveGame = gameViewModel.hasActiveGame
    val hasHistoryRecords = historyRecords.isNotEmpty()

    // Determine start destination: skip auth screen if already signed in
    val startDestination = if (authState is AuthState.SignedIn) Screen.Home.route
    else Screen.Auth.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Auth.route) {
            // Navigate to Home once auth succeeds, clearing Auth from the back stack
            LaunchedEffect(authState) {
                if (authState is AuthState.SignedIn) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            }
            AuthScreen(viewModel = authViewModel)
        }

        composable(Screen.Home.route) {
            val profile = (authState as? AuthState.SignedIn)?.profile
            HomeScreen(
                onNewGame     = { navController.navigate(Screen.Setup.route) },
                onResume      = { navController.navigate(Screen.Game.route) },
                onHistory     = { navController.navigate(Screen.History.route) },
                onPlayOnline  = { navController.navigate(Screen.OnlineLobby.route) },
                onSignIn      = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                hasActiveGame     = hasActiveGame,
                hasHistoryRecords = hasHistoryRecords,
                isSessionRestored = isSessionRestored,
                userProfile       = profile
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel  = gameViewModel,
                onBack     = { navController.popBackStack() },
                onOpenGame = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.History.route)
                    }
                }
            )
        }

        composable(Screen.Setup.route) {
            GameSetupScreen(
                viewModel   = gameViewModel,
                onStartGame = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Game.route) {
            GameBoardScreen(
                viewModel  = gameViewModel,
                onGameOver = {
                    navController.navigate(Screen.Win.route) {
                        popUpTo(Screen.Game.route) { inclusive = true }
                    }
                },
                onQuit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.OnlineLobby.route) {
            OnlineLobbyScreen(
                viewModel   = onlineLobbyViewModel,
                onBack      = { navController.popBackStack() },
                onRoomReady = { room ->
                    navController.navigate(Screen.WaitingRoom.route(room.id)) {
                        popUpTo(Screen.OnlineLobby.route)
                    }
                }
            )
        }

        composable(
            route = Screen.WaitingRoom.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            WaitingRoomScreen(
                roomId          = roomId,
                viewModel       = waitingRoomViewModel,
                onGameStarting  = { id ->
                    navController.navigate(Screen.OnlineGame.route(id)) {
                        popUpTo(Screen.WaitingRoom.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.OnlineGame.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) {
            // Phase 4 placeholder
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Online game coming in Phase 4")
            }
        }

        composable(Screen.Win.route) {
            WinScreen(
                viewModel   = gameViewModel,
                onPlayAgain = {
                    gameViewModel.replayWithSameSetup()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
