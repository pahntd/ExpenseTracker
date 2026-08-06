package com.example.expensetracker.ui.add

import android.app.DatePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.expensetracker.R
import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.databinding.FragmentAddExpenseBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddExpenseFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!

    private val addExpenseViewModel: AddExpenseViewModel by viewModels()

    private lateinit var categoryAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoryAdapter()
        observerUi()
        setupListener()
    }

    private fun setupCategoryAdapter() {
        categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        binding.actCategory.setAdapter(categoryAdapter)
    }

    private fun observerUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    addExpenseViewModel.uiState.collect { state ->
                        val names = state.categories.map { it.name }
                        categoryAdapter.clear()
                        categoryAdapter.addAll(names)
                        binding.etDate.setText(
                            state.date.toDateString()
                        )
                    }
                }
                launch {
                    addExpenseViewModel.eventState.collect { event ->
                        when (event) {
                            is AddExpenseEventState.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            AddExpenseEventState.Success -> {
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupListener() {
        binding.etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val millis = calendar.timeInMillis
                    addExpenseViewModel.updateDate(millis)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        binding.etAmount.doAfterTextChanged {
            addExpenseViewModel.updateAmount(it.toString())
        }
        binding.etNote.doAfterTextChanged {
            addExpenseViewModel.updateNote(it.toString())
        }
        binding.rgType.setOnCheckedChangeListener { _, checkId ->
            when (checkId) {
                R.id.rbExpense -> addExpenseViewModel.updateType(TransactionType.EXPENSE)
                R.id.rbIncome -> addExpenseViewModel.updateType(TransactionType.INCOME)
            }
        }
        binding.actCategory.setOnItemClickListener { _, _, position, _ ->
            addExpenseViewModel.updateCategory(position)
        }
        binding.btnSave.setOnClickListener {
            addExpenseViewModel.save()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun Long.toDateString(): String {
        return SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.getDefault()
        ).format(Date(this))
    }
}