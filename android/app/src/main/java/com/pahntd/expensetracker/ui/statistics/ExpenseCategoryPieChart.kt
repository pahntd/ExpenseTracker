package com.pahntd.expensetracker.ui.statistics

import android.graphics.Color
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import com.pahntd.expensetracker.data.local.relation.CategoryWithAmountSummary
import com.pahntd.expensetracker.databinding.ItemCategoryLegendBinding
import com.pahntd.expensetracker.utils.toCurrency
import java.util.Locale

/**
 * One distinct color per slice, in the DAO's amount-descending order. Categories ranked past the
 * palette share [OTHERS_COLOR] and are merged into a single slice, so many categories never produce
 * repeated colors or unreadably thin slices.
 */
private val SLICE_COLORS = intArrayOf(
    Color.parseColor("#E53935"),
    Color.parseColor("#1E88E5"),
    Color.parseColor("#6D4C41"),
    Color.parseColor("#43A047"),
    Color.parseColor("#8E24AA"),
    Color.parseColor("#FB8C00"),
    Color.parseColor("#00ACC1")
)
private val OTHERS_COLOR = Color.parseColor("#9E9E9E")

/** Slices below this share (in %) get no value label; the legend still lists them. */
private const val MIN_LABELED_PERCENT = 5f

private const val NO_DATA_TEXT = "No expenses in this period"

private fun sliceColor(rank: Int): Int = SLICE_COLORS.getOrElse(rank) { OTHERS_COLOR }

/** Categories that can be drawn: a pie cannot show zero or negative shares. */
private fun List<CategoryWithAmountSummary>.chartable() = filter { it.totalAmount > 0.0 }

/**
 * Static look of the Expense by Category donut: no rotation/highlight, no built-in legend (the
 * category names, amounts and percentages go in the legend rendered by [renderCategoryLegend],
 * where long names can ellipsize), no slice name labels. Call once per view.
 */
fun PieChart.setupExpenseCategoryChart() {
    val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    description.isEnabled = false
    legend.isEnabled = false
    setUsePercentValues(true)
    setDrawEntryLabels(false)
    isRotationEnabled = false
    isHighlightPerTapEnabled = false

    isDrawHoleEnabled = true
    setHoleColor(Color.TRANSPARENT)
    holeRadius = 55f
    transparentCircleRadius = 55f
    setCenterTextColor(textColor)
    setCenterTextSize(14f)

    setNoDataText(NO_DATA_TEXT)
    setNoDataTextColor(textColor)
}

/**
 * Renders the already-grouped [categories] (as returned by the DAO, largest first) as slices, with
 * the total in the hole. With no positive amounts the chart is cleared to show [NO_DATA_TEXT].
 */
fun PieChart.renderExpenseByCategory(categories: List<CategoryWithAmountSummary>) {
    val items = categories.chartable()
    if (items.isEmpty()) {
        clear()
        return
    }

    val top = items.take(SLICE_COLORS.size)
    val rest = items.drop(SLICE_COLORS.size)

    val entries = top.map { PieEntry(it.totalAmount.toFloat(), it.categoryName) }.toMutableList()
    val colors = top.indices.map(::sliceColor).toMutableList()
    if (rest.isNotEmpty()) {
        entries += PieEntry(rest.sumOf { it.totalAmount }.toFloat(), "Others")
        colors += OTHERS_COLOR
    }

    val dataSet = PieDataSet(entries, "").apply {
        this.colors = colors
        // Ignored by the renderer when only one slice is visible, so a single category is a full ring.
        sliceSpace = 2f
        valueTextColor = Color.WHITE
        valueTextSize = 12f
        valueFormatter = object : ValueFormatter() {
            override fun getPieLabel(value: Float, pieEntry: PieEntry?): String =
                if (value < MIN_LABELED_PERCENT) "" else String.format(Locale.US, "%.0f%%", value)
        }
    }

    centerText = "Total\n${items.sumOf { it.totalAmount }.toCurrency()}"
    data = PieData(dataSet)
    invalidate()
}

/**
 * Fills this container with one row per category - color key, name, share and amount - matching
 * the slice colors of [renderExpenseByCategory]. Hidden when there is nothing to show, since the
 * chart already displays its no-data text.
 */
fun LinearLayout.renderCategoryLegend(categories: List<CategoryWithAmountSummary>) {
    removeAllViews()
    val items = categories.chartable()
    isVisible = items.isNotEmpty()
    if (items.isEmpty()) return

    val total = items.sumOf { it.totalAmount }
    val inflater = LayoutInflater.from(context)
    items.forEachIndexed { rank, item ->
        ItemCategoryLegendBinding.inflate(inflater, this, true).apply {
            viewColor.setBackgroundColor(sliceColor(rank))
            tvCategoryName.text = item.categoryName
            tvPercent.text = String.format(Locale.US, "%.1f%%", item.totalAmount / total * 100)
            tvAmount.text = item.totalAmount.toCurrency()
        }
    }
}
