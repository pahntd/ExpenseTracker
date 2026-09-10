package com.pahntd.expensetracker.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pahntd.expensetracker.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUi()
        setupInputListeners()
        setupClick()
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.eventState.collect { event ->
                        when (event) {
                            is LoginEvent.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            LoginEvent.Success -> Unit // Navigation on success is handled in a later step.
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: LoginUiState) {
        binding.tilEmail.error = state.emailError
        binding.tilPassword.error = state.passwordError
        binding.btnLogin.isEnabled = !state.isLoading
    }

    private fun setupInputListeners() {
        binding.etEmail.doAfterTextChanged {
            viewModel.updateEmail(it.toString())
        }
        binding.etPassword.doAfterTextChanged {
            viewModel.updatePassword(it.toString())
        }
    }

    private fun setupClick() {
        binding.btnLogin.setOnClickListener {
            viewModel.login()
        }
        binding.tvGoToRegister.setOnClickListener {
            findNavController().navigate(
                LoginFragmentDirections.actionLoginFragmentToRegisterFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
