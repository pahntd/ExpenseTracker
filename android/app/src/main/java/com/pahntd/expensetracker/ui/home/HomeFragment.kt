package com.pahntd.expensetracker.ui.home

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pahntd.expensetracker.R
import com.pahntd.expensetracker.databinding.FragmentHomeBinding
import com.pahntd.expensetracker.utils.dp
import com.pahntd.expensetracker.utils.toCurrency
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        setupSearchView(requireContext())
        observeUi()
        setupListener()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(onClickItem = {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToExpenseDetailFragment(
                    it.expense.id
                )
            )
        })
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

    private fun setupSearchView(context: Context) {
        val container = binding.searchViewContainer
        val searchButton = binding.btnSearch
        val searchView = binding.searchView

        searchButton.visibility = View.VISIBLE
        searchView.visibility = View.GONE

        setColorHintSearchView(R.color.blur_text_color)

        searchButton.setOnClickListener {
            val startWidth = container.width
            val endWidth = binding.root.width - 40.dp(context)
            animationExpandSearch(startWidth, endWidth, context)
        }

        searchView.setOnCloseListener {
            searchButton.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
            )

            val startWidth = container.width
            val endWidth = searchButton.measuredWidth
            animationCollapseSearch(startWidth, endWidth, context)
            false
        }
    }

    private fun setColorHintSearchView(idColor: Int) {
        val searchView = binding.searchView
        val searchEditText =
            searchView.findViewById<AutoCompleteTextView>(
                androidx.appcompat.R.id.search_src_text
            )
        val searchIcon =
            binding.searchView.findViewById<ImageView>(
                androidx.appcompat.R.id.search_mag_icon
            )
        val closeButton =
            binding.searchView.findViewById<ImageView>(
                androidx.appcompat.R.id.search_close_btn
            )
        searchEditText.setHintTextColor(
            ContextCompat.getColor(requireContext(), idColor)
        )
        searchEditText.setTextColor(
            ContextCompat.getColor(requireContext(), idColor)
        )
        searchIcon.imageTintList =
            ContextCompat.getColorStateList(
                requireContext(),
                idColor
            )
        closeButton.imageTintList =
            ContextCompat.getColorStateList(
                requireContext(),
                idColor
            )
    }

    private fun animationExpandSearch(startWidth: Int, endWidth: Int, context: Context) {
        val container = binding.searchViewContainer
        val searchView = binding.searchView
        val btnSearch = binding.btnSearch

        ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = 150L
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                container.layoutParams =
                    container.layoutParams.apply {
                        this.width = width
                    }
                container.requestLayout()
            }

            doOnStart {
                btnSearch.visibility = View.GONE
                searchView.visibility = View.VISIBLE
                searchView.isIconified = false
            }

            doOnEnd {
                val searchEditText =
                    searchView.findViewById<AutoCompleteTextView>(
                        androidx.appcompat.R.id.search_src_text
                    )
                searchEditText.requestFocus()
                searchEditText.post {
                    val imm =
                        context.getSystemService<InputMethodManager>()
                    imm?.showSoftInput(
                        searchEditText,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
            }
            start()
        }
    }

    private fun animationCollapseSearch(startWidth: Int, endWidth: Int, context: Context) {
        val container = binding.searchViewContainer
        val searchView = binding.searchView
        val btnSearch = binding.btnSearch
        ValueAnimator.ofInt(startWidth, endWidth).apply {
            duration = 150L
            addUpdateListener { animator ->
                val width =
                    animator.animatedValue as Int
                container.layoutParams =
                    container.layoutParams.apply {
                        this.width = width
                    }
                container.requestLayout()
            }

            doOnStart {
                searchView.clearFocus()
                val imm = context.getSystemService<InputMethodManager>()
                imm?.hideSoftInputFromWindow(
                    searchView.windowToken,
                    0
                )
            }

            doOnEnd {
                searchView.visibility = View.GONE
                btnSearch.visibility = View.VISIBLE

                container.layoutParams =
                    container.layoutParams.apply {
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }

                container.requestLayout()
            }
            start()
        }
    }

    private fun setupListener() {
        binding.fabAdd.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToAddExpenseFragment()
            )
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}