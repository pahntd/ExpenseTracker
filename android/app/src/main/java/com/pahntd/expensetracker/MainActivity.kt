package com.pahntd.expensetracker

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.pahntd.expensetracker.ads.AdMobInitializer
import com.pahntd.expensetracker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var adMobInitializer: AdMobInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        // Fire-and-forget: consent + SDK init run asynchronously and never gate the app.
        adMobInitializer.start(this)
        setScreenOrientation()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment

        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setOnItemReselectedListener {
            // Do nothing when the current tab is tapped again
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val shouldShow = destination.id in setOf(
                R.id.homeFragment,
                R.id.statisticsFragment,
                R.id.categoryFragment,
                R.id.settingsFragment
            )
            setBottomBarVisible(shouldShow)
        }
    }

    private fun setBottomBarVisible(visible: Boolean) {
        val bottomBar = binding.bottomNavigation
        // Cancel an in-flight slide first: otherwise a hide started on Splash can finish after Home
        // asked to show the bar (its end action is skipped on cancel) and leave the bar GONE.
        bottomBar.animate().cancel()
        if (visible) {
            if (bottomBar.visibility == View.VISIBLE && bottomBar.translationY == 0f) return
            bottomBar.visibility = View.VISIBLE
            bottomBar.animate()
                .translationY(0f)
                .setDuration(150)
                .start()

        } else {
            if (bottomBar.visibility == View.GONE) return
            bottomBar.animate()
                .translationY(bottomBar.height.toFloat())
                .setDuration(150)
                .withEndAction {
                    bottomBar.visibility = View.GONE
                }
                .start()
        }
    }

    private fun setScreenOrientation() {
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}