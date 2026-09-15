package com.pahntd.expensetracker.data.remote.mapper

import java.math.BigDecimal

/**
 * Formats a local amount as the plain decimal string the backend expects (it serializes its own
 * BigDecimal amounts via toPlainString()), avoiding scientific notation or raw Double artifacts.
 * Going through BigDecimal.valueOf (backed by Double.toString) rather than the Double directly
 * keeps the shortest decimal representation that round-trips the value.
 */
fun Double.toNetworkAmount(): String {
    return BigDecimal.valueOf(this).toPlainString()
}
