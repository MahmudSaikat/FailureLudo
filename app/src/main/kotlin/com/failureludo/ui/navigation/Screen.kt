package com.failureludo.ui.navigation

/** All nav-graph destinations. */
sealed class Screen(val route: String) {
    object Auth        : Screen("auth")
    object Home        : Screen("home")
    object History     : Screen("history")
    object Setup       : Screen("setup")
    object Game        : Screen("game")
    object Win         : Screen("win")
    object OnlineLobby : Screen("online_lobby")
    object WaitingRoom : Screen("waiting_room/{roomId}") {
        fun route(roomId: String) = "waiting_room/$roomId"
    }
    object OnlineGame  : Screen("online_game/{roomId}") {
        fun route(roomId: String) = "online_game/$roomId"
    }
}
