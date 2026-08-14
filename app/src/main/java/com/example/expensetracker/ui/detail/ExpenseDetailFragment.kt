package com.example.expensetracker.ui.detail

import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expensetracker.databinding.FragmentExpenseDetailBinding
import com.example.expensetracker.utils.toCurrency
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
        binding.toolbar.title = "Transaction Detail"
        observeState()
        setupClick()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
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
                launch {
                    viewModel.eventState.collect { event ->
                        when (event) {
                            ExpenseDetailEventState.DeleteSuccess -> {
                                findNavController().popBackStack()
                            }

                            is ExpenseDetailEventState.Error -> {
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
    }

    private fun setupClick() {
        binding.btnEdit.setOnClickListener {
            findNavController().navigate(
                ExpenseDetailFragmentDirections.actionExpenseDetailFragmentToAddExpenseFragment(
                    expenseId = args.expenseId
                )
            )
        }
        binding.btnDelete.setOnClickListener {
            showDeleteDialog()
        }
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete?")
            .setMessage("Are you sure you want to delete this expense?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteExpense()
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}