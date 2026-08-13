package com.example.expensetracker.ui.setting

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.example.expensetracker.R
import com.example.expensetracker.databinding.FragmentSettingBinding
import com.example.expensetracker.utils.AppPreferences

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
    }

    private fun setupDarkMode() {
        binding.switchDarkMode.isChecked = isDarkMode()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            setDarkMode(isChecked)
        }
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