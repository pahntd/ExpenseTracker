package com.pahntd.expensetracker.ui.statistics

import android.graphics.DashPathEffect
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import com.pahntd.expensetracker.R
import com.pahntd.expensetracker.utils.toCurrency
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LABEL_INCOME = "Income"
private const val LABEL_EXPENSE = "Expense"

/** Y axis maximum as a multiple of the largest monthly amount, so the top point is not clipped. */
private const val TOP_HEADROOM = 1.2

/** Beyond this many months the x labels are slanted so they do not overlap. */
private const val MAX_UPRIGHT_LABELS = 6

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMM yy", Locale.US)

/**
 * Static look of the monthly trend chart: no zoom/pan/highlight, no description, a Y axis that
 * always starts at 0 and a bottom legend naming both lines. [noDataText] is shown when there is no
 * month to draw. Call once per view.
 */
fun LineChart.setupMonthlyTrendChart(noDataText: String) {
    val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    description.isEnabled = false
    setTouchEnabled(false)
    setScaleEnabled(false)
    setPinchZoom(false)
    isDragEnabled = false
    isDoubleTapToZoomEnabled = false

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(false)
        // One label per month, never in between.
        granularity = 1f
        isGranularityEnabled = true
        setAvoidFirstLastClipping(true)
        this.textColor = textColor
    }

    axisLeft.apply {
        axisMinimum = 0f
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toDouble().toCurrency()
        }
        this.textColor = textColor
    }
    axisRight.isEnabled = false

    legend.apply {
        verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        form = Legend.LegendForm.LINE
        formLineWidth = 3f
        formSize = 16f
        this.textColor = textColor
    }

    setNoDataText(noDataText)
    setNoDataTextColor(textColor)
}

/**
 * Renders [points] (already one per consecutive month, oldest first) as an Income and an Expense
 * line, x = index of the month. Month labels gain a 2-digit year when the months span more than
 * one year, so e.g. January 2025 and January 2026 stay distinguishable. With no points the chart
 * is cleared to show the no-data text given to [setupMonthlyTrendChart].
 */
fun LineChart.renderMonthlyTrend(points: List<MonthlyTrendPoint>) {
    if (points.isEmpty()) {
        clear()
        return
    }

    val spansYears = points.first().yearMonth.year != points.last().yearMonth.year
    val labelFormat = if (spansYears) MONTH_YEAR_FORMAT else MONTH_FORMAT
    val labels = points.map { it.yearMonth.format(labelFormat) }

    xAxis.apply {
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String =
                labels.getOrElse(value.toInt()) { "" }
        }
        // Half a step of padding keeps the first/last point (and a lone single month) off the edges.
        axisMinimum = -0.5f
        axisMaximum = points.size - 0.5f
        setLabelCount(points.size, false)
        labelRotationAngle = if (points.size > MAX_UPRIGHT_LABELS) -45f else 0f
    }

    // Set before the data, since setData() computes the axis range.
    val maxAmount = points.maxOf { maxOf(it.totalIncome, it.totalExpense) }
    axisLeft.axisMaximum = if (maxAmount > 0.0) (maxAmount * TOP_HEADROOM).toFloat() else 1f

    fun dataSet(label: String, colorRes: Int, amount: (MonthlyTrendPoint) -> Double) =
        LineDataSet(
            points.mapIndexed { index, point -> Entry(index.toFloat(), amount(point).toFloat()) },
            label
        ).apply {
            val lineColor = ContextCompat.getColor(context, colorRes)
            color = lineColor
            setCircleColor(lineColor)
            setDrawCircleHole(false)
            circleRadius = 3.5f
            lineWidth = 2f
            // Straight segments: a cubic curve would overshoot below 0 between months.
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
            isHighlightEnabled = false
        }

    data = LineData(
        dataSet(LABEL_INCOME, R.color.green) { it.totalIncome },
        // Dashed so the two lines stay distinguishable without relying on color alone.
        dataSet(LABEL_EXPENSE, R.color.red) { it.totalExpense }.apply {
            enableDashedLine(12f, 6f, 0f)
            formLineDashEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
        }
    )

    invalidate()
}
