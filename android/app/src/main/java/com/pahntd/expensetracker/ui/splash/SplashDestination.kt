package com.pahntd.expensetracker.ui.splash

/** Where Splash sends the user once startup session restoration has finished. */
sealed interface SplashDestination {
    data object Home : SplashDestination
    data object Login : SplashDestination
}
