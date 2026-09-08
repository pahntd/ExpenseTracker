package com.pahntd.expensetracker

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.pahntd.expensetracker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment

        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val shouldShow = destination.id in setOf(
                R.id.homeFragment,
                R.id.statisticsFragment,
                R.id.categoryFragment,
                R.id.settingsFragment
            )
            setBottomBarVisible(shouldShow)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (navController.currentDestination?.id == item.itemId) {
                return@setOnItemSelectedListener true
            }
            while (navController.popBackStack()) {
                // Clear navigation back stack
            }
            navController.navigate(item.itemId)
            true
        }
    }

    private fun setBottomBarVisible(visible: Boolean) {
        val bottomBar = binding.bottomNavigation
        if (visible) {
            if (bottomBar.visibility == View.VISIBLE) return
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
}