package com.example.expensetracker.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val TAG = "HOME_FRAGMENT"
    private var _binding: FragmentHomeBinding? = null
    private val binding
        get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeUi()
        setupListener()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter { it ->
            Log.e(TAG, "setupRecyclerView: navigate to DETAIL Screen")
        }
        binding.rvTransactions.adapter = adapter
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
    }

    @SuppressLint("SetTextI18n")
    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvIncome.text = state.totalIncome.toCurrency()
                    binding.tvExpense.text = state.totalExpense.toCurrency()
                    binding.tvBalance.text = state.balance.toCurrency()
                    adapter.submitList(state.transactions)
                }
            }
        }
    }

    private fun setupListener() {
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToAddExpenseFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun Double.toCurrency(): String {
        return NumberFormat
            .getNumberInstance(Locale("vi", "VN"))
            .format(this)
    }
}