package com.pahntd.expensetracker.api

import com.pahntd.expensetracker.model.Transaction

fun Transaction.toResponse(): TransactionResponse {
    return TransactionResponse(
        id = id.toString(),
        amount = amount.toPlainString(),
        type = type.name,
        categoryId = categoryId?.toString(),
        date = date.toString(),
        title = title,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
