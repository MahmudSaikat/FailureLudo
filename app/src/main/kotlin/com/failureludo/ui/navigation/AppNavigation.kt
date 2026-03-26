package com.failureludo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.failureludo.ui.screens.GameBoardScreen
import com.failureludo.ui.screens.GameSetupScreen
import com.failureludo.ui.screens.HomeScreen
import com.failureludo.ui.screens.WinScreen
import com.failureludo.viewmodel.GameViewModel

@Composable
fun AppNavigation(navController: NavHostController) {
    // Single shared ViewModel scoped to the nav graph
    val gameViewModel: GameViewModel = viewModel()
    val gameState by gameViewModel.gameState.collectAsState()
    val isSessionRestored by gameViewModel.isSessionRestored.collectAsState()

    val hasActiveGame = gameState?.let { !it.isGameOver } ?: false

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNewGame   = { navController.navigate(Screen.Setup.route) },
                onResume    = { navController.navigate(Screen.Game.route) },
                hasActiveGame = hasActiveGame,
                isSessionRestored = isSessionRestored
            )
        }

        composable(Screen.Setup.route) {
            GameSetupScreen(
                viewModel = gameViewModel,
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

        composable(Screen.Win.route) {
            WinScreen(
                viewModel   = gameViewModel,
                onPlayAgain = {
                    gameViewModel.replayWithSameSetup()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onMainMenu  = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
