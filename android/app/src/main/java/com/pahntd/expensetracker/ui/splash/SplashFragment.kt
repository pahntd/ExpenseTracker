package com.pahntd.expensetracker.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.pahntd.expensetracker.R
import com.pahntd.expensetracker.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDestination()
        viewModel.start()
    }

    private fun observeDestination() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.destination.collect { destination ->
                    when (destination) {
                        // Still restoring — keep the Splash UI on screen, show nothing else.
                        null -> Unit
                        SplashDestination.Home -> navigateOnce(
                            SplashFragmentDirections.actionSplashFragmentToHomeFragment()
                        )
                        SplashDestination.Login -> navigateOnce(
                            SplashFragmentDirections.actionSplashFragmentToLoginFragment()
                        )
                    }
                }
            }
        }
    }

    private fun navigateOnce(directions: NavDirections) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.splashFragment) {
            navController.navigate(directions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
