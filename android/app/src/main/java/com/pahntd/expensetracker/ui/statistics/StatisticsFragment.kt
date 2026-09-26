package com.pahntd.expensetracker.ui.statistics

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pahntd.expensetracker.ads.BannerAdManager
import com.pahntd.expensetracker.ads.FeatureUnlockManager
import com.pahntd.expensetracker.ads.LockedFeature
import com.pahntd.expensetracker.databinding.FragmentStatisticsBinding
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class StatisticsFragment : Fragment() {

    @Inject
    lateinit var bannerAdManager: BannerAdManager

    @Inject
    lateinit var featureUnlockManager: FeatureUnlockManager

    /** Bumped after a successful unlock so the lock-state loop re-renders and reschedules at once. */
    private val lockStateRefresh = MutableStateFlow(0)

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
        setupChart()
        observeUiState()
        setupFeatureLocks()
        loadDataStatistic()
        setupBannerAds()
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

    private fun setupChart() {
        binding.barChartIncomeExpense.setupIncomeExpenseChart()
        binding.pieChartExpenseByCategory.setupCategoryPieChart("No expenses in this period")
        binding.pieChartIncomeByCategory.setupCategoryPieChart("No income in this period")
        binding.lineChartMonthlyTrend.setupMonthlyTrendChart("No transactions in the last 12 months")
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.barChartIncomeExpense.renderIncomeExpense(
                        totalIncome = state.totalIncome,
                        totalExpense = state.totalExpense
                    )
                    binding.pieChartExpenseByCategory.renderByCategory(state.expenseByCategory)
                    binding.layoutExpenseLegend.renderCategoryLegend(state.expenseByCategory)
                    binding.pieChartIncomeByCategory.renderByCategory(state.incomeByCategory)
                    binding.layoutIncomeLegend.renderCategoryLegend(state.incomeByCategory)
                    binding.lineChartMonthlyTrend.renderMonthlyTrend(state.monthlyTrend)
                }
            }
        }
    }

    /**
     * Rewarded-unlock gating for the premium cards. Independent of [viewModel]: the charts are
     * always rendered from Room data and only their visibility depends on unlock state, so an ad
     * failure (e.g. offline) can never affect what the free cards show.
     */
    private fun setupFeatureLocks() {
        binding.btnUnlockIncomeByCategory.setOnClickListener {
            requestUnlock(LockedFeature.INCOME_BY_CATEGORY)
        }
        binding.btnUnlockMonthlyTrend.setOnClickListener {
            requestUnlock(LockedFeature.MONTHLY_TREND)
        }

        // Re-render on start, after each unlock, and again when the nearest unlock expires.
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                lockStateRefresh.collectLatest {
                    while (true) {
                        renderLockState()
                        val nextExpiry = LockedFeature.entries
                            .map { featureUnlockManager.remainingUnlockMillis(it) }
                            .filter { it > 0 }
                            .minOrNull() ?: break
                        delay(nextExpiry)
                    }
                }
            }
        }
    }

    private fun renderLockState() {
        val binding = _binding ?: return
        val incomeUnlocked = featureUnlockManager.isUnlocked(LockedFeature.INCOME_BY_CATEGORY)
        binding.layoutIncomeByCategoryContent.visibility = if (incomeUnlocked) View.VISIBLE else View.GONE
        binding.layoutIncomeByCategoryLocked.visibility = if (incomeUnlocked) View.GONE else View.VISIBLE

        val trendUnlocked = featureUnlockManager.isUnlocked(LockedFeature.MONTHLY_TREND)
        binding.lineChartMonthlyTrend.visibility = if (trendUnlocked) View.VISIBLE else View.GONE
        binding.layoutMonthlyTrendLocked.visibility = if (trendUnlocked) View.GONE else View.VISIBLE
    }

    /**
     * Unlocks only [feature], and only from the reward callback. Callbacks may arrive after the
     * view is gone (the ad is full screen), so they touch the view only through [_binding].
     */
    private fun requestUnlock(feature: LockedFeature) {
        if (featureUnlockManager.isUnlocked(feature)) {
            lockStateRefresh.value++
            return
        }
        // An ad from an earlier request is still up (e.g. this view was recreated meanwhile);
        // its own callbacks restore the CTAs, and a second request would get no callback at all.
        if (featureUnlockManager.isUnlockInProgress) return

        // One rewarded ad at a time: block both CTAs until this show resolves.
        setUnlockButtonsEnabled(false)
        featureUnlockManager.requestUnlock(
            activity = requireActivity(),
            feature = feature,
            onUnlocked = {
                setUnlockButtonsEnabled(true)
                lockStateRefresh.value++
            },
            onAdUnavailable = {
                setUnlockButtonsEnabled(true)
                showToast("Ad isn't available right now. Please try again later.")
            },
            onDismissedWithoutReward = {
                setUnlockButtonsEnabled(true)
                showToast("Watch the full ad to unlock this feature.")
            }
        )
    }

    private fun setUnlockButtonsEnabled(enabled: Boolean) {
        val binding = _binding ?: return
        // The Login-style background has no disabled state, so dim the whole button instead.
        val alpha = if (enabled) 1f else DISABLED_CTA_ALPHA
        listOf(binding.btnUnlockIncomeByCategory, binding.btnUnlockMonthlyTrend).forEach {
            it.isEnabled = enabled
            it.alpha = alpha
        }
    }

    private fun showToast(message: String) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun loadDataStatistic() {
        viewModel.loadStatistics(savedTimeFilter())
    }

    private fun setupBannerAds(){
        bannerAdManager.bind(binding.adContainer, viewLifecycleOwner)
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

    private companion object {
        const val DISABLED_CTA_ALPHA = 0.5f
    }
}