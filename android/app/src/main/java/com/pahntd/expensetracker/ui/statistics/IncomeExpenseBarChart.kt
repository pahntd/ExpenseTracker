package com.pahntd.expensetracker.ui.statistics

import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import com.pahntd.expensetracker.R
import com.pahntd.expensetracker.utils.toCurrency

private const val X_INCOME = 0f
private const val X_EXPENSE = 1f
private const val LABEL_INCOME = "Income"
private const val LABEL_EXPENSE = "Expense"

/** Y axis maximum as a multiple of the larger total, leaving room for its value label. */
private const val TOP_HEADROOM = 1.2

/**
 * Static look of the Income vs Expense chart: no zoom/pan/highlight, no description, one bar per
 * x position (0 = Income, 1 = Expense) and a Y axis that always starts at 0. Call once per view.
 */
fun BarChart.setupIncomeExpenseChart() {
    val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    description.isEnabled = false
    setTouchEnabled(false)
    setScaleEnabled(false)
    setPinchZoom(false)
    isDragEnabled = false
    isDoubleTapToZoomEnabled = false
    setFitBars(true)
    setDrawValueAboveBar(true)

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(false)
        // The legend already names each bar; x labels would only repeat it.
        setDrawLabels(false)
        axisMinimum = -0.5f
        axisMaximum = 1.5f
    }

    axisLeft.apply {
        axisMinimum = 0f
        granularity = 1f
        isGranularityEnabled = true
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toDouble().toCurrency()
        }
        this.textColor = textColor
    }
    axisRight.isEnabled = false

    legend.apply {
        verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        form = Legend.LegendForm.SQUARE
        this.textColor = textColor
    }
}

/**
 * Renders [totalIncome]/[totalExpense] as two bars, each its own data set so the legend shows a
 * colored "Income" and "Expense" entry. The exact Double amount rides along as the entry's data so
 * value labels are not subject to Float rounding of large amounts. Zero totals still render as a
 * flat bar labeled "0"; when both are zero the Y axis is pinned to a small range instead of
 * collapsing.
 */
fun BarChart.renderIncomeExpense(totalIncome: Double, totalExpense: Double) {
    val labelFormatter = object : ValueFormatter() {
        override fun getBarLabel(barEntry: BarEntry): String =
            ((barEntry.data as? Double) ?: barEntry.y.toDouble()).toCurrency()
    }
    val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    fun dataSet(x: Float, amount: Double, label: String, colorRes: Int) =
        BarDataSet(listOf(BarEntry(x, amount.toFloat(), amount)), label).apply {
            color = ContextCompat.getColor(context, colorRes)
            valueFormatter = labelFormatter
            valueTextColor = textColor
            valueTextSize = 11f
            isHighlightEnabled = false
        }

    // Set before the data, since setData() computes the axis range. Explicit rather than spaceTop,
    // which is a percentage of the data's min-max span: with two close totals it leaves almost no
    // headroom for the label above the taller bar.
    val maxAmount = maxOf(totalIncome, totalExpense)
    axisLeft.axisMaximum = if (maxAmount > 0.0) (maxAmount * TOP_HEADROOM).toFloat() else 1f

    data = BarData(
        dataSet(X_INCOME, totalIncome, LABEL_INCOME, R.color.green),
        dataSet(X_EXPENSE, totalExpense, LABEL_EXPENSE, R.color.red)
    ).apply { barWidth = 0.5f }

    invalidate()
}
