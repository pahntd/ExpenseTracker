package com.example.expensetracker.ui.detail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expensetracker.databinding.FragmentExpenseDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ExpenseDetailFragment : Fragment() {

    private var _binding: FragmentExpenseDetailBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: ExpenseDetailViewModel by viewModels()

    private val args: ExpenseDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentExpenseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUiState()
        setupClick()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.expense?.let { expenseWithCategory ->
                        binding.tvAmount.text =
                            expenseWithCategory.expense.amount.toCurrency()
                        binding.tvCategory.text =
                            expenseWithCategory.category.name
                        binding.tvDate.text =
                            SimpleDateFormat(
                                "dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(
                                Date(expenseWithCategory.expense.date)
                            )
                        binding.tvNote.text =
                            expenseWithCategory.expense.note ?: "No note"
                    }
                }
            }
        }
    }

    private fun setupClick() {
        binding.btnEdit.setOnClickListener {
            findNavController().navigate(
                ExpenseDetailFragmentDirections.actionExpenseDetailFragmentToAddExpenseFragment(
                    expenseId = args.expenseId
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun Double.toCurrency(): String {
        return NumberFormat
            .getNumberInstance(Locale("vi", "VN"))
            .format(this)
    }
}