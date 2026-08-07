package com.example.expensetracker.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.data.local.entity.ExpenseEntity

data class ExpenseWithCategory(

    @Embedded
    val expense: ExpenseEntity,

    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity

)