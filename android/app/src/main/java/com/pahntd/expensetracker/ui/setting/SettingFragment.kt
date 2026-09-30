package com.pahntd.expensetracker.ui.setting

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pahntd.expensetracker.ads.AdsConsentManager
import com.pahntd.expensetracker.databinding.FragmentSettingBinding
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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

    @Inject
    lateinit var adsConsentManager: AdsConsentManager

    /** Blocks a second tap from requesting the form again while it is already opening/open. */
    private var isPrivacyOptionsFormShowing = false

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
        binding.tvLogout.setOnClickListener {
            viewModel.onLogoutClick()
        }
        binding.tvPrivacyOptions.setOnClickListener {
            showPrivacyOptionsForm()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read on every resume: the requirement status is only known once the consent info
        // update started from MainActivity has completed, which may be after this view exists.
        updatePrivacyOptionsVisibility()
    }

    private fun updatePrivacyOptionsVisibility() {
        val binding = _binding ?: return
        binding.layoutPrivacy.isVisible = adsConsentManager.isPrivacyOptionsRequired()
    }

    private fun showPrivacyOptionsForm() {
        if (isPrivacyOptionsFormShowing) return
        isPrivacyOptionsFormShowing = true
        adsConsentManager.showPrivacyOptionsForm(requireActivity()) {
            // May arrive after this view is gone, so the view is only touched through _binding.
            isPrivacyOptionsFormShowing = false
            updatePrivacyOptionsVisibility()
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

                        SettingEventState.PendingChangesWarning -> {
                            showLogoutWarningDialog()
                        }

                        SettingEventState.LoggedOut -> {
                            findNavController().navigate(
                                SettingFragmentDirections.actionSettingsFragmentToSplashFragment()
                            )
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

    private fun showLogoutWarningDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout ?")
            .setMessage("Unsynced local changes will be lost.")
            .setNegativeButton("Cancel", null)
            // AlertDialog dismisses itself before invoking the listener, so the dialog is
            // already gone when the sync is enqueued.
            .setNeutralButton("Sync now") { _, _ ->
                viewModel.onSyncNowClick()
            }
            .setPositiveButton("Logout") { _, _ ->
                viewModel.onLogoutConfirmed()
            }
            .show()
    }

//    private fun showAlertDialog() {
//        AlertDialog.Builder(requireContext())
//            .setTitle("Delete All Data ?")
//            .setMessage(
//                "All expenses will be deleted.\n\n" +
//                        "Categories will be reset to the default list."
//            )
//            .setNegativeButton("Cancel", null)
//            .setPositiveButton("Delete") { _, _ ->
//                viewModel.deleteAllData()
//            }
//            .show()
//    }

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