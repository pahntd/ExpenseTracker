package com.example.expensetracker.ui.statistics

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.R
import com.example.expensetracker.databinding.FragmentStatisticsBinding
import com.example.expensetracker.utils.toCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale


@AndroidEntryPoint
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModels()

    private lateinit var expenseAdapter: CategoryStatisticsAdapter
    private lateinit var incomeAdapter: CategoryStatisticsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeUiState()
        loadDataStatistic()
    }

    private fun setupRecyclerView() {
        expenseAdapter = CategoryStatisticsAdapter()
        incomeAdapter = CategoryStatisticsAdapter()

        binding.rvExpenseByCategory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
            isNestedScrollingEnabled = false
        }

        binding.rvIncomeByCategory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = incomeAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvTotalIncome.text = state.totalIncome.toCurrency()
                    binding.tvTotalExpense.text = state.totalExpense.toCurrency()
                    binding.tvBalance.text = state.balance.toCurrency()

                    expenseAdapter.submitList(state.expenseByCategory)
                    incomeAdapter.submitList(state.incomeByCategory)
                }
            }
        }
    }

    private fun loadDataStatistic() {
        viewModel.loadStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}