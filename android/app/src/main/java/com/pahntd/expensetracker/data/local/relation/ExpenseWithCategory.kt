package com.pahntd.expensetracker.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity

data class ExpenseWithCategory(

    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity

)