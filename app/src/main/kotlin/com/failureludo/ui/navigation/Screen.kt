package com.failureludo.ui.navigation

/** All nav-graph destinations. */
sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Setup    : Screen("setup")
    object Game     : Screen("game")
    object Win      : Screen("win")
}
