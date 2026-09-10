package com.pahntd.expensetracker.ui.register

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
import com.pahntd.expensetracker.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
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
                            is RegisterEvent.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            is RegisterEvent.Success -> {
                                // No auto-login: confirm and send the user back to Login.
                                Toast.makeText(
                                    requireContext(),
                                    "Account created. Please log in.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigate(
                                    RegisterFragmentDirections.actionRegisterFragmentToLoginFragment()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: RegisterUiState) {
        binding.tilEmail.error = state.emailError
        binding.tilPassword.error = state.passwordError
        binding.tilConfirmPassword.error = state.confirmPasswordError
        binding.btnRegister.isEnabled = !state.isLoading
    }

    private fun setupInputListeners() {
        binding.etEmail.doAfterTextChanged {
            viewModel.updateEmail(it.toString())
        }
        binding.etPassword.doAfterTextChanged {
            viewModel.updatePassword(it.toString())
        }
        binding.etConfirmPassword.doAfterTextChanged {
            viewModel.updateConfirmPassword(it.toString())
        }
    }

    private fun setupClick() {
        binding.btnRegister.setOnClickListener {
            viewModel.register()
        }
        binding.tvGoToLogin.setOnClickListener {
            findNavController().navigate(
                RegisterFragmentDirections.actionRegisterFragmentToLoginFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
