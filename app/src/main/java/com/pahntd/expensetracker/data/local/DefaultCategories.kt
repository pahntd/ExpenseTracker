package com.pahntd.expensetracker.data.local

import com.pahntd.expensetracker.data.local.entity.CategoryEntity

object DefaultCategories {

    val categories = listOf(

        CategoryEntity(
            name = "Food",
            icon = "ic_food"
        ),

        CategoryEntity(
            name = "Transport",
            icon = "ic_transport"
        ),

        CategoryEntity(
            name = "Shopping",
            icon = "ic_shopping"
        ),

        CategoryEntity(
            name = "Salary",
            icon = "ic_salary"
        ),

        CategoryEntity(
            name = "Entertainment",
            icon = "ic_entertainment"
        ),

        CategoryEntity(
            name = "Education",
            icon = "ic_education"
        ),

        CategoryEntity(
            name = "Health",
            icon = "ic_health"
        ),

        CategoryEntity(
            name = "Other",
            icon = "ic_other"
        )
    )
}