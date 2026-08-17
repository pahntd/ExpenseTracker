package com.pahntd.expensetracker.ui.statistics

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
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.databinding.FragmentStatisticsBinding
import com.pahntd.expensetracker.utils.toCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


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
        expenseAdapter = CategoryStatisticsAdapter(TransactionType.EXPENSE)
        incomeAdapter = CategoryStatisticsAdapter(TransactionType.INCOME)

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