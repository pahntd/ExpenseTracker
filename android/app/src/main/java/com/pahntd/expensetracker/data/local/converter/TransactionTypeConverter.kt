package com.pahntd.expensetracker.data.local.converter

import androidx.room.TypeConverter

class TransactionTypeConverter {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }
}