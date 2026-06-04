package com.failureludo.ui.navigation

/** All nav-graph destinations. */
sealed class Screen(val route: String) {
    object Auth     : Screen("auth")
    object Home     : Screen("home")
    object History  : Screen("history")
    object Setup    : Screen("setup")
    object Game     : Screen("game")
    object Win      : Screen("win")
}
