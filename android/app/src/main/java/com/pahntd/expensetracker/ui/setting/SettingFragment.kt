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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.pahntd.expensetracker.R
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
        observeState()
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
        binding.tvDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                    if (state.isAccountDeleted) {
                        navigateToSplashAfterAccountDeleted()
                    }
                }
            }
        }
    }

    private fun renderState(state: SettingUiState) {
        val isBusy = state.isDeletingAccount || state.isAccountDeleted
        binding.tvDeleteAccount.isEnabled = !isBusy
        binding.tvDeleteAccount.alpha = if (isBusy) DISABLED_ALPHA else 1f
        binding.tvDeleteAccount.setText(
            if (state.isDeletingAccount) R.string.settings_deleting_account
            else R.string.settings_delete_account
        )
        binding.tvLogout.isEnabled = !isBusy
        binding.tvLogout.alpha = if (isBusy) DISABLED_ALPHA else 1f
    }

    /**
     * Same destination and back-stack clearing as logout: the action pops the whole graph, and
     * Splash (no session left) forwards to Login while popping itself, so Login ends up alone on
     * the back stack. Guarded so a re-collected state can't navigate twice.
     */
    private fun navigateToSplashAfterAccountDeleted() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.settingsFragment) return
        Toast.makeText(
            requireContext(),
            R.string.settings_account_deleted,
            Toast.LENGTH_SHORT
        ).show()
        navController.navigate(SettingFragmentDirections.actionSettingsFragmentToSplashFragment())
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

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_delete_account_dialog_title)
            .setMessage(R.string.settings_delete_account_dialog_message)
            .setNegativeButton(R.string.settings_delete_account_cancel, null)
            // AlertDialog dismisses itself on click, and the ViewModel ignores the call while a
            // deletion is already running, so this can never start a second request.
            .setPositiveButton(R.string.settings_delete_account_confirm) { _, _ ->
                viewModel.onDeleteAccountConfirmed()
            }
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
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

    private companion object {
        const val DISABLED_ALPHA = 0.5f
    }

}