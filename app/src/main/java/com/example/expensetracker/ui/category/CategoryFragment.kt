package com.example.expensetracker.ui.category

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensetracker.databinding.FragmentCategoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
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
        adapter = CategoryRecyclerViewAdapter({
            Log.d(TAG, "setupRecyclerView: Click to detail category")
        })
        binding.rvCategories.adapter = adapter
    }

    private fun setupListener() {
        binding.fabAddCategory.setOnClickListener {
            AddCategoryDialog().show(
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}