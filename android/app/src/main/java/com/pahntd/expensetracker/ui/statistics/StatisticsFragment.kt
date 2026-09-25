package com.pahntd.expensetracker.ui.statistics

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pahntd.expensetracker.databinding.FragmentStatisticsBinding
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModels()

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
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTimeFilter()
        observeUiState()
        loadDataStatistic()
    }

    private fun setupTimeFilter() {
        val filters = StatisticTimeFilter.entries
        binding.actTimeFilter.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                filters.map { it.label }
            )
        )
        // filter = false: show the label without AutoCompleteTextView narrowing the option list.
        binding.actTimeFilter.setText(savedTimeFilter().label, false)

        binding.actTimeFilter.setOnItemClickListener { _, _, position, _ ->
            val timeFilter = filters[position]
            saveTimeFilter(timeFilter)
            viewModel.loadStatistics(timeFilter)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { _ ->
                    // Chart binding for the state is added in a later checkpoint.
                }
            }
        }
    }

    private fun loadDataStatistic() {
        viewModel.loadStatistics(savedTimeFilter())
    }

    /** Only the filter type is persisted; its date range is recalculated on every load. */
    private fun savedTimeFilter(): StatisticTimeFilter {
        return StatisticTimeFilter.fromName(
            preferences.getString(AppPreferences.KEY_STATISTIC_TIME_FILTER, null)
        )
    }

    private fun saveTimeFilter(timeFilter: StatisticTimeFilter) {
        preferences.edit().putString(
            AppPreferences.KEY_STATISTIC_TIME_FILTER,
            timeFilter.name
        ).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}