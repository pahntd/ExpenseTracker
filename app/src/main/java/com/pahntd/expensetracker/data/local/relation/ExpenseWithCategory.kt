package com.pahntd.expensetracker.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.ExpenseEntity

data class ExpenseWithCategory(

    @Embedded
    val expense: ExpenseEntity,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity

)