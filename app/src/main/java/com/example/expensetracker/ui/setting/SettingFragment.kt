package com.example.expensetracker.ui.setting

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.expensetracker.databinding.FragmentSettingBinding
import com.example.expensetracker.utils.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding
        get() = _binding!!

    private val preferences by lazy {
        requireContext().getSharedPreferences(
            AppPreferences.PREF_NAME,
            Context.MODE_PRIVATE
        )
    }

    private val viewModel: SettingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDarkMode()
        setupClick()
        observeEvent()
    }

    private fun setupDarkMode() {
        binding.switchDarkMode.isChecked = isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            setDarkMode(isChecked)
        }
    }

    private fun setupClick() {
        binding.btnDeleteAllData.setOnClickListener {
            showAlertDialog()
        }
    }

    private fun observeEvent() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventState.collect { event ->
                    when (event) {
                        SettingEventState.DeleteAllSuccess -> {
                            Toast.makeText(
                                requireContext(),
                                "Delete done !",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is SettingEventState.Error -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun showAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Data ?")
            .setMessage(
                "All expenses will be deleted.\n\n" +
                        "Categories will be reset to the default list."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAllData()
            }
            .show()
    }

    private fun isDarkMode(): Boolean {
        return preferences.getBoolean(
            AppPreferences.KEY_DARK_MODE,
            false
        )
    }

    private fun setDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean(
            AppPreferences.KEY_DARK_MODE,
            enabled
        ).apply()

        AppCompatDelegate.setDefaultNightMode(
            if (enabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}