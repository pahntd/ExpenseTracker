package com.pahntd.expensetracker.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun Double.toCurrency(): String {
    return NumberFormat
        .getNumberInstance(Locale("vi", "VN"))
        .format(this)
}

fun Long.toDateString(): String {
    return SimpleDateFormat(
        "dd/MM/yyyy",
        Locale.getDefault()
    ).format(Date(this))
}

fun Int.dp(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density)
        .roundToInt()
}