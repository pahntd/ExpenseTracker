package com.example.expensetracker.data.local

import com.example.expensetracker.R

object IconMapper {
    fun getDrawable(icon: String): Int {
        return when (icon) {
            "ic_food" -> R.drawable.ic_food

            "ic_transport" -> R.drawable.ic_transport

            "ic_shopping" -> R.drawable.ic_shopping

            "ic_salary" -> R.drawable.ic_salary

            "ic_entertainment" -> R.drawable.ic_entertainment

            "ic_education" -> R.drawable.ic_education

            "ic_health" -> R.drawable.ic_health

            "ic_air_conditioner" -> R.drawable.ic_air_conditioner

            "ic_bath_outdoor" -> R.drawable.ic_bath_outdoor

            "ic_door" -> R.drawable.ic_door

            "ic_hiking" -> R.drawable.ic_hiking

            "ic_remote" -> R.drawable.ic_remote

            "icon_flower" -> R.drawable.icon_flower

            "icon_grass" -> R.drawable.icon_grass

            else -> R.drawable.ic_other
        }
    }
}