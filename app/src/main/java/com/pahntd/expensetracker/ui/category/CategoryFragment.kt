package com.pahntd.expensetracker.ui.category

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.pahntd.expensetracker.databinding.FragmentCategoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoryFragment : Fragment() {
    private val TAG = "CATEGORY_FRAGMENT"
    private var _binding: FragmentCategoryBinding? = null
    private val binding
        get() = _binding!!

    private val categoryViewModel: CategoryViewModel by viewModels()
    private lateinit var adapter: CategoryRecyclerViewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListener()
        observeUI()
    }

    private fun setupRecyclerView() {
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        adapter = CategoryRecyclerViewAdapter(
            onClickEdit = { item ->
                AddCategoryDialog.newInstance(item).show(
                    childFragmentManager,
                    "AddCategoryDialog"
                )
            },
            onClickDelete = { item ->
                deleteCategory(item)
            }
        )
        binding.rvCategories.adapter = adapter
    }

    private fun setupListener() {
        binding.fabAddCategory.setOnClickListener {
            AddCategoryDialog.newInstance().show(
                childFragmentManager,
                "AddCategoryDialog"
            )
        }
    }

    private fun observeUI() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoryViewModel.categories.collect {
                    adapter.submitList(it)
                }
            }
        }
    }

    private fun deleteCategory(category: CategoryWithExpenseCount) {
        if (category.expenseCount > 0) {
            Toast.makeText(
                requireContext(),
                "This category is in use and cannot be deleted !",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showDeleteDialog(category)
    }

    private fun showDeleteDialog(category: CategoryWithExpenseCount) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category?")
            .setMessage("Are you sure you want to delete \"${category.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                categoryViewModel.deleteCategoryById(category.categoryId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}